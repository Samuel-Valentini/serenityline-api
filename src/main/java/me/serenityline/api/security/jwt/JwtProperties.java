package me.serenityline.api.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "serenityline.auth.jwt")
public record JwtProperties(
        String issuer,
        Duration accessTokenTtl,
        String secret
) {
    private static final Duration MIN_ACCESS_TOKEN_TTL = Duration.ofMinutes(1);
    private static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("security.jwt.issuer.required");
        }

        if (accessTokenTtl == null
                || accessTokenTtl.isZero()
                || accessTokenTtl.isNegative()
                || accessTokenTtl.compareTo(MIN_ACCESS_TOKEN_TTL) < 0) {
            throw new IllegalStateException("security.jwt.accessTokenTtl.invalid");
        }

        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("security.jwt.secret.required");
        }

        if (secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("security.jwt.secret.tooShort");
        }
    }
}