package com.mygamelist.api.controller;

import com.mygamelist.api.controller.dto.RegisterUserRequest;
import com.mygamelist.api.controller.dto.UserResponse;
import com.mygamelist.api.entity.UserAccount;
import com.mygamelist.api.service.UserAccountService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserAccountController {

  private final UserAccountService userAccountService;

  public UserAccountController(UserAccountService userAccountService) {
    this.userAccountService = userAccountService;
  }

  @PostMapping
  public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
    UserAccount user =
        userAccountService.register(request.username(), request.email(), request.password());
    URI location = URI.create("/api/users/" + user.getUsername());
    return ResponseEntity.created(location).body(UserResponse.from(user));
  }
}
