package com.mygamelist.api.service;

import com.mygamelist.api.config.ResendProperties;
import com.mygamelist.api.service.exception.RegistrationUnavailableException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class ResendEmailSender {
  private final ResendProperties properties;
  private final RestClient client;

  public ResendEmailSender(ResendProperties properties) {
    this.properties = properties;
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(5));
    factory.setReadTimeout(Duration.ofSeconds(10));
    client = RestClient.builder().baseUrl("https://api.resend.com").requestFactory(factory).build();
  }

  public void sendVerification(String email, String code) {
    if (properties.apiKey().isBlank()) {
      throw new RegistrationUnavailableException();
    }
    try {
      client
          .post()
          .uri("/emails")
          .header("Authorization", "Bearer " + properties.apiKey())
          .header("Idempotency-Key", UUID.randomUUID().toString())
          .body(
              Map.of(
                  "from",
                  properties.from(),
                  "to",
                  List.of(email),
                  "subject",
                  "Confirme seu cadastro no MyGameList",
                  "text",
                  "Seu código de confirmação é "
                      + code
                      + ". Ele vale por até 10 minutos. Use o código mais recente na tela de cadastro."
                      + " Se você não solicitou este cadastro, pode ignorar esta mensagem."))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException exception) {
      throw new RegistrationUnavailableException();
    }
  }
}
