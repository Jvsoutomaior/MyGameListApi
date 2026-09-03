package com.mygamelist.api.controller.dto;

import com.mygamelist.api.entity.Game;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record GameResponse(
    UUID id,
    String source,
    String externalId,
    String title,
    String coverUrl,
    LocalDate releaseDate,
    Instant synchronizedAt) {

  public static GameResponse from(Game game) {
    return new GameResponse(
        game.getId(),
        game.getSource(),
        game.getExternalId(),
        game.getTitle(),
        game.getCoverUrl(),
        game.getReleaseDate(),
        game.getSynchronizedAt());
  }
}
