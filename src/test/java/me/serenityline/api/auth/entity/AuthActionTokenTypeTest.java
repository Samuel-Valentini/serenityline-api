package me.serenityline.api.auth.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthActionTokenTypeTest {

    @Test
    void authActionTokenTypeShouldContainAllDatabaseAllowedValues() {
        assertThat(AuthActionTokenType.values())
                .containsExactly(
                        AuthActionTokenType.PASSWORD_RESET,
                        AuthActionTokenType.EMAIL_VERIFICATION,
                        AuthActionTokenType.EMAIL_VERIFICATION_RESEND,
                        AuthActionTokenType.LOGIN_2FA_CODE,
                        AuthActionTokenType.EMAIL_2FA_ENABLE_CONFIRMATION,
                        AuthActionTokenType.EMAIL_2FA_DISABLE_CONFIRMATION,
                        AuthActionTokenType.EMAIL_CHANGE_CONFIRMATION,
                        AuthActionTokenType.RESTORE_ACCOUNT,
                        AuthActionTokenType.USER_INVITATION
                );
    }
}