package me.serenityline.api.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "serenityline.security.cors")
public record SecurityCorsProperties(List<String> allowedOrigins) {
    public SecurityCorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);

        if (allowedOrigins.contains("*")) {
            throw new IllegalArgumentException("security.cors.allowedOrigins.wildcardNotAllowed");
        }
    }
}
