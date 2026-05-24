package me.serenityline.api.finance.account.controller;

import jakarta.validation.Valid;
import me.serenityline.api.finance.account.dto.AccountResponse;
import me.serenityline.api.finance.account.dto.CreateAccountRequest;
import me.serenityline.api.finance.account.entity.Account;
import me.serenityline.api.finance.account.service.AccountCreationService;
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
@RequestMapping("/api/finance/accounts")
public class AccountController {

    private final AccountCreationService accountCreationService;

    public AccountController(AccountCreationService accountCreationService) {
        this.accountCreationService = Objects.requireNonNull(accountCreationService, "accountCreationService");
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody CreateAccountRequest request
    ) {
        Account account = accountCreationService.createAccount(
                authenticatedUser.userId(),
                request.toCommand()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(AccountResponse.from(account));
    }
}