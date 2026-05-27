package me.serenityline.api.finance.transaction.controller;

import jakarta.validation.Valid;
import me.serenityline.api.finance.transaction.dto.RecurringTransactionCreateRequest;
import me.serenityline.api.finance.transaction.dto.RecurringTransactionHistoryResponse;
import me.serenityline.api.finance.transaction.dto.RecurringTransactionResponse;
import me.serenityline.api.finance.transaction.service.*;
import me.serenityline.api.security.auth.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance/recurring-transactions")
public class RecurringTransactionController {

    private final RecurringTransactionCreationService recurringTransactionCreationService;
    private final RecurringTransactionReadService recurringTransactionReadService;
    private final RecurringTransactionListService recurringTransactionListService;
    private final RecurringTransactionHistoryReadService recurringTransactionHistoryReadService;
    private final RecurringTransactionPatchService recurringTransactionPatchService;
    private final RecurringTransactionDeleteService recurringTransactionDeleteService;

    public RecurringTransactionController(
            RecurringTransactionCreationService recurringTransactionCreationService,
            RecurringTransactionReadService recurringTransactionReadService,
            RecurringTransactionListService recurringTransactionListService,
            RecurringTransactionHistoryReadService recurringTransactionHistoryReadService,
            RecurringTransactionPatchService recurringTransactionPatchService,
            RecurringTransactionDeleteService recurringTransactionDeleteService
    ) {
        this.recurringTransactionCreationService = Objects.requireNonNull(
                recurringTransactionCreationService,
                "recurringTransactionCreationService"
        );
        this.recurringTransactionReadService = Objects.requireNonNull(
                recurringTransactionReadService,
                "recurringTransactionReadService"
        );
        this.recurringTransactionListService = Objects.requireNonNull(
                recurringTransactionListService,
                "recurringTransactionListService"
        );
        this.recurringTransactionHistoryReadService = Objects.requireNonNull(
                recurringTransactionHistoryReadService,
                "recurringTransactionHistoryReadService"
        );
        this.recurringTransactionPatchService = Objects.requireNonNull(
                recurringTransactionPatchService,
                "recurringTransactionPatchService"
        );
        this.recurringTransactionDeleteService = Objects.requireNonNull(
                recurringTransactionDeleteService,
                "recurringTransactionDeleteService"
        );
    }

    @PostMapping
    public ResponseEntity<RecurringTransactionResponse> createRecurringTransaction(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody RecurringTransactionCreateRequest request
    ) {
        RecurringTransactionResponse response = recurringTransactionCreationService.createRecurringTransaction(
                authenticatedUser.userId(),
                request
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{recurringTransactionId}")
    public ResponseEntity<RecurringTransactionResponse> getRecurringTransaction(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID recurringTransactionId
    ) {
        RecurringTransactionResponse response = recurringTransactionReadService.getRecurringTransaction(
                authenticatedUser.userId(),
                recurringTransactionId
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<RecurringTransactionResponse>> listRecurringTransactions(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) List<UUID> simulationGroupIds
    ) {
        List<RecurringTransactionResponse> response = recurringTransactionListService.listRecurringTransactions(
                authenticatedUser.userId(),
                accountId,
                simulationGroupIds
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{recurringTransactionId}/history")
    public ResponseEntity<RecurringTransactionHistoryResponse> getRecurringTransactionHistory(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID recurringTransactionId
    ) {
        RecurringTransactionHistoryResponse response = recurringTransactionHistoryReadService
                .getRecurringTransactionHistory(
                        authenticatedUser.userId(),
                        recurringTransactionId
                );

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{recurringTransactionId}")
    public ResponseEntity<RecurringTransactionResponse> patchRecurringTransaction(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID recurringTransactionId,
            @RequestBody JsonNode body
    ) {
        RecurringTransactionResponse response = recurringTransactionPatchService.patchRecurringTransaction(
                authenticatedUser.userId(),
                recurringTransactionId,
                body
        );

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{recurringTransactionId}")
    public ResponseEntity<Void> deleteRecurringTransaction(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID recurringTransactionId,
            @RequestBody(required = false) JsonNode body
    ) {
        recurringTransactionDeleteService.deleteRecurringTransaction(
                authenticatedUser.userId(),
                recurringTransactionId,
                body
        );

        return ResponseEntity.noContent().build();
    }
}