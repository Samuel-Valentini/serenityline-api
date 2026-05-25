package me.serenityline.api.finance.bucket.service;

import me.serenityline.api.finance.bucket.entity.Bucket;

import java.util.List;
import java.util.UUID;

public record BucketCreationResult(
        Bucket bucket,
        List<UUID> linkedAccountIds
) {
}