package com.mygamelist.api.service;

import com.mygamelist.api.entity.UserAccount;
import com.mygamelist.api.repository.UserAccountRepository;
import com.mygamelist.api.service.exception.RegistrationCooldownException;
import com.mygamelist.api.service.exception.ResourceConflictException;
import com.mygamelist.api.service.exception.VerificationCodeException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationVerificationStore {
  private final UserAccountRepository users;
  private final UserAccountService accounts;
  private final PasswordEncoder encoder;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  @Scheduled(fixedDelay = 3600000, initialDelay = 3600000)
  @Transactional
  public void removeExpiredRegistrations() {
    users.deleteExpiredRegistrations(clock.instant().minusSeconds(86400));
  }

  public RegistrationVerificationStore(
      UserAccountRepository users,
      UserAccountService accounts,
      PasswordEncoder encoder,
      Clock clock) {
    this.users = users;
    this.accounts = accounts;
    this.encoder = encoder;
    this.clock = clock;
  }

  @Transactional
  public PreparedCode prepare(String username, String email, String password, String invitation) {
    accounts.validateInvitation(invitation);
    UserAccount user = users.lockByEmail(normalize(email)).orElse(null);
    if (user == null) {
      user = accounts.register(username, email, password, invitation);
    } else if (user.isEmailVerified()
        || !user.getUsername().equals(normalize(username))
        || !encoder.matches(password, user.getPasswordHash())) {
      throw new ResourceConflictException("Email is already in use");
    }
    return issue(user);
  }

  @Transactional
  public PreparedCode resend(String email, String password) {
    UserAccount user =
        users.lockByEmail(normalize(email)).orElseThrow(VerificationCodeException::new);
    if (user.isEmailVerified() || !encoder.matches(password, user.getPasswordHash())) {
      throw new VerificationCodeException();
    }
    return issue(user);
  }

  private PreparedCode issue(UserAccount user) {
    Instant now = clock.instant();
    if (!user.isEmailVerified() && user.getVerificationLastSentAt() != null) {
      Instant nextSend = user.getVerificationLastSentAt().plusSeconds(60);
      if (nextSend.isAfter(now)) {
        throw new RegistrationCooldownException(
            Math.ceilDiv(Duration.between(now, nextSend).toMillis(), 1000));
      }
      if (user.getVerificationExpiresAt().isAfter(now)
          && (user.getVerificationSendCount() >= 3 || user.getVerificationAttempts() >= 5)) {
        throw new RegistrationCooldownException(
            Math.ceilDiv(Duration.between(now, user.getVerificationExpiresAt()).toMillis(), 1000));
      }
    }
    String code;
    do {
      code = String.format(Locale.ROOT, "%08d", random.nextInt(100_000_000));
    } while (user.getVerificationCodeHash() != null
        && encoder.matches(code, user.getVerificationCodeHash()));
    user.issueVerificationCode(encoder.encode(code), now);
    return new PreparedCode(user.getEmail(), code, user.getVerificationExpiresAt());
  }

  @Transactional(noRollbackFor = VerificationCodeException.class)
  public void confirm(String email, String code) {
    UserAccount user =
        users.lockByEmail(normalize(email)).orElseThrow(VerificationCodeException::new);
    Instant now = clock.instant();
    if (user.isEmailVerified()
        || user.getVerificationExpiresAt() == null
        || !user.getVerificationExpiresAt().isAfter(now)
        || user.getVerificationAttempts() >= 5) {
      throw new VerificationCodeException();
    }
    user.recordVerificationAttempt();
    if (!encoder.matches(code, user.getVerificationCodeHash())) {
      throw new VerificationCodeException();
    }
    user.verifyEmail(now);
  }

  private static String normalize(String value) {
    return value.trim().toLowerCase(Locale.ROOT);
  }

  public record PreparedCode(String email, String code, Instant expiresAt) {
    @Override
    public String toString() {
      return "PreparedCode[redacted]";
    }
  }
}
