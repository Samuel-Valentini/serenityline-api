package me.serenityline.api.user.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class UserPreferencesTest {

    private static final String DEFAULT_USER_NAME = "Samuel";
    private static final String DEFAULT_EMAIL = "samuel@example.com";
    private static final String REMINDERS_ENABLED_EMAIL = "samuel.enabled@example.com";
    private static final String REMINDERS_DISABLED_EMAIL = "samuel.disabled@example.com";
    private static final String DEFAULT_PASSWORD_HASH = "encoded-password-hash";

    private static final UserRole DEFAULT_USER_ROLE = UserRole.values()[0];
    private static final UserPlatformRole DEFAULT_PLATFORM_ROLE = UserPlatformRole.USER;
    private static final String DEFAULT_PREFERRED_LOCALE = "it-IT";
    private static final PreferredTheme DEFAULT_PREFERRED_THEME = PreferredTheme.DEFAULT;

    private static final boolean DEFAULT_WANTS_INVOICE = false;
    private static final boolean DEFAULT_USER_IS_ENABLED = false;
    private static final long DEFAULT_TOKEN_VERSION = 0L;

    private static final boolean PAYMENT_EMAIL_REMINDERS_ENABLED = true;
    private static final boolean PAYMENT_EMAIL_REMINDERS_DISABLED = false;

    private static User defaultShortConstructorUser() {
        return new User(
                DEFAULT_USER_NAME,
                DEFAULT_EMAIL,
                userGroup(),
                DEFAULT_USER_ROLE,
                DEFAULT_PASSWORD_HASH
        );
    }

    private static User defaultLongConstructorUser(
            String email,
            boolean paymentEmailRemindersEnabled
    ) {
        return new User(
                DEFAULT_USER_NAME,
                email,
                userGroup(),
                DEFAULT_USER_ROLE,
                DEFAULT_PLATFORM_ROLE,
                DEFAULT_PREFERRED_LOCALE,
                DEFAULT_PREFERRED_THEME,
                DEFAULT_WANTS_INVOICE,
                paymentEmailRemindersEnabled,
                DEFAULT_PASSWORD_HASH,
                DEFAULT_USER_IS_ENABLED,
                DEFAULT_TOKEN_VERSION
        );
    }

    private static UserGroup userGroup() {
        return mock(UserGroup.class);
    }

    @Test
    void shortConstructorShouldDisableEmailTwoFactorAndEnablePaymentEmailRemindersByDefault() {
        User user = defaultShortConstructorUser();

        assertThat(user.isEmailTwoFactorEnabled()).isFalse();
        assertThat(user.isPaymentEmailRemindersEnabled()).isTrue();
    }

    @Test
    void longConstructorShouldAlwaysDisableEmailTwoFactor() {
        User user = defaultLongConstructorUser(
                DEFAULT_EMAIL,
                PAYMENT_EMAIL_REMINDERS_ENABLED
        );

        assertThat(user.isEmailTwoFactorEnabled()).isFalse();
    }

    @Test
    void longConstructorShouldUseProvidedPaymentEmailReminderPreference() {
        User userWithRemindersEnabled = defaultLongConstructorUser(
                REMINDERS_ENABLED_EMAIL,
                PAYMENT_EMAIL_REMINDERS_ENABLED
        );

        User userWithRemindersDisabled = defaultLongConstructorUser(
                REMINDERS_DISABLED_EMAIL,
                PAYMENT_EMAIL_REMINDERS_DISABLED
        );

        assertThat(userWithRemindersEnabled.isPaymentEmailRemindersEnabled()).isTrue();
        assertThat(userWithRemindersDisabled.isPaymentEmailRemindersEnabled()).isFalse();
    }

    @Test
    void emailTwoFactorCanBeEnabledAndDisabledThroughDomainMethods() {
        User user = defaultShortConstructorUser();

        assertThat(user.isEmailTwoFactorEnabled()).isFalse();

        user.enableEmailTwoFactorEnabled();

        assertThat(user.isEmailTwoFactorEnabled()).isTrue();

        user.disableEmailTwoFactorEnabled();

        assertThat(user.isEmailTwoFactorEnabled()).isFalse();
    }

    @Test
    void paymentEmailRemindersCanBeEnabledAndDisabledThroughDomainMethods() {
        User user = defaultShortConstructorUser();

        assertThat(user.isPaymentEmailRemindersEnabled()).isTrue();

        user.disablePaymentEmailReminders();

        assertThat(user.isPaymentEmailRemindersEnabled()).isFalse();

        user.enablePaymentEmailReminders();

        assertThat(user.isPaymentEmailRemindersEnabled()).isTrue();
    }

    @Test
    void onCreateShouldKeepNewPreferenceDefaultsValid() {
        User user = defaultShortConstructorUser();

        user.onCreate();

        assertThat(user.isEmailTwoFactorEnabled()).isFalse();
        assertThat(user.isPaymentEmailRemindersEnabled()).isTrue();
        assertThat(user.getUserCreatedAt()).isNotNull();
        assertThat(user.getUserUpdatedAt()).isNotNull();
    }
}