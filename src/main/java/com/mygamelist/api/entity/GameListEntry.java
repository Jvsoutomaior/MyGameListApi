package com.mygamelist.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
    name = "game_list_entries",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_game_list_entries_user_game",
            columnNames = {"user_id", "game_id"}))
public class GameListEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false, updatable = false)
  private UserAccount user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "game_id", nullable = false, updatable = false)
  private Game game;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private GameStatus status;

  private Integer score;

  @Column(length = 5000)
  private String comment;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected GameListEntry() {}

  public GameListEntry(
      UserAccount user,
      Game game,
      GameStatus status,
      Integer score,
      String comment,
      Instant createdAt) {
    this.user = Objects.requireNonNull(user, "user must not be null");
    this.game = Objects.requireNonNull(game, "game must not be null");
    this.status = Objects.requireNonNull(status, "status must not be null");
    this.score = validateScore(score);
    this.comment = normalizeOptionalText(comment);
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    this.updatedAt = createdAt;
  }

  public void update(GameStatus status, Integer score, String comment, Instant updatedAt) {
    this.status = Objects.requireNonNull(status, "status must not be null");
    this.score = validateScore(score);
    this.comment = normalizeOptionalText(comment);
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
  }

  public UUID getId() {
    return id;
  }

  public UserAccount getUser() {
    return user;
  }

  public Game getGame() {
    return game;
  }

  public GameStatus getStatus() {
    return status;
  }

  public Integer getScore() {
    return score;
  }

  public String getComment() {
    return comment;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  private static Integer validateScore(Integer score) {
    if (score != null && (score < 1 || score > 10)) {
      throw new IllegalArgumentException("score must be between 1 and 10");
    }

    return score;
  }

  private static String normalizeOptionalText(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
