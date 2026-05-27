package me.serenityline.api.finance.transaction.controller;

import jakarta.validation.Valid;
import me.serenityline.api.finance.transaction.dto.RecurringTransactionCreateRequest;
import me.serenityline.api.finance.transaction.dto.RecurringTransactionResponse;
import me.serenityline.api.finance.transaction.service.RecurringTransactionCreationService;
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
@RequestMapping("/api/finance/recurring-transactions")
public class RecurringTransactionController {

    private final RecurringTransactionCreationService recurringTransactionCreationService;

    public RecurringTransactionController(
            RecurringTransactionCreationService recurringTransactionCreationService
    ) {
        this.recurringTransactionCreationService = Objects.requireNonNull(
                recurringTransactionCreationService,
                "recurringTransactionCreationService"
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
}