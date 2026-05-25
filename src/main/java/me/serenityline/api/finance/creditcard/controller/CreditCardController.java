package me.serenityline.api.finance.creditcard.controller;

import jakarta.validation.Valid;
import me.serenityline.api.finance.creditcard.dto.CreateCreditCardRequest;
import me.serenityline.api.finance.creditcard.dto.CreditCardResponse;
import me.serenityline.api.finance.creditcard.entity.CreditCard;
import me.serenityline.api.finance.creditcard.service.CreditCardCreationService;
import me.serenityline.api.finance.creditcard.service.CreditCardQueryService;
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

    public CreditCardController(CreditCardCreationService creditCardCreationService, CreditCardQueryService creditCardQueryService) {
        this.creditCardCreationService = Objects.requireNonNull(creditCardCreationService, "creditCardCreationService");
        this.creditCardQueryService = Objects.requireNonNull(creditCardQueryService, "creditCardQueryService");
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
}