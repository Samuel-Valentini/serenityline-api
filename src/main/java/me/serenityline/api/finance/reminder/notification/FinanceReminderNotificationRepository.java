package me.serenityline.api.finance.reminder.notification;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select n
            from FinanceReminderNotification n
            where n.financeReminderNotificationId = :id
            """)
    Optional<FinanceReminderNotification> findByIdForUpdate(@Param("id") UUID id);
}