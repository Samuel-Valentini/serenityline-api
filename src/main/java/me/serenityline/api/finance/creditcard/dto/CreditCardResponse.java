package me.serenityline.api.finance.creditcard.dto;

import me.serenityline.api.finance.creditcard.entity.CreditCard;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreditCardResponse(
        UUID creditCardId,
        String creditCardName,
        String creditCardDescription,
        Short creditCardChargeDay,
        UUID accountId,
        UUID userGroupId,
        OffsetDateTime creditCardCreatedAt,
        OffsetDateTime creditCardUpdatedAt
) {

    public static CreditCardResponse from(CreditCard creditCard) {
        return new CreditCardResponse(
                creditCard.getCreditCardId(),
                creditCard.getCreditCardName(),
                creditCard.getCreditCardDescription(),
                creditCard.getCreditCardChargeDay(),
                creditCard.getAccountId(),
                creditCard.getUserGroupId(),
                creditCard.getCreditCardCreatedAt(),
                creditCard.getCreditCardUpdatedAt()
        );
    }
}