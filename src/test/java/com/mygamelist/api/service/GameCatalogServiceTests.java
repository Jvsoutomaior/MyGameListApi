package com.mygamelist.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.mygamelist.api.entity.Game;
import com.mygamelist.api.repository.GameRepository;

@ExtendWith(MockitoExtension.class)
class GameCatalogServiceTests {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");

    @Mock
    private GameRepository gameRepository;

    private GameCatalogService gameCatalogService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        gameCatalogService = new GameCatalogService(gameRepository, clock);
    }

    @Test
    void updatesMetadataWhenExternalGameAlreadyExists() {
        Game game = new Game("IGDB", "1942", "Old title", null, null, NOW.minusSeconds(60));
        LocalDate releaseDate = LocalDate.of(2015, 5, 19);
        when(gameRepository.findBySourceAndExternalId("IGDB", "1942")).thenReturn(Optional.of(game));

        Game synchronizedGame = gameCatalogService.synchronize("IGDB", "1942", "The Witcher 3", "cover.jpg", releaseDate);

        assertThat(synchronizedGame).isSameAs(game);
        assertThat(game.getTitle()).isEqualTo("The Witcher 3");
        assertThat(game.getCoverUrl()).isEqualTo("cover.jpg");
        assertThat(game.getReleaseDate()).isEqualTo(releaseDate);
        assertThat(game.getSynchronizedAt()).isEqualTo(NOW);
        verify(gameRepository, never()).save(any());
    }

    @Test
    void rejectsPageSizeAboveMaximum() {
        assertThatThrownBy(() -> gameCatalogService.search("witcher", 0, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("size must be between 1 and 100");
    }

    @Test
    void searchesWithStablePagination() {
        when(gameRepository.findByTitleContainingIgnoreCase(eq("witcher"), any(Pageable.class))).thenReturn(Page.empty());

        gameCatalogService.search(" witcher ", 2, 20);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(gameRepository).findByTitleContainingIgnoreCase(eq("witcher"), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().getOrderFor("title").getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(pageable.getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);
    }
}
