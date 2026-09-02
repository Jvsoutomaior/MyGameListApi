package com.mygamelist.api.controller.dto;

import com.mygamelist.api.entity.UserAccount;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String username, String bio, Instant createdAt) {

  public static UserResponse from(UserAccount user) {
    return new UserResponse(user.getId(), user.getUsername(), user.getBio(), user.getCreatedAt());
  }
}
