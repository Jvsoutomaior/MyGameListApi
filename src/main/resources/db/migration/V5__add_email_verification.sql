ALTER TABLE users
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN verification_code_hash VARCHAR(255),
    ADD COLUMN verification_expires_at TIMESTAMPTZ,
    ADD COLUMN verification_last_sent_at TIMESTAMPTZ,
    ADD COLUMN verification_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN verification_send_count INTEGER NOT NULL DEFAULT 0;
