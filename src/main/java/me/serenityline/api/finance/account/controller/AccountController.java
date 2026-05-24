package me.serenityline.api.finance.account.controller;

import jakarta.validation.Valid;
import me.serenityline.api.finance.account.dto.AccountResponse;
import me.serenityline.api.finance.account.dto.CreateAccountRequest;
import me.serenityline.api.finance.account.dto.UpdateAccountRequest;
import me.serenityline.api.finance.account.entity.Account;
import me.serenityline.api.finance.account.service.AccountCreationService;
import me.serenityline.api.finance.account.service.AccountQueryService;
import me.serenityline.api.finance.account.service.AccountUpdateService;
import me.serenityline.api.security.auth.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance/accounts")
public class AccountController {

    private final AccountCreationService accountCreationService;
    private final AccountQueryService accountQueryService;
    private final AccountUpdateService accountUpdateService;

    public AccountController(AccountCreationService accountCreationService, AccountQueryService accountQueryService, AccountUpdateService accountUpdateService) {
        this.accountCreationService = Objects.requireNonNull(accountCreationService, "accountCreationService");
        this.accountQueryService = Objects.requireNonNull(accountQueryService, "accountQueryService");
        this.accountUpdateService = Objects.requireNonNull(accountUpdateService, "accountUpdateService");
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

    @GetMapping
    public List<AccountResponse> getAccounts(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return accountQueryService.findVisibleAccounts(authenticatedUser.userId())
                .stream()
                .map(AccountResponse::from)
                .toList();
    }

    @GetMapping("/{accountId}")
    public AccountResponse getAccount(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID accountId
    ) {
        Account account = accountQueryService.findVisibleAccount(
                authenticatedUser.userId(),
                accountId
        );

        return AccountResponse.from(account);
    }

    @PatchMapping("/{accountId}")
    public AccountResponse updateAccount(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID accountId,
            @Valid @RequestBody UpdateAccountRequest request
    ) {
        Account account = accountUpdateService.updateAccount(
                authenticatedUser.userId(),
                accountId,
                request.toCommand()
        );

        return AccountResponse.from(account);
    }
}