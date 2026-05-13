package me.serenityline.api.security.jwt;

import java.time.OffsetDateTime;

public record JwtAccessToken(
        String token,
        OffsetDateTime expiresAt
) {
}