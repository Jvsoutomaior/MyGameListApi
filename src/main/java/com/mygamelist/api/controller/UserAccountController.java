package com.mygamelist.api.controller;

import com.mygamelist.api.controller.dto.RegisterUserRequest;
import com.mygamelist.api.service.EmailRegistrationService;
import com.mygamelist.api.service.EmailRegistrationService.PendingRegistration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserAccountController {

  private final EmailRegistrationService registration;

  public UserAccountController(EmailRegistrationService registration) {
    this.registration = registration;
  }

  @PostMapping
  public ResponseEntity<PendingRegistration> register(
      @Valid @RequestBody RegisterUserRequest request) {
    return ResponseEntity.accepted()
        .body(registration.register(request.username(), request.email(), request.password()));
  }

  @PostMapping("/verify-email")
  public ResponseEntity<Void> verify(@Valid @RequestBody VerifyEmailRequest request) {
    registration.confirm(request.email(), request.code());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/resend-code")
  public ResponseEntity<PendingRegistration> resend(@Valid @RequestBody ResendCodeRequest request) {
    return ResponseEntity.accepted().body(registration.resend(request.email(), request.password()));
  }

  public record VerifyEmailRequest(
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Pattern(regexp = "[0-9]{8}") String code) {}

  public record ResendCodeRequest(
      @NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 128) String password) {}
}
