package me.serenityline.api.auth.entity;

import me.serenityline.api.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AuthActionTokenAttemptLimitTest {

    private static AuthActionToken defaultToken() {
        return new AuthActionToken(
                mock(User.class),
                "token-hash",
                AuthActionTokenType.LOGIN_2FA_CODE,
                OffsetDateTime.now().plusMinutes(10)
        );
    }

    @Test
    void constructorShouldUseDefaultMaxAttemptsAndZeroFailedAttempts() {
        AuthActionToken token = defaultToken();

        token.onCreate();

        assertThat(token.getAuthActionFailedAttemptCount()).isZero();
        assertThat(token.getAuthActionLastFailedAt()).isNull();
        assertThat(token.getAuthActionMaxAttempts()).isEqualTo(5);
        assertThat(token.hasReachedFailedAttemptLimit()).isFalse();
        assertThat(token.isPending()).isTrue();
    }

    @Test
    void constructorShouldUseCustomMaxAttempts() {
        AuthActionToken token = new AuthActionToken(
                mock(User.class),
                "token-hash",
                AuthActionTokenType.LOGIN_2FA_CODE,
                OffsetDateTime.now().plusMinutes(10),
                3
        );

        token.onCreate();

        assertThat(token.getAuthActionMaxAttempts()).isEqualTo(3);
        assertThat(token.getAuthActionFailedAttemptCount()).isZero();
    }

    @Test
    void constructorShouldRejectMaxAttemptsBelowMinimum() {
        assertThatThrownBy(() -> new AuthActionToken(
                mock(User.class),
                "token-hash",
                AuthActionTokenType.LOGIN_2FA_CODE,
                OffsetDateTime.now().plusMinutes(10),
                0
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("authActionToken.maxAttempts.invalid");
    }

    @Test
    void constructorShouldRejectMaxAttemptsAboveMaximum() {
        assertThatThrownBy(() -> new AuthActionToken(
                mock(User.class),
                "token-hash",
                AuthActionTokenType.LOGIN_2FA_CODE,
                OffsetDateTime.now().plusMinutes(10),
                21
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("authActionToken.maxAttempts.invalid");
    }

    @Test
    void constructorShouldTrimTokenHash() {
        AuthActionToken token = new AuthActionToken(
                mock(User.class),
                "  token-hash  ",
                AuthActionTokenType.LOGIN_2FA_CODE,
                OffsetDateTime.now().plusMinutes(10)
        );

        assertThat(token.getAuthActionTokenHash()).isEqualTo("token-hash");
    }

    @Test
    void constructorShouldRejectBlankTokenHash() {
        assertThatThrownBy(() -> new AuthActionToken(
                mock(User.class),
                "   ",
                AuthActionTokenType.LOGIN_2FA_CODE,
                OffsetDateTime.now().plusMinutes(10)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("authActionToken.hash.required");
    }

    @Test
    void recordFailedAttemptShouldIncrementCounterAndSetLastFailedAt() {
        AuthActionToken token = new AuthActionToken(
                mock(User.class),
                "token-hash",
                AuthActionTokenType.LOGIN_2FA_CODE,
                OffsetDateTime.now().plusMinutes(10),
                3
        );

        token.onCreate();

        token.recordFailedAttempt();

        assertThat(token.getAuthActionFailedAttemptCount()).isEqualTo(1);
        assertThat(token.getAuthActionLastFailedAt()).isNotNull();
        assertThat(token.isRevoked()).isFalse();
        assertThat(token.isUsed()).isFalse();
        assertThat(token.isPending()).isTrue();
        assertThat(token.hasReachedFailedAttemptLimit()).isFalse();
    }

    @Test
    void recordFailedAttemptShouldRevokeTokenWhenMaxAttemptsIsReached() {
        AuthActionToken token = new AuthActionToken(
                mock(User.class),
                "token-hash",
                AuthActionTokenType.LOGIN_2FA_CODE,
                OffsetDateTime.now().plusMinutes(10),
                2
        );

        token.onCreate();

        token.recordFailedAttempt();

        assertThat(token.getAuthActionFailedAttemptCount()).isEqualTo(1);
        assertThat(token.isRevoked()).isFalse();

        token.recordFailedAttempt();

        assertThat(token.getAuthActionFailedAttemptCount()).isEqualTo(2);
        assertThat(token.getAuthActionLastFailedAt()).isNotNull();
        assertThat(token.hasReachedFailedAttemptLimit()).isTrue();
        assertThat(token.isRevoked()).isTrue();
        assertThat(token.getAuthActionRevokedAt()).isNotNull();
        assertThat(token.isPending()).isFalse();
    }

    @Test
    void recordFailedAttemptShouldRejectTokenAlreadyAtLimit() {
        AuthActionToken token = new AuthActionToken(
                mock(User.class),
                "token-hash",
                AuthActionTokenType.LOGIN_2FA_CODE,
                OffsetDateTime.now().plusMinutes(10),
                1
        );

        token.onCreate();

        token.recordFailedAttempt();

        assertThatThrownBy(token::recordFailedAttempt)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("authActionToken.revoked");
    }

    @Test
    void recordFailedAttemptShouldRejectUsedToken() {
        AuthActionToken token = defaultToken();

        token.onCreate();
        token.markUsed(AuthActionTokenType.LOGIN_2FA_CODE);

        assertThatThrownBy(token::recordFailedAttempt)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("authActionToken.alreadyUsed");
    }

    @Test
    void recordFailedAttemptShouldRejectRevokedToken() {
        AuthActionToken token = defaultToken();

        token.onCreate();
        token.revoke();

        assertThatThrownBy(token::recordFailedAttempt)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("authActionToken.revoked");
    }

    @Test
    void recordFailedAttemptShouldRejectExpiredToken() {
        AuthActionToken token = new AuthActionToken(
                mock(User.class),
                "token-hash",
                AuthActionTokenType.LOGIN_2FA_CODE,
                OffsetDateTime.now().minusMinutes(1)
        );

        ReflectionTestUtils.setField(
                token,
                "authActionCreatedAt",
                OffsetDateTime.now().minusMinutes(10)
        );

        assertThatThrownBy(token::recordFailedAttempt)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("authActionToken.expired");
    }

    @Test
    void markUsedShouldBeRejectedAfterFailedAttemptLimitRevokedToken() {
        AuthActionToken token = new AuthActionToken(
                mock(User.class),
                "token-hash",
                AuthActionTokenType.LOGIN_2FA_CODE,
                OffsetDateTime.now().plusMinutes(10),
                1
        );

        token.onCreate();
        token.recordFailedAttempt();

        assertThatThrownBy(() -> token.markUsed(AuthActionTokenType.LOGIN_2FA_CODE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("authActionToken.revoked");
    }

    @Test
    void markUsedShouldKeepTokenConsistentAfterPreviousFailedAttemptsBelowLimit() {
        AuthActionToken token = new AuthActionToken(
                mock(User.class),
                "token-hash",
                AuthActionTokenType.LOGIN_2FA_CODE,
                OffsetDateTime.now().plusMinutes(10),
                3
        );

        token.onCreate();
        token.recordFailedAttempt();
        token.markUsed(AuthActionTokenType.LOGIN_2FA_CODE);

        assertThat(token.getAuthActionFailedAttemptCount()).isEqualTo(1);
        assertThat(token.getAuthActionLastFailedAt()).isNotNull();
        assertThat(token.isUsed()).isTrue();
        assertThat(token.isRevoked()).isFalse();
        assertThat(token.getAuthActionUsedAt()).isNotNull();
        assertThat(token.getAuthActionUsedAt()).isAfterOrEqualTo(token.getAuthActionLastFailedAt());
    }
}