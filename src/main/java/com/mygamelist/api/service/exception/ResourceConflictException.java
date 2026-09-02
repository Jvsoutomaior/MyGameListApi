package com.mygamelist.api.service.exception;

public class ResourceConflictException extends RuntimeException {

  public ResourceConflictException(String message) {
    super(message);
  }
}
