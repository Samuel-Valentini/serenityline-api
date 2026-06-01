package me.serenityline.api.finance.reminder.worker;

public record FinanceReminderWorkerResult(
        int candidatesFound,
        int notificationsCreated,
        int emailOutboxesEnsured,
        int alreadyNotified,
        int failures
) {

    public FinanceReminderWorkerResult {
        if (candidatesFound < 0
                || notificationsCreated < 0
                || emailOutboxesEnsured < 0
                || alreadyNotified < 0
                || failures < 0) {
            throw new IllegalArgumentException("finance.reminderWorker.result.negativeCount");
        }

        if (notificationsCreated + alreadyNotified + failures > candidatesFound) {
            throw new IllegalArgumentException("finance.reminderWorker.result.inconsistentCount");
        }
    }
}