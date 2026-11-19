package com.mygamelist.api.repository;

import com.mygamelist.api.entity.UserAccount;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

  Optional<UserAccount> findByEmail(String email);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from UserAccount u where u.email = :email")
  Optional<UserAccount> lockByEmail(String email);

  @Modifying
  @Query(
      "delete from UserAccount u where u.emailVerified = false and u.verificationExpiresAt < :cutoff")
  int deleteExpiredRegistrations(Instant cutoff);

  Optional<UserAccount> findByUsername(String username);

  boolean existsByEmail(String email);

  boolean existsByUsername(String username);

  boolean existsByUsernameAndEmailVerifiedTrue(String username);
}
