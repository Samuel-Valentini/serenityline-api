package me.serenityline.api.finance.reminder.candidate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record FinanceReminderConfirmedRecurringOccurrenceSnapshot(
        UUID recurringTransactionId,
        LocalDate recurringTransactionLogicalDate,
        LocalDate chargeDate,
        String notifiedDescription,
        BigDecimal notifiedAmount,
        String notifiedCurrency
) {

    public FinanceReminderConfirmedRecurringOccurrenceSnapshot {
        Objects.requireNonNull(recurringTransactionId, "recurringTransactionId");
        Objects.requireNonNull(recurringTransactionLogicalDate, "recurringTransactionLogicalDate");
        Objects.requireNonNull(chargeDate, "chargeDate");
        Objects.requireNonNull(notifiedAmount, "notifiedAmount");

        if (notifiedDescription == null || notifiedDescription.isBlank()) {
            throw new IllegalArgumentException("finance.reminderCandidate.confirmedDescription.required");
        }

        notifiedDescription = notifiedDescription.trim();

        if (notifiedDescription.length() > 500) {
            throw new IllegalArgumentException("finance.reminderCandidate.confirmedDescription.tooLong");
        }

        if (notifiedCurrency == null || notifiedCurrency.isBlank()) {
            throw new IllegalArgumentException("finance.reminderCandidate.confirmedCurrency.required");
        }

        notifiedCurrency = notifiedCurrency.trim().toUpperCase();
    }
}