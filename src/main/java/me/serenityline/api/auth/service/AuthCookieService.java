package me.serenityline.api.auth.service;

import me.serenityline.api.auth.config.RefreshTokenProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class AuthCookieService {

    private static final String REFRESH_COOKIE_PATH = "/api/auth";

    private final RefreshTokenProperties refreshTokenProperties;

    public AuthCookieService(RefreshTokenProperties refreshTokenProperties) {
        this.refreshTokenProperties = Objects.requireNonNull(refreshTokenProperties, "refreshTokenProperties");
    }

    public ResponseCookie createRefreshTokenCookie(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("auth.refreshToken.required");
        }

        return ResponseCookie.from(refreshTokenProperties.cookieName(), refreshToken)
                .httpOnly(true)
                .secure(refreshTokenProperties.cookieSecure())
                .sameSite(refreshTokenProperties.cookieSameSite())
                .path(REFRESH_COOKIE_PATH)
                .maxAge(refreshTokenProperties.ttl())
                .build();
    }

    public ResponseCookie clearRefreshTokenCookie() {
        return ResponseCookie.from(refreshTokenProperties.cookieName(), "")
                .httpOnly(true)
                .secure(refreshTokenProperties.cookieSecure())
                .sameSite(refreshTokenProperties.cookieSameSite())
                .path(REFRESH_COOKIE_PATH)
                .maxAge(0)
                .build();
    }

    public String refreshCookieName() {
        return refreshTokenProperties.cookieName();
    }
}