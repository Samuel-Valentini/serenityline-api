package me.serenityline.api.auth.dto;

import java.util.Objects;

public record LoginResult(
        AuthenticatedLoginResult authenticatedLogin,
        RestoreAccountChallengeResponse restoreAccountChallenge,
        EmailVerificationRequiredResponse emailVerificationRequiredResponse
) {
    public static LoginResult authenticated(AuthenticatedLoginResult authenticatedLogin) {
        return new LoginResult(
                Objects.requireNonNull(authenticatedLogin, "authenticatedLogin"),
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

    public boolean isAuthenticated() {
        return authenticatedLogin != null;
    }

    public boolean isRestoreRequired() {
        return restoreAccountChallenge != null;
    }

    public boolean isEmailVerificationRequired() {
        return emailVerificationRequiredResponse != null;
    }
}