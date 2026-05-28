package me.serenityline.api.finance.calendar;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record PaymentAccountCurrencySnapshot(
        LocalDate effectiveFrom,
        UUID accountId,
        String currencyCode,
        long precedence
) {
    public PaymentAccountCurrencySnapshot {
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        Objects.requireNonNull(accountId, "accountId");

        if (currencyCode == null || currencyCode.isBlank()) {
            throw new IllegalArgumentException("finance.account.currencyRequired");
        }

        if (precedence <= 0) {
            throw new IllegalArgumentException("finance.accountCurrency.precedenceInvalid");
        }

        currencyCode = currencyCode.trim().toUpperCase();
    }

    boolean isEffectiveAt(LocalDate logicalDate) {
        return !effectiveFrom.isAfter(logicalDate);
    }
}