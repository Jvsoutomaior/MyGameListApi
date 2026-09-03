package com.mygamelist.api.controller;

import com.mygamelist.api.controller.dto.GameListEntryResponse;
import com.mygamelist.api.controller.dto.PageResponse;
import com.mygamelist.api.controller.dto.UserResponse;
import com.mygamelist.api.service.GameListService;
import com.mygamelist.api.service.UserAccountService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/{username}")
public class PublicProfileController {

  private final UserAccountService userAccountService;
  private final GameListService gameListService;

  public PublicProfileController(
      UserAccountService userAccountService, GameListService gameListService) {
    this.userAccountService = userAccountService;
    this.gameListService = gameListService;
  }

  @GetMapping
  public UserResponse findProfile(@PathVariable String username) {
    return UserResponse.from(userAccountService.findByUsername(username));
  }

  @GetMapping("/games")
  public PageResponse<GameListEntryResponse> findPublicList(
      @PathVariable String username,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(
        gameListService.findPublicList(username, page, size), GameListEntryResponse::from);
  }
}
