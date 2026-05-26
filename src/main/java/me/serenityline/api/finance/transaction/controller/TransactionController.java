package me.serenityline.api.finance.transaction.controller;

import jakarta.validation.Valid;
import me.serenityline.api.finance.transaction.dto.TransactionCreateRequest;
import me.serenityline.api.finance.transaction.dto.TransactionResponse;
import me.serenityline.api.finance.transaction.dto.TransactionSearchRequest;
import me.serenityline.api.finance.transaction.dto.TransactionUpdateRequest;
import me.serenityline.api.finance.transaction.service.TransactionCreationService;
import me.serenityline.api.finance.transaction.service.TransactionReadService;
import me.serenityline.api.finance.transaction.service.TransactionUpdateService;
import me.serenityline.api.security.auth.AuthenticatedUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance/transactions")
public class TransactionController {

    private final TransactionCreationService transactionCreationService;
    private final TransactionReadService transactionReadService;
    private final TransactionUpdateService transactionUpdateService;

    public TransactionController(
            TransactionCreationService transactionCreationService,
            TransactionReadService transactionReadService,
            TransactionUpdateService transactionUpdateService
    ) {
        this.transactionCreationService = Objects.requireNonNull(transactionCreationService, "transactionCreationService");
        this.transactionReadService = Objects.requireNonNull(transactionReadService, "transactionReadService");
        this.transactionUpdateService = Objects.requireNonNull(transactionUpdateService, "transactionUpdateService");
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody TransactionCreateRequest request
    ) {
        TransactionResponse response = transactionCreationService.createTransaction(
                authenticatedUser.userId(),
                request
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> getTransaction(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID transactionId
    ) {
        TransactionResponse response = transactionReadService.getTransaction(
                authenticatedUser.userId(),
                transactionId
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<TransactionResponse>> listTransactions(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) UUID simulationGroupId
    ) {
        List<TransactionResponse> response = transactionReadService.listTransactions(
                authenticatedUser.userId(),
                new TransactionSearchRequest(
                        from,
                        to,
                        accountId,
                        simulationGroupId
                )
        );

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> updateTransaction(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID transactionId,
            @Valid @RequestBody TransactionUpdateRequest request
    ) {
        TransactionResponse response = transactionUpdateService.updateTransaction(
                authenticatedUser.userId(),
                transactionId,
                request
        );

        return ResponseEntity.ok(response);
    }
}