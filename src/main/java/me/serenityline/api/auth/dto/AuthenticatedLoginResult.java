package me.serenityline.api.auth.dto;

import me.serenityline.api.security.jwt.JwtAccessToken;

import java.util.Objects;

public record AuthenticatedLoginResult(
        LoginResponse user,
        JwtAccessToken accessToken,
        IssuedRefreshToken refreshToken
) {
    public AuthenticatedLoginResult {
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(accessToken, "accessToken");
        Objects.requireNonNull(refreshToken, "refreshToken");
    }
}