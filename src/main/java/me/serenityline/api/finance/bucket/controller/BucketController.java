package me.serenityline.api.finance.bucket.controller;

import jakarta.validation.Valid;
import me.serenityline.api.finance.bucket.dto.BucketResponse;
import me.serenityline.api.finance.bucket.dto.CreateBucketRequest;
import me.serenityline.api.finance.bucket.dto.UpdateBucketRequest;
import me.serenityline.api.finance.bucket.service.*;
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
    private final BucketUpdateService bucketUpdateService;
    private final BucketAccountLinkService bucketAccountLinkService;

    public BucketController(
            BucketCreationService bucketCreationService,
            BucketQueryService bucketQueryService, BucketUpdateService bucketUpdateService, BucketAccountLinkService bucketAccountLinkService
    ) {
        this.bucketCreationService = Objects.requireNonNull(bucketCreationService, "bucketCreationService");
        this.bucketQueryService = Objects.requireNonNull(bucketQueryService, "bucketQueryService");
        this.bucketUpdateService = Objects.requireNonNull(bucketUpdateService, "bucketUpdateService");
        this.bucketAccountLinkService = Objects.requireNonNull(bucketAccountLinkService, "bucketAccountLinkService");
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
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(name = "status", required = false) String status
    ) {
        BucketStatusFilter statusFilter = BucketStatusFilter.from(status);

        List<BucketResponse> response = bucketQueryService.findVisibleBuckets(
                        authenticatedUser.userId(),
                        statusFilter
                )
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

    @PatchMapping("/{bucketId}")
    public ResponseEntity<BucketResponse> updateBucket(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID bucketId,
            @Valid @RequestBody UpdateBucketRequest request
    ) {
        return ResponseEntity.ok(
                BucketResponse.from(
                        bucketUpdateService.updateBucket(
                                authenticatedUser.userId(),
                                bucketId,
                                request.toCommand()
                        )
                )
        );
    }

    @PostMapping("/{bucketId}/accounts/{accountId}")
    public ResponseEntity<Void> linkAccount(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID bucketId,
            @PathVariable UUID accountId
    ) {
        bucketAccountLinkService.linkAccount(
                authenticatedUser.userId(),
                bucketId,
                accountId
        );

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{bucketId}/accounts/{accountId}")
    public ResponseEntity<Void> unlinkAccount(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID bucketId,
            @PathVariable UUID accountId
    ) {
        bucketAccountLinkService.unlinkAccount(
                authenticatedUser.userId(),
                bucketId,
                accountId
        );

        return ResponseEntity.noContent().build();
    }
}