package me.serenityline.api.finance.transaction.controller;

import jakarta.validation.Valid;
import me.serenityline.api.finance.transaction.dto.TransactionCreateRequest;
import me.serenityline.api.finance.transaction.dto.TransactionResponse;
import me.serenityline.api.finance.transaction.service.TransactionCreationService;
import me.serenityline.api.finance.transaction.service.TransactionReadService;
import me.serenityline.api.security.auth.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance/transactions")
public class TransactionController {

    private final TransactionCreationService transactionCreationService;
    private final TransactionReadService transactionReadService;

    public TransactionController(
            TransactionCreationService transactionCreationService,
            TransactionReadService transactionReadService
    ) {
        this.transactionCreationService = Objects.requireNonNull(transactionCreationService, "transactionCreationService");
        this.transactionReadService = Objects.requireNonNull(transactionReadService, "transactionReadService");
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
}