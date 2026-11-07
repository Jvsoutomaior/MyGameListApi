package com.mygamelist.api.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mygamelist.api.entity.UserAccount;
import com.mygamelist.api.service.UserAccountService;
import com.mygamelist.api.service.exception.RegistrationUnavailableException;
import com.mygamelist.api.service.exception.ResourceConflictException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class UserAccountControllerTests {

  private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final Instant CREATED_AT = Instant.parse("2026-09-02T12:00:00Z");

  @Mock private UserAccountService userAccountService;

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
    UserAccount user =
        new UserAccount("playerone", "player@example.com", "argon2id-hash", CREATED_AT);
    ReflectionTestUtils.setField(user, "id", USER_ID);
    when(userAccountService.register(
            "PlayerOne", "player@example.com", "plain password", "test-invitation"))
        .thenReturn(user);

    mockMvc
        .perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "username": "PlayerOne",
                      "email": "player@example.com",
                      "password": "plain password",
                      "invitationCode": "test-invitation"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/users/playerone"))
        .andExpect(jsonPath("$.id").value(USER_ID.toString()))
        .andExpect(jsonPath("$.username").value("playerone"))
        .andExpect(jsonPath("$.createdAt").value(CREATED_AT.toString()))
        .andExpect(jsonPath("$.email").doesNotExist())
        .andExpect(jsonPath("$.password").doesNotExist())
        .andExpect(jsonPath("$.passwordHash").doesNotExist())
        .andExpect(jsonPath("$.invitationCode").doesNotExist());

    verify(userAccountService)
        .register("PlayerOne", "player@example.com", "plain password", "test-invitation");
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
    when(userAccountService.register(
            "playerone", "player@example.com", "plain password", "test-invitation"))
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
                      "password": "plain password",
                      "invitationCode": "test-invitation"
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Resource conflict"))
        .andExpect(jsonPath("$.detail").value("Username is already in use"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"invitationCode\":null}",
        "{\"invitationCode\":\"\"}",
        "{\"invitationCode\":\"   \"}"
      })
  void rejectsMissingOrBlankInvitationBeforeCallingService(String invitationJson) throws Exception {
    String fields = invitationJson.substring(1, invitationJson.length() - 1);
    String request =
        """
        {"username":"playerone","email":"player@example.com","password":"plain password"%s}
        """
            .formatted(fields.isEmpty() ? "" : "," + fields);

    mockMvc
        .perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(request))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.invitationCode").exists());

    verifyNoInteractions(userAccountService);
  }

  @Test
  void rejectsOversizedInvitationBeforeCallingService() throws Exception {
    mockMvc
        .perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
        {"username":"playerone","email":"player@example.com","password":"plain password","invitationCode":"%s"}
        """
                        .formatted("x".repeat(129))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.invitationCode").exists());

    verifyNoInteractions(userAccountService);
  }

  @Test
  void returnsBadRequestForIncorrectInvitation() throws Exception {
    when(userAccountService.register(
            "playerone", "player@example.com", "plain password", "wrong-code"))
        .thenThrow(new IllegalArgumentException("Invalid invitation code"));

    mockMvc
        .perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
        {"username":"playerone","email":"player@example.com","password":"plain password","invitationCode":"wrong-code"}
        """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Invalid invitation code"));
  }

  @Test
  void returnsServiceUnavailableWhenRegistrationIsDisabled() throws Exception {
    when(userAccountService.register(
            "playerone", "player@example.com", "plain password", "test-invitation"))
        .thenThrow(new RegistrationUnavailableException());

    mockMvc
        .perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
        {"username":"playerone","email":"player@example.com","password":"plain password","invitationCode":"test-invitation"}
        """))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.detail").value("Registration is unavailable"));
  }
}
