package me.serenityline.api.auth.entity;

import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.entity.UserGroup;
import me.serenityline.api.user.entity.UserRole;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthActionTokenTargetValueTest {

    private static final String USER_GROUP_NAME = "Samuel household";
    private static final String USER_NAME = "Samuel";
    private static final String USER_EMAIL = "samuel@example.com";
    private static final String USER_PASSWORD_HASH = "$2a$12$validPasswordHashForEntityTestOnly";
    private static final UserRole USER_ROLE = UserRole.OWNER;

    private static final String VALID_TOKEN_HASH = "valid-token-hash";
    private static final String NORMALIZED_NEW_EMAIL = "new.email@example.com";
    private static final String RAW_NEW_EMAIL_WITH_SPACES_AND_UPPERCASE = "  New.Email@Example.COM  ";
    private static final String TOO_LONG_EMAIL = "a".repeat(309) + "@example.com";

    private static final int TOKEN_EXPIRATION_MINUTES = 15;

    private static User createUser() {
        UserGroup userGroup = new UserGroup(USER_GROUP_NAME);

        return new User(
                USER_NAME,
                USER_EMAIL,
                userGroup,
                USER_ROLE,
                USER_PASSWORD_HASH
        );
    }

    private static OffsetDateTime validExpiresAt() {
        return OffsetDateTime.now().plusMinutes(TOKEN_EXPIRATION_MINUTES);
    }

    @Test
    void emailChangeConfirmationShouldRequireTargetValueOnPersist() {
        User user = createUser();
        OffsetDateTime expiresAt = validExpiresAt();
        AuthActionToken token = new AuthActionToken(
                user,
                VALID_TOKEN_HASH,
                AuthActionTokenType.EMAIL_CHANGE_CONFIRMATION,
                expiresAt
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                token::onCreate
        );

        assertThat(exception)
                .hasMessage("authActionToken.targetValue.required");
    }

    @Test
    void emailChangeConfirmationShouldNormalizeTargetValue() {
        User user = createUser();
        OffsetDateTime expiresAt = validExpiresAt();
        AuthActionToken token = new AuthActionToken(
                user,
                VALID_TOKEN_HASH,
                AuthActionTokenType.EMAIL_CHANGE_CONFIRMATION,
                expiresAt
        );

        token.setEmailChangeTargetValue(RAW_NEW_EMAIL_WITH_SPACES_AND_UPPERCASE);
        token.onCreate();

        assertThat(token.getAuthActionTargetValue())
                .isEqualTo(NORMALIZED_NEW_EMAIL);
    }

    @Test
    void emailChangeConfirmationShouldReturnRequiredTargetValue() {
        User user = createUser();
        OffsetDateTime expiresAt = validExpiresAt();
        AuthActionToken token = new AuthActionToken(
                user,
                VALID_TOKEN_HASH,
                AuthActionTokenType.EMAIL_CHANGE_CONFIRMATION,
                expiresAt
        );

        token.setEmailChangeTargetValue(RAW_NEW_EMAIL_WITH_SPACES_AND_UPPERCASE);
        token.onCreate();

        assertThat(token.requireEmailChangeTargetValue())
                .isEqualTo(NORMALIZED_NEW_EMAIL);
    }

    @Test
    void emailChangeConfirmationShouldRejectTooLongTargetValue() {
        User user = createUser();
        OffsetDateTime expiresAt = validExpiresAt();
        AuthActionToken token = new AuthActionToken(
                user,
                VALID_TOKEN_HASH,
                AuthActionTokenType.EMAIL_CHANGE_CONFIRMATION,
                expiresAt
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> token.setEmailChangeTargetValue(TOO_LONG_EMAIL)
        );

        assertThat(exception)
                .hasMessage("authActionToken.targetValue.tooLong");
    }

    @Test
    void nonEmailChangeTokenShouldRejectEmailChangeTargetValue() {
        User user = createUser();
        OffsetDateTime expiresAt = validExpiresAt();
        AuthActionToken token = new AuthActionToken(
                user,
                VALID_TOKEN_HASH,
                AuthActionTokenType.PASSWORD_RESET,
                expiresAt
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> token.setEmailChangeTargetValue(NORMALIZED_NEW_EMAIL)
        );

        assertThat(exception)
                .hasMessage("authActionToken.targetValue.notAllowedForType");
    }

    @Test
    void nonEmailChangeTokenShouldPersistWithoutTargetValue() {
        User user = createUser();
        OffsetDateTime expiresAt = validExpiresAt();
        AuthActionToken token = new AuthActionToken(
                user,
                VALID_TOKEN_HASH,
                AuthActionTokenType.PASSWORD_RESET,
                expiresAt
        );

        token.onCreate();

        assertThat(token.getAuthActionTargetValue())
                .isNull();
    }
}