package me.serenityline.api.security.token;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenHashingServiceTest {

    private static final String SECRET =
            "test-token-hashing-secret-for-test-39445347-32-bytes";

    @Test
    void hashShouldBeDeterministicForSameTokenAndSecret() {
        TokenHashingService service = new TokenHashingService(SECRET);

        String firstHash = service.hash("token-value");
        String secondHash = service.hash("token-value");

        assertThat(firstHash).isEqualTo(secondHash);
    }

    @Test
    void hashShouldChangeForDifferentTokens() {
        TokenHashingService service = new TokenHashingService(SECRET);

        String firstHash = service.hash("first-token");
        String secondHash = service.hash("second-token");

        assertThat(firstHash).isNotEqualTo(secondHash);
    }

    @Test
    void hashShouldChangeForDifferentSecrets() {
        TokenHashingService firstService = new TokenHashingService(
                "first-token-hashing-secret-for-test-32-bytes"
        );
        TokenHashingService secondService = new TokenHashingService(
                "second-token-hashing-secret-for-test-32-bytes"
        );

        String firstHash = firstService.hash("token-value");
        String secondHash = secondService.hash("token-value");

        assertThat(firstHash).isNotEqualTo(secondHash);
    }

    @Test
    void hashShouldNotExposeOriginalToken() {
        TokenHashingService service = new TokenHashingService(SECRET);

        String token = "token-value";
        String hash = service.hash(token);

        assertThat(hash)
                .isNotEqualTo(token)
                .matches("[A-Za-z0-9_-]+");
    }

    @Test
    void matchesShouldReturnTrueForValidTokenAndHash() {
        TokenHashingService service = new TokenHashingService(SECRET);

        String token = "token-value";
        String hash = service.hash(token);

        assertThat(service.matches(token, hash)).isTrue();
    }

    @Test
    void matchesShouldReturnFalseForInvalidToken() {
        TokenHashingService service = new TokenHashingService(SECRET);

        String hash = service.hash("valid-token");

        assertThat(service.matches("invalid-token", hash)).isFalse();
    }

    @Test
    void hashShouldRejectBlankToken() {
        TokenHashingService service = new TokenHashingService(SECRET);

        assertThatThrownBy(() -> service.hash(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("auth.token.required");
    }

    @Test
    void constructorShouldRejectBlankSecret() {
        assertThatThrownBy(() -> new TokenHashingService(" "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("security.tokenHashing.secret.required");
    }

    @Test
    void constructorShouldRejectShortSecret() {
        assertThatThrownBy(() -> new TokenHashingService("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("security.tokenHashing.secret.tooShort");
    }
}