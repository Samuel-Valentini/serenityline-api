package me.serenityline.api.finance.reminder.worker;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FinanceReminderEmailFinalStatusWorkerTest {

    @Test
    void syncFinalStatusesShouldDelegateToSyncService() {
        FinanceReminderEmailFinalStatusSyncService syncService =
                mock(FinanceReminderEmailFinalStatusSyncService.class);

        FinanceReminderEmailFinalStatusWorker worker =
                new FinanceReminderEmailFinalStatusWorker(syncService);

        worker.syncFinalStatuses();

        verify(syncService).syncFinalStatuses();
    }
}