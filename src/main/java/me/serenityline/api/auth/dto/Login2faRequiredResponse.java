package me.serenityline.api.auth.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Login2faRequiredResponse(
        UUID login2faChallengeId,
        OffsetDateTime login2faCodeExpiresAt
) {
    public Login2faRequiredResponse {
        if (login2faChallengeId == null) {
            throw new IllegalArgumentException("auth.login2fa.challengeId.required");
        }

        if (login2faCodeExpiresAt == null) {
            throw new IllegalArgumentException("auth.login2fa.expiresAt.required");
        }
    }
}