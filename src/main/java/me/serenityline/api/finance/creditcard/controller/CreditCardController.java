package me.serenityline.api.finance.creditcard.controller;

import jakarta.validation.Valid;
import me.serenityline.api.finance.creditcard.dto.CreateCreditCardRequest;
import me.serenityline.api.finance.creditcard.dto.CreditCardResponse;
import me.serenityline.api.finance.creditcard.entity.CreditCard;
import me.serenityline.api.finance.creditcard.service.CreditCardCreationService;
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
@RequestMapping("/api/finance/credit-cards")
public class CreditCardController {

    private final CreditCardCreationService creditCardCreationService;

    public CreditCardController(CreditCardCreationService creditCardCreationService) {
        this.creditCardCreationService = Objects.requireNonNull(creditCardCreationService, "creditCardCreationService");
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
}