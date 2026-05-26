package me.serenityline.api.finance.transaction.repository;

import me.serenityline.api.finance.transaction.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}