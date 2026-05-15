package me.serenityline.api.user.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Email2faChallengeResponse(
        UUID challengeId,
        OffsetDateTime codeExpiresAt
) {
}