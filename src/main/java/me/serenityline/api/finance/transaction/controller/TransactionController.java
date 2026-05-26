package me.serenityline.api.finance.transaction.controller;

import jakarta.validation.Valid;
import me.serenityline.api.finance.transaction.dto.TransactionCreateRequest;
import me.serenityline.api.finance.transaction.dto.TransactionResponse;
import me.serenityline.api.finance.transaction.service.TransactionCreationService;
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
@RequestMapping("/api/finance/transactions")
public class TransactionController {

    private final TransactionCreationService transactionCreationService;

    public TransactionController(TransactionCreationService transactionCreationService) {
        this.transactionCreationService = Objects.requireNonNull(transactionCreationService, "transactionCreationService");
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
}