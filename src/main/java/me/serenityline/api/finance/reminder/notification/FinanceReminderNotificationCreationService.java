package me.serenityline.api.finance.reminder.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class FinanceReminderNotificationCreationService {

    private final FinanceReminderNotificationRepository repository;
    private final FinanceReminderNotificationInsertRepository insertRepository;
    private final Clock clock;

    public FinanceReminderNotificationCreationService(
            FinanceReminderNotificationRepository repository,
            FinanceReminderNotificationInsertRepository insertRepository,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.insertRepository = Objects.requireNonNull(insertRepository, "insertRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    private static UUID requireUuid(UUID value, String messageKey) {
        if (value == null) {
            throw new IllegalArgumentException(messageKey);
        }

        return value;
    }

    private static LocalDate requireDate(LocalDate value, String messageKey) {
        if (value == null) {
            throw new IllegalArgumentException(messageKey);
        }

        return value;
    }

    private static void requireNonZeroAmount(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("finance.reminderNotification.notifiedAmount.required");
        }

        if (BigDecimal.ZERO.compareTo(value) == 0) {
            throw new IllegalArgumentException("finance.reminderNotification.notifiedAmount.zero");
        }
    }

    private static String requireCurrency(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("finance.reminderNotification.notifiedCurrency.required");
        }

        String normalized = value.trim().toUpperCase();

        if (!normalized.matches("^[A-Z]{3}$")) {
            throw new IllegalArgumentException("finance.reminderNotification.notifiedCurrency.invalid");
        }

        return normalized;
    }

    private static void validateReminderDate(
            LocalDate reminderDate,
            LocalDate chargeDate
    ) {
        if (reminderDate.isAfter(chargeDate)) {
            throw new IllegalArgumentException("finance.reminderNotification.reminderDate.afterChargeDate");
        }
    }

    @Transactional
    public Optional<FinanceReminderNotification> createForTransactionIfAbsent(
            UUID userId,
            UUID userGroupId,
            UUID transactionId,
            LocalDate chargeDate,
            BigDecimal notifiedAmount,
            String notifiedCurrency,
            LocalDate reminderDate
    ) {
        requireUuid(userId, "finance.reminderNotification.userId.required");
        requireUuid(userGroupId, "finance.reminderNotification.userGroupId.required");
        requireUuid(transactionId, "finance.reminderNotification.transactionId.required");
        requireDate(chargeDate, "finance.reminderNotification.chargeDate.required");
        requireNonZeroAmount(notifiedAmount);
        String normalizedCurrency = requireCurrency(notifiedCurrency);
        requireDate(reminderDate, "finance.reminderNotification.reminderDate.required");
        validateReminderDate(reminderDate, chargeDate);

        UUID notificationId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now(clock);

        Optional<UUID> insertedId = insertRepository.insertTransactionNotificationIfAbsent(
                notificationId,
                userId,
                userGroupId,
                transactionId,
                chargeDate,
                notifiedAmount,
                normalizedCurrency,
                reminderDate,
                createdAt
        );

        if (insertedId.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(repository.findById(insertedId.get())
                .orElseThrow(() -> new IllegalStateException("finance.reminderNotification.createdButNotFound")));
    }

    @Transactional
    public Optional<FinanceReminderNotification> createForRecurringOccurrenceIfAbsent(
            UUID userId,
            UUID userGroupId,
            UUID recurringTransactionId,
            LocalDate recurringTransactionLogicalDate,
            LocalDate chargeDate,
            BigDecimal notifiedAmount,
            String notifiedCurrency,
            LocalDate reminderDate
    ) {
        requireUuid(userId, "finance.reminderNotification.userId.required");
        requireUuid(userGroupId, "finance.reminderNotification.userGroupId.required");
        requireUuid(recurringTransactionId, "finance.reminderNotification.recurringTransactionId.required");
        requireDate(recurringTransactionLogicalDate, "finance.reminderNotification.recurringTransactionLogicalDate.required");
        requireDate(chargeDate, "finance.reminderNotification.chargeDate.required");
        requireNonZeroAmount(notifiedAmount);
        String normalizedCurrency = requireCurrency(notifiedCurrency);
        requireDate(reminderDate, "finance.reminderNotification.reminderDate.required");
        validateReminderDate(reminderDate, chargeDate);

        UUID notificationId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now(clock);

        Optional<UUID> insertedId = insertRepository.insertRecurringNotificationIfAbsent(
                notificationId,
                userId,
                userGroupId,
                recurringTransactionId,
                recurringTransactionLogicalDate,
                chargeDate,
                notifiedAmount,
                normalizedCurrency,
                reminderDate,
                createdAt
        );

        if (insertedId.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(repository.findById(insertedId.get())
                .orElseThrow(() -> new IllegalStateException("finance.reminderNotification.createdButNotFound")));
    }

    @Transactional
    public void attachEmailOutboxId(
            UUID financeReminderNotificationId,
            UUID emailOutboxId
    ) {
        requireUuid(financeReminderNotificationId, "finance.reminderNotification.id.required");
        requireUuid(emailOutboxId, "finance.reminderNotification.emailOutboxId.required");

        FinanceReminderNotification notification = repository.findById(financeReminderNotificationId)
                .orElseThrow(() -> new IllegalArgumentException("finance.reminderNotification.notFound"));

        notification.attachEmailOutbox(emailOutboxId);

        repository.flush();
    }

    @Transactional
    public void recordFinalEmailStatus(
            UUID emailOutboxId,
            FinanceReminderEmailFinalStatus finalStatus,
            String emailProvider,
            String providerMessageId
    ) {
        recordFinalEmailStatus(
                emailOutboxId,
                finalStatus,
                OffsetDateTime.now(clock),
                emailProvider,
                providerMessageId
        );
    }

    @Transactional
    public void recordFinalEmailStatus(
            UUID emailOutboxId,
            FinanceReminderEmailFinalStatus finalStatus,
            OffsetDateTime recordedAt,
            String emailProvider,
            String providerMessageId
    ) {
        requireUuid(emailOutboxId, "finance.reminderNotification.emailOutboxId.required");

        if (finalStatus == null) {
            throw new IllegalArgumentException("finance.reminderNotification.emailFinalStatus.required");
        }

        if (recordedAt == null) {
            throw new IllegalArgumentException("finance.reminderNotification.emailFinalStatusRecordedAt.required");
        }

        FinanceReminderNotification notification = repository.findByEmailOutboxId(emailOutboxId)
                .orElseThrow(() -> new IllegalArgumentException("finance.reminderNotification.emailOutboxNotFound"));

        if (notification.getEmailFinalStatus() != null) {
            if (notification.getEmailFinalStatus() == finalStatus) {
                return;
            }

            throw new IllegalStateException("finance.reminderNotification.emailFinalStatus.alreadyRecorded");
        }

        notification.recordFinalEmailStatus(
                finalStatus,
                recordedAt,
                emailProvider,
                providerMessageId
        );

        repository.flush();
    }
}