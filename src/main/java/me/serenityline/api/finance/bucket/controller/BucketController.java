package me.serenityline.api.finance.bucket.controller;

import jakarta.validation.Valid;
import me.serenityline.api.finance.bucket.dto.BucketResponse;
import me.serenityline.api.finance.bucket.dto.CreateBucketRequest;
import me.serenityline.api.finance.bucket.service.BucketCreationResult;
import me.serenityline.api.finance.bucket.service.BucketCreationService;
import me.serenityline.api.security.auth.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping("/api/finance/buckets")
public class BucketController {

    private final BucketCreationService bucketCreationService;

    public BucketController(BucketCreationService bucketCreationService) {
        this.bucketCreationService = Objects.requireNonNull(bucketCreationService, "bucketCreationService");
    }

    @PostMapping
    public ResponseEntity<BucketResponse> createBucket(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody CreateBucketRequest request
    ) {
        BucketCreationResult result = bucketCreationService.createBucket(
                authenticatedUser.userId(),
                request.toCommand()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(BucketResponse.from(result));
    }
}