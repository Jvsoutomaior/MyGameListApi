package com.mygamelist.api.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(
    @NotBlank
        @Size(max = 30)
        @Pattern(
            regexp = "^[A-Za-z0-9_]+$",
            message = "must contain only letters, numbers, and underscores")
        String username,
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(min = 8, max = 128) String password) {}
