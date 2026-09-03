package com.mygamelist.api.controller;

import com.mygamelist.api.controller.dto.GameResponse;
import com.mygamelist.api.controller.dto.PageResponse;
import com.mygamelist.api.service.GameCatalogService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games")
public class GameCatalogController {

  private final GameCatalogService gameCatalogService;

  public GameCatalogController(GameCatalogService gameCatalogService) {
    this.gameCatalogService = gameCatalogService;
  }

  @GetMapping
  public PageResponse<GameResponse> search(
      @RequestParam String query,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(gameCatalogService.search(query, page, size), GameResponse::from);
  }

  @GetMapping("/{gameId}")
  public GameResponse findById(@PathVariable UUID gameId) {
    return GameResponse.from(gameCatalogService.findById(gameId));
  }
}
