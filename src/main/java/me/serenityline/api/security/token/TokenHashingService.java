package me.serenityline.api.security.token;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Service
public class TokenHashingService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKeySpec secretKeySpec;

    public TokenHashingService(
            @Value("${serenityline.security.token-hashing.secret}") String secret
    ) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("security.tokenHashing.secret.required");
        }

        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);

        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("security.tokenHashing.secret.tooShort");
        }

        this.secretKeySpec = new SecretKeySpec(secretBytes, HMAC_ALGORITHM);
    }

    public String hash(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("auth.token.required");
        }

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(secretKeySpec);

            byte[] digest = mac.doFinal(token.getBytes(StandardCharsets.UTF_8));

            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(digest);

        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("security.tokenHashing.failed", ex);
        }
    }

    public boolean matches(String token, String expectedHash) {
        if (token == null || token.isBlank() || expectedHash == null || expectedHash.isBlank()) {
            return false;
        }

        String actualHash = hash(token);

        return MessageDigest.isEqual(
                actualHash.getBytes(StandardCharsets.US_ASCII),
                expectedHash.getBytes(StandardCharsets.US_ASCII)
        );
    }
}