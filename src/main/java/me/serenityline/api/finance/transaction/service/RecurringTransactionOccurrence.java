package me.serenityline.api.finance.transaction.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RecurringTransactionOccurrence(
        UUID recurringTransactionId,
        LocalDate logicalDate,
        LocalDate chargeDate,
        BigDecimal amount,
        boolean finalOccurrence
) {
}