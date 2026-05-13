package me.serenityline.api.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Set;

@ConfigurationProperties(prefix = "serenityline.auth.refresh-token")
public record RefreshTokenProperties(
        Duration ttl,
        String cookieName,
        boolean cookieSecure,
        String cookieSameSite
) {
    private static final Duration MIN_TTL = Duration.ofHours(1);
    private static final Set<String> VALID_SAME_SITE = Set.of("Strict", "Lax", "None");

    public RefreshTokenProperties {
        if (ttl == null || ttl.isZero() || ttl.isNegative() || ttl.compareTo(MIN_TTL) < 0) {
            throw new IllegalStateException("auth.refreshToken.ttl.invalid");
        }

        if (cookieName == null || cookieName.isBlank()) {
            throw new IllegalStateException("auth.refreshToken.cookieName.required");
        }

        if (cookieSameSite == null || !VALID_SAME_SITE.contains(cookieSameSite)) {
            throw new IllegalStateException("auth.refreshToken.cookieSameSite.invalid");
        }

        if ("None".equals(cookieSameSite) && !cookieSecure) {
            throw new IllegalStateException("auth.refreshToken.cookieSameSiteNone.requiresSecure");
        }
    }
}