package me.serenityline.api.finance.reminder.worker;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FinanceReminderNotificationWorkerTest {

    @Test
    void processDueRemindersShouldDelegateToWorkerService() {
        FinanceReminderNotificationWorkerService workerService =
                mock(FinanceReminderNotificationWorkerService.class);

        FinanceReminderNotificationWorker worker =
                new FinanceReminderNotificationWorker(workerService);

        worker.processDueReminders();

        verify(workerService).processDueReminders();
    }
}