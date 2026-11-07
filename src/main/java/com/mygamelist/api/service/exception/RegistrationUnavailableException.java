package com.mygamelist.api.service.exception;

public class RegistrationUnavailableException extends RuntimeException {

  public RegistrationUnavailableException() {
    super("Registration is unavailable");
  }
}
