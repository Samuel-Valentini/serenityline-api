package me.serenityline.api.finance.transaction.dto;

import me.serenityline.api.finance.transaction.entity.Transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransactionResponse(
        UUID transactionId,
        String transactionDescription,
        BigDecimal transactionAmount,
        boolean transactionAffectsAccountBalance,
        boolean transactionAffectsLiquidity,
        UUID categoryId,
        LocalDate transactionChargeDate,
        boolean transactionIsConfirmed,
        UUID accountId,
        UUID creditCardId,
        UUID bucketId,
        boolean transactionIsSimulated,
        UUID simulationGroupId,
        boolean transactionIsUserEntered,
        UUID recurringTransactionId,
        LocalDate recurringTransactionLogicalDate,
        OffsetDateTime recurringTransactionConfirmedAt,
        boolean transactionReminderEnabled,
        short transactionReminderDaysBefore,
        OffsetDateTime transactionCreatedAt,
        OffsetDateTime transactionUpdatedAt
) {

    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getTransactionId(),
                transaction.getTransactionDescription(),
                transaction.getTransactionAmount(),
                transaction.isTransactionAffectsAccountBalance(),
                transaction.isTransactionAffectsLiquidity(),
                transaction.getCategory().getCategoryId(),
                transaction.getTransactionChargeDate(),
                transaction.isTransactionIsConfirmed(),
                transaction.getAccount().getAccountId(),
                transaction.getCreditCard() == null ? null : transaction.getCreditCard().getCreditCardId(),
                transaction.getBucket() == null ? null : transaction.getBucket().getBucketId(),
                transaction.isTransactionIsSimulated(),
                transaction.getSimulationGroup() == null ? null : transaction.getSimulationGroup().getSimulationGroupId(),
                transaction.isTransactionIsUserEntered(),
                transaction.getRecurringTransaction() == null ? null : transaction.getRecurringTransaction().getRecurringTransactionId(),
                transaction.getRecurringTransactionLogicalDate(),
                transaction.getRecurringTransactionConfirmedAt(),
                transaction.isTransactionReminderEnabled(),
                transaction.getTransactionReminderDaysBefore(),
                transaction.getTransactionCreatedAt(),
                transaction.getTransactionUpdatedAt()
        );
    }
}