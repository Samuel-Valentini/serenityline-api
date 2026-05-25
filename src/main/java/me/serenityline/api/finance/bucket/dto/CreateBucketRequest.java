package me.serenityline.api.finance.bucket.dto;

import jakarta.validation.constraints.Size;
import me.serenityline.api.finance.bucket.service.CreateBucketCommand;

import java.util.List;
import java.util.UUID;

public record CreateBucketRequest(
        @Size(max = 255, message = "finance.bucket.name.tooLong")
        String bucketName,

        @Size(max = 2000, message = "finance.bucket.description.tooLong")
        String bucketDescription,

        List<UUID> accountIds
) {

    public CreateBucketCommand toCommand() {
        return new CreateBucketCommand(
                bucketName,
                bucketDescription,
                accountIds
        );
    }
}