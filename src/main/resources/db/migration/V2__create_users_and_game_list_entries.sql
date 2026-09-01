CREATE TABLE users (
    id UUID NOT NULL,
    username VARCHAR(30) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    bio VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE game_list_entries (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    game_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    score INTEGER,
    comment VARCHAR(5000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_game_list_entries PRIMARY KEY (id),
    CONSTRAINT fk_game_list_entries_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_game_list_entries_game FOREIGN KEY (game_id) REFERENCES games (id),
    CONSTRAINT uk_game_list_entries_user_game UNIQUE (user_id, game_id),
    CONSTRAINT ck_game_list_entries_status CHECK (status IN ('PLAN_TO_PLAY', 'PLAYING', 'COMPLETED', 'ON_HOLD', 'DROPPED')),
    CONSTRAINT ck_game_list_entries_score CHECK (score BETWEEN 1 AND 10)
);

CREATE INDEX idx_game_list_entries_game_id ON game_list_entries (game_id);
