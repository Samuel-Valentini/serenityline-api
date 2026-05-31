package me.serenityline.api.finance.bucket.service;

import me.serenityline.api.finance.transaction.repository.RecurringTransactionDetailsHistoryRepository;
import me.serenityline.api.finance.transaction.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class BucketAccountUsageChecker {

    private final TransactionRepository transactionRepository;
    private final RecurringTransactionDetailsHistoryRepository recurringTransactionDetailsHistoryRepository;

    public BucketAccountUsageChecker(
            TransactionRepository transactionRepository,
            RecurringTransactionDetailsHistoryRepository recurringTransactionDetailsHistoryRepository
    ) {
        this.transactionRepository = Objects.requireNonNull(transactionRepository, "transactionRepository");
        this.recurringTransactionDetailsHistoryRepository = Objects.requireNonNull(
                recurringTransactionDetailsHistoryRepository,
                "recurringTransactionDetailsHistoryRepository"
        );
    }

    public boolean isBucketAccountUsed(
            UUID bucketId,
            UUID accountId,
            UUID userGroupId
    ) {
        Objects.requireNonNull(bucketId, "bucketId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(userGroupId, "userGroupId");

        return transactionRepository.existsByBucketIdAndAccountIdAndUserGroupId(
                bucketId,
                accountId,
                userGroupId
        ) || recurringTransactionDetailsHistoryRepository.existsByLinkedBucketIdAndLinkedAccountIdAndUserGroupId(
                bucketId,
                accountId,
                userGroupId
        );
    }
}