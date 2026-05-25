package me.serenityline.api.finance.creditcard.service;

public record UpdateCreditCardCommand(
        String creditCardName,
        String creditCardDescription,
        Integer creditCardChargeDay
) {
}