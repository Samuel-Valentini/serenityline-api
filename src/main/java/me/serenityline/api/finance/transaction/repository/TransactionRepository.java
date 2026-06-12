package me.serenityline.api.finance.transaction.repository;

import me.serenityline.api.finance.transaction.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByTransactionIdAndUserGroup_UserGroupId(
            UUID transactionId,
            UUID userGroupId
    );

    @Query(value = """
            SELECT t.*
            FROM transactions t
            JOIN accounts_users au
                ON au.account_id = t.account_id
                AND au.user_group_id = t.user_group_id
            WHERE t.transaction_id = :transactionId
                AND t.user_group_id = :userGroupId
                AND au.user_id = :userId
            """, nativeQuery = true)
    Optional<Transaction> findByTransactionIdAndLinkedUserAccess(
            @Param("transactionId") UUID transactionId,
            @Param("userGroupId") UUID userGroupId,
            @Param("userId") UUID userId
    );

    @Query(value = """
            SELECT t.*
            FROM transactions t
            WHERE t.user_group_id = :userGroupId
                AND t.transaction_charge_date >= :from
                AND t.transaction_charge_date <= :to
                AND (:accountId IS NULL OR t.account_id = :accountId)
                AND (:simulationGroupId IS NULL OR t.simulation_group_id = :simulationGroupId)
            ORDER BY
                t.transaction_charge_date ASC,
                t.transaction_created_at ASC,
                t.transaction_id ASC
            """, nativeQuery = true)
    List<Transaction> findGroupTransactionsInRange(
            @Param("userGroupId") UUID userGroupId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("accountId") UUID accountId,
            @Param("simulationGroupId") UUID simulationGroupId
    );

    @Query(value = """
            SELECT t.*
            FROM transactions t
            JOIN accounts_users au
                ON au.account_id = t.account_id
                AND au.user_group_id = t.user_group_id
            WHERE t.user_group_id = :userGroupId
                AND au.user_id = :userId
                AND t.transaction_charge_date >= :from
                AND t.transaction_charge_date <= :to
                AND (:accountId IS NULL OR t.account_id = :accountId)
                AND (:simulationGroupId IS NULL OR t.simulation_group_id = :simulationGroupId)
            ORDER BY
                t.transaction_charge_date ASC,
                t.transaction_created_at ASC,
                t.transaction_id ASC
            """, nativeQuery = true)
    List<Transaction> findLinkedUserTransactionsInRange(
            @Param("userGroupId") UUID userGroupId,
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("accountId") UUID accountId,
            @Param("simulationGroupId") UUID simulationGroupId
    );

    @Query(value = """
            SELECT t.*
            FROM transactions t
            WHERE t.user_group_id = :userGroupId
                AND t.transaction_charge_date >= :from
                AND t.transaction_charge_date <= :to
                AND (:accountId IS NULL OR t.account_id = :accountId)
                AND t.transaction_is_simulated = FALSE
            ORDER BY
                t.transaction_charge_date ASC,
                t.transaction_created_at ASC,
                t.transaction_id ASC
            """, nativeQuery = true)
    List<Transaction> findBaseGroupTransactionsInRange(
            @Param("userGroupId") UUID userGroupId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("accountId") UUID accountId
    );

    @Query(value = """
            SELECT t.*
            FROM transactions t
            WHERE t.user_group_id = :userGroupId
                AND t.transaction_charge_date >= :from
                AND t.transaction_charge_date <= :to
                AND (:accountId IS NULL OR t.account_id = :accountId)
                AND (
                    t.transaction_is_simulated = FALSE
                    OR t.simulation_group_id IN (:simulationGroupIds)
                )
            ORDER BY
                t.transaction_charge_date ASC,
                t.transaction_created_at ASC,
                t.transaction_id ASC
            """, nativeQuery = true)
    List<Transaction> findBaseAndSimulatedGroupTransactionsInRange(
            @Param("userGroupId") UUID userGroupId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("accountId") UUID accountId,
            @Param("simulationGroupIds") List<UUID> simulationGroupIds
    );

    @Query(value = """
            SELECT t.*
            FROM transactions t
            JOIN accounts_users au
                ON au.account_id = t.account_id
                AND au.user_group_id = t.user_group_id
            WHERE t.user_group_id = :userGroupId
                AND au.user_id = :userId
                AND t.transaction_charge_date >= :from
                AND t.transaction_charge_date <= :to
                AND (:accountId IS NULL OR t.account_id = :accountId)
                AND t.transaction_is_simulated = FALSE
            ORDER BY
                t.transaction_charge_date ASC,
                t.transaction_created_at ASC,
                t.transaction_id ASC
            """, nativeQuery = true)
    List<Transaction> findBaseLinkedUserTransactionsInRange(
            @Param("userGroupId") UUID userGroupId,
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("accountId") UUID accountId
    );

    @Query(value = """
            SELECT t.*
            FROM transactions t
            JOIN accounts_users au
                ON au.account_id = t.account_id
                AND au.user_group_id = t.user_group_id
            WHERE t.user_group_id = :userGroupId
                AND au.user_id = :userId
                AND t.transaction_charge_date >= :from
                AND t.transaction_charge_date <= :to
                AND (:accountId IS NULL OR t.account_id = :accountId)
                AND (
                    t.transaction_is_simulated = FALSE
                    OR t.simulation_group_id IN (:simulationGroupIds)
                )
            ORDER BY
                t.transaction_charge_date ASC,
                t.transaction_created_at ASC,
                t.transaction_id ASC
            """, nativeQuery = true)
    List<Transaction> findBaseAndSimulatedLinkedUserTransactionsInRange(
            @Param("userGroupId") UUID userGroupId,
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("accountId") UUID accountId,
            @Param("simulationGroupIds") List<UUID> simulationGroupIds
    );

    @Query(value = """
            SELECT
                t.recurring_transaction_id AS "recurringTransactionId",
                t.recurring_transaction_logical_date AS "recurringTransactionLogicalDate"
            FROM transactions t
            WHERE t.user_group_id = :userGroupId
                AND t.recurring_transaction_id IN (:recurringTransactionIds)
                AND t.transaction_is_user_entered = FALSE
                AND t.transaction_is_confirmed = TRUE
                AND t.recurring_transaction_id IS NOT NULL
                AND t.recurring_transaction_logical_date >= :from
                AND t.recurring_transaction_logical_date <= :to
            """, nativeQuery = true)
    List<ConfirmedRecurringOccurrenceKeyView> findConfirmedRecurringOccurrenceKeysForRecurringTransactions(
            @Param("userGroupId") UUID userGroupId,
            @Param("recurringTransactionIds") Collection<UUID> recurringTransactionIds,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    @Query(value = """
            SELECT t.*
            FROM transactions t
            WHERE t.user_group_id = :userGroupId
                AND t.transaction_charge_date >= :from
                AND t.transaction_charge_date <= :to
                AND t.account_id IN (:accountIds)
                AND t.transaction_is_simulated = FALSE
            ORDER BY
                t.transaction_charge_date ASC,
                t.transaction_created_at ASC,
                t.transaction_id ASC
            """, nativeQuery = true)
    List<Transaction> findBaseGroupTransactionsInRangeForAccounts(
            @Param("userGroupId") UUID userGroupId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("accountIds") Collection<UUID> accountIds
    );

    @Query(value = """
            SELECT t.*
            FROM transactions t
            WHERE t.user_group_id = :userGroupId
                AND t.transaction_charge_date >= :from
                AND t.transaction_charge_date <= :to
                AND t.account_id IN (:accountIds)
                AND (
                    t.transaction_is_simulated = FALSE
                    OR t.simulation_group_id IN (:simulationGroupIds)
                )
            ORDER BY
                t.transaction_charge_date ASC,
                t.transaction_created_at ASC,
                t.transaction_id ASC
            """, nativeQuery = true)
    List<Transaction> findBaseAndSimulatedGroupTransactionsInRangeForAccounts(
            @Param("userGroupId") UUID userGroupId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("accountIds") Collection<UUID> accountIds,
            @Param("simulationGroupIds") Collection<UUID> simulationGroupIds
    );

    @Query(value = """
            SELECT t.*
            FROM transactions t
            JOIN accounts_users au
                ON au.account_id = t.account_id
                AND au.user_group_id = t.user_group_id
            WHERE t.user_group_id = :userGroupId
                AND au.user_id = :userId
                AND t.transaction_charge_date >= :from
                AND t.transaction_charge_date <= :to
                AND t.account_id IN (:accountIds)
                AND t.transaction_is_simulated = FALSE
            ORDER BY
                t.transaction_charge_date ASC,
                t.transaction_created_at ASC,
                t.transaction_id ASC
            """, nativeQuery = true)
    List<Transaction> findBaseLinkedUserTransactionsInRangeForAccounts(
            @Param("userGroupId") UUID userGroupId,
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("accountIds") Collection<UUID> accountIds
    );

    @Query(value = """
            SELECT t.*
            FROM transactions t
            JOIN accounts_users au
                ON au.account_id = t.account_id
                AND au.user_group_id = t.user_group_id
            WHERE t.user_group_id = :userGroupId
                AND au.user_id = :userId
                AND t.transaction_charge_date >= :from
                AND t.transaction_charge_date <= :to
                AND t.account_id IN (:accountIds)
                AND (
                    t.transaction_is_simulated = FALSE
                    OR t.simulation_group_id IN (:simulationGroupIds)
                )
            ORDER BY
                t.transaction_charge_date ASC,
                t.transaction_created_at ASC,
                t.transaction_id ASC
            """, nativeQuery = true)
    List<Transaction> findBaseAndSimulatedLinkedUserTransactionsInRangeForAccounts(
            @Param("userGroupId") UUID userGroupId,
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("accountIds") Collection<UUID> accountIds,
            @Param("simulationGroupIds") Collection<UUID> simulationGroupIds
    );

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM transactions t
                WHERE t.user_group_id = :userGroupId
                  AND t.recurring_transaction_id = :recurringTransactionId
                  AND t.recurring_transaction_logical_date = :logicalDate
                  AND t.transaction_is_user_entered = FALSE
                  AND t.transaction_is_confirmed = TRUE
            )
            """, nativeQuery = true)
    boolean existsConfirmedRecurringOccurrence(
            @Param("userGroupId") UUID userGroupId,
            @Param("recurringTransactionId") UUID recurringTransactionId,
            @Param("logicalDate") LocalDate logicalDate
    );

    boolean existsByCreditCard_CreditCardId(UUID creditCardId);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM transactions transaction
                WHERE transaction.bucket_id = :bucketId
                  AND transaction.account_id = :accountId
                  AND transaction.user_group_id = :userGroupId
            )
            """, nativeQuery = true)
    boolean existsByBucketIdAndAccountIdAndUserGroupId(
            @Param("bucketId") UUID bucketId,
            @Param("accountId") UUID accountId,
            @Param("userGroupId") UUID userGroupId
    );

    @Query(value = """
            SELECT COALESCE(SUM(
                CASE
                    WHEN t.transaction_affects_account_balance = FALSE
                     AND t.transaction_affects_serenityline = TRUE
                        THEN -t.transaction_amount
                    ELSE t.transaction_amount
                END
            ), 0)
            FROM transactions t
            WHERE t.bucket_id = :bucketId
              AND t.user_group_id = :userGroupId
              AND t.transaction_is_simulated = FALSE
              AND t.transaction_charge_date <= :asOfDate
            """, nativeQuery = true)
    BigDecimal calculatePersistedBaseBucketBalanceAt(
            @Param("bucketId") UUID bucketId,
            @Param("userGroupId") UUID userGroupId,
            @Param("asOfDate") LocalDate asOfDate
    );

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM transactions t
                WHERE t.bucket_id = :bucketId
                  AND t.user_group_id = :userGroupId
                  AND t.transaction_is_simulated = FALSE
                  AND t.transaction_charge_date > :asOfDate
            )
            """, nativeQuery = true)
    boolean existsFutureBaseBucketTransaction(
            @Param("bucketId") UUID bucketId,
            @Param("userGroupId") UUID userGroupId,
            @Param("asOfDate") LocalDate asOfDate
    );

    interface ConfirmedRecurringOccurrenceKeyView {

        UUID getRecurringTransactionId();

        LocalDate getRecurringTransactionLogicalDate();
    }

}

