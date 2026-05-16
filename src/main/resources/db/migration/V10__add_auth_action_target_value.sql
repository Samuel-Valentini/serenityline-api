ALTER TABLE auth_action_tokens
    ADD COLUMN auth_action_target_value VARCHAR(500);

ALTER TABLE auth_action_tokens
    ADD CONSTRAINT chk_auth_action_tokens_target_value_not_blank
        CHECK (
            auth_action_target_value IS NULL
                OR length(btrim(auth_action_target_value)) > 0
            );

ALTER TABLE auth_action_tokens
    ADD CONSTRAINT chk_auth_action_tokens_target_value_allowed_types
        CHECK (
            auth_action_target_value IS NULL
                OR auth_action_token_type IN ('EMAIL_CHANGE_CONFIRMATION')
            );

ALTER TABLE auth_action_tokens
    ADD CONSTRAINT chk_auth_action_tokens_email_change_requires_target_value
        CHECK (
            auth_action_token_type <> 'EMAIL_CHANGE_CONFIRMATION'
                OR auth_action_target_value IS NOT NULL
            );

ALTER TABLE auth_action_tokens
    ADD CONSTRAINT chk_auth_action_tokens_email_change_target_value_normalized
        CHECK (
            auth_action_token_type <> 'EMAIL_CHANGE_CONFIRMATION'
                OR auth_action_target_value = lower(btrim(auth_action_target_value))
            );

ALTER TABLE auth_action_tokens
    ADD CONSTRAINT chk_auth_action_tokens_email_change_target_value_length
        CHECK (
            auth_action_token_type <> 'EMAIL_CHANGE_CONFIRMATION'
                OR length(auth_action_target_value) <= 320
            );