package me.serenityline.api.security.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Service
public class EmailOutboxEncryptionService {

    private static final String AES_ALGORITHM = "AES";
    private static final String AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int AES_256_KEY_BYTES = 32;
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BYTES = 16;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final String encryptionKeyId;
    private final SecretKeySpec secretKeySpec;
    private final SecureRandom secureRandom;

    public EmailOutboxEncryptionService(
            @Value("${serenityline.security.email-outbox.encryption-key-id}") String encryptionKeyId,
            @Value("${serenityline.security.email-outbox.encryption-key-base64}") String encryptionKeyBase64
    ) {
        this.encryptionKeyId = normalizeEncryptionKeyId(encryptionKeyId);
        this.secretKeySpec = new SecretKeySpec(decodeKey(encryptionKeyBase64), AES_ALGORITHM);
        this.secureRandom = new SecureRandom();
    }

    private static String normalizeEncryptionKeyId(String encryptionKeyId) {
        if (encryptionKeyId == null || encryptionKeyId.isBlank()) {
            throw new IllegalStateException("security.encryption.keyId.required");
        }

        String normalized = encryptionKeyId.trim();

        if (normalized.length() > 100) {
            throw new IllegalStateException("security.encryption.keyId.tooLong");
        }

        return normalized;
    }

    private static byte[] decodeKey(String encryptionKeyBase64) {
        if (encryptionKeyBase64 == null || encryptionKeyBase64.isBlank()) {
            throw new IllegalStateException("security.encryption.key.required");
        }

        byte[] keyBytes;

        try {
            keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("security.encryption.key.invalidBase64", ex);
        }

        if (keyBytes.length != AES_256_KEY_BYTES) {
            throw new IllegalStateException("security.encryption.key.invalidLength");
        }

        return keyBytes;
    }

    public String getEncryptionKeyId() {
        return encryptionKeyId;
    }

    public EncryptedValue encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            throw new IllegalArgumentException("security.encryption.plainText.required");
        }

        byte[] plainBytes = plainText.getBytes(StandardCharsets.UTF_8);

        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    secretKeySpec,
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            );

            byte[] encryptedWithTag = cipher.doFinal(plainBytes);

            int tagStart = encryptedWithTag.length - GCM_TAG_LENGTH_BYTES;

            byte[] encrypted = Arrays.copyOfRange(encryptedWithTag, 0, tagStart);
            byte[] tag = Arrays.copyOfRange(encryptedWithTag, tagStart, encryptedWithTag.length);

            return new EncryptedValue(encrypted, iv, tag);

        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("security.encryption.failed", ex);
        }
    }
    
    public String decrypt(EncryptedValue encryptedValue) {
        if (encryptedValue == null) {
            throw new IllegalArgumentException("security.encryption.value.required");
        }

        try {
            byte[] encrypted = encryptedValue.encrypted();
            byte[] tag = encryptedValue.tag();

            byte[] encryptedWithTag = new byte[encrypted.length + tag.length];

            System.arraycopy(encrypted, 0, encryptedWithTag, 0, encrypted.length);
            System.arraycopy(tag, 0, encryptedWithTag, encrypted.length, tag.length);

            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKeySpec,
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, encryptedValue.iv())
            );

            byte[] plainBytes = cipher.doFinal(encryptedWithTag);

            return new String(plainBytes, StandardCharsets.UTF_8);

        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("security.encryption.failed", ex);
        }
    }
}