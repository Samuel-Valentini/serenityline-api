package me.serenityline.api.auth.dto;

import me.serenityline.api.security.jwt.JwtAccessToken;

import java.time.OffsetDateTime;

public record AuthenticatedResponse(
        String accessToken,
        OffsetDateTime accessTokenExpiresAt,
        LoginResponse user
) {
    public static AuthenticatedResponse of(
            JwtAccessToken accessToken,
            LoginResponse user
    ) {
        if (accessToken == null) {
            throw new IllegalArgumentException("auth.accessToken.required");
        }

        if (user == null) {
            throw new IllegalArgumentException("auth.login.user.required");
        }

        return new AuthenticatedResponse(
                accessToken.token(),
                accessToken.expiresAt(),
                user
        );
    }
}