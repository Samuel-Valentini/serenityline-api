package me.serenityline.api.finance.transaction.repository;

import me.serenityline.api.finance.transaction.entity.RecurringTransactionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecurringTransactionHistoryRepository extends JpaRepository<RecurringTransactionHistory, UUID> {

    Optional<RecurringTransactionHistory>
    findFirstByRecurringTransaction_RecurringTransactionIdAndEffectiveToIsNullOrderByEffectiveFromDescRecurringTransactionHistoryCreatedAtDesc(
            UUID recurringTransactionId
    );

    List<RecurringTransactionHistory>
    findAllByRecurringTransaction_RecurringTransactionIdOrderByEffectiveFromAscRecurringTransactionHistoryCreatedAtAsc(
            UUID recurringTransactionId
    );
}