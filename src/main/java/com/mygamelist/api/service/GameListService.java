package com.mygamelist.api.service;

import static org.springframework.data.core.TypedPropertyPath.path;

import java.time.Clock;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mygamelist.api.entity.Game;
import com.mygamelist.api.entity.GameListEntry;
import com.mygamelist.api.entity.GameStatus;
import com.mygamelist.api.entity.UserAccount;
import com.mygamelist.api.repository.GameListEntryRepository;
import com.mygamelist.api.repository.GameRepository;
import com.mygamelist.api.repository.UserAccountRepository;
import com.mygamelist.api.service.exception.ResourceConflictException;
import com.mygamelist.api.service.exception.ResourceNotFoundException;

@Service
@Transactional(readOnly = true)
public class GameListService {

    private static final int MAX_PAGE_SIZE = 100;

    private final GameListEntryRepository gameListEntryRepository;
    private final UserAccountRepository userAccountRepository;
    private final GameRepository gameRepository;
    private final Clock clock;

    public GameListService(GameListEntryRepository gameListEntryRepository, UserAccountRepository userAccountRepository,
            GameRepository gameRepository, Clock clock) {
        this.gameListEntryRepository = gameListEntryRepository;
        this.userAccountRepository = userAccountRepository;
        this.gameRepository = gameRepository;
        this.clock = clock;
    }

    @Transactional
    public GameListEntry add(UUID userId, UUID gameId, GameStatus status, Integer score, String comment) {
        if (gameListEntryRepository.findByUserIdAndGameId(userId, gameId).isPresent()) {
            throw new ResourceConflictException("Game is already in the user's list");
        }

        UserAccount user = findUser(userId);
        Game game = findGame(gameId);
        return gameListEntryRepository.save(new GameListEntry(user, game, status, score, comment, clock.instant()));
    }

    @Transactional
    public GameListEntry update(UUID userId, UUID gameId, GameStatus status, Integer score, String comment) {
        GameListEntry entry = findEntry(userId, gameId);
        entry.update(status, score, comment, clock.instant());
        return entry;
    }

    @Transactional
    public void remove(UUID userId, UUID gameId) {
        gameListEntryRepository.delete(findEntry(userId, gameId));
    }

    public Page<GameListEntry> findPublicList(String username, int page, int size) {
        String normalizedUsername = normalizeUsername(username);

        if (!userAccountRepository.existsByUsername(normalizedUsername)) {
            throw new ResourceNotFoundException("User not found: " + normalizedUsername);
        }

        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }

        Sort sort = Sort.by(Sort.Direction.DESC, path(GameListEntry::getUpdatedAt), path(GameListEntry::getId));
        return gameListEntryRepository.findByUserUsername(normalizedUsername, PageRequest.of(page, size, sort));
    }

    private GameListEntry findEntry(UUID userId, UUID gameId) {
        return gameListEntryRepository.findByUserIdAndGameId(userId, gameId)
                .orElseThrow(() -> new ResourceNotFoundException("Game list entry not found"));
    }

    private UserAccount findUser(UUID userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private Game findGame(UUID gameId) {
        return gameRepository.findById(gameId)
                .orElseThrow(() -> new ResourceNotFoundException("Game not found: " + gameId));
    }

    private static String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }

        return username.trim().toLowerCase(Locale.ROOT);
    }
}
