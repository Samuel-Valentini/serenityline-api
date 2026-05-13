package me.serenityline.api.auth.dto;

import java.util.Objects;

public record RestoreAccountResult(
        LoginResponse loginResponse,
        EmailVerificationRequiredResponse emailVerificationRequiredResponse
) {
    public static RestoreAccountResult authenticated(LoginResponse loginResponse) {
        return new RestoreAccountResult(
                Objects.requireNonNull(loginResponse, "loginResponse"),
                null
        );
    }

    public static RestoreAccountResult emailVerificationRequired(
            EmailVerificationRequiredResponse emailVerificationRequiredResponse
    ) {
        return new RestoreAccountResult(
                null,
                Objects.requireNonNull(emailVerificationRequiredResponse, "emailVerificationRequiredResponse")
        );
    }

    public boolean isEmailVerificationRequired() {
        return emailVerificationRequiredResponse != null;
    }
}