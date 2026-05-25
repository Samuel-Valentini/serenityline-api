package me.serenityline.api.finance.bucket.dto;

import jakarta.validation.constraints.Size;
import me.serenityline.api.finance.bucket.service.UpdateBucketCommand;

public record UpdateBucketRequest(

        @Size(max = 255, message = "finance.bucket.name.tooLong")
        String bucketName,

        @Size(max = 2000, message = "finance.bucket.description.tooLong")
        String bucketDescription
) {

    public UpdateBucketCommand toCommand() {
        return new UpdateBucketCommand(
                bucketName,
                bucketDescription
        );
    }
}