package me.serenityline.api.finance.reminder.notification;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "finance_reminder_notifications")
public class FinanceReminderNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "finance_reminder_notification_id", nullable = false, updatable = false)
    private UUID financeReminderNotificationId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "user_group_id", nullable = false, updatable = false)
    private UUID userGroupId;

    @Column(name = "transaction_id", updatable = false)
    private UUID transactionId;

    @Column(name = "recurring_transaction_id", updatable = false)
    private UUID recurringTransactionId;

    @Column(name = "recurring_transaction_logical_date", updatable = false)
    private LocalDate recurringTransactionLogicalDate;

    @Column(name = "charge_date", nullable = false, updatable = false)
    private LocalDate chargeDate;

    @Column(name = "notified_amount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal notifiedAmount;

    @Column(name = "notified_currency", nullable = false, length = 3, updatable = false)
    private String notifiedCurrency;

    @Column(name = "reminder_date", nullable = false, updatable = false)
    private LocalDate reminderDate;

    @Column(name = "email_outbox_id")
    private UUID emailOutboxId;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_final_status", length = 30)
    private FinanceReminderEmailFinalStatus emailFinalStatus;

    @Column(name = "email_final_status_recorded_at")
    private OffsetDateTime emailFinalStatusRecordedAt;

    @Column(name = "email_provider", length = 100)
    private String emailProvider;

    @Column(name = "provider_message_id", length = 255)
    private String providerMessageId;

    @Column(name = "reminder_notification_created_at", nullable = false, updatable = false)
    private OffsetDateTime reminderNotificationCreatedAt;

    protected FinanceReminderNotification() {
    }

    private FinanceReminderNotification(
            UUID userId,
            UUID userGroupId,
            UUID transactionId,
            UUID recurringTransactionId,
            LocalDate recurringTransactionLogicalDate,
            LocalDate chargeDate,
            BigDecimal notifiedAmount,
            String notifiedCurrency,
            LocalDate reminderDate,
            OffsetDateTime createdAt
    ) {
        this.userId = requireUuid(userId, "finance.reminderNotification.userId.required");
        this.userGroupId = requireUuid(userGroupId, "finance.reminderNotification.userGroupId.required");
        this.transactionId = transactionId;
        this.recurringTransactionId = recurringTransactionId;
        this.recurringTransactionLogicalDate = recurringTransactionLogicalDate;
        this.chargeDate = requireDate(chargeDate, "finance.reminderNotification.chargeDate.required");
        this.notifiedAmount = requireNonZeroAmount(notifiedAmount);
        this.notifiedCurrency = requireCurrency(notifiedCurrency);
        this.reminderDate = requireDate(reminderDate, "finance.reminderNotification.reminderDate.required");
        this.reminderNotificationCreatedAt = createdAt == null ? OffsetDateTime.now() : createdAt;

        validateSource();
        validateReminderDate();
    }

    public static FinanceReminderNotification forTransaction(
            UUID userId,
            UUID userGroupId,
            UUID transactionId,
            LocalDate chargeDate,
            BigDecimal notifiedAmount,
            String notifiedCurrency,
            LocalDate reminderDate,
            OffsetDateTime createdAt
    ) {
        return new FinanceReminderNotification(
                userId,
                userGroupId,
                requireUuid(transactionId, "finance.reminderNotification.transactionId.required"),
                null,
                null,
                chargeDate,
                notifiedAmount,
                notifiedCurrency,
                reminderDate,
                createdAt
        );
    }

    public static FinanceReminderNotification forRecurringOccurrence(
            UUID userId,
            UUID userGroupId,
            UUID recurringTransactionId,
            LocalDate recurringTransactionLogicalDate,
            LocalDate chargeDate,
            BigDecimal notifiedAmount,
            String notifiedCurrency,
            LocalDate reminderDate,
            OffsetDateTime createdAt
    ) {
        return new FinanceReminderNotification(
                userId,
                userGroupId,
                null,
                requireUuid(recurringTransactionId, "finance.reminderNotification.recurringTransactionId.required"),
                requireDate(recurringTransactionLogicalDate, "finance.reminderNotification.recurringTransactionLogicalDate.required"),
                chargeDate,
                notifiedAmount,
                notifiedCurrency,
                reminderDate,
                createdAt
        );
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

    private static BigDecimal requireNonZeroAmount(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("finance.reminderNotification.notifiedAmount.required");
        }

        if (BigDecimal.ZERO.compareTo(value) == 0) {
            throw new IllegalArgumentException("finance.reminderNotification.notifiedAmount.zero");
        }

        return value;
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

    private static String normalizeNullable(String value, int maxLength, String tooLongKey) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        if (normalized.isBlank()) {
            return null;
        }

        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(tooLongKey);
        }

        return normalized;
    }

    @PrePersist
    protected void onCreate() {
        if (this.reminderNotificationCreatedAt == null) {
            this.reminderNotificationCreatedAt = OffsetDateTime.now();
        }

        validateSource();
        validateReminderDate();
    }

    public void attachEmailOutbox(UUID emailOutboxId) {
        UUID requiredEmailOutboxId = requireUuid(
                emailOutboxId,
                "finance.reminderNotification.emailOutboxId.required"
        );

        if (this.emailOutboxId != null) {
            if (this.emailOutboxId.equals(requiredEmailOutboxId)) {
                return;
            }

            throw new IllegalStateException("finance.reminderNotification.emailOutboxId.alreadyAttached");
        }

        this.emailOutboxId = requiredEmailOutboxId;
    }

    public void recordFinalEmailStatus(
            FinanceReminderEmailFinalStatus finalStatus,
            OffsetDateTime recordedAt,
            String emailProvider,
            String providerMessageId
    ) {
        if (finalStatus == null) {
            throw new IllegalArgumentException("finance.reminderNotification.emailFinalStatus.required");
        }

        if (recordedAt == null) {
            throw new IllegalArgumentException("finance.reminderNotification.emailFinalStatusRecordedAt.required");
        }

        String normalizedProvider = normalizeNullable(emailProvider, 100, "finance.reminderNotification.emailProvider.tooLong");
        String normalizedProviderMessageId = normalizeNullable(providerMessageId, 255, "finance.reminderNotification.providerMessageId.tooLong");

        if (normalizedProviderMessageId != null && normalizedProvider == null) {
            throw new IllegalArgumentException("finance.reminderNotification.emailProvider.requiredWithProviderMessageId");
        }

        this.emailFinalStatus = finalStatus;
        this.emailFinalStatusRecordedAt = recordedAt;
        this.emailProvider = normalizedProvider;
        this.providerMessageId = normalizedProviderMessageId;
    }

    private void validateSource() {
        boolean transactionSource = transactionId != null
                && recurringTransactionId == null
                && recurringTransactionLogicalDate == null;

        boolean recurringSource = transactionId == null
                && recurringTransactionId != null
                && recurringTransactionLogicalDate != null;

        if (!transactionSource && !recurringSource) {
            throw new IllegalArgumentException("finance.reminderNotification.source.invalid");
        }
    }

    private void validateReminderDate() {
        if (reminderDate != null && chargeDate != null && reminderDate.isAfter(chargeDate)) {
            throw new IllegalArgumentException("finance.reminderNotification.reminderDate.afterChargeDate");
        }
    }

    public UUID getFinanceReminderNotificationId() {
        return financeReminderNotificationId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getUserGroupId() {
        return userGroupId;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public UUID getRecurringTransactionId() {
        return recurringTransactionId;
    }

    public LocalDate getRecurringTransactionLogicalDate() {
        return recurringTransactionLogicalDate;
    }

    public LocalDate getChargeDate() {
        return chargeDate;
    }

    public BigDecimal getNotifiedAmount() {
        return notifiedAmount;
    }

    public String getNotifiedCurrency() {
        return notifiedCurrency;
    }

    public LocalDate getReminderDate() {
        return reminderDate;
    }

    public UUID getEmailOutboxId() {
        return emailOutboxId;
    }

    public FinanceReminderEmailFinalStatus getEmailFinalStatus() {
        return emailFinalStatus;
    }

    public OffsetDateTime getEmailFinalStatusRecordedAt() {
        return emailFinalStatusRecordedAt;
    }

    public String getEmailProvider() {
        return emailProvider;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public OffsetDateTime getReminderNotificationCreatedAt() {
        return reminderNotificationCreatedAt;
    }
}