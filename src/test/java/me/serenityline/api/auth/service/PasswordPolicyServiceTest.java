package me.serenityline.api.auth.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyServiceTest {

    private final PasswordPolicyService passwordPolicyService = new PasswordPolicyService();

    @Test
    void shouldRejectNullPassword() {
        assertThatThrownBy(() -> passwordPolicyService.validateRegistrationPassword(
                null,
                "Samuel",
                "samuel@example.com"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("auth.password.required");
    }

    @Test
    void shouldRejectBlankPassword() {
        assertThatThrownBy(() -> passwordPolicyService.validateRegistrationPassword(
                "   ",
                "Samuel",
                "samuel@example.com"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("auth.password.required");
    }

    @Test
    void shouldRejectTooShortPassword() {
        assertThatThrownBy(() -> passwordPolicyService.validateRegistrationPassword(
                "short",
                "Samuel",
                "samuel@example.com"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("auth.password.invalidLength");
    }

    @Test
    void shouldRejectTooLongPassword() {
        String password = "a".repeat(129);

        assertThatThrownBy(() -> passwordPolicyService.validateRegistrationPassword(
                password,
                "Samuel",
                "samuel@example.com"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("auth.password.invalidLength");
    }

    @Test
    void shouldRejectWeakPassword() {
        assertThatThrownBy(() -> passwordPolicyService.validateRegistrationPassword(
                "password12345",
                "Samuel",
                "samuel@example.com"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("auth.password.tooWeak");
    }

    @Test
    void shouldAcceptStrongPassword() {
        assertThatCode(() -> passwordPolicyService.validateRegistrationPassword(
                "TrenoMareLuna2026!",
                "Samuel",
                "samuel@example.com"
        )).doesNotThrowAnyException();
    }
}