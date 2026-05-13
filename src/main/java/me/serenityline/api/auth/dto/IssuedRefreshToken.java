package me.serenityline.api.auth.dto;

import java.time.OffsetDateTime;

public record IssuedRefreshToken(
        String token,
        OffsetDateTime expiresAt
) {
}