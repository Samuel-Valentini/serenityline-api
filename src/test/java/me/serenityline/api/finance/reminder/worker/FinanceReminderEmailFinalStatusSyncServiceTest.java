package me.serenityline.api.finance.reminder.worker;

import me.serenityline.api.finance.reminder.notification.FinanceReminderEmailFinalStatus;
import me.serenityline.api.finance.reminder.notification.FinanceReminderNotificationCreationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinanceReminderEmailFinalStatusSyncServiceTest {

    @Mock
    private FinanceReminderEmailFinalStatusSyncRepository syncRepository;

    @Mock
    private FinanceReminderNotificationCreationService notificationCreationService;

    private FinanceReminderEmailFinalStatusSyncService service;

    @BeforeEach
    void setUp() {
        service = new FinanceReminderEmailFinalStatusSyncService(
                syncRepository,
                notificationCreationService,
                100
        );
    }

    @Test
    void syncFinalStatusesShouldRecordAllCandidatesAndReturnSyncedCount() {
        UUID firstNotificationId = UUID.randomUUID();
        UUID firstEmailOutboxId = UUID.randomUUID();

        UUID secondNotificationId = UUID.randomUUID();
        UUID secondEmailOutboxId = UUID.randomUUID();

        FinanceReminderEmailFinalStatusSyncCandidate firstCandidate =
                new FinanceReminderEmailFinalStatusSyncCandidate(
                        firstNotificationId,
                        firstEmailOutboxId,
                        FinanceReminderEmailFinalStatus.SENT,
                        "RESEND",
                        "msg-1"
                );

        FinanceReminderEmailFinalStatusSyncCandidate secondCandidate =
                new FinanceReminderEmailFinalStatusSyncCandidate(
                        secondNotificationId,
                        secondEmailOutboxId,
                        FinanceReminderEmailFinalStatus.FAILED,
                        null,
                        null
                );

        when(syncRepository.findFinalStatusCandidates(100))
                .thenReturn(List.of(firstCandidate, secondCandidate));

        int synced = service.syncFinalStatuses();

        assertThat(synced).isEqualTo(2);

        verify(notificationCreationService).recordFinalEmailStatus(
                firstEmailOutboxId,
                FinanceReminderEmailFinalStatus.SENT,
                "RESEND",
                "msg-1"
        );

        verify(notificationCreationService).recordFinalEmailStatus(
                secondEmailOutboxId,
                FinanceReminderEmailFinalStatus.FAILED,
                null,
                null
        );
    }

    @Test
    void syncFinalStatusesShouldContinueAfterSingleCandidateFailure() {
        UUID failingNotificationId = UUID.randomUUID();
        UUID failingEmailOutboxId = UUID.randomUUID();

        UUID successfulNotificationId = UUID.randomUUID();
        UUID successfulEmailOutboxId = UUID.randomUUID();

        FinanceReminderEmailFinalStatusSyncCandidate failingCandidate =
                new FinanceReminderEmailFinalStatusSyncCandidate(
                        failingNotificationId,
                        failingEmailOutboxId,
                        FinanceReminderEmailFinalStatus.SENT,
                        "RESEND",
                        "msg-failing"
                );

        FinanceReminderEmailFinalStatusSyncCandidate successfulCandidate =
                new FinanceReminderEmailFinalStatusSyncCandidate(
                        successfulNotificationId,
                        successfulEmailOutboxId,
                        FinanceReminderEmailFinalStatus.CANCELLED,
                        null,
                        null
                );

        when(syncRepository.findFinalStatusCandidates(100))
                .thenReturn(List.of(failingCandidate, successfulCandidate));

        doThrow(new IllegalStateException("boom"))
                .when(notificationCreationService)
                .recordFinalEmailStatus(
                        failingEmailOutboxId,
                        FinanceReminderEmailFinalStatus.SENT,
                        "RESEND",
                        "msg-failing"
                );

        int synced = service.syncFinalStatuses();

        assertThat(synced).isEqualTo(1);

        verify(notificationCreationService).recordFinalEmailStatus(
                failingEmailOutboxId,
                FinanceReminderEmailFinalStatus.SENT,
                "RESEND",
                "msg-failing"
        );

        verify(notificationCreationService).recordFinalEmailStatus(
                successfulEmailOutboxId,
                FinanceReminderEmailFinalStatus.CANCELLED,
                null,
                null
        );
    }

    @Test
    void syncFinalStatusesShouldReturnZeroWhenNoCandidatesExist() {
        when(syncRepository.findFinalStatusCandidates(100))
                .thenReturn(List.of());

        int synced = service.syncFinalStatuses();

        assertThat(synced).isZero();

        verifyNoInteractions(notificationCreationService);
    }

    @Test
    void constructorShouldRejectInvalidBatchSize() {
        assertThatThrownBy(() -> new FinanceReminderEmailFinalStatusSyncService(
                syncRepository,
                notificationCreationService,
                0
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("finance.reminderFinalStatus.batchSize.invalid");
    }
}