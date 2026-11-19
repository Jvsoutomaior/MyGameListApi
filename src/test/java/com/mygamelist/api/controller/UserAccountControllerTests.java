package com.mygamelist.api.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mygamelist.api.service.EmailRegistrationService;
import com.mygamelist.api.service.EmailRegistrationService.PendingRegistration;
import com.mygamelist.api.service.exception.RegistrationUnavailableException;
import com.mygamelist.api.service.exception.ResourceConflictException;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class UserAccountControllerTests {

  private static final Instant CREATED_AT = Instant.parse("2026-09-02T12:00:00Z");

  @Mock private EmailRegistrationService userAccountService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    UserAccountController controller = new UserAccountController(userAccountService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @Test
  void registersUserWithoutExposingSensitiveFields() throws Exception {
    when(userAccountService.register("PlayerOne", "player@example.com", "plain password"))
        .thenReturn(
            new PendingRegistration("player@example.com", CREATED_AT.plusSeconds(600), true));

    mockMvc
        .perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "username": "PlayerOne",
                      "email": "player@example.com",
                      "password": "plain password"
                    }
                    """))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.email").value("player@example.com"))
        .andExpect(jsonPath("$.emailSent").value(true))
        .andExpect(jsonPath("$.code").doesNotExist())
        .andExpect(jsonPath("$.password").doesNotExist())
        .andExpect(jsonPath("$.passwordHash").doesNotExist())
        .andExpect(jsonPath("$.invitationCode").doesNotExist());

    verify(userAccountService).register("PlayerOne", "player@example.com", "plain password");
  }

  @Test
  void rejectsInvalidRequestBeforeCallingService() throws Exception {
    mockMvc
        .perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "username": "invalid username",
                      "email": "not-an-email",
                      "password": "short"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Validation failed"))
        .andExpect(jsonPath("$.errors.username").exists())
        .andExpect(jsonPath("$.errors.email").exists())
        .andExpect(jsonPath("$.errors.password").exists());

    verifyNoInteractions(userAccountService);
  }

  @Test
  void returnsConflictWhenUsernameOrEmailAlreadyExists() throws Exception {
    when(userAccountService.register("playerone", "player@example.com", "plain password"))
        .thenThrow(new ResourceConflictException("Username is already in use"));

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
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Resource conflict"))
        .andExpect(jsonPath("$.detail").value("Username is already in use"));
  }

  @Test
  void returnsServiceUnavailableWhenRegistrationIsDisabled() throws Exception {
    when(userAccountService.register("playerone", "player@example.com", "plain password"))
        .thenThrow(new RegistrationUnavailableException());

    mockMvc
        .perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
        {"username":"playerone","email":"player@example.com","password":"plain password"}
        """))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.detail").value("Registration is unavailable"));
  }
}
