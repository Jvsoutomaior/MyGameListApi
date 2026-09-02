package com.mygamelist.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mygamelist.api.entity.Game;
import com.mygamelist.api.entity.GameListEntry;
import com.mygamelist.api.entity.GameStatus;
import com.mygamelist.api.entity.UserAccount;
import com.mygamelist.api.repository.GameListEntryRepository;
import com.mygamelist.api.repository.GameRepository;
import com.mygamelist.api.repository.UserAccountRepository;
import com.mygamelist.api.service.exception.ResourceConflictException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class GameListServiceTests {

  private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");
  private static final UUID USER_ID = UUID.fromString("36a45326-c1b9-4558-a981-cedae8c4204b");
  private static final UUID GAME_ID = UUID.fromString("5f567cce-c009-4bb0-a042-87380044f70c");

  @Mock private GameListEntryRepository gameListEntryRepository;

  @Mock private UserAccountRepository userAccountRepository;

  @Mock private GameRepository gameRepository;

  private GameListService gameListService;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    gameListService =
        new GameListService(gameListEntryRepository, userAccountRepository, gameRepository, clock);
  }

  @Test
  void addsGameToUsersList() {
    UserAccount user = new UserAccount("playerone", "player@example.com", "argon2id-hash", NOW);
    Game game = new Game("IGDB", "1942", "The Witcher 3", null, null, NOW);
    when(gameListEntryRepository.findByUserIdAndGameId(USER_ID, GAME_ID))
        .thenReturn(Optional.empty());
    when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));
    when(gameListEntryRepository.save(any(GameListEntry.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    GameListEntry entry =
        gameListService.add(USER_ID, GAME_ID, GameStatus.COMPLETED, 9, "Ótimo RPG.");

    assertThat(entry.getUser()).isSameAs(user);
    assertThat(entry.getGame()).isSameAs(game);
    assertThat(entry.getStatus()).isEqualTo(GameStatus.COMPLETED);
    assertThat(entry.getScore()).isEqualTo(9);
    assertThat(entry.getCreatedAt()).isEqualTo(NOW);
    verify(gameListEntryRepository).save(entry);
  }

  @Test
  void rejectsGameAlreadyInUsersList() {
    GameListEntry existingEntry = org.mockito.Mockito.mock(GameListEntry.class);
    when(gameListEntryRepository.findByUserIdAndGameId(USER_ID, GAME_ID))
        .thenReturn(Optional.of(existingEntry));

    assertThatThrownBy(() -> gameListService.add(USER_ID, GAME_ID, GameStatus.PLAYING, null, null))
        .isInstanceOf(ResourceConflictException.class)
        .hasMessage("Game is already in the user's list");

    verify(userAccountRepository, never()).findById(any());
    verify(gameRepository, never()).findById(any());
    verify(gameListEntryRepository, never()).save(any());
  }

  @Test
  void findsPublicListWithNormalizedUsernameAndStablePagination() {
    when(userAccountRepository.existsByUsername("playerone")).thenReturn(true);
    when(gameListEntryRepository.findByUserUsername(eq("playerone"), any(Pageable.class)))
        .thenReturn(Page.empty());

    gameListService.findPublicList(" PlayerOne ", 1, 20);

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(gameListEntryRepository).findByUserUsername(eq("playerone"), pageableCaptor.capture());
    Pageable pageable = pageableCaptor.getValue();
    assertThat(pageable.getPageNumber()).isEqualTo(1);
    assertThat(pageable.getPageSize()).isEqualTo(20);
    assertThat(pageable.getSort().getOrderFor("updatedAt").getDirection())
        .isEqualTo(Sort.Direction.DESC);
    assertThat(pageable.getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);
  }
}
