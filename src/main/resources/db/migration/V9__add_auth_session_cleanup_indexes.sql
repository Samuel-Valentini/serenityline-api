CREATE INDEX IF NOT EXISTS idx_refresh_tokens_used_at
    ON refresh_tokens (refresh_token_used_at)
    WHERE refresh_token_used_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_revoked_at
    ON refresh_tokens (refresh_token_revoked_at)
    WHERE refresh_token_revoked_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_reuse_detected_at
    ON refresh_tokens (refresh_token_reuse_detected_at)
    WHERE refresh_token_reuse_detected_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_user_sessions_revoked_at
    ON user_sessions (session_revoked_at)
    WHERE session_revoked_at IS NOT NULL;