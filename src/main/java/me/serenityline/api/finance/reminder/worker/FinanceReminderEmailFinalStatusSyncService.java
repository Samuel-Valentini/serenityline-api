package me.serenityline.api.finance.reminder.worker;

import me.serenityline.api.finance.reminder.notification.FinanceReminderNotificationCreationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class FinanceReminderEmailFinalStatusSyncService {

    private static final Logger log = LoggerFactory.getLogger(
            FinanceReminderEmailFinalStatusSyncService.class
    );

    private final FinanceReminderEmailFinalStatusSyncRepository syncRepository;
    private final FinanceReminderNotificationCreationService notificationCreationService;
    private final int batchSize;

    public FinanceReminderEmailFinalStatusSyncService(
            FinanceReminderEmailFinalStatusSyncRepository syncRepository,
            FinanceReminderNotificationCreationService notificationCreationService,
            @Value("${serenityline.finance.reminder-final-status-worker.batch-size:100}") int batchSize
    ) {
        if (batchSize <= 0) {
            throw new IllegalStateException("finance.reminderFinalStatus.batchSize.invalid");
        }

        this.syncRepository = Objects.requireNonNull(syncRepository, "syncRepository");
        this.notificationCreationService = Objects.requireNonNull(
                notificationCreationService,
                "notificationCreationService"
        );
        this.batchSize = batchSize;
    }

    public int syncFinalStatuses() {
        List<FinanceReminderEmailFinalStatusSyncCandidate> candidates =
                syncRepository.findFinalStatusCandidates(batchSize);

        int synced = 0;

        for (FinanceReminderEmailFinalStatusSyncCandidate candidate : candidates) {
            try {
                notificationCreationService.recordFinalEmailStatus(
                        candidate.emailOutboxId(),
                        candidate.emailFinalStatus(),
                        candidate.emailProvider(),
                        candidate.emailProviderMessageId()
                );

                synced++;
            } catch (RuntimeException ex) {
                log.warn(
                        "Finance reminder final email status sync failed: notificationId={}, emailOutboxId={}, finalStatus={}",
                        candidate.financeReminderNotificationId(),
                        candidate.emailOutboxId(),
                        candidate.emailFinalStatus(),
                        ex
                );
            }
        }

        log.info(
                "Finance reminder final email status sync completed: candidatesFound={}, synced={}",
                candidates.size(),
                synced
        );

        return synced;
    }
}