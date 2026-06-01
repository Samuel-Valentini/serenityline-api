package me.serenityline.api.finance.reminder.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface FinanceReminderNotificationRepository
        extends JpaRepository<FinanceReminderNotification, UUID> {

    Optional<FinanceReminderNotification> findByUserGroupIdAndUserIdAndTransactionId(
            UUID userGroupId,
            UUID userId,
            UUID transactionId
    );

    Optional<FinanceReminderNotification> findByUserGroupIdAndUserIdAndRecurringTransactionIdAndRecurringTransactionLogicalDate(
            UUID userGroupId,
            UUID userId,
            UUID recurringTransactionId,
            LocalDate recurringTransactionLogicalDate
    );

    Optional<FinanceReminderNotification> findByEmailOutboxId(UUID emailOutboxId);
}