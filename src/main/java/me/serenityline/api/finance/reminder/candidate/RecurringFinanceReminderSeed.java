package me.serenityline.api.finance.reminder.candidate;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record RecurringFinanceReminderSeed(
        UUID recurringTransactionId,
        UUID userGroupId,
        LocalDate firstPaymentDate,
        short reminderDaysBefore
) {

    public RecurringFinanceReminderSeed {
        Objects.requireNonNull(recurringTransactionId, "recurringTransactionId");
        Objects.requireNonNull(userGroupId, "userGroupId");
        Objects.requireNonNull(firstPaymentDate, "firstPaymentDate");

        if (reminderDaysBefore < 0 || reminderDaysBefore > 366) {
            throw new IllegalArgumentException("finance.reminderCandidate.reminderDaysBefore.invalid");
        }
    }
}