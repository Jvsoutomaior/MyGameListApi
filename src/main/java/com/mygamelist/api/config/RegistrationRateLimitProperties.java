package com.mygamelist.api.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.registration.rate-limit")
public record RegistrationRateLimitProperties(
    @DefaultValue("5") @Min(1) @Max(10000) int requestsPerIp,
    @DefaultValue("200") @Min(1) @Max(100000) int globalRequests,
    @DefaultValue("900") @Min(1) @Max(86400) int windowSeconds) {}
