package me.serenityline.api.auth.dto;

import java.util.Objects;

public record RestoreAccountResult(
        LoginResponse loginResponse,
        RestoreAccountVerificationRequiredResponse verificationRequiredResponse
) {
    public static RestoreAccountResult authenticated(LoginResponse loginResponse) {
        return new RestoreAccountResult(
                Objects.requireNonNull(loginResponse, "loginResponse"),
                null
        );
    }

    public static RestoreAccountResult verificationRequired(
            RestoreAccountVerificationRequiredResponse verificationRequiredResponse
    ) {
        return new RestoreAccountResult(
                null,
                Objects.requireNonNull(verificationRequiredResponse, "verificationRequiredResponse")
        );
    }

    public boolean isVerificationRequired() {
        return verificationRequiredResponse != null;
    }
}