CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE user_groups
(
    user_group_id         UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    user_group_name       VARCHAR(255) NOT NULL,
    user_group_created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_user_groups_name_not_blank
        CHECK (length(btrim(user_group_name)) > 0)
);

CREATE TABLE users
(
    user_id                         UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    user_name                       VARCHAR(255) NOT NULL,
    email                           VARCHAR(320) NOT NULL UNIQUE,
    user_group_id                   UUID         NOT NULL REFERENCES user_groups (user_group_id) ON DELETE RESTRICT,
    user_role                       VARCHAR(50)  NOT NULL,
    user_platform_role              VARCHAR(50)  NOT NULL DEFAULT 'USER',
    preferred_locale                VARCHAR(10)  NOT NULL DEFAULT 'it-IT',
    preferred_theme                 VARCHAR(30)  NOT NULL DEFAULT 'DEFAULT',
    wants_invoice                   BOOLEAN      NOT NULL DEFAULT FALSE,
    user_password_hash              VARCHAR(255) NOT NULL,
    user_created_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    user_updated_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    user_deleted_at                 TIMESTAMPTZ,
    user_is_enabled                 BOOLEAN      NOT NULL DEFAULT FALSE,
    token_version                   BIGINT       NOT NULL DEFAULT 0,
    user_last_login_at              TIMESTAMPTZ,
    email_2fa_enabled               BOOLEAN      NOT NULL DEFAULT FALSE,
    payment_email_reminders_enabled BOOLEAN      NOT NULL DEFAULT TRUE,

    CONSTRAINT chk_users_user_role
        CHECK (user_role IN ('OWNER', 'COLLABORATOR')),

    CONSTRAINT chk_users_token_version_non_negative
        CHECK (token_version >= 0),

    CONSTRAINT chk_users_preferred_theme
        CHECK (preferred_theme IN ('DEFAULT', 'LIGHT', 'DARK')),

    CONSTRAINT chk_users_preferred_locale
        CHECK (preferred_locale IN ('it-IT', 'en-US')),

    CONSTRAINT chk_users_user_platform_role
        CHECK (user_platform_role IN ('USER', 'ADMIN', 'SUPERADMIN')),

    CONSTRAINT chk_users_email_normalized
        CHECK (email = lower(btrim(email))),

    CONSTRAINT chk_users_email_not_blank
        CHECK (length(btrim(email)) > 0),

    CONSTRAINT chk_users_user_name_not_blank
        CHECK (length(btrim(user_name)) > 0),

    CONSTRAINT chk_users_password_hash_not_blank
        CHECK (length(btrim(user_password_hash)) > 0),

    CONSTRAINT chk_users_updated_after_created
        CHECK (user_updated_at >= user_created_at),

    CONSTRAINT chk_users_deleted_after_created
        CHECK (
            user_deleted_at IS NULL
                OR user_deleted_at >= user_created_at
            ),

    CONSTRAINT chk_users_last_login_after_created
        CHECK (
            user_last_login_at IS NULL
                OR user_last_login_at >= user_created_at
            )
);

CREATE INDEX idx_users_user_group_id
    ON users (user_group_id);

CREATE UNIQUE INDEX uq_users_one_owner_per_group
    ON users (user_group_id)
    WHERE user_role = 'OWNER';

CREATE TABLE user_sessions
(
    user_session_id       UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    user_id               UUID        NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,

    session_created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    session_last_seen_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    session_expires_at    TIMESTAMPTZ NOT NULL DEFAULT (now() + interval '1 day'),
    session_revoked_at    TIMESTAMPTZ,
    session_revoke_reason VARCHAR(50),
    ip_address_hash       VARCHAR(255),
    user_agent            TEXT CHECK (user_agent IS NULL OR length(user_agent) <= 2000),
    device_label          VARCHAR(255),

    CONSTRAINT chk_user_sessions_revoke_reason_value
        CHECK (
            session_revoke_reason IS NULL
                OR session_revoke_reason IN (
                                             'USER_LOGOUT',
                                             'PASSWORD_CHANGED',
                                             'TOKEN_REUSE_DETECTED',
                                             'ADMIN_REVOKED',
                                             'ACCOUNT_DELETED',
                                             'EMAIL_CHANGED'
                )
            ),

    CONSTRAINT chk_user_sessions_expires_after_created
        CHECK (session_expires_at > session_created_at),

    CONSTRAINT chk_user_sessions_last_seen_after_created
        CHECK (session_last_seen_at >= session_created_at),

    CONSTRAINT chk_user_sessions_revoked_after_created
        CHECK (
            session_revoked_at IS NULL
                OR session_revoked_at >= session_created_at
            ),

    CONSTRAINT chk_user_sessions_revocation_consistency
        CHECK (
            (session_revoked_at IS NULL AND session_revoke_reason IS NULL)
                OR
            (session_revoked_at IS NOT NULL AND session_revoke_reason IS NOT NULL)
            ),

    CONSTRAINT uq_user_sessions_session_user
        UNIQUE (user_session_id, user_id)
);

CREATE INDEX idx_user_sessions_user_id
    ON user_sessions (user_id);

CREATE INDEX idx_user_sessions_expires_at
    ON user_sessions (session_expires_at);

CREATE INDEX idx_user_sessions_active
    ON user_sessions (user_id, session_expires_at)
    WHERE session_revoked_at IS NULL;

CREATE INDEX idx_user_sessions_revoked_at
    ON user_sessions (session_revoked_at)
    WHERE session_revoked_at IS NOT NULL;

CREATE TABLE refresh_tokens
(
    refresh_token_id                UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    user_id                         UUID         NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    user_session_id                 UUID         NOT NULL REFERENCES user_sessions (user_session_id) ON DELETE CASCADE,

    refresh_token_hash              VARCHAR(255) NOT NULL UNIQUE,
    parent_refresh_token_id         UUID         REFERENCES refresh_tokens (refresh_token_id) ON DELETE SET NULL,
    replaced_by_refresh_token_id    UUID         REFERENCES refresh_tokens (refresh_token_id) ON DELETE SET NULL,

    refresh_token_created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    refresh_token_expires_at        TIMESTAMPTZ  NOT NULL,
    refresh_token_used_at           TIMESTAMPTZ,
    refresh_token_revoked_at        TIMESTAMPTZ,
    refresh_token_revoke_reason     VARCHAR(50),
    refresh_token_reuse_detected_at TIMESTAMPTZ,

    CONSTRAINT chk_refresh_tokens_revoke_reason_value
        CHECK (
            refresh_token_revoke_reason IS NULL
                OR refresh_token_revoke_reason IN (
                                                   'USER_LOGOUT',
                                                   'PASSWORD_CHANGED',
                                                   'REUSE_DETECTED',
                                                   'SESSION_REVOKED',
                                                   'ADMIN_REVOKED',
                                                   'ACCOUNT_DELETED',
                                                   'EMAIL_CHANGED'
                )
            ),

    CONSTRAINT chk_refresh_tokens_revocation_consistent
        CHECK (
            (refresh_token_revoked_at IS NULL AND refresh_token_revoke_reason IS NULL)
                OR
            (refresh_token_revoked_at IS NOT NULL AND refresh_token_revoke_reason IS NOT NULL)
            ),

    CONSTRAINT chk_refresh_tokens_expires_after_created
        CHECK (refresh_token_expires_at > refresh_token_created_at),

    CONSTRAINT chk_refresh_tokens_used_after_created
        CHECK (
            refresh_token_used_at IS NULL
                OR refresh_token_used_at >= refresh_token_created_at
            ),

    CONSTRAINT chk_refresh_tokens_revoked_after_created
        CHECK (
            refresh_token_revoked_at IS NULL
                OR refresh_token_revoked_at >= refresh_token_created_at
            ),

    CONSTRAINT chk_refresh_tokens_reuse_detected_after_created
        CHECK (
            refresh_token_reuse_detected_at IS NULL
                OR refresh_token_reuse_detected_at >= refresh_token_created_at
            ),

    CONSTRAINT chk_refresh_tokens_parent_not_self
        CHECK (
            parent_refresh_token_id IS NULL
                OR parent_refresh_token_id <> refresh_token_id
            ),

    CONSTRAINT chk_refresh_tokens_replaced_by_not_self
        CHECK (
            replaced_by_refresh_token_id IS NULL
                OR replaced_by_refresh_token_id <> refresh_token_id
            ),

    CONSTRAINT chk_refresh_tokens_replaced_requires_used
        CHECK (
            replaced_by_refresh_token_id IS NULL
                OR refresh_token_used_at IS NOT NULL
            ),

    CONSTRAINT chk_refresh_tokens_reuse_reason_consistent
        CHECK (
            refresh_token_reuse_detected_at IS NULL
                OR (
                refresh_token_revoked_at IS NOT NULL
                    AND refresh_token_revoke_reason = 'REUSE_DETECTED'
                )
            ),

    CONSTRAINT fk_refresh_tokens_session_user
        FOREIGN KEY (user_session_id, user_id)
            REFERENCES user_sessions (user_session_id, user_id)
            ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_user_id
    ON refresh_tokens (user_id);

CREATE INDEX idx_refresh_tokens_session_id
    ON refresh_tokens (user_session_id);

CREATE INDEX idx_refresh_tokens_expires_at
    ON refresh_tokens (refresh_token_expires_at);

CREATE INDEX idx_refresh_tokens_session_unused_not_revoked
    ON refresh_tokens (user_session_id, refresh_token_expires_at)
    WHERE refresh_token_revoked_at IS NULL
        AND refresh_token_used_at IS NULL;

CREATE UNIQUE INDEX uq_refresh_tokens_parent
    ON refresh_tokens (parent_refresh_token_id)
    WHERE parent_refresh_token_id IS NOT NULL;

CREATE UNIQUE INDEX uq_refresh_tokens_replaced_by
    ON refresh_tokens (replaced_by_refresh_token_id)
    WHERE replaced_by_refresh_token_id IS NOT NULL;

CREATE INDEX idx_refresh_tokens_used_at
    ON refresh_tokens (refresh_token_used_at)
    WHERE refresh_token_used_at IS NOT NULL;

CREATE INDEX idx_refresh_tokens_revoked_at
    ON refresh_tokens (refresh_token_revoked_at)
    WHERE refresh_token_revoked_at IS NOT NULL;

CREATE INDEX idx_refresh_tokens_reuse_detected_at
    ON refresh_tokens (refresh_token_reuse_detected_at)
    WHERE refresh_token_reuse_detected_at IS NOT NULL;

CREATE TABLE auth_action_tokens
(
    auth_action_token_id             UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    user_id                          UUID         NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    auth_action_token_hash           VARCHAR(255) NOT NULL UNIQUE,
    auth_action_token_type           VARCHAR(50)  NOT NULL,
    auth_action_expires_at           TIMESTAMPTZ  NOT NULL,
    auth_action_used_at              TIMESTAMPTZ,
    auth_action_revoked_at           TIMESTAMPTZ,
    auth_action_created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    auth_action_failed_attempt_count INTEGER      NOT NULL DEFAULT 0,
    auth_action_last_failed_at       TIMESTAMPTZ,
    auth_action_max_attempts         INTEGER      NOT NULL DEFAULT 5,
    auth_action_target_value         VARCHAR(500),

    CONSTRAINT chk_auth_action_tokens_token_type
        CHECK (
            auth_action_token_type IN (
                                       'PASSWORD_RESET',
                                       'EMAIL_VERIFICATION',
                                       'EMAIL_VERIFICATION_RESEND',
                                       'LOGIN_2FA_CODE',
                                       'EMAIL_2FA_ENABLE_CONFIRMATION',
                                       'EMAIL_2FA_DISABLE_CONFIRMATION',
                                       'EMAIL_CHANGE_CONFIRMATION',
                                       'RESTORE_ACCOUNT',
                                       'USER_INVITATION'
                )
            ),

    CONSTRAINT chk_auth_action_tokens_expires_after_created
        CHECK (auth_action_expires_at > auth_action_created_at),

    CONSTRAINT chk_auth_action_tokens_used_after_created
        CHECK (
            auth_action_used_at IS NULL
                OR auth_action_used_at >= auth_action_created_at
            ),

    CONSTRAINT chk_auth_action_tokens_revoked_after_created
        CHECK (
            auth_action_revoked_at IS NULL
                OR auth_action_revoked_at >= auth_action_created_at
            ),

    CONSTRAINT chk_auth_action_tokens_not_used_and_revoked
        CHECK (
            NOT (
                auth_action_used_at IS NOT NULL
                    AND auth_action_revoked_at IS NOT NULL
                )
            ),

    CONSTRAINT chk_auth_action_tokens_failed_attempt_count_non_negative
        CHECK (auth_action_failed_attempt_count >= 0),

    CONSTRAINT chk_auth_action_tokens_max_attempts_range
        CHECK (auth_action_max_attempts BETWEEN 1 AND 20),

    CONSTRAINT chk_auth_action_tokens_failed_less_than_or_equal_max
        CHECK (auth_action_failed_attempt_count <= auth_action_max_attempts),

    CONSTRAINT chk_auth_action_tokens_last_failed_requires_failed_attempt
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
            ),

    CONSTRAINT chk_auth_action_tokens_last_failed_after_created
        CHECK (
            auth_action_last_failed_at IS NULL
                OR auth_action_last_failed_at >= auth_action_created_at
            ),

    CONSTRAINT chk_auth_action_tokens_used_after_last_failed
        CHECK (
            auth_action_last_failed_at IS NULL
                OR auth_action_used_at IS NULL
                OR auth_action_used_at >= auth_action_last_failed_at
            ),

    CONSTRAINT chk_auth_action_tokens_revoked_after_last_failed
        CHECK (
            auth_action_last_failed_at IS NULL
                OR auth_action_revoked_at IS NULL
                OR auth_action_revoked_at >= auth_action_last_failed_at
            ),

    CONSTRAINT chk_auth_action_tokens_token_hash_not_blank
        CHECK (length(btrim(auth_action_token_hash)) > 0),

    CONSTRAINT chk_auth_action_tokens_target_value_not_blank
        CHECK (
            auth_action_target_value IS NULL
                OR length(btrim(auth_action_target_value)) > 0
            ),

    CONSTRAINT chk_auth_action_tokens_target_value_allowed_types
        CHECK (
            auth_action_target_value IS NULL
                OR auth_action_token_type IN ('EMAIL_CHANGE_CONFIRMATION')
            ),

    CONSTRAINT chk_auth_action_tokens_email_change_requires_target_value
        CHECK (
            auth_action_token_type <> 'EMAIL_CHANGE_CONFIRMATION'
                OR auth_action_target_value IS NOT NULL
            ),

    CONSTRAINT chk_auth_action_tokens_email_change_target_value_normalized
        CHECK (
            auth_action_token_type <> 'EMAIL_CHANGE_CONFIRMATION'
                OR auth_action_target_value = lower(btrim(auth_action_target_value))
            ),

    CONSTRAINT chk_auth_action_tokens_email_change_target_value_length
        CHECK (
            auth_action_token_type <> 'EMAIL_CHANGE_CONFIRMATION'
                OR length(auth_action_target_value) <= 320
            )
);

CREATE INDEX idx_auth_action_tokens_pending_user_type
    ON auth_action_tokens (user_id, auth_action_token_type, auth_action_expires_at)
    WHERE auth_action_used_at IS NULL
        AND auth_action_revoked_at IS NULL;

CREATE TABLE email_outbox
(
    email_outbox_id        UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    user_id                UUID         REFERENCES users (user_id) ON DELETE SET NULL,

    recipient_email        VARCHAR(320) NOT NULL,
    email_type             VARCHAR(50)  NOT NULL,

    encryption_key_id      VARCHAR(100) NOT NULL,

    subject_encrypted      BYTEA        NOT NULL,
    subject_iv             BYTEA        NOT NULL,
    subject_tag            BYTEA        NOT NULL,

    body_html_encrypted    BYTEA,
    body_html_iv           BYTEA,
    body_html_tag          BYTEA,

    body_text_encrypted    BYTEA,
    body_text_iv           BYTEA,
    body_text_tag          BYTEA,

    delete_body_after_send BOOLEAN      NOT NULL DEFAULT FALSE,
    email_body_deleted_at  TIMESTAMPTZ,

    provider               VARCHAR(100),
    provider_message_id    VARCHAR(255),

    email_status           VARCHAR(30)  NOT NULL DEFAULT 'PENDING',

    attempts               INTEGER      NOT NULL DEFAULT 0,
    max_attempts           INTEGER      NOT NULL DEFAULT 6,

    last_error             TEXT,

    email_scheduled_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    email_sent_at          TIMESTAMPTZ,
    email_last_failed_at   TIMESTAMPTZ,

    email_created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    email_updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    email_cancelled_at     TIMESTAMPTZ,

    CONSTRAINT chk_email_outbox_email_type
        CHECK (
            email_type IN (
                           'EMAIL_VERIFICATION',
                           'PASSWORD_RESET',
                           'EMAIL_CHANGE_CONFIRMATION',
                           'LOGIN_2FA_CODE',
                           'EMAIL_2FA_ENABLE_CONFIRMATION',
                           'EMAIL_2FA_DISABLE_CONFIRMATION',
                           'EMAIL_CHANGE_NOTIFICATION',
                           'GENERIC'
                )
            ),

    CONSTRAINT chk_email_outbox_email_status
        CHECK (
            email_status IN (
                             'PENDING',
                             'SENT',
                             'FAILED',
                             'CANCELLED'
                )
            ),

    CONSTRAINT chk_email_outbox_attempts_non_negative
        CHECK (attempts >= 0),

    CONSTRAINT chk_email_outbox_max_attempts_positive
        CHECK (max_attempts > 0),

    CONSTRAINT chk_email_outbox_html_body_complete
        CHECK (
            (
                body_html_encrypted IS NULL
                    AND body_html_iv IS NULL
                    AND body_html_tag IS NULL
                )
                OR
            (
                body_html_encrypted IS NOT NULL
                    AND body_html_iv IS NOT NULL
                    AND body_html_tag IS NOT NULL
                )
            ),

    CONSTRAINT chk_email_outbox_text_body_complete
        CHECK (
            (
                body_text_encrypted IS NULL
                    AND body_text_iv IS NULL
                    AND body_text_tag IS NULL
                )
                OR
            (
                body_text_encrypted IS NOT NULL
                    AND body_text_iv IS NOT NULL
                    AND body_text_tag IS NOT NULL
                )
            ),

    CONSTRAINT chk_email_outbox_body_present_or_deleted_after_terminal_status
        CHECK (
            body_html_encrypted IS NOT NULL
                OR body_text_encrypted IS NOT NULL
                OR (
                email_body_deleted_at IS NOT NULL
                    AND (
                    (
                        email_status = 'SENT'
                            AND email_sent_at IS NOT NULL
                        )
                        OR
                    (
                        email_status = 'FAILED'
                            AND email_last_failed_at IS NOT NULL
                            AND attempts >= max_attempts
                        )
                        OR
                    (
                        email_status = 'CANCELLED'
                            AND email_cancelled_at IS NOT NULL
                        )
                    )
                )
            ),

    CONSTRAINT chk_email_outbox_body_deleted_consistency
        CHECK (
            email_body_deleted_at IS NULL
                OR (
                body_html_encrypted IS NULL
                    AND body_html_iv IS NULL
                    AND body_html_tag IS NULL
                    AND body_text_encrypted IS NULL
                    AND body_text_iv IS NULL
                    AND body_text_tag IS NULL
                )
            ),

    CONSTRAINT chk_email_outbox_recipient_email_normalized
        CHECK (recipient_email = lower(btrim(recipient_email))),

    CONSTRAINT chk_email_outbox_recipient_email_not_blank
        CHECK (length(btrim(recipient_email)) > 0),

    CONSTRAINT chk_email_outbox_sent_status_consistency
        CHECK (
            email_status <> 'SENT'
                OR email_sent_at IS NOT NULL
            ),

    CONSTRAINT chk_email_outbox_sent_at_status_consistency
        CHECK (
            email_sent_at IS NULL
                OR email_status = 'SENT'
            ),

    CONSTRAINT chk_email_outbox_failed_status_consistency
        CHECK (
            email_status <> 'FAILED'
                OR (
                email_last_failed_at IS NOT NULL
                    AND attempts >= max_attempts
                )
            ),

    CONSTRAINT chk_email_outbox_attempts_not_greater_than_max
        CHECK (attempts <= max_attempts),

    CONSTRAINT chk_email_outbox_subject_iv_length
        CHECK (octet_length(subject_iv) = 12),

    CONSTRAINT chk_email_outbox_subject_tag_length
        CHECK (octet_length(subject_tag) = 16),

    CONSTRAINT chk_email_outbox_html_iv_length
        CHECK (
            body_html_iv IS NULL
                OR octet_length(body_html_iv) = 12
            ),

    CONSTRAINT chk_email_outbox_html_tag_length
        CHECK (
            body_html_tag IS NULL
                OR octet_length(body_html_tag) = 16
            ),

    CONSTRAINT chk_email_outbox_text_iv_length
        CHECK (
            body_text_iv IS NULL
                OR octet_length(body_text_iv) = 12
            ),

    CONSTRAINT chk_email_outbox_text_tag_length
        CHECK (
            body_text_tag IS NULL
                OR octet_length(body_text_tag) = 16
            ),

    CONSTRAINT chk_email_outbox_cancelled_status_consistency
        CHECK (
            email_status <> 'CANCELLED'
                OR email_cancelled_at IS NOT NULL
            ),

    CONSTRAINT chk_email_outbox_cancelled_at_status_consistency
        CHECK (
            email_cancelled_at IS NULL
                OR email_status = 'CANCELLED'
            ),

    CONSTRAINT chk_email_outbox_updated_after_created
        CHECK (email_updated_at >= email_created_at),

    CONSTRAINT chk_email_outbox_sent_after_created
        CHECK (
            email_sent_at IS NULL
                OR email_sent_at >= email_created_at
            ),

    CONSTRAINT chk_email_outbox_last_failed_after_created
        CHECK (
            email_last_failed_at IS NULL
                OR email_last_failed_at >= email_created_at
            ),

    CONSTRAINT chk_email_outbox_cancelled_after_created
        CHECK (
            email_cancelled_at IS NULL
                OR email_cancelled_at >= email_created_at
            ),

    CONSTRAINT chk_email_outbox_body_deleted_after_created
        CHECK (
            email_body_deleted_at IS NULL
                OR email_body_deleted_at >= email_created_at
            ),

    CONSTRAINT chk_email_outbox_encryption_key_id_not_blank
        CHECK (length(btrim(encryption_key_id)) > 0),

    CONSTRAINT chk_email_outbox_subject_encrypted_not_empty
        CHECK (octet_length(subject_encrypted) > 0),

    CONSTRAINT chk_email_outbox_html_encrypted_not_empty
        CHECK (
            body_html_encrypted IS NULL
                OR octet_length(body_html_encrypted) > 0
            ),

    CONSTRAINT chk_email_outbox_text_encrypted_not_empty
        CHECK (
            body_text_encrypted IS NULL
                OR octet_length(body_text_encrypted) > 0
            )
);

CREATE INDEX idx_email_outbox_pending
    ON email_outbox (email_scheduled_at)
    WHERE email_status = 'PENDING'
        AND attempts < max_attempts;

CREATE INDEX idx_email_outbox_user_id
    ON email_outbox (user_id);

CREATE TABLE auth_login_attempts
(
    auth_login_attempt_id UUID PRIMARY KEY      DEFAULT gen_random_uuid(),

    user_id               UUID         REFERENCES users (user_id) ON DELETE SET NULL,

    email_hash            VARCHAR(255) NOT NULL,
    ip_address_hash       VARCHAR(255) NOT NULL,

    login_successful      BOOLEAN      NOT NULL,
    failure_reason        VARCHAR(50),

    auth_login_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_auth_login_attempts_failure_reason
        CHECK (
            (login_successful = true AND failure_reason IS NULL)
                OR
            (
                login_successful = false
                    AND failure_reason IN (
                                           'INVALID_CREDENTIALS',
                                           'USER_DISABLED',
                                           'RATE_LIMITED'
                    )
                )
            ),

    CONSTRAINT chk_auth_login_attempts_email_hash_not_blank
        CHECK (length(btrim(email_hash)) > 0),

    CONSTRAINT chk_auth_login_attempts_ip_address_hash_not_blank
        CHECK (length(btrim(ip_address_hash)) > 0)
);

CREATE INDEX idx_auth_login_attempts_email_success_recent
    ON auth_login_attempts (email_hash, login_successful, auth_login_attempt_at DESC);

CREATE INDEX idx_auth_login_attempts_ip_success_recent
    ON auth_login_attempts (ip_address_hash, login_successful, auth_login_attempt_at DESC);

CREATE INDEX idx_auth_login_attempts_email_ip_success_recent
    ON auth_login_attempts (email_hash, ip_address_hash, login_successful, auth_login_attempt_at DESC);

CREATE INDEX idx_auth_login_attempts_attempt_at
    ON auth_login_attempts (auth_login_attempt_at);

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
