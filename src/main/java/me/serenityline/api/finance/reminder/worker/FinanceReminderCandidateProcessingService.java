package me.serenityline.api.finance.reminder.worker;

import me.serenityline.api.finance.reminder.candidate.FinanceReminderCandidate;
import me.serenityline.api.finance.reminder.email.FinanceReminderEmailOutboxService;
import me.serenityline.api.finance.reminder.notification.FinanceReminderNotification;
import me.serenityline.api.finance.reminder.notification.FinanceReminderNotificationCreationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class FinanceReminderCandidateProcessingService {

    private final FinanceReminderNotificationCreationService notificationCreationService;
    private final FinanceReminderEmailOutboxService emailOutboxService;

    public FinanceReminderCandidateProcessingService(
            FinanceReminderNotificationCreationService notificationCreationService,
            FinanceReminderEmailOutboxService emailOutboxService
    ) {
        this.notificationCreationService = Objects.requireNonNull(
                notificationCreationService,
                "notificationCreationService"
        );
        this.emailOutboxService = Objects.requireNonNull(
                emailOutboxService,
                "emailOutboxService"
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FinanceReminderCandidateProcessingResult process(FinanceReminderCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate");

        Optional<FinanceReminderNotification> financeReminderNotification = candidate.isTransactionCandidate()
                ? createTransactionNotification(candidate)
                : createRecurringNotification(candidate);

        if (financeReminderNotification.isEmpty()) {
            return FinanceReminderCandidateProcessingResult.alreadyExists();
        }

        UUID financeReminderNotificationId = financeReminderNotification
                .get()
                .getFinanceReminderNotificationId();

        UUID emailOutboxId = emailOutboxService.ensureEmailOutboxForNotification(
                financeReminderNotificationId
        );

        return FinanceReminderCandidateProcessingResult.created(
                financeReminderNotificationId,
                emailOutboxId
        );
    }

    private Optional<FinanceReminderNotification> createTransactionNotification(FinanceReminderCandidate candidate) {
        return notificationCreationService.createForTransactionIfAbsent(
                candidate.userId(),
                candidate.userGroupId(),
                candidate.transactionId(),
                candidate.chargeDate(),
                candidate.notifiedDescription(),
                candidate.notifiedAmount(),
                candidate.notifiedCurrency(),
                candidate.reminderDate()
        );
    }

    private Optional<FinanceReminderNotification> createRecurringNotification(FinanceReminderCandidate candidate) {
        return notificationCreationService.createForRecurringOccurrenceIfAbsent(
                candidate.userId(),
                candidate.userGroupId(),
                candidate.recurringTransactionId(),
                candidate.recurringTransactionLogicalDate(),
                candidate.chargeDate(),
                candidate.notifiedDescription(),
                candidate.notifiedAmount(),
                candidate.notifiedCurrency(),
                candidate.reminderDate()
        );
    }
}