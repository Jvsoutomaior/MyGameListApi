package com.mygamelist.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserAccount {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(nullable = false, updatable = false, length = 30)
  private String username;

  @Column(nullable = false, updatable = false, length = 254)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Column(length = 500)
  private String bio;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "email_verified", nullable = false)
  private boolean emailVerified = true;

  @Column(name = "verification_code_hash")
  private String verificationCodeHash;

  @Column(name = "verification_expires_at")
  private Instant verificationExpiresAt;

  @Column(name = "verification_last_sent_at")
  private Instant verificationLastSentAt;

  @Column(name = "verification_attempts", nullable = false)
  private int verificationAttempts;

  @Column(name = "verification_send_count", nullable = false)
  private int verificationSendCount;

  public void issueVerificationCode(String hash, Instant now) {
    if (emailVerified || verificationExpiresAt == null || !verificationExpiresAt.isAfter(now)) {
      verificationExpiresAt = now.plusSeconds(600);
      verificationAttempts = 0;
      verificationSendCount = 0;
    }
    emailVerified = false;
    verificationCodeHash = hash;
    verificationLastSentAt = now;
    verificationSendCount++;
  }

  public boolean isEmailVerified() {
    return emailVerified;
  }

  public String getVerificationCodeHash() {
    return verificationCodeHash;
  }

  public Instant getVerificationExpiresAt() {
    return verificationExpiresAt;
  }

  public Instant getVerificationLastSentAt() {
    return verificationLastSentAt;
  }

  public int getVerificationSendCount() {
    return verificationSendCount;
  }

  public int getVerificationAttempts() {
    return verificationAttempts;
  }

  public void recordVerificationAttempt() {
    verificationAttempts++;
  }

  public void verifyEmail(Instant now) {
    emailVerified = true;
    verificationCodeHash = null;
    verificationExpiresAt = null;
    verificationLastSentAt = null;
    verificationAttempts = 0;
    verificationSendCount = 0;
    updatedAt = now;
  }

  protected UserAccount() {}

  public UserAccount(String username, String email, String passwordHash, Instant createdAt) {
    this.username = normalizeRequiredText(username, "username").toLowerCase(Locale.ROOT);
    this.email = normalizeRequiredText(email, "email").toLowerCase(Locale.ROOT);
    this.passwordHash = normalizeRequiredText(passwordHash, "passwordHash");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    this.updatedAt = createdAt;
  }

  public void updateBio(String bio, Instant updatedAt) {
    this.bio = normalizeOptionalText(bio);
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
  }

  public void changePasswordHash(String passwordHash, Instant updatedAt) {
    this.passwordHash = normalizeRequiredText(passwordHash, "passwordHash");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
  }

  public UUID getId() {
    return id;
  }

  public String getUsername() {
    return username;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getBio() {
    return bio;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  private static String normalizeRequiredText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }

    return value.trim();
  }

  private static String normalizeOptionalText(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
