package com.mygamelist.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mygamelist.api.repository.UserAccountRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
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
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @Autowired private MockMvc mockMvc;

  @Autowired private UserAccountRepository userAccountRepository;

  @BeforeEach
  void cleanDatabase() {
    userAccountRepository.deleteAll();
  }

  @Test
  void registersLogsInUsesSessionAndLogsOut() throws Exception {
    CsrfCredentials csrf = obtainCsrf(null);

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
        .andExpect(status().isCreated());

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
