package me.serenityline.api.security.crypto;

import java.util.Arrays;

public record EncryptedValue(byte[] encrypted,
                             byte[] iv,
                             byte[] tag) {

    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BYTES = 16;

    public EncryptedValue {
        if (encrypted == null || encrypted.length == 0) {
            throw new IllegalArgumentException("security.encryption.encrypted.required");
        }

        if (iv == null || iv.length != GCM_IV_LENGTH_BYTES) {
            throw new IllegalArgumentException("security.encryption.iv.invalidLength");
        }

        if (tag == null || tag.length != GCM_TAG_LENGTH_BYTES) {
            throw new IllegalArgumentException("security.encryption.tag.invalidLength");
        }

        encrypted = Arrays.copyOf(encrypted, encrypted.length);
        iv = Arrays.copyOf(iv, iv.length);
        tag = Arrays.copyOf(tag, tag.length);
    }

    @Override
    public byte[] encrypted() {
        return Arrays.copyOf(encrypted, encrypted.length);
    }

    @Override
    public byte[] iv() {
        return Arrays.copyOf(iv, iv.length);
    }

    @Override
    public byte[] tag() {
        return Arrays.copyOf(tag, tag.length);
    }
}
