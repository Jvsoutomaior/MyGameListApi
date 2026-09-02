package com.mygamelist.api.controller;

import com.mygamelist.api.service.exception.ResourceConflictException;
import com.mygamelist.api.service.exception.ResourceNotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(AuthenticationException.class)
  ProblemDetail handleAuthentication() {
    return problem(HttpStatus.UNAUTHORIZED, "Authentication failed", "Invalid email or password");
  }

  @ExceptionHandler(ResourceConflictException.class)
  ProblemDetail handleConflict(ResourceConflictException exception) {
    return problem(HttpStatus.CONFLICT, "Resource conflict", exception.getMessage());
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  ProblemDetail handleNotFound(ResourceNotFoundException exception) {
    return problem(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail handleBadRequest(IllegalArgumentException exception) {
    return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
    Map<String, String> errors = new LinkedHashMap<>();
    exception
        .getBindingResult()
        .getFieldErrors()
        .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));

    ProblemDetail problem =
        problem(HttpStatus.BAD_REQUEST, "Validation failed", "One or more fields are invalid");
    problem.setProperty("errors", errors);
    return problem;
  }

  private static ProblemDetail problem(HttpStatus status, String title, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    return problem;
  }
}
