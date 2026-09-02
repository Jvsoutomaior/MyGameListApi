package com.mygamelist.api.service;

import static org.springframework.data.core.TypedPropertyPath.path;

import com.mygamelist.api.entity.Game;
import com.mygamelist.api.repository.GameRepository;
import com.mygamelist.api.service.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GameCatalogService {

  private static final int MAX_PAGE_SIZE = 100;

  private final GameRepository gameRepository;
  private final Clock clock;

  public GameCatalogService(GameRepository gameRepository, Clock clock) {
    this.gameRepository = gameRepository;
    this.clock = clock;
  }

  @Transactional
  public Game synchronize(
      String source, String externalId, String title, String coverUrl, LocalDate releaseDate) {
    String normalizedSource = requireText(source, "source");
    String normalizedExternalId = requireText(externalId, "externalId");

    return gameRepository
        .findBySourceAndExternalId(normalizedSource, normalizedExternalId)
        .map(game -> updateMetadata(game, title, coverUrl, releaseDate))
        .orElseGet(
            () ->
                gameRepository.save(
                    new Game(
                        normalizedSource,
                        normalizedExternalId,
                        title,
                        coverUrl,
                        releaseDate,
                        clock.instant())));
  }

  public Game findById(UUID gameId) {
    return gameRepository
        .findById(gameId)
        .orElseThrow(() -> new ResourceNotFoundException("Game not found: " + gameId));
  }

  public Page<Game> search(String title, int page, int size) {
    String normalizedTitle = requireText(title, "title");
    Sort sort = Sort.by(Sort.Direction.ASC, path(Game::getTitle), path(Game::getId));
    PageRequest pageRequest = pageRequest(page, size, sort);
    return gameRepository.findByTitleContainingIgnoreCase(normalizedTitle, pageRequest);
  }

  private Game updateMetadata(Game game, String title, String coverUrl, LocalDate releaseDate) {
    game.updateMetadata(title, coverUrl, releaseDate, clock.instant());
    return game;
  }

  private static PageRequest pageRequest(int page, int size, Sort sort) {
    if (page < 0) {
      throw new IllegalArgumentException("page must not be negative");
    }

    if (size < 1 || size > MAX_PAGE_SIZE) {
      throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
    }

    return PageRequest.of(page, size, sort);
  }

  private static String requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }

    return value.trim();
  }
}
