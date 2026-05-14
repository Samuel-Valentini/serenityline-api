ALTER TABLE user_sessions
    DROP CONSTRAINT IF EXISTS chk_user_sessions_revoke_reason_value;

ALTER TABLE user_sessions
    ADD CONSTRAINT chk_user_sessions_revoke_reason_value
        CHECK (
            session_revoke_reason IS NULL
                OR session_revoke_reason IN (
                                             'USER_LOGOUT',
                                             'PASSWORD_CHANGED',
                                             'TOKEN_REUSE_DETECTED',
                                             'ADMIN_REVOKED',
                                             'ACCOUNT_DELETED'
                )
            );

ALTER TABLE refresh_tokens
    DROP CONSTRAINT IF EXISTS chk_refresh_tokens_revoke_reason_value;

ALTER TABLE refresh_tokens
    ADD CONSTRAINT chk_refresh_tokens_revoke_reason_value
        CHECK (
            refresh_token_revoke_reason IS NULL
                OR refresh_token_revoke_reason IN (
                                                   'USER_LOGOUT',
                                                   'PASSWORD_CHANGED',
                                                   'REUSE_DETECTED',
                                                   'SESSION_REVOKED',
                                                   'ADMIN_REVOKED',
                                                   'ACCOUNT_DELETED'
                )
            );