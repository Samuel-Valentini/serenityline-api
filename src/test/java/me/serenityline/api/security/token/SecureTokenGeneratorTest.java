package me.serenityline.api.security.token;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecureTokenGeneratorTest {

    private final SecureTokenGenerator tokenGenerator = new SecureTokenGenerator();

    @Test
    void generateShouldReturnUrlSafeToken() {
        String token = tokenGenerator.generate();

        assertThat(token)
                .hasSize(43)
                .matches("[A-Za-z0-9_-]+");
    }

    @Test
    void generateShouldReturnDifferentTokens() {
        String firstToken = tokenGenerator.generate();
        String secondToken = tokenGenerator.generate();

        assertThat(firstToken).isNotEqualTo(secondToken);
    }
}