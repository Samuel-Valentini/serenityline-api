package me.serenityline.api.finance.bucket.service;

import java.util.Locale;

public enum BucketStatusFilter {

    ACTIVE,
    CLOSED,
    ALL;

    public static BucketStatusFilter from(String value) {
        if (value == null || value.isBlank()) {
            return ACTIVE;
        }

        try {
            return BucketStatusFilter.valueOf(
                    value.trim().toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("finance.bucket.status.invalid", exception);
        }
    }

    public boolean includeActive() {
        return this == ACTIVE || this == ALL;
    }

    public boolean includeClosed() {
        return this == CLOSED || this == ALL;
    }
}