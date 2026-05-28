package me.serenityline.api.finance.transaction.service;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record RecurringTransactionProjectedMovementSeed(
        UUID recurringTransactionId,
        UUID userGroupId,
        LocalDate firstPaymentDate
) {
    public RecurringTransactionProjectedMovementSeed {
        Objects.requireNonNull(recurringTransactionId, "recurringTransactionId");
        Objects.requireNonNull(userGroupId, "userGroupId");
        Objects.requireNonNull(firstPaymentDate, "firstPaymentDate");
    }
}