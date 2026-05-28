package me.serenityline.api.finance.transaction.repository;

import me.serenityline.api.finance.transaction.entity.RecurringTransactionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecurringTransactionHistoryRepository extends JpaRepository<RecurringTransactionHistory, UUID> {

    @Query(value = """
            SELECT history.*
            FROM recurring_transaction_history history
            WHERE history.recurring_transaction_id = :recurringTransactionId
              AND history.effective_to IS NULL
            ORDER BY
                history.recurring_transaction_history_created_at DESC,
                history.recurring_transaction_history_id DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<RecurringTransactionHistory> findCurrentOpenByRecurringTransactionId(
            @Param("recurringTransactionId") UUID recurringTransactionId
    );

    @Query(value = """
            SELECT history.*
            FROM recurring_transaction_history history
            WHERE history.recurring_transaction_id = :recurringTransactionId
              AND history.effective_from <= :targetDate
              AND (
                    history.effective_to IS NULL
                    OR history.effective_to > :targetDate
              )
            ORDER BY
                history.recurring_transaction_history_created_at DESC,
                history.recurring_transaction_history_id DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<RecurringTransactionHistory> findEffectiveAt(
            @Param("recurringTransactionId") UUID recurringTransactionId,
            @Param("targetDate") LocalDate targetDate
    );

    @Query(value = """
            SELECT history.*
            FROM recurring_transaction_history history
            WHERE history.recurring_transaction_id = :recurringTransactionId
            ORDER BY
                history.recurring_transaction_history_created_at ASC,
                history.recurring_transaction_history_id ASC
            """, nativeQuery = true)
    List<RecurringTransactionHistory> findAllHistoryByRecurringTransactionId(
            @Param("recurringTransactionId") UUID recurringTransactionId
    );

    @Query("""
            SELECT history
            FROM RecurringTransactionHistory history
            JOIN FETCH history.recurringTransaction recurringTransaction
            WHERE recurringTransaction.recurringTransactionId IN :recurringTransactionIds
              AND recurringTransaction.userGroup.userGroupId = :userGroupId
            ORDER BY
                recurringTransaction.recurringTransactionId ASC,
                history.recurringTransactionHistoryCreatedAt ASC,
                history.recurringTransactionHistoryId ASC
            """)
    List<RecurringTransactionHistory> findAllHistoryByRecurringTransactionIdsAndUserGroupId(
            @Param("recurringTransactionIds") Collection<UUID> recurringTransactionIds,
            @Param("userGroupId") UUID userGroupId
    );
}