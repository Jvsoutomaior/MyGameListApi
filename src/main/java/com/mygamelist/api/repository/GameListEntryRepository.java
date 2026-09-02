package com.mygamelist.api.repository;

import com.mygamelist.api.entity.GameListEntry;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameListEntryRepository extends JpaRepository<GameListEntry, UUID> {

  Optional<GameListEntry> findByUserIdAndGameId(UUID userId, UUID gameId);

  Page<GameListEntry> findByUserUsername(String username, Pageable pageable);
}
