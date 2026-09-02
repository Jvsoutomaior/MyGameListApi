package com.mygamelist.api.repository;

import com.mygamelist.api.entity.Game;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRepository extends JpaRepository<Game, UUID> {

  Optional<Game> findBySourceAndExternalId(String source, String externalId);

  Page<Game> findByTitleContainingIgnoreCase(String title, Pageable pageable);
}
