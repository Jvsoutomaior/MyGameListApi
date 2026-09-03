package com.mygamelist.api.controller.dto;

import com.mygamelist.api.entity.GameListEntry;
import com.mygamelist.api.entity.GameStatus;
import java.time.Instant;
import java.util.UUID;

public record GameListEntryResponse(
    UUID id,
    GameResponse game,
    GameStatus status,
    Integer score,
    String comment,
    Instant createdAt,
    Instant updatedAt) {

  public static GameListEntryResponse from(GameListEntry entry) {
    return new GameListEntryResponse(
        entry.getId(),
        GameResponse.from(entry.getGame()),
        entry.getStatus(),
        entry.getScore(),
        entry.getComment(),
        entry.getCreatedAt(),
        entry.getUpdatedAt());
  }
}
