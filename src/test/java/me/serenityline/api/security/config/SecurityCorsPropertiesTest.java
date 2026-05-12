package me.serenityline.api.security.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityCorsPropertiesTest {

    @Test
    void shouldUseEmptyListWhenAllowedOriginsIsNull() {
        SecurityCorsProperties properties = new SecurityCorsProperties(null);

        assertThat(properties.allowedOrigins()).isEmpty();
    }

    @Test
    void shouldCopyAllowedOriginsDefensively() {
        List<String> origins = List.of("http://localhost:5173");

        SecurityCorsProperties properties = new SecurityCorsProperties(origins);

        assertThat(properties.allowedOrigins()).containsExactly("http://localhost:5173");
    }

    @Test
    void shouldRejectWildcardOrigin() {
        assertThatThrownBy(() -> new SecurityCorsProperties(List.of("*")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("security.cors.allowedOrigins.wildcardNotAllowed");
    }
}