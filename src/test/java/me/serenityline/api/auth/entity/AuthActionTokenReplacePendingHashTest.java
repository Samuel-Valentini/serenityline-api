package me.serenityline.api.auth.entity;

import me.serenityline.api.user.entity.User;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AuthActionTokenReplacePendingHashTest {

    private static final String INITIAL_HASH = "initial-token-hash";
    private static final String REPLACEMENT_HASH = "replacement-token-hash";

    private static AuthActionToken defaultToken() {
        return new AuthActionToken(
                mock(User.class),
                INITIAL_HASH,
                AuthActionTokenType.LOGIN_2FA_CODE,
                OffsetDateTime.now().plusMinutes(10)
        );
    }

    @Test
    void replacePendingHashShouldReplaceHashOnFreshTokenBeforeCreateCallback() {
        AuthActionToken token = defaultToken();

        token.replacePendingHash(REPLACEMENT_HASH);

        assertThat(token.getAuthActionTokenHash()).isEqualTo(REPLACEMENT_HASH);
    }

    @Test
    void replacePendingHashShouldReplaceHashOnFreshPersistableToken() {
        AuthActionToken token = defaultToken();

        token.onCreate();

        token.replacePendingHash(REPLACEMENT_HASH);

        assertThat(token.getAuthActionTokenHash()).isEqualTo(REPLACEMENT_HASH);
        assertThat(token.isPending()).isTrue();
        assertThat(token.getAuthActionFailedAttemptCount()).isZero();
        assertThat(token.getAuthActionLastFailedAt()).isNull();
    }

    @Test
    void replacePendingHashShouldTrimReplacementHash() {
        AuthActionToken token = defaultToken();

        token.replacePendingHash("  " + REPLACEMENT_HASH + "  ");

        assertThat(token.getAuthActionTokenHash()).isEqualTo(REPLACEMENT_HASH);
    }

    @Test
    void replacePendingHashShouldRejectBlankReplacementHash() {
        AuthActionToken token = defaultToken();

        assertThatThrownBy(() -> token.replacePendingHash("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("authActionToken.hash.required");
    }

    @Test
    void replacePendingHashShouldRejectUsedToken() {
        AuthActionToken token = defaultToken();

        token.onCreate();
        token.markUsed(AuthActionTokenType.LOGIN_2FA_CODE);

        assertThatThrownBy(() -> token.replacePendingHash(REPLACEMENT_HASH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("authActionToken.alreadyUsed");
    }

    @Test
    void replacePendingHashShouldRejectRevokedToken() {
        AuthActionToken token = defaultToken();

        token.onCreate();
        token.revoke();

        assertThatThrownBy(() -> token.replacePendingHash(REPLACEMENT_HASH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("authActionToken.revoked");
    }

    @Test
    void replacePendingHashShouldRejectTokenWithFailedAttempts() {
        AuthActionToken token = new AuthActionToken(
                mock(User.class),
                INITIAL_HASH,
                AuthActionTokenType.LOGIN_2FA_CODE,
                OffsetDateTime.now().plusMinutes(10),
                3
        );

        token.onCreate();
        token.recordFailedAttempt();

        assertThatThrownBy(() -> token.replacePendingHash(REPLACEMENT_HASH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("authActionToken.failedAttemptsAlreadyRecorded");
    }
}