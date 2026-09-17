-- Refresh tokens: opaque, stored as a SHA-256 hash only (doc/scope.md §4, §9).

CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users (id),
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked    BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);

-- Reuse-detection and "revoke whole chain for user" both scan by user_id.
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
