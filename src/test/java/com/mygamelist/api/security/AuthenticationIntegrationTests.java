package com.mygamelist.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mygamelist.api.repository.UserAccountRepository;
import com.mygamelist.api.service.EmailRegistrationService;
import com.mygamelist.api.service.RegistrationVerificationStore;
import com.mygamelist.api.service.ResendEmailSender;
import com.mygamelist.api.service.exception.RegistrationCooldownException;
import com.mygamelist.api.service.exception.RegistrationUnavailableException;
import com.mygamelist.api.service.exception.VerificationCodeException;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthenticationIntegrationTests {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

  @Autowired private MockMvc mockMvc;
  @MockitoBean private ResendEmailSender sender;
  private String confirmationCode;
  @Autowired private EmailRegistrationService registration;
  @Autowired private RegistrationVerificationStore verificationStore;

  @Autowired private UserAccountRepository userAccountRepository;
  @Autowired private JdbcClient jdbc;

  @BeforeEach
  void cleanDatabase() {
    jdbc.sql("DELETE FROM registration_rate_limits").update();
    userAccountRepository.deleteAll();
    doAnswer(
            invocation -> {
              confirmationCode = invocation.getArgument(1);
              return null;
            })
        .when(sender)
        .sendVerification(anyString(), anyString());
  }

  @Test
  void locksConfirmationAfterFiveIncorrectCodes() {
    registration.register("player", "player@example.com", "password123");
    String correct = confirmationCode;
    String wrong = correct.equals("00000000") ? "11111111" : "00000000";
    for (int attempt = 0; attempt < 5; attempt++) {
      assertThatThrownBy(() -> registration.confirm("player@example.com", wrong))
          .isInstanceOf(VerificationCodeException.class);
    }
    assertThatThrownBy(() -> registration.confirm("player@example.com", correct))
        .isInstanceOf(VerificationCodeException.class);
    var user = userAccountRepository.findByEmail("player@example.com").orElseThrow();
    assertThat(user.isEmailVerified()).isFalse();
    assertThat(user.getVerificationAttempts()).isEqualTo(5);
    assertThat(user.getVerificationCodeHash()).doesNotContain(correct);
  }

  @Test
  void expiresCodesAndDeletesAbandonedRegistrations() {
    registration.register("player", "player@example.com", "password123");
    var user = userAccountRepository.findByEmail("player@example.com").orElseThrow();
    ReflectionTestUtils.setField(user, "verificationExpiresAt", Instant.now().minusSeconds(90000));
    userAccountRepository.save(user);
    assertThatThrownBy(() -> registration.confirm("player@example.com", confirmationCode))
        .isInstanceOf(VerificationCodeException.class);
    verificationStore.removeExpiredRegistrations();
    assertThat(userAccountRepository.findByEmail("player@example.com")).isEmpty();
  }

  @Test
  void rateLimitsResendsAndInvalidatesPreviousCodes() {
    registration.register("player", "player@example.com", "password123");
    String previous = confirmationCode;
    assertThatThrownBy(() -> registration.resend("player@example.com", "password123"))
        .isInstanceOf(RegistrationCooldownException.class);
    var user = userAccountRepository.findByEmail("player@example.com").orElseThrow();
    ReflectionTestUtils.setField(user, "verificationLastSentAt", Instant.now().minusSeconds(61));
    userAccountRepository.save(user);
    registration.resend("player@example.com", "password123");
    assertThatThrownBy(() -> registration.confirm("player@example.com", previous))
        .isInstanceOf(VerificationCodeException.class);
    registration.confirm("player@example.com", confirmationCode);
    assertThatThrownBy(() -> registration.confirm("player@example.com", confirmationCode))
        .isInstanceOf(VerificationCodeException.class);
    assertThat(
            userAccountRepository.findByEmail("player@example.com").orElseThrow().isEmailVerified())
        .isTrue();
  }

  @Test
  void keepsAccountPendingWhenEmailProviderFails() {
    doThrow(new RegistrationUnavailableException())
        .when(sender)
        .sendVerification(anyString(), anyString());
    var result = registration.register("player", "player@example.com", "password123");
    assertThat(result.emailSent()).isFalse();
    assertThat(
            userAccountRepository.findByEmail("player@example.com").orElseThrow().isEmailVerified())
        .isFalse();
  }

  @Test
  void registersLogsInUsesSessionAndLogsOut() throws Exception {
    CsrfCredentials csrf = obtainCsrf(null);
    doAnswer(
            invocation -> {
              confirmationCode = invocation.getArgument(1);
              return null;
            })
        .when(sender)
        .sendVerification(anyString(), anyString());

    mockMvc
        .perform(
            withCsrf(
                post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                            {
                              "username": "PlayerOne",
                              "email": "player@example.com",
                              "password": "plain password"
                            }
                            """),
                csrf))
        .andExpect(status().isAccepted());

    mockMvc.perform(get("/api/users/playerone")).andExpect(status().isNotFound());
    mockMvc.perform(get("/api/users/playerone/games")).andExpect(status().isNotFound());

    mockMvc
        .perform(
            withCsrf(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"player@example.com\",\"password\":\"plain password\"}"),
                csrf))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            withCsrf(
                post("/api/users/verify-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"email\":\"player@example.com\",\"code\":\"" + confirmationCode + "\"}"),
                csrf))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            withCsrf(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                            {
                              "email": "player@example.com",
                              "password": "wrong password"
                            }
                            """),
                csrf))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.detail").value("Invalid email or password"));

    MockHttpSession sessionBeforeLogin = new MockHttpSession();
    String sessionIdBeforeLogin = sessionBeforeLogin.getId();
    MvcResult loginResult =
        mockMvc
            .perform(
                withCsrf(
                    post("/api/auth/login")
                        .session(sessionBeforeLogin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                                {
                                  "email": "PLAYER@example.com",
                                  "password": "plain password"
                                }
                                """),
                    csrf))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("playerone"))
            .andExpect(cookie().maxAge("XSRF-TOKEN", 0))
            .andReturn();

    MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
    assertThat(session).isNotNull();
    assertThat(session.getId()).isNotEqualTo(sessionIdBeforeLogin);

    mockMvc
        .perform(get("/api/auth/me").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("playerone"))
        .andExpect(jsonPath("$.email").doesNotExist())
        .andExpect(jsonPath("$.passwordHash").doesNotExist());

    CsrfCredentials renewedCsrf = obtainCsrf(session);
    mockMvc
        .perform(withCsrf(post("/api/auth/logout").session(session), renewedCsrf))
        .andExpect(status().isNoContent());

    assertThat(session.isInvalid()).isTrue();
    mockMvc
        .perform(get("/api/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Authentication required"));
  }

  @Test
  void rejectsStateChangingRequestWithoutCsrfToken() throws Exception {
    mockMvc
        .perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "username": "playerone",
                      "email": "player@example.com",
                      "password": "plain password"
                    }
                    """))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Access denied"));

    assertThat(userAccountRepository.count()).isZero();
  }

  private CsrfCredentials obtainCsrf(MockHttpSession session) throws Exception {
    MockHttpServletRequestBuilder request = get("/api/auth/csrf");
    if (session != null) {
      request.session(session);
    }

    MvcResult result =
        mockMvc
            .perform(request)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(cookie().exists("XSRF-TOKEN"))
            .andReturn();

    return new CsrfCredentials(result.getResponse().getCookie("XSRF-TOKEN"));
  }

  private static MockHttpServletRequestBuilder withCsrf(
      MockHttpServletRequestBuilder request, CsrfCredentials csrf) {
    return request.cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.cookie().getValue());
  }

  private record CsrfCredentials(Cookie cookie) {}
}
