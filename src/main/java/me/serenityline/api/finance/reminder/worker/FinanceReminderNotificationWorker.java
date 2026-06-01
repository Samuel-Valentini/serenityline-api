package me.serenityline.api.finance.reminder.worker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@ConditionalOnProperty(
        prefix = "serenityline.finance.reminder-worker",
        name = "enabled",
        havingValue = "true"
)
public class FinanceReminderNotificationWorker {

    private final FinanceReminderNotificationWorkerService workerService;

    public FinanceReminderNotificationWorker(
            FinanceReminderNotificationWorkerService workerService
    ) {
        this.workerService = Objects.requireNonNull(workerService, "workerService");
    }

    @Scheduled(fixedDelayString = "${serenityline.finance.reminder-worker.fixed-delay-ms:600000}")
    public void processDueReminders() {
        workerService.processDueReminders();
    }
}