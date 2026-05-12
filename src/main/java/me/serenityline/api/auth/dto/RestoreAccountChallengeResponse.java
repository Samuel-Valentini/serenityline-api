package me.serenityline.api.auth.dto;

import java.time.OffsetDateTime;

public record RestoreAccountChallengeResponse(
        String restoreToken,
        OffsetDateTime restoreTokenExpiresAt
) {
    public static RestoreAccountChallengeResponse of(
            String restoreToken,
            OffsetDateTime restoreTokenExpiresAt
    ) {
        return new RestoreAccountChallengeResponse(
                restoreToken,
                restoreTokenExpiresAt
        );
    }
}