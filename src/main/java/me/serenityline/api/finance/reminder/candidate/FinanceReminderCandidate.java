package me.serenityline.api.finance.reminder.candidate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record FinanceReminderCandidate(
        UUID userId,
        UUID userGroupId,
        UUID transactionId,
        UUID recurringTransactionId,
        LocalDate recurringTransactionLogicalDate,
        LocalDate chargeDate,
        String notifiedDescription,
        BigDecimal notifiedAmount,
        String notifiedCurrency,
        LocalDate reminderDate
) {

    public FinanceReminderCandidate {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(userGroupId, "userGroupId");
        Objects.requireNonNull(chargeDate, "chargeDate");
        Objects.requireNonNull(notifiedAmount, "notifiedAmount");
        Objects.requireNonNull(notifiedCurrency, "notifiedCurrency");
        Objects.requireNonNull(reminderDate, "reminderDate");

        if (notifiedDescription == null || notifiedDescription.isBlank()) {
            throw new IllegalArgumentException("finance.reminderCandidate.notifiedDescription.required");
        }

        notifiedDescription = notifiedDescription.trim();

        if (notifiedDescription.length() > 500) {
            throw new IllegalArgumentException("finance.reminderCandidate.notifiedDescription.tooLong");
        }

        if (notifiedCurrency.isBlank()) {
            throw new IllegalArgumentException("finance.reminderCandidate.notifiedCurrency.required");
        }

        notifiedCurrency = notifiedCurrency.trim().toUpperCase();

        boolean transactionSource = transactionId != null
                && recurringTransactionId == null
                && recurringTransactionLogicalDate == null;

        boolean recurringSource = transactionId == null
                && recurringTransactionId != null
                && recurringTransactionLogicalDate != null;

        if (!transactionSource && !recurringSource) {
            throw new IllegalArgumentException("finance.reminderCandidate.source.invalid");
        }

        if (reminderDate.isAfter(chargeDate)) {
            throw new IllegalArgumentException("finance.reminderCandidate.reminderDate.afterChargeDate");
        }
    }

    public static FinanceReminderCandidate forTransaction(
            UUID userId,
            UUID userGroupId,
            UUID transactionId,
            LocalDate chargeDate,
            String notifiedDescription,
            BigDecimal notifiedAmount,
            String notifiedCurrency,
            LocalDate reminderDate
    ) {
        return new FinanceReminderCandidate(
                userId,
                userGroupId,
                transactionId,
                null,
                null,
                chargeDate,
                notifiedDescription,
                notifiedAmount,
                notifiedCurrency,
                reminderDate
        );
    }

    public static FinanceReminderCandidate forRecurringOccurrence(
            UUID userId,
            UUID userGroupId,
            UUID recurringTransactionId,
            LocalDate recurringTransactionLogicalDate,
            LocalDate chargeDate,
            String notifiedDescription,
            BigDecimal notifiedAmount,
            String notifiedCurrency,
            LocalDate reminderDate
    ) {
        return new FinanceReminderCandidate(
                userId,
                userGroupId,
                null,
                recurringTransactionId,
                recurringTransactionLogicalDate,
                chargeDate,
                notifiedDescription,
                notifiedAmount,
                notifiedCurrency,
                reminderDate
        );
    }

    public boolean isTransactionCandidate() {
        return transactionId != null;
    }

    public boolean isRecurringCandidate() {
        return recurringTransactionId != null;
    }
}