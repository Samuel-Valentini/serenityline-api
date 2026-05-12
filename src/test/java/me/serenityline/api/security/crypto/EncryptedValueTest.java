package me.serenityline.api.security.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptedValueTest {

    @Test
    void constructorShouldAcceptValidEncryptedValue() {
        byte[] encrypted = new byte[]{1, 2, 3};
        byte[] iv = new byte[12];
        byte[] tag = new byte[16];

        EncryptedValue encryptedValue = new EncryptedValue(encrypted, iv, tag);

        assertThat(encryptedValue.encrypted()).containsExactly(encrypted);
        assertThat(encryptedValue.iv()).containsExactly(iv);
        assertThat(encryptedValue.tag()).containsExactly(tag);
    }

    @Test
    void constructorShouldRejectNullEncrypted() {
        byte[] iv = new byte[12];
        byte[] tag = new byte[16];

        assertThatThrownBy(() -> new EncryptedValue(null, iv, tag))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("security.encryption.encrypted.required");
    }

    @Test
    void constructorShouldRejectEmptyEncrypted() {
        byte[] encrypted = new byte[0];
        byte[] iv = new byte[12];
        byte[] tag = new byte[16];

        assertThatThrownBy(() -> new EncryptedValue(encrypted, iv, tag))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("security.encryption.encrypted.required");
    }

    @Test
    void constructorShouldRejectInvalidIvLength() {
        byte[] encrypted = new byte[]{1, 2, 3};
        byte[] iv = new byte[11];
        byte[] tag = new byte[16];

        assertThatThrownBy(() -> new EncryptedValue(encrypted, iv, tag))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("security.encryption.iv.invalidLength");
    }

    @Test
    void constructorShouldRejectInvalidTagLength() {
        byte[] encrypted = new byte[]{1, 2, 3};
        byte[] iv = new byte[12];
        byte[] tag = new byte[15];

        assertThatThrownBy(() -> new EncryptedValue(encrypted, iv, tag))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("security.encryption.tag.invalidLength");
    }

    @Test
    void constructorShouldDefensivelyCopyConstructorArguments() {
        byte[] encrypted = new byte[]{1, 2, 3};
        byte[] iv = new byte[12];
        byte[] tag = new byte[16];

        EncryptedValue encryptedValue = new EncryptedValue(encrypted, iv, tag);

        encrypted[0] = 99;
        iv[0] = 99;
        tag[0] = 99;

        assertThat(encryptedValue.encrypted()[0]).isEqualTo((byte) 1);
        assertThat(encryptedValue.iv()[0]).isEqualTo((byte) 0);
        assertThat(encryptedValue.tag()[0]).isEqualTo((byte) 0);
    }

    @Test
    void accessorsShouldReturnDefensiveCopies() {
        byte[] encrypted = new byte[]{1, 2, 3};
        byte[] iv = new byte[12];
        byte[] tag = new byte[16];

        EncryptedValue encryptedValue = new EncryptedValue(encrypted, iv, tag);

        byte[] returnedEncrypted = encryptedValue.encrypted();
        byte[] returnedIv = encryptedValue.iv();
        byte[] returnedTag = encryptedValue.tag();

        returnedEncrypted[0] = 99;
        returnedIv[0] = 99;
        returnedTag[0] = 99;

        assertThat(encryptedValue.encrypted()[0]).isEqualTo((byte) 1);
        assertThat(encryptedValue.iv()[0]).isEqualTo((byte) 0);
        assertThat(encryptedValue.tag()[0]).isEqualTo((byte) 0);
    }
}