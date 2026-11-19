package com.mygamelist.api.service.exception;

public class RegistrationCooldownException extends RuntimeException {
  private final long retryAfterSeconds;

  public RegistrationCooldownException(long retryAfterSeconds) {
    super("Please wait before requesting another verification code");
    this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
  }

  public long retryAfterSeconds() {
    return retryAfterSeconds;
  }
}
