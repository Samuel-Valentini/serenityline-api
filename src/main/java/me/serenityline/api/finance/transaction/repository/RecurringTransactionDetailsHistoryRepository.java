package me.serenityline.api.finance.transaction.repository;

import me.serenityline.api.finance.transaction.entity.RecurringTransactionDetailsHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecurringTransactionDetailsHistoryRepository
        extends JpaRepository<RecurringTransactionDetailsHistory, UUID> {

    Optional<RecurringTransactionDetailsHistory>
    findFirstByRecurringTransaction_RecurringTransactionIdAndUserGroup_UserGroupIdOrderByRecurringTransactionDetailsEffectiveFromDescRecurringTransactionDetailsHistoryCreatedAtDesc(
            UUID recurringTransactionId,
            UUID userGroupId
    );

    List<RecurringTransactionDetailsHistory>
    findAllByRecurringTransaction_RecurringTransactionIdAndUserGroup_UserGroupIdOrderByRecurringTransactionDetailsEffectiveFromAscRecurringTransactionDetailsHistoryCreatedAtAsc(
            UUID recurringTransactionId,
            UUID userGroupId
    );
}