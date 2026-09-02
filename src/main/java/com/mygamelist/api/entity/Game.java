package com.mygamelist.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
    name = "games",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_games_source_external_id",
            columnNames = {"source", "external_id"}))
public class Game {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(nullable = false, updatable = false, length = 50)
  private String source;

  @Column(name = "external_id", nullable = false, updatable = false, length = 100)
  private String externalId;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(name = "cover_url", length = 2048)
  private String coverUrl;

  @Column(name = "release_date")
  private LocalDate releaseDate;

  @Column(name = "synchronized_at", nullable = false)
  private Instant synchronizedAt;

  protected Game() {}

  public Game(
      String source,
      String externalId,
      String title,
      String coverUrl,
      LocalDate releaseDate,
      Instant synchronizedAt) {
    this.source = requireText(source, "source");
    this.externalId = requireText(externalId, "externalId");
    this.title = requireText(title, "title");
    this.coverUrl = normalizeOptionalText(coverUrl);
    this.releaseDate = releaseDate;
    this.synchronizedAt = Objects.requireNonNull(synchronizedAt, "synchronizedAt must not be null");
  }

  public void updateMetadata(
      String title, String coverUrl, LocalDate releaseDate, Instant synchronizedAt) {
    this.title = requireText(title, "title");
    this.coverUrl = normalizeOptionalText(coverUrl);
    this.releaseDate = releaseDate;
    this.synchronizedAt = Objects.requireNonNull(synchronizedAt, "synchronizedAt must not be null");
  }

  public UUID getId() {
    return id;
  }

  public String getSource() {
    return source;
  }

  public String getExternalId() {
    return externalId;
  }

  public String getTitle() {
    return title;
  }

  public String getCoverUrl() {
    return coverUrl;
  }

  public LocalDate getReleaseDate() {
    return releaseDate;
  }

  public Instant getSynchronizedAt() {
    return synchronizedAt;
  }

  private static String requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }

    return value.trim();
  }

  private static String normalizeOptionalText(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
