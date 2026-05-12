package me.serenityline.api.auth.dto;

public record LoginResult(
        LoginResponse loginResponse,
        RestoreAccountChallengeResponse restoreAccountChallenge
) {
    public static LoginResult authenticated(LoginResponse loginResponse) {
        return new LoginResult(loginResponse, null);
    }

    public static LoginResult restoreRequired(RestoreAccountChallengeResponse restoreAccountChallenge) {
        return new LoginResult(null, restoreAccountChallenge);
    }

    public boolean isRestoreRequired() {
        return restoreAccountChallenge != null;
    }
}