package me.serenityline.api.auth.cleanup;

public record AuthSessionCleanupResult(
        int refreshTokensDeleted,
        int userSessionsDeleted
) {

    public int totalDeleted() {
        return refreshTokensDeleted + userSessionsDeleted;
    }
}