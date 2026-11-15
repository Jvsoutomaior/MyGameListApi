package com.mygamelist.api.repository;

import com.mygamelist.api.entity.Game;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRepository extends JpaRepository<Game, UUID> {

  Optional<Game> findBySourceAndExternalId(String source, String externalId);

  Page<Game> findByTitleContainingIgnoreCase(String title, Pageable pageable);

  List<Game> findTop18ByCoverUrlIsNotNullAndReleaseDateLessThanEqualOrderByReleaseDateDescIdAsc(
      LocalDate releaseDate);
}
