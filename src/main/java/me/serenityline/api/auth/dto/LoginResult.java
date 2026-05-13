package me.serenityline.api.auth.dto;

import java.util.Objects;

public record LoginResult(
        LoginResponse loginResponse,
        RestoreAccountChallengeResponse restoreAccountChallenge,
        EmailVerificationRequiredResponse emailVerificationRequiredResponse
) {
    public static LoginResult authenticated(LoginResponse loginResponse) {
        return new LoginResult(
                Objects.requireNonNull(loginResponse, "loginResponse"),
                null,
                null
        );
    }

    public static LoginResult restoreRequired(RestoreAccountChallengeResponse restoreAccountChallenge) {
        return new LoginResult(
                null,
                Objects.requireNonNull(restoreAccountChallenge, "restoreAccountChallenge"),
                null
        );
    }

    public static LoginResult emailVerificationRequired(
            EmailVerificationRequiredResponse emailVerificationRequiredResponse
    ) {
        return new LoginResult(
                null,
                null,
                Objects.requireNonNull(emailVerificationRequiredResponse, "emailVerificationRequiredResponse")
        );
    }

    public boolean isRestoreRequired() {
        return restoreAccountChallenge != null;
    }

    public boolean isEmailVerificationRequired() {
        return emailVerificationRequiredResponse != null;
    }
}