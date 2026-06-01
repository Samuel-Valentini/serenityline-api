package me.serenityline.api.finance.reminder.worker;

import me.serenityline.api.finance.reminder.candidate.FinanceReminderCandidate;
import me.serenityline.api.finance.reminder.email.FinanceReminderEmailOutboxService;
import me.serenityline.api.finance.reminder.notification.FinanceReminderNotification;
import me.serenityline.api.finance.reminder.notification.FinanceReminderNotificationCreationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinanceReminderCandidateProcessingServiceTest {

    @Mock
    private FinanceReminderNotificationCreationService notificationCreationService;

    @Mock
    private FinanceReminderEmailOutboxService emailOutboxService;

    private FinanceReminderCandidateProcessingService service;

    @BeforeEach
    void setUp() {
        service = new FinanceReminderCandidateProcessingService(
                notificationCreationService,
                emailOutboxService
        );
    }

    @Test
    void processShouldCreateTransactionNotificationAndEnsureEmailOutbox() {
        UUID userId = UUID.randomUUID();
        UUID userGroupId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        UUID emailOutboxId = UUID.randomUUID();

        FinanceReminderCandidate candidate = FinanceReminderCandidate.forTransaction(
                userId,
                userGroupId,
                transactionId,
                LocalDate.of(2026, 6, 17),
                "Affitto",
                new BigDecimal("-750.00"),
                "EUR",
                LocalDate.of(2026, 6, 10)
        );

        FinanceReminderNotification notification = mock(FinanceReminderNotification.class);
        when(notification.getFinanceReminderNotificationId()).thenReturn(notificationId);

        when(notificationCreationService.createForTransactionIfAbsent(
                userId,
                userGroupId,
                transactionId,
                LocalDate.of(2026, 6, 17),
                "Affitto",
                new BigDecimal("-750.00"),
                "EUR",
                LocalDate.of(2026, 6, 10)
        )).thenReturn(Optional.of(notification));

        when(emailOutboxService.ensureEmailOutboxForNotification(notificationId))
                .thenReturn(emailOutboxId);

        FinanceReminderCandidateProcessingResult result = service.process(candidate);

        assertThat(result.status())
                .isEqualTo(FinanceReminderCandidateProcessingResult.Status.CREATED);

        assertThat(result.financeReminderNotificationId())
                .isEqualTo(notificationId);

        assertThat(result.emailOutboxId())
                .isEqualTo(emailOutboxId);

        verify(notificationCreationService, never())
                .createForRecurringOccurrenceIfAbsent(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()
                );
    }

    @Test
    void processShouldCreateRecurringNotificationAndEnsureEmailOutbox() {
        UUID userId = UUID.randomUUID();
        UUID userGroupId = UUID.randomUUID();
        UUID recurringTransactionId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        UUID emailOutboxId = UUID.randomUUID();

        LocalDate logicalDate = LocalDate.of(2026, 6, 17);

        FinanceReminderCandidate candidate = FinanceReminderCandidate.forRecurringOccurrence(
                userId,
                userGroupId,
                recurringTransactionId,
                logicalDate,
                LocalDate.of(2026, 6, 17),
                "Palestra",
                new BigDecimal("-49.90"),
                "EUR",
                LocalDate.of(2026, 6, 10)
        );

        FinanceReminderNotification notification = mock(FinanceReminderNotification.class);
        when(notification.getFinanceReminderNotificationId()).thenReturn(notificationId);

        when(notificationCreationService.createForRecurringOccurrenceIfAbsent(
                userId,
                userGroupId,
                recurringTransactionId,
                logicalDate,
                LocalDate.of(2026, 6, 17),
                "Palestra",
                new BigDecimal("-49.90"),
                "EUR",
                LocalDate.of(2026, 6, 10)
        )).thenReturn(Optional.of(notification));

        when(emailOutboxService.ensureEmailOutboxForNotification(notificationId))
                .thenReturn(emailOutboxId);

        FinanceReminderCandidateProcessingResult result = service.process(candidate);

        assertThat(result.status())
                .isEqualTo(FinanceReminderCandidateProcessingResult.Status.CREATED);

        assertThat(result.financeReminderNotificationId())
                .isEqualTo(notificationId);

        assertThat(result.emailOutboxId())
                .isEqualTo(emailOutboxId);

        verify(notificationCreationService, never())
                .createForTransactionIfAbsent(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()
                );
    }

    @Test
    void processShouldReturnAlreadyExistsWhenTransactionNotificationAlreadyExists() {
        FinanceReminderCandidate candidate = FinanceReminderCandidate.forTransaction(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.of(2026, 6, 17),
                "Affitto",
                new BigDecimal("-750.00"),
                "EUR",
                LocalDate.of(2026, 6, 10)
        );

        when(notificationCreationService.createForTransactionIfAbsent(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(Optional.empty());

        FinanceReminderCandidateProcessingResult result = service.process(candidate);

        assertThat(result.status())
                .isEqualTo(FinanceReminderCandidateProcessingResult.Status.ALREADY_EXISTS);

        assertThat(result.financeReminderNotificationId()).isNull();
        assertThat(result.emailOutboxId()).isNull();

        verifyNoInteractions(emailOutboxService);
    }

    @Test
    void processShouldRejectNullCandidate() {
        assertThatThrownBy(() -> service.process(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("candidate");

        verifyNoInteractions(notificationCreationService, emailOutboxService);
    }
}