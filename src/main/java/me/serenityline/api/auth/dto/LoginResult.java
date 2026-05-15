package me.serenityline.api.auth.dto;

import java.util.Objects;

public record LoginResult(
        AuthenticatedLoginResult authenticatedLogin,
        RestoreAccountChallengeResponse restoreAccountChallenge,
        EmailVerificationRequiredResponse emailVerificationRequiredResponse,
        Login2faRequiredResponse login2faRequiredResponse
) {
    public static LoginResult authenticated(AuthenticatedLoginResult authenticatedLogin) {
        return new LoginResult(
                Objects.requireNonNull(authenticatedLogin, "authenticatedLogin"),
                null,
                null,
                null
        );
    }

    public static LoginResult restoreRequired(RestoreAccountChallengeResponse restoreAccountChallenge) {
        return new LoginResult(
                null,
                Objects.requireNonNull(restoreAccountChallenge, "restoreAccountChallenge"),
                null,
                null
        );
    }

    public static LoginResult emailVerificationRequired(
            EmailVerificationRequiredResponse emailVerificationRequiredResponse
    ) {
        return new LoginResult(
                null,
                null,
                Objects.requireNonNull(emailVerificationRequiredResponse, "emailVerificationRequiredResponse"),
                null
        );
    }

    public static LoginResult login2faRequired(Login2faRequiredResponse login2faRequiredResponse) {
        return new LoginResult(
                null,
                null,
                null,
                Objects.requireNonNull(login2faRequiredResponse, "login2faRequiredResponse")
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

    public boolean isLogin2faRequired() {
        return login2faRequiredResponse != null;
    }
}