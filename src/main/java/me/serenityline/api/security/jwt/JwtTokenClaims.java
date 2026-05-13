package me.serenityline.api.security.jwt;

import java.time.OffsetDateTime;
import java.util.UUID;

public record JwtTokenClaims(
        UUID userId,
        Long tokenVersion,
        OffsetDateTime issuedAt,
        OffsetDateTime expiresAt
) {
}