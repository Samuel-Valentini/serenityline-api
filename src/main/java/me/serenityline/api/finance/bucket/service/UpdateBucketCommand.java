package me.serenityline.api.finance.bucket.service;

public record UpdateBucketCommand(
        String bucketName,
        String bucketDescription
) {
}