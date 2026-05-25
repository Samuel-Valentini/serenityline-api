package me.serenityline.api.finance.bucket.dto;

import me.serenityline.api.finance.bucket.entity.Bucket;
import me.serenityline.api.finance.bucket.service.BucketCreationResult;
import me.serenityline.api.finance.bucket.service.BucketDetails;

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
        return from(
                new BucketDetails(
                        result.bucket(),
                        result.linkedAccountIds()
                )
        );
    }

    public static BucketResponse from(BucketDetails details) {
        Bucket bucket = details.bucket();

        return new BucketResponse(
                bucket.getBucketId(),
                bucket.getBucketName(),
                bucket.getBucketDescription(),
                details.linkedAccountIds(),
                bucket.getUserGroupId(),
                bucket.getBucketCreatedAt(),
                bucket.getBucketUpdatedAt(),
                bucket.getBucketClosedAt()
        );
    }
}