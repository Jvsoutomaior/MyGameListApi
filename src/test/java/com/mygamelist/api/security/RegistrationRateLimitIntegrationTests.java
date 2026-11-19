package com.mygamelist.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mygamelist.api.config.RegistrationRateLimitProperties;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(
    properties = {
      "app.registration.invite-code=test-invitation",
      "app.registration.rate-limit.requests-per-ip=2",
      "app.registration.rate-limit.global-requests=4",
      "app.registration.rate-limit.window-seconds=60"
    })
@AutoConfigureMockMvc
@Testcontainers
class RegistrationRateLimitIntegrationTests {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcClient jdbc;
  @Autowired private RegistrationRateLimiter rateLimiter;
  @Autowired private RegistrationRateLimitProperties properties;
  @Autowired private Clock clock;

  @BeforeEach
  void resetLimits() {
    jdbc.sql("DELETE FROM registration_rate_limits").update();
  }

  @Test
  void countsFailedRegistrationsAndIgnoresSpoofedForwardedHeaders() throws Exception {
    mockMvc.perform(registration("192.0.2.1")).andExpect(status().isBadRequest());
    mockMvc.perform(registration("192.0.2.1")).andExpect(status().isBadRequest());
    mockMvc
        .perform(registration("192.0.2.1").header("X-Forwarded-For", "192.0.2.99"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.title").value("Too many registration attempts"));

    mockMvc.perform(registration("192.0.2.2")).andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/games").param("query", "Celeste")).andExpect(status().isOk());
    mockMvc.perform(post("/api/users")).andExpect(status().isForbidden());
  }

  @Test
  void enforcesGlobalLimitAcrossAddressesAndKeepsStorageBounded() {
    for (int address = 1; address <= 4; address++) {
      assertThat(rateLimiter.retryAfterSeconds("192.0.2." + address)).isZero();
    }
    assertThat(rateLimiter.retryAfterSeconds("192.0.2.5")).isBetween(1L, 60L);
    assertThat(jdbc.sql("SELECT COUNT(*) FROM registration_rate_limits").query(Long.class).single())
        .isEqualTo(5L);
  }

  @Test
  void retainsCountersAcrossLimiterInstancesAndReleasesExpiredLimits() {
    assertThat(rateLimiter.retryAfterSeconds("192.0.2.1")).isZero();
    assertThat(rateLimiter.retryAfterSeconds("192.0.2.1")).isZero();
    RegistrationRateLimiter restarted = new RegistrationRateLimiter(jdbc, clock, properties);
    assertThat(restarted.retryAfterSeconds("192.0.2.1")).isBetween(1L, 60L);

    jdbc.sql("UPDATE registration_rate_limits SET expires_at = :past")
        .param("past", Timestamp.from(Instant.now().minusSeconds(1)))
        .update();
    assertThat(restarted.retryAfterSeconds("192.0.2.1")).isZero();
    assertThat(
            jdbc.sql(
                    "SELECT attempts FROM registration_rate_limits WHERE bucket_key = 'ip:192.0.2.1'")
                .query(Integer.class)
                .single())
        .isEqualTo(1);
  }

  @Test
  void handlesConcurrentRequestsWithoutExceedingTheLimit() throws Exception {
    List<Callable<Long>> requests =
        IntStream.range(0, 10)
            .mapToObj(index -> (Callable<Long>) () -> rateLimiter.retryAfterSeconds("192.0.2.1"))
            .toList();
    int allowed = 0;
    try (var executor = Executors.newFixedThreadPool(5)) {
      for (var result : executor.invokeAll(requests)) {
        if (result.get() == 0) {
          allowed++;
        }
      }
    }
    assertThat(allowed).isEqualTo(2);
  }

  private static MockHttpServletRequestBuilder registration(String address) {
    return post("/api/users")
        .with(csrf())
        .with(
            request -> {
              request.setRemoteAddr(address);
              return request;
            })
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {
              "username": "player",
              "email": "player@example.com",
              "password": "password123",
              "invitationCode": "wrong-invitation"
            }
            """);
  }
}
