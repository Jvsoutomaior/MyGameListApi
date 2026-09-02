package com.mygamelist.api.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.mygamelist.api.entity.Game;
import com.mygamelist.api.entity.GameListEntry;
import com.mygamelist.api.entity.GameStatus;
import com.mygamelist.api.entity.UserAccount;

@Transactional
@Testcontainers
@SpringBootTest
class RepositoryPersistenceTests {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private GameListEntryRepository gameListEntryRepository;

    @Test
    void savesAndFindsUserByNormalizedEmailAndUsername() {
        UserAccount user = new UserAccount(" PlayerOne ", " PLAYER@example.com ", "argon2id-hash", NOW);

        userAccountRepository.saveAndFlush(user);

        assertThat(userAccountRepository.findByEmail("player@example.com")).containsSame(user);
        assertThat(userAccountRepository.findByUsername("playerone")).containsSame(user);
    }

    @Test
    void savesAndSearchesGame() {
        Game game = createGame();

        gameRepository.saveAndFlush(game);

        assertThat(gameRepository.findBySourceAndExternalId("IGDB", "1942")).containsSame(game);
        assertThat(gameRepository.findByTitleContainingIgnoreCase("witcher", PageRequest.of(0, 10))).containsExactly(game);
    }

    @Test
    void savesAndFindsEntryInUsersPublicList() {
        UserAccount user = userAccountRepository.save(new UserAccount("playerone", "player@example.com", "argon2id-hash", NOW));
        Game game = gameRepository.save(createGame());
        GameListEntry entry = new GameListEntry(user, game, GameStatus.COMPLETED, 9, "Ótimo RPG.", NOW);

        gameListEntryRepository.saveAndFlush(entry);

        assertThat(gameListEntryRepository.findByUserIdAndGameId(user.getId(), game.getId())).containsSame(entry);
        assertThat(gameListEntryRepository.findByUserUsername("playerone", PageRequest.of(0, 10)))
                .containsExactly(entry);
    }

    @Test
    void rejectsDuplicateGameInTheSameUsersList() {
        UserAccount user = userAccountRepository.save(new UserAccount("playerone", "player@example.com", "argon2id-hash", NOW));
        Game game = gameRepository.save(createGame());
        gameListEntryRepository.saveAndFlush(new GameListEntry(user, game, GameStatus.PLAYING, null, null, NOW));

        GameListEntry duplicate = new GameListEntry(user, game, GameStatus.COMPLETED, 10, null, NOW);

        assertThatThrownBy(() -> gameListEntryRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Game createGame() {
        return new Game("IGDB", "1942", "The Witcher 3", "https://example.com/cover.jpg", LocalDate.of(2015, 5, 19), NOW);
    }
}
