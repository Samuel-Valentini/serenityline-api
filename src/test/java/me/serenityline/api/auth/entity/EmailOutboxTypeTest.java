package me.serenityline.api.auth.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailOutboxTypeTest {

    @Test
    void emailOutboxTypeShouldContainAllDatabaseAllowedValues() {
        assertThat(EmailOutboxType.values())
                .containsExactly(
                        EmailOutboxType.EMAIL_VERIFICATION,
                        EmailOutboxType.PASSWORD_RESET,
                        EmailOutboxType.EMAIL_CHANGE_CONFIRMATION,
                        EmailOutboxType.LOGIN_2FA_CODE,
                        EmailOutboxType.EMAIL_2FA_ENABLE_CONFIRMATION,
                        EmailOutboxType.EMAIL_2FA_DISABLE_CONFIRMATION,
                        EmailOutboxType.EMAIL_CHANGE_NOTIFICATION,
                        EmailOutboxType.GENERIC
                );
    }
}