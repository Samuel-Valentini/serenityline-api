package me.serenityline.api.finance.reminder.worker;

import me.serenityline.api.finance.reminder.candidate.FinanceReminderCandidate;
import me.serenityline.api.finance.reminder.candidate.FinanceReminderCandidateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinanceReminderNotificationWorkerServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 10);

    @Mock
    private FinanceReminderCandidateService candidateService;

    @Mock
    private FinanceReminderCandidateProcessingService candidateProcessingService;

    private FinanceReminderNotificationWorkerService service;

    private static FinanceReminderCandidate transactionCandidate() {
        return FinanceReminderCandidate.forTransaction(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.of(2026, 6, 17),
                "Affitto",
                new BigDecimal("-750.00"),
                "EUR",
                TODAY
        );
    }

    private static FinanceReminderCandidate recurringCandidate() {
        return FinanceReminderCandidate.forRecurringOccurrence(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.of(2026, 6, 17),
                LocalDate.of(2026, 6, 17),
                "Palestra",
                new BigDecimal("-49.90"),
                "EUR",
                TODAY
        );
    }

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-06-10T08:00:00Z"),
                ZoneOffset.UTC
        );

        service = new FinanceReminderNotificationWorkerService(
                candidateService,
                candidateProcessingService,
                clock,
                30_000L,
                1_000
        );
    }

    @Test
    void processDueRemindersShouldProcessAllCandidatesAndReturnCounters() {
        FinanceReminderCandidate createdCandidate = transactionCandidate();
        FinanceReminderCandidate alreadyExistingCandidate = transactionCandidate();
        FinanceReminderCandidate failingCandidate = recurringCandidate();

        UUID notificationId = UUID.randomUUID();
        UUID emailOutboxId = UUID.randomUUID();

        when(candidateService.findDueCandidates(TODAY))
                .thenReturn(List.of(
                        createdCandidate,
                        alreadyExistingCandidate,
                        failingCandidate
                ));

        when(candidateProcessingService.process(createdCandidate))
                .thenReturn(FinanceReminderCandidateProcessingResult.created(
                        notificationId,
                        emailOutboxId
                ));

        when(candidateProcessingService.process(alreadyExistingCandidate))
                .thenReturn(FinanceReminderCandidateProcessingResult.alreadyExists());

        when(candidateProcessingService.process(failingCandidate))
                .thenThrow(new IllegalStateException("boom"));

        FinanceReminderWorkerResult result = service.processDueReminders(TODAY);

        assertThat(result.candidatesFound()).isEqualTo(3);
        assertThat(result.notificationsCreated()).isEqualTo(1);
        assertThat(result.emailOutboxesEnsured()).isEqualTo(1);
        assertThat(result.alreadyNotified()).isEqualTo(1);
        assertThat(result.failures()).isEqualTo(1);

        verify(candidateProcessingService).process(createdCandidate);
        verify(candidateProcessingService).process(alreadyExistingCandidate);
        verify(candidateProcessingService).process(failingCandidate);
    }

    @Test
    void processDueRemindersShouldUseClockDate() {
        when(candidateService.findDueCandidates(TODAY))
                .thenReturn(List.of());

        FinanceReminderWorkerResult result = service.processDueReminders();

        assertThat(result.candidatesFound()).isZero();
        assertThat(result.notificationsCreated()).isZero();
        assertThat(result.emailOutboxesEnsured()).isZero();
        assertThat(result.alreadyNotified()).isZero();
        assertThat(result.failures()).isZero();

        verify(candidateService).findDueCandidates(TODAY);
        verifyNoInteractions(candidateProcessingService);
    }

    @Test
    void processDueRemindersShouldContinueAfterFailure() {
        FinanceReminderCandidate failingCandidate = transactionCandidate();
        FinanceReminderCandidate successfulCandidate = recurringCandidate();

        UUID notificationId = UUID.randomUUID();
        UUID emailOutboxId = UUID.randomUUID();

        when(candidateService.findDueCandidates(TODAY))
                .thenReturn(List.of(
                        failingCandidate,
                        successfulCandidate
                ));

        when(candidateProcessingService.process(failingCandidate))
                .thenThrow(new IllegalStateException("boom"));

        when(candidateProcessingService.process(successfulCandidate))
                .thenReturn(FinanceReminderCandidateProcessingResult.created(
                        notificationId,
                        emailOutboxId
                ));

        FinanceReminderWorkerResult result = service.processDueReminders(TODAY);

        assertThat(result.candidatesFound()).isEqualTo(2);
        assertThat(result.notificationsCreated()).isEqualTo(1);
        assertThat(result.emailOutboxesEnsured()).isEqualTo(1);
        assertThat(result.alreadyNotified()).isZero();
        assertThat(result.failures()).isEqualTo(1);

        verify(candidateProcessingService).process(failingCandidate);
        verify(candidateProcessingService).process(successfulCandidate);
    }

    @Test
    void processDueRemindersShouldRejectNullToday() {
        assertThatThrownBy(() -> service.processDueReminders(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("today");

        verifyNoInteractions(candidateService, candidateProcessingService);
    }

    @Test
    void constructorShouldRejectInvalidSlowRunWarningMs() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-06-10T08:00:00Z"),
                ZoneOffset.UTC
        );

        assertThatThrownBy(() -> new FinanceReminderNotificationWorkerService(
                candidateService,
                candidateProcessingService,
                clock,
                0L,
                1_000
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("finance.reminderWorker.slowRunWarningMs.invalid");
    }

    @Test
    void constructorShouldRejectInvalidHighCandidatesWarningThreshold() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-06-10T08:00:00Z"),
                ZoneOffset.UTC
        );

        assertThatThrownBy(() -> new FinanceReminderNotificationWorkerService(
                candidateService,
                candidateProcessingService,
                clock,
                30_000L,
                0
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("finance.reminderWorker.highCandidatesWarningThreshold.invalid");
    }
}