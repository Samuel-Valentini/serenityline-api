package me.serenityline.api.security.token;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

@Service
public class SecureTokenGenerator {

    private static final int DEFAULT_TOKEN_BYTES = 32;
    private static final int REFRESH_TOKEN_BYTES = 64;
    private static final int MIN_TOKEN_BYTES = 32;

    private final SecureRandom secureRandom;

    public SecureTokenGenerator() {
        this(new SecureRandom());
    }

    SecureTokenGenerator(SecureRandom secureRandom) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    public String generate() {
        return generate(DEFAULT_TOKEN_BYTES);
    }

    public String generateRefreshToken() {
        return generate(REFRESH_TOKEN_BYTES);
    }

    public String generate(int tokenBytes) {
        if (tokenBytes < MIN_TOKEN_BYTES) {
            throw new IllegalArgumentException("security.token.bytes.tooShort");
        }

        byte[] bytes = new byte[tokenBytes];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }
}