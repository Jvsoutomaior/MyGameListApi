package com.mygamelist.api.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.mygamelist.api.entity.GameListEntry;

public interface GameListEntryRepository extends JpaRepository<GameListEntry, UUID> {

    Optional<GameListEntry> findByUserIdAndGameId(UUID userId, UUID gameId);

    Page<GameListEntry> findByUserUsernameOrderByUpdatedAtDesc(String username, Pageable pageable);
}
