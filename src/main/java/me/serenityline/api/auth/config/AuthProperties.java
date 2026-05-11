package me.serenityline.api.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "serenityline.auth")
public record AuthProperties(
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        Duration sessionTtl
) {
    public AuthProperties {
        if (accessTokenTtl == null || accessTokenTtl.isNegative() || accessTokenTtl.isZero()) {
            throw new IllegalArgumentException("auth.accessTokenTtl.invalid");
        }

        if (refreshTokenTtl == null || refreshTokenTtl.isNegative() || refreshTokenTtl.isZero()) {
            throw new IllegalArgumentException("auth.refreshTokenTtl.invalid");
        }

        if (sessionTtl == null || sessionTtl.isNegative() || sessionTtl.isZero()) {
            throw new IllegalArgumentException("auth.sessionTtl.invalid");
        }

        if (refreshTokenTtl.compareTo(sessionTtl) > 0) {
            throw new IllegalArgumentException("auth.refreshTokenTtl.longerThanSessionTtl");
        }
    }
}