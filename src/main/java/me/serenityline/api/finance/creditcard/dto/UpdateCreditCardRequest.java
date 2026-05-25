package me.serenityline.api.finance.creditcard.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import me.serenityline.api.finance.creditcard.service.UpdateCreditCardCommand;

public record UpdateCreditCardRequest(
        @Size(max = 255, message = "finance.creditCard.name.tooLong")
        String creditCardName,

        @Size(max = 2000, message = "finance.creditCard.description.tooLong")
        String creditCardDescription,

        @Min(value = 1, message = "finance.creditCard.chargeDay.invalid")
        @Max(value = 31, message = "finance.creditCard.chargeDay.invalid")
        Integer creditCardChargeDay
) {

    public UpdateCreditCardCommand toCommand() {
        return new UpdateCreditCardCommand(
                creditCardName,
                creditCardDescription,
                creditCardChargeDay
        );
    }
}