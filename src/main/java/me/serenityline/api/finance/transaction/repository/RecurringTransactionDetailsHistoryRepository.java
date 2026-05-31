package me.serenityline.api.finance.transaction.repository;

import me.serenityline.api.finance.transaction.entity.RecurringTransactionDetailsHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecurringTransactionDetailsHistoryRepository
        extends JpaRepository<RecurringTransactionDetailsHistory, UUID> {

    @Query(value = """
            SELECT details.*
            FROM recurring_transaction_details_history details
            WHERE details.recurring_transaction_id = :recurringTransactionId
              AND details.user_group_id = :userGroupId
            ORDER BY
                details.recurring_transaction_details_history_created_at DESC,
                details.recurring_transaction_details_history_id DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<RecurringTransactionDetailsHistory> findCurrentByRecurringTransactionIdAndUserGroupId(
            @Param("recurringTransactionId") UUID recurringTransactionId,
            @Param("userGroupId") UUID userGroupId
    );

    @Query(value = """
            SELECT details.*
            FROM recurring_transaction_details_history details
            WHERE details.recurring_transaction_id = :recurringTransactionId
              AND details.user_group_id = :userGroupId
              AND details.recurring_transaction_details_effective_from <= :targetDate
            ORDER BY
                details.recurring_transaction_details_history_created_at DESC,
                details.recurring_transaction_details_history_id DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<RecurringTransactionDetailsHistory> findEffectiveAt(
            @Param("recurringTransactionId") UUID recurringTransactionId,
            @Param("userGroupId") UUID userGroupId,
            @Param("targetDate") LocalDate targetDate
    );

    @Query(value = """
            SELECT details.*
            FROM recurring_transaction_details_history details
            WHERE details.recurring_transaction_id = :recurringTransactionId
              AND details.user_group_id = :userGroupId
            ORDER BY
                details.recurring_transaction_details_history_created_at ASC,
                details.recurring_transaction_details_history_id ASC
            """, nativeQuery = true)
    List<RecurringTransactionDetailsHistory> findAllHistoryByRecurringTransactionIdAndUserGroupId(
            @Param("recurringTransactionId") UUID recurringTransactionId,
            @Param("userGroupId") UUID userGroupId
    );

    @Query("""
            SELECT details
            FROM RecurringTransactionDetailsHistory details
            JOIN FETCH details.linkedAccount linkedAccount
            JOIN FETCH details.category category
            JOIN FETCH details.financialPriority financialPriority
            LEFT JOIN FETCH details.linkedCreditCard linkedCreditCard
            LEFT JOIN FETCH details.linkedBucket linkedBucket
            WHERE details.recurringTransaction.recurringTransactionId = :recurringTransactionId
              AND details.userGroup.userGroupId = :userGroupId
            ORDER BY
                details.recurringTransactionDetailsHistoryCreatedAt ASC,
                details.recurringTransactionDetailsHistoryId ASC
            """)
    List<RecurringTransactionDetailsHistory> findAllHistoryWithLinkedAccountByRecurringTransactionIdAndUserGroupId(
            @Param("recurringTransactionId") UUID recurringTransactionId,
            @Param("userGroupId") UUID userGroupId
    );

    @Query("""
            SELECT details
            FROM RecurringTransactionDetailsHistory details
            JOIN FETCH details.recurringTransaction recurringTransaction
            JOIN FETCH details.linkedAccount linkedAccount
            JOIN FETCH details.category category
            JOIN FETCH details.financialPriority financialPriority
            LEFT JOIN FETCH details.linkedCreditCard linkedCreditCard
            LEFT JOIN FETCH details.linkedBucket linkedBucket
            WHERE recurringTransaction.recurringTransactionId IN :recurringTransactionIds
              AND details.userGroup.userGroupId = :userGroupId
            ORDER BY
                recurringTransaction.recurringTransactionId ASC,
                details.recurringTransactionDetailsHistoryCreatedAt ASC,
                details.recurringTransactionDetailsHistoryId ASC
            """)
    List<RecurringTransactionDetailsHistory> findAllHistoryWithDetailsByRecurringTransactionIdsAndUserGroupId(
            @Param("recurringTransactionIds") Collection<UUID> recurringTransactionIds,
            @Param("userGroupId") UUID userGroupId
    );

    boolean existsByLinkedCreditCard_CreditCardId(UUID creditCardId);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM recurring_transaction_details_history details
                WHERE details.linked_bucket_id = :bucketId
                  AND details.linked_account_id = :accountId
                  AND details.user_group_id = :userGroupId
            )
            """, nativeQuery = true)
    boolean existsByLinkedBucketIdAndLinkedAccountIdAndUserGroupId(
            @Param("bucketId") UUID bucketId,
            @Param("accountId") UUID accountId,
            @Param("userGroupId") UUID userGroupId
    );
}