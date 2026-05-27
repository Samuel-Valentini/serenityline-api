package me.serenityline.api.finance.transaction.dto;

import me.serenityline.api.finance.transaction.entity.RecurringTransactionDetailsHistory;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RecurringTransactionDetailsHistoryItemResponse(
        UUID recurringTransactionDetailsHistoryId,
        LocalDate effectiveFrom,
        String recurringTransactionDescription,
        UUID categoryId,
        UUID financialPriorityId,
        UUID linkedAccountId,
        UUID linkedCreditCardId,
        UUID linkedBucketId,
        boolean recurringTransactionAffectsAccountBalance,
        boolean recurringTransactionAffectsLiquidity,
        OffsetDateTime createdAt
) {

    public static RecurringTransactionDetailsHistoryItemResponse from(
            RecurringTransactionDetailsHistory history
    ) {
        return new RecurringTransactionDetailsHistoryItemResponse(
                history.getRecurringTransactionDetailsHistoryId(),
                history.getRecurringTransactionDetailsEffectiveFrom(),
                history.getRecurringTransactionDescription(),
                history.getCategory().getCategoryId(),
                history.getFinancialPriority().getFinancialPriorityId(),
                history.getLinkedAccount().getAccountId(),
                history.getLinkedCreditCard() == null
                        ? null
                        : history.getLinkedCreditCard().getCreditCardId(),
                history.getLinkedBucket() == null
                        ? null
                        : history.getLinkedBucket().getBucketId(),
                history.isRecurringTransactionAffectsAccountBalance(),
                history.isRecurringTransactionAffectsLiquidity(),
                history.getRecurringTransactionDetailsHistoryCreatedAt()
        );
    }
}