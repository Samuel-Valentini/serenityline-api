package me.serenityline.api.finance.creditcard.service;

import java.util.UUID;

public record CreateCreditCardCommand(
        String creditCardName,
        String creditCardDescription,
        Integer creditCardChargeDay,
        UUID accountId
) {
}