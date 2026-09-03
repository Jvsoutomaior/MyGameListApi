package com.mygamelist.api.controller;

import com.mygamelist.api.controller.dto.AddGameListEntryRequest;
import com.mygamelist.api.controller.dto.GameListEntryRequest;
import com.mygamelist.api.controller.dto.GameListEntryResponse;
import com.mygamelist.api.controller.dto.PageResponse;
import com.mygamelist.api.entity.GameListEntry;
import com.mygamelist.api.security.AuthenticatedUser;
import com.mygamelist.api.service.GameListService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/games")
public class PersonalGameListController {

  private final GameListService gameListService;

  public PersonalGameListController(GameListService gameListService) {
    this.gameListService = gameListService;
  }

  @GetMapping
  public PageResponse<GameListEntryResponse> findMyList(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(
        gameListService.findList(principal.id(), page, size), GameListEntryResponse::from);
  }

  @PostMapping
  public ResponseEntity<GameListEntryResponse> add(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody AddGameListEntryRequest request) {
    GameListEntry entry =
        gameListService.add(
            principal.id(), request.gameId(), request.status(), request.score(), request.comment());
    URI location = URI.create("/api/me/games/" + request.gameId());
    return ResponseEntity.created(location).body(GameListEntryResponse.from(entry));
  }

  @PatchMapping("/{gameId}")
  public GameListEntryResponse update(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable UUID gameId,
      @Valid @RequestBody GameListEntryRequest request) {
    return GameListEntryResponse.from(
        gameListService.update(
            principal.id(), gameId, request.status(), request.score(), request.comment()));
  }

  @DeleteMapping("/{gameId}")
  public ResponseEntity<Void> remove(
      @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID gameId) {
    gameListService.remove(principal.id(), gameId);
    return ResponseEntity.noContent().build();
  }
}
