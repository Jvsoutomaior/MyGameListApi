package com.mygamelist.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mygamelist.api.entity.UserAccount;
import com.mygamelist.api.repository.UserAccountRepository;
import com.mygamelist.api.service.exception.RegistrationUnavailableException;
import com.mygamelist.api.service.exception.ResourceConflictException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTests {

  private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");

  @Mock private UserAccountRepository userAccountRepository;

  @Mock private PasswordEncoder passwordEncoder;

  private UserAccountService userAccountService;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    userAccountService =
        new UserAccountService(userAccountRepository, passwordEncoder, "test-invitation", clock);
  }

  @Test
  void registersNormalizedUserWithEncodedPassword() {
    when(passwordEncoder.encode("plain password")).thenReturn("argon2id-hash");
    when(userAccountRepository.save(any(UserAccount.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    UserAccount user =
        userAccountService.register(
            " PlayerOne ", " PLAYER@example.com ", "plain password", "test-invitation");

    assertThat(user.getUsername()).isEqualTo("playerone");
    assertThat(user.getEmail()).isEqualTo("player@example.com");
    assertThat(user.getPasswordHash()).isEqualTo("argon2id-hash");
    assertThat(user.getCreatedAt()).isEqualTo(NOW);
    verify(passwordEncoder).encode("plain password");
    verify(userAccountRepository).save(user);
  }

  @Test
  void rejectsUsernameAlreadyInUseBeforeEncodingPassword() {
    when(userAccountRepository.existsByUsername("playerone")).thenReturn(true);

    assertThatThrownBy(
            () ->
                userAccountService.register(
                    "PlayerOne", "player@example.com", "plain password", "test-invitation"))
        .isInstanceOf(ResourceConflictException.class)
        .hasMessage("Username is already in use");

    verifyNoInteractions(passwordEncoder);
    verify(userAccountRepository, never()).save(any());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"wrong-code", "   ", "TEST-INVITATION"})
  void rejectsInvalidInvitationBeforeAccessingRepositoryOrEncodingPassword(String invitationCode) {
    assertThatThrownBy(
            () ->
                userAccountService.register(
                    "playerone", "player@example.com", "plain password", invitationCode))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid invitation code");

    verifyNoInteractions(userAccountRepository, passwordEncoder);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void disablesRegistrationWhenExpectedInvitationIsNotConfigured(String configuredCode) {
    UserAccountService service =
        new UserAccountService(
            userAccountRepository,
            passwordEncoder,
            configuredCode,
            Clock.fixed(NOW, ZoneOffset.UTC));

    // Even an identical empty input must never open registration.
    assertThatThrownBy(
            () ->
                service.register(
                    "playerone", "player@example.com", "plain password", configuredCode))
        .isInstanceOf(RegistrationUnavailableException.class)
        .hasMessage("Registration is unavailable");

    verifyNoInteractions(userAccountRepository, passwordEncoder);
  }
}
