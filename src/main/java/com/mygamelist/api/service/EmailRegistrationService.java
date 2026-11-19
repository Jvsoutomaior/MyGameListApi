package com.mygamelist.api.service;

import com.mygamelist.api.service.exception.RegistrationUnavailableException;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class EmailRegistrationService {
  private final RegistrationVerificationStore store;
  private final ResendEmailSender sender;

  public EmailRegistrationService(RegistrationVerificationStore store, ResendEmailSender sender) {
    this.store = store;
    this.sender = sender;
  }

  public PendingRegistration register(String username, String email, String password) {
    return deliver(store.prepare(username, email, password));
  }

  public PendingRegistration resend(String email, String password) {
    return deliver(store.resend(email, password));
  }

  public void confirm(String email, String code) {
    store.confirm(email, code);
  }

  private PendingRegistration deliver(RegistrationVerificationStore.PreparedCode prepared) {
    boolean sent = true;
    try {
      sender.sendVerification(prepared.email(), prepared.code());
    } catch (RegistrationUnavailableException exception) {
      sent = false;
    }
    return new PendingRegistration(prepared.email(), prepared.expiresAt(), sent);
  }

  public record PendingRegistration(String email, Instant expiresAt, boolean emailSent) {}
}
