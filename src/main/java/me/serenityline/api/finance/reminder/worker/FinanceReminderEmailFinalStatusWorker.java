package me.serenityline.api.finance.reminder.worker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@ConditionalOnProperty(
        prefix = "serenityline.finance.reminder-final-status-worker",
        name = "enabled",
        havingValue = "true"
)
public class FinanceReminderEmailFinalStatusWorker {

    private final FinanceReminderEmailFinalStatusSyncService syncService;

    public FinanceReminderEmailFinalStatusWorker(
            FinanceReminderEmailFinalStatusSyncService syncService
    ) {
        this.syncService = Objects.requireNonNull(syncService, "syncService");
    }

    @Scheduled(fixedDelayString = "${serenityline.finance.reminder-final-status-worker.fixed-delay-ms:60000}")
    public void syncFinalStatuses() {
        syncService.syncFinalStatuses();
    }
}