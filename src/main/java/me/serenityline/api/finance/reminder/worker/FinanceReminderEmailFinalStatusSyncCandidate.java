package me.serenityline.api.finance.reminder.worker;

import me.serenityline.api.finance.reminder.notification.FinanceReminderEmailFinalStatus;

import java.util.Objects;
import java.util.UUID;

public record FinanceReminderEmailFinalStatusSyncCandidate(
        UUID financeReminderNotificationId,
        UUID emailOutboxId,
        FinanceReminderEmailFinalStatus emailFinalStatus,
        String emailProvider,
        String emailProviderMessageId
) {

    public FinanceReminderEmailFinalStatusSyncCandidate {
        Objects.requireNonNull(financeReminderNotificationId, "financeReminderNotificationId");
        Objects.requireNonNull(emailOutboxId, "emailOutboxId");
        Objects.requireNonNull(emailFinalStatus, "emailFinalStatus");

        emailProvider = normalizeNullable(
                emailProvider,
                100,
                "finance.reminderFinalStatus.emailProvider.tooLong"
        );

        emailProviderMessageId = normalizeNullable(
                emailProviderMessageId,
                255,
                "finance.reminderFinalStatus.emailProviderMessageId.tooLong"
        );

        if (emailProvider == null) {
            emailProviderMessageId = null;
        }
    }

    private static String normalizeNullable(
            String value,
            int maxLength,
            String tooLongKey
    ) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        if (normalized.isBlank()) {
            return null;
        }

        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(tooLongKey);
        }

        return normalized;
    }
}