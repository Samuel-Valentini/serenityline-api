package me.serenityline.api.finance.creditcard.service;

import me.serenityline.api.finance.transaction.repository.RecurringTransactionDetailsHistoryRepository;
import me.serenityline.api.finance.transaction.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class CreditCardUsageChecker {

    private final TransactionRepository transactionRepository;
    private final RecurringTransactionDetailsHistoryRepository recurringTransactionDetailsHistoryRepository;

    public CreditCardUsageChecker(
            TransactionRepository transactionRepository,
            RecurringTransactionDetailsHistoryRepository recurringTransactionDetailsHistoryRepository
    ) {
        this.transactionRepository = Objects.requireNonNull(transactionRepository, "transactionRepository");
        this.recurringTransactionDetailsHistoryRepository = Objects.requireNonNull(
                recurringTransactionDetailsHistoryRepository,
                "recurringTransactionDetailsHistoryRepository"
        );
    }

    public boolean isCreditCardUsed(UUID creditCardId) {
        Objects.requireNonNull(creditCardId, "creditCardId");

        return transactionRepository.existsByCreditCard_CreditCardId(creditCardId)
                || recurringTransactionDetailsHistoryRepository.existsByLinkedCreditCard_CreditCardId(creditCardId);
    }
}