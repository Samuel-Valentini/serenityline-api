package me.serenityline.api.finance.transaction.repository;

import me.serenityline.api.finance.transaction.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
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
}