package me.serenityline.api.finance.bucket.controller;

import jakarta.validation.Valid;
import me.serenityline.api.finance.bucket.dto.BucketResponse;
import me.serenityline.api.finance.bucket.dto.CreateBucketRequest;
import me.serenityline.api.finance.bucket.service.BucketCreationResult;
import me.serenityline.api.finance.bucket.service.BucketCreationService;
import me.serenityline.api.finance.bucket.service.BucketQueryService;
import me.serenityline.api.security.auth.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance/buckets")
public class BucketController {

    private final BucketCreationService bucketCreationService;
    private final BucketQueryService bucketQueryService;

    public BucketController(
            BucketCreationService bucketCreationService,
            BucketQueryService bucketQueryService
    ) {
        this.bucketCreationService = Objects.requireNonNull(bucketCreationService, "bucketCreationService");
        this.bucketQueryService = Objects.requireNonNull(bucketQueryService, "bucketQueryService");
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

    @GetMapping
    public ResponseEntity<List<BucketResponse>> findBuckets(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        List<BucketResponse> response = bucketQueryService.findVisibleBuckets(authenticatedUser.userId())
                .stream()
                .map(BucketResponse::from)
                .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{bucketId}")
    public ResponseEntity<BucketResponse> findBucket(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID bucketId
    ) {
        return ResponseEntity.ok(
                BucketResponse.from(
                        bucketQueryService.findVisibleBucket(
                                authenticatedUser.userId(),
                                bucketId
                        )
                )
        );
    }
}