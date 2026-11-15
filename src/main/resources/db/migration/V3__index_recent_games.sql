CREATE INDEX idx_games_recent_with_cover
    ON games (release_date DESC, id ASC)
    WHERE cover_url IS NOT NULL AND release_date IS NOT NULL;
