package me.serenityline.api.security.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

@Service
public class SensitiveHashService {

    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";
    private static final int MIN_HMAC_KEY_BYTES = 32;

    private final SecretKeySpec secretKeySpec;

    public SensitiveHashService(
            @Value("${serenityline.security.sensitive-hash.key-base64}") String keyBase64
    ) {
        this.secretKeySpec = new SecretKeySpec(
                decodeKey(keyBase64),
                HMAC_SHA256_ALGORITHM
        );
    }

    private static byte[] decodeKey(String keyBase64) {
        if (keyBase64 == null || keyBase64.isBlank()) {
            throw new IllegalStateException("security.sensitiveHash.key.required");
        }

        byte[] keyBytes;

        try {
            keyBytes = Base64.getDecoder().decode(keyBase64);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("security.sensitiveHash.key.invalidBase64", ex);
        }

        if (keyBytes.length < MIN_HMAC_KEY_BYTES) {
            throw new IllegalStateException("security.sensitiveHash.key.invalidLength");
        }

        return keyBytes;
    }

    public String hash(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            throw new IllegalArgumentException("security.sensitiveHash.plainText.required");
        }

        try {
            Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
            mac.init(secretKeySpec);

            byte[] digest = mac.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(digest);

        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("security.sensitiveHash.failed", ex);
        }
    }
}