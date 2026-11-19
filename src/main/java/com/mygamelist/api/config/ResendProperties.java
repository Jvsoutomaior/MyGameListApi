package com.mygamelist.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.mail.resend")
public record ResendProperties(
    @DefaultValue("") String apiKey,
    @DefaultValue("MyGameList <conta@contact.mygamelist.com.br>") String from) {
  @Override
  public String toString() {
    return "ResendProperties[redacted]";
  }
}
