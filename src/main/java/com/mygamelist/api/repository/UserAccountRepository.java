package com.mygamelist.api.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mygamelist.api.entity.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmail(String email);

    Optional<UserAccount> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);
}
