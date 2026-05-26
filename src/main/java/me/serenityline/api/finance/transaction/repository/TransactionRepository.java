package me.serenityline.api.finance.transaction.repository;

import me.serenityline.api.finance.transaction.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByTransactionIdAndUserGroup_UserGroupId(
            UUID transactionId,
            UUID userGroupId
    );
}