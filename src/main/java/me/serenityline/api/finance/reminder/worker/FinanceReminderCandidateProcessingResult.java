package me.serenityline.api.finance.reminder.worker;

import java.util.Objects;
import java.util.UUID;

public record FinanceReminderCandidateProcessingResult(
        Status status,
        UUID financeReminderNotificationId,
        UUID emailOutboxId
) {

    public FinanceReminderCandidateProcessingResult {
        Objects.requireNonNull(status, "status");

        if (status == Status.CREATED) {
            Objects.requireNonNull(financeReminderNotificationId, "financeReminderNotificationId");
            Objects.requireNonNull(emailOutboxId, "emailOutboxId");
        }

        if (status == Status.ALREADY_EXISTS
                && (financeReminderNotificationId != null || emailOutboxId != null)) {
            throw new IllegalArgumentException("finance.reminderWorker.alreadyExistsResult.invalid");
        }
    }

    public static FinanceReminderCandidateProcessingResult created(
            UUID financeReminderNotificationId,
            UUID emailOutboxId
    ) {
        return new FinanceReminderCandidateProcessingResult(
                Status.CREATED,
                financeReminderNotificationId,
                emailOutboxId
        );
    }

    public static FinanceReminderCandidateProcessingResult alreadyExists() {
        return new FinanceReminderCandidateProcessingResult(
                Status.ALREADY_EXISTS,
                null,
                null
        );
    }

    public enum Status {
        CREATED,
        ALREADY_EXISTS
    }
}