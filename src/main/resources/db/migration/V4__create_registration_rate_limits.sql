CREATE TABLE registration_rate_limits (
    bucket_key VARCHAR(64) PRIMARY KEY,
    attempts INTEGER NOT NULL CHECK (attempts > 0),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_registration_rate_limits_expiry ON registration_rate_limits (expires_at);
