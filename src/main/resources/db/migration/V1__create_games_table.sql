CREATE TABLE games (
    id UUID NOT NULL,
    source VARCHAR(50) NOT NULL,
    external_id VARCHAR(100) NOT NULL,
    title VARCHAR(255) NOT NULL,
    cover_url VARCHAR(2048),
    release_date DATE,
    synchronized_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_games PRIMARY KEY (id),
    CONSTRAINT uk_games_source_external_id UNIQUE (source, external_id)
);
