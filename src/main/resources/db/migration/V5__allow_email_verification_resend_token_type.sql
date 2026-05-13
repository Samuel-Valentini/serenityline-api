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
                                       'EMAIL_CHANGE_CONFIRMATION',
                                       'RESTORE_ACCOUNT'
                )
            );