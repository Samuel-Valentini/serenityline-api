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
                           'EMAIL_CHANGE_NOTIFICATION',
                           'GENERIC'
                )
            );