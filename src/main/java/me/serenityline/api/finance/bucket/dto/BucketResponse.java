package me.serenityline.api.finance.bucket.dto;

import me.serenityline.api.finance.bucket.entity.Bucket;
import me.serenityline.api.finance.bucket.service.BucketCreationResult;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record BucketResponse(
        UUID bucketId,
        String bucketName,
        String bucketDescription,
        List<UUID> accountIds,
        UUID userGroupId,
        OffsetDateTime bucketCreatedAt,
        OffsetDateTime bucketUpdatedAt,
        OffsetDateTime bucketClosedAt
) {

    public static BucketResponse from(BucketCreationResult result) {
        Bucket bucket = result.bucket();

        return new BucketResponse(
                bucket.getBucketId(),
                bucket.getBucketName(),
                bucket.getBucketDescription(),
                result.linkedAccountIds(),
                bucket.getUserGroupId(),
                bucket.getBucketCreatedAt(),
                bucket.getBucketUpdatedAt(),
                bucket.getBucketClosedAt()
        );
    }
}