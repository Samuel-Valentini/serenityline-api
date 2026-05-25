package me.serenityline.api.finance.bucket.service;

import java.util.List;
import java.util.UUID;

public record CreateBucketCommand(
        String bucketName,
        String bucketDescription,
        List<UUID> accountIds
) {
}