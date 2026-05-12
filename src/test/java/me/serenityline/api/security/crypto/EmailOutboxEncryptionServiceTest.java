package me.serenityline.api.security.crypto;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailOutboxEncryptionServiceTest {

    private static final String KEY_ID = "test-email-outbox-key-v1";

    private static final String KEY_BASE64 =
            "pDlfBuN/UQmI8VXLiaSTCeJAJxqmEilHOyifhrTXRRY=";

    @Test
    void constructorShouldNormalizeEncryptionKeyId() {
        EmailOutboxEncryptionService service = new EmailOutboxEncryptionService(
                "  " + KEY_ID + "  ",
                KEY_BASE64
        );

        assertThat(service.getEncryptionKeyId()).isEqualTo(KEY_ID);
    }

    @Test
    void constructorShouldRejectBlankEncryptionKeyId() {
        assertThatThrownBy(() -> new EmailOutboxEncryptionService(" ", KEY_BASE64))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("security.encryption.keyId.required");
    }

    @Test
    void constructorShouldRejectTooLongEncryptionKeyId() {
        String tooLongKeyId = "a".repeat(101);

        assertThatThrownBy(() -> new EmailOutboxEncryptionService(tooLongKeyId, KEY_BASE64))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("security.encryption.keyId.tooLong");
    }

    @Test
    void constructorShouldRejectBlankEncryptionKey() {
        assertThatThrownBy(() -> new EmailOutboxEncryptionService(KEY_ID, " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("security.encryption.key.required");
    }

    @Test
    void constructorShouldRejectInvalidBase64EncryptionKey() {
        assertThatThrownBy(() -> new EmailOutboxEncryptionService(KEY_ID, "not-valid-base64!!!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("security.encryption.key.invalidBase64");
    }

    @Test
    void constructorShouldRejectEncryptionKeyWithInvalidLength() {
        String shortKeyBase64 = Base64.getEncoder()
                .encodeToString("too-short".getBytes());

        assertThatThrownBy(() -> new EmailOutboxEncryptionService(KEY_ID, shortKeyBase64))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("security.encryption.key.invalidLength");
    }

    @Test
    void encryptShouldProduceValidEncryptedValue() {
        EmailOutboxEncryptionService service = new EmailOutboxEncryptionService(
                KEY_ID,
                KEY_BASE64
        );

        EncryptedValue encryptedValue = service.encrypt("Verify your SerenityLine email");

        assertThat(encryptedValue.encrypted()).isNotEmpty();
        assertThat(encryptedValue.iv()).hasSize(12);
        assertThat(encryptedValue.tag()).hasSize(16);
    }

    @Test
    void encryptShouldRejectBlankPlainText() {
        EmailOutboxEncryptionService service = new EmailOutboxEncryptionService(
                KEY_ID,
                KEY_BASE64
        );

        assertThatThrownBy(() -> service.encrypt(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("security.encryption.plainText.required");
    }

    @Test
    void encryptingSamePlainTextTwiceShouldProduceDifferentValues() {
        EmailOutboxEncryptionService service = new EmailOutboxEncryptionService(
                KEY_ID,
                KEY_BASE64
        );

        EncryptedValue first = service.encrypt("Verify your SerenityLine email");
        EncryptedValue second = service.encrypt("Verify your SerenityLine email");

        assertThat(first.iv()).isNotEqualTo(second.iv());
        assertThat(first.encrypted()).isNotEqualTo(second.encrypted());
        assertThat(first.tag()).isNotEqualTo(second.tag());
    }

    @Test
    void decryptShouldReturnOriginalPlainText() {
        EmailOutboxEncryptionService service = new EmailOutboxEncryptionService(
                KEY_ID,
                KEY_BASE64
        );

        String plainText = "Verify your SerenityLine email";

        EncryptedValue encryptedValue = service.encrypt(plainText);

        assertThat(service.decrypt(encryptedValue)).isEqualTo(plainText);
    }

    @Test
    void decryptShouldRejectNullEncryptedValue() {
        EmailOutboxEncryptionService service = new EmailOutboxEncryptionService(
                KEY_ID,
                KEY_BASE64
        );

        assertThatThrownBy(() -> service.decrypt(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("security.encryption.value.required");
    }

    @Test
    void decryptShouldFailWhenTagIsTampered() {
        EmailOutboxEncryptionService service = new EmailOutboxEncryptionService(
                KEY_ID,
                KEY_BASE64
        );

        EncryptedValue encryptedValue = service.encrypt("Verify your SerenityLine email");

        byte[] tamperedTag = encryptedValue.tag();
        tamperedTag[0] = (byte) (tamperedTag[0] ^ 1);

        EncryptedValue tampered = new EncryptedValue(
                encryptedValue.encrypted(),
                encryptedValue.iv(),
                tamperedTag
        );

        assertThatThrownBy(() -> service.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("security.encryption.failed");
    }

    @Test
    void decryptShouldFailWhenCiphertextIsTampered() {
        EmailOutboxEncryptionService service = new EmailOutboxEncryptionService(
                KEY_ID,
                KEY_BASE64
        );

        EncryptedValue encryptedValue = service.encrypt("Verify your SerenityLine email");

        byte[] tamperedEncrypted = encryptedValue.encrypted();
        tamperedEncrypted[0] = (byte) (tamperedEncrypted[0] ^ 1);

        EncryptedValue tampered = new EncryptedValue(
                tamperedEncrypted,
                encryptedValue.iv(),
                encryptedValue.tag()
        );

        assertThatThrownBy(() -> service.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("security.encryption.failed");
    }
}