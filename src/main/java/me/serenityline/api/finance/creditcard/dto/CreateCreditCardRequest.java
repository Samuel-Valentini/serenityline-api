package me.serenityline.api.finance.creditcard.dto;

import jakarta.validation.constraints.*;
import me.serenityline.api.finance.creditcard.service.CreateCreditCardCommand;

import java.util.UUID;

public record CreateCreditCardRequest(
        @NotBlank(message = "finance.creditCard.name.required")
        @Size(max = 255, message = "finance.creditCard.name.tooLong")
        String creditCardName,

        @Size(max = 2000, message = "finance.creditCard.description.tooLong")
        String creditCardDescription,

        @NotNull(message = "finance.creditCard.chargeDay.required")
        @Min(value = 1, message = "finance.creditCard.chargeDay.invalid")
        @Max(value = 31, message = "finance.creditCard.chargeDay.invalid")
        Integer creditCardChargeDay,

        @NotNull(message = "finance.creditCard.account.required")
        UUID accountId
) {

    public CreateCreditCardCommand toCommand() {
        return new CreateCreditCardCommand(
                creditCardName,
                creditCardDescription,
                creditCardChargeDay,
                accountId
        );
    }
}