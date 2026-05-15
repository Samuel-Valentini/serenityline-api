ALTER TABLE users
    ADD COLUMN email_2fa_enabled               BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN payment_email_reminders_enabled BOOLEAN NOT NULL DEFAULT TRUE;


ALTER TABLE auth_action_tokens
    ADD COLUMN auth_action_failed_attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN auth_action_last_failed_at       TIMESTAMPTZ,
    ADD COLUMN auth_action_max_attempts         INTEGER NOT NULL DEFAULT 5;


ALTER TABLE auth_action_tokens
    ADD CONSTRAINT chk_auth_action_tokens_failed_attempt_count_non_negative
        CHECK (auth_action_failed_attempt_count >= 0);

ALTER TABLE auth_action_tokens
    ADD CONSTRAINT chk_auth_action_tokens_max_attempts_range
        CHECK (auth_action_max_attempts BETWEEN 1 AND 20);

ALTER TABLE auth_action_tokens
    ADD CONSTRAINT chk_auth_action_tokens_failed_less_than_or_equal_max
        CHECK (auth_action_failed_attempt_count <= auth_action_max_attempts);

ALTER TABLE auth_action_tokens
    ADD CONSTRAINT chk_auth_action_tokens_last_failed_requires_failed_attempt
        CHECK (
            (
                auth_action_failed_attempt_count = 0
                    AND auth_action_last_failed_at IS NULL
                )
                OR
            (
                auth_action_failed_attempt_count > 0
                    AND auth_action_last_failed_at IS NOT NULL
                )
            );

ALTER TABLE auth_action_tokens
    ADD CONSTRAINT chk_auth_action_tokens_last_failed_after_created
        CHECK (
            auth_action_last_failed_at IS NULL
                OR auth_action_last_failed_at >= auth_action_created_at
            );

ALTER TABLE auth_action_tokens
    ADD CONSTRAINT chk_auth_action_tokens_used_after_last_failed
        CHECK (
            auth_action_last_failed_at IS NULL
                OR auth_action_used_at IS NULL
                OR auth_action_used_at >= auth_action_last_failed_at
            );

ALTER TABLE auth_action_tokens
    ADD CONSTRAINT chk_auth_action_tokens_revoked_after_last_failed
        CHECK (
            auth_action_last_failed_at IS NULL
                OR auth_action_revoked_at IS NULL
                OR auth_action_revoked_at >= auth_action_last_failed_at
            );

ALTER TABLE auth_action_tokens
    ADD CONSTRAINT chk_auth_action_tokens_token_hash_not_blank
        CHECK (length(btrim(auth_action_token_hash)) > 0);

ALTER TABLE auth_action_tokens
    DROP CONSTRAINT IF EXISTS chk_auth_action_tokens_token_type;

ALTER TABLE auth_action_tokens
    ADD CONSTRAINT chk_auth_action_tokens_token_type
        CHECK (
            auth_action_token_type IN (
                                       'PASSWORD_RESET',
                                       'EMAIL_VERIFICATION',
                                       'EMAIL_VERIFICATION_RESEND',
                                       'LOGIN_2FA_CODE',
                                       'EMAIL_2FA_ENABLE_CONFIRMATION',
                                       'EMAIL_2FA_DISABLE_CONFIRMATION',
                                       'EMAIL_CHANGE_CONFIRMATION',
                                       'RESTORE_ACCOUNT'
                )
            );


ALTER TABLE email_outbox
    DROP CONSTRAINT IF EXISTS chk_email_outbox_email_type;

ALTER TABLE email_outbox
    ADD CONSTRAINT chk_email_outbox_email_type
        CHECK (
            email_type IN (
                           'EMAIL_VERIFICATION',
                           'PASSWORD_RESET',
                           'EMAIL_CHANGE_CONFIRMATION',
                           'LOGIN_2FA_CODE',
                           'EMAIL_2FA_ENABLE_CONFIRMATION',
                           'EMAIL_2FA_DISABLE_CONFIRMATION',
                           'GENERIC'
                )
            );