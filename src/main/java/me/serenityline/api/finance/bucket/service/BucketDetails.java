package me.serenityline.api.finance.bucket.service;

import me.serenityline.api.finance.bucket.entity.Bucket;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record BucketDetails(
        Bucket bucket,
        List<UUID> linkedAccountIds
) {

    public BucketDetails {
        Objects.requireNonNull(bucket, "bucket");
        linkedAccountIds = List.copyOf(Objects.requireNonNull(linkedAccountIds, "linkedAccountIds"));
    }
}