package me.serenityline.api.finance.creditcard.controller;

import jakarta.validation.Valid;
import me.serenityline.api.finance.creditcard.dto.CreateCreditCardRequest;
import me.serenityline.api.finance.creditcard.dto.CreditCardResponse;
import me.serenityline.api.finance.creditcard.dto.UpdateCreditCardRequest;
import me.serenityline.api.finance.creditcard.entity.CreditCard;
import me.serenityline.api.finance.creditcard.service.CreditCardCreationService;
import me.serenityline.api.finance.creditcard.service.CreditCardQueryService;
import me.serenityline.api.finance.creditcard.service.CreditCardUpdateService;
import me.serenityline.api.security.auth.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance/credit-cards")
public class CreditCardController {

    private final CreditCardCreationService creditCardCreationService;
    private final CreditCardQueryService creditCardQueryService;
    private final CreditCardUpdateService creditCardUpdateService;

    public CreditCardController(CreditCardCreationService creditCardCreationService, CreditCardQueryService creditCardQueryService, CreditCardUpdateService creditCardUpdateService) {
        this.creditCardCreationService = Objects.requireNonNull(creditCardCreationService, "creditCardCreationService");
        this.creditCardQueryService = Objects.requireNonNull(creditCardQueryService, "creditCardQueryService");
        this.creditCardUpdateService = Objects.requireNonNull(creditCardUpdateService, "creditCardUpdateService");
    }

    @PostMapping
    public ResponseEntity<CreditCardResponse> createCreditCard(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody CreateCreditCardRequest request
    ) {
        CreditCard creditCard = creditCardCreationService.createCreditCard(
                authenticatedUser.userId(),
                request.toCommand()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(CreditCardResponse.from(creditCard));
    }

    @GetMapping
    public List<CreditCardResponse> getCreditCards(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return creditCardQueryService.findVisibleCreditCards(authenticatedUser.userId())
                .stream()
                .map(CreditCardResponse::from)
                .toList();
    }

    @GetMapping("/{creditCardId}")
    public CreditCardResponse getCreditCard(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID creditCardId
    ) {
        CreditCard creditCard = creditCardQueryService.findVisibleCreditCard(
                authenticatedUser.userId(),
                creditCardId
        );

        return CreditCardResponse.from(creditCard);
    }

    @PatchMapping("/{creditCardId}")
    public CreditCardResponse updateCreditCard(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID creditCardId,
            @Valid @RequestBody UpdateCreditCardRequest request
    ) {
        CreditCard creditCard = creditCardUpdateService.updateCreditCard(
                authenticatedUser.userId(),
                creditCardId,
                request.toCommand()
        );

        return CreditCardResponse.from(creditCard);
    }
}