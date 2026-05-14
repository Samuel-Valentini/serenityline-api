CREATE INDEX idx_auth_login_attempts_invalid_email_ip_recent
    ON auth_login_attempts (email_hash, ip_address_hash, auth_login_attempt_at DESC)
    WHERE login_successful = false
        AND failure_reason = 'INVALID_CREDENTIALS';

CREATE INDEX idx_auth_login_attempts_invalid_email_recent
    ON auth_login_attempts (email_hash, auth_login_attempt_at DESC)
    WHERE login_successful = false
        AND failure_reason = 'INVALID_CREDENTIALS';

CREATE INDEX idx_auth_login_attempts_invalid_ip_recent
    ON auth_login_attempts (ip_address_hash, auth_login_attempt_at DESC)
    WHERE login_successful = false
        AND failure_reason = 'INVALID_CREDENTIALS';