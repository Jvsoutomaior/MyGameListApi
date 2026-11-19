package com.mygamelist.api.service.exception;

public class VerificationCodeException extends IllegalArgumentException {
  public VerificationCodeException() {
    super("Invalid or expired verification code");
  }
}
