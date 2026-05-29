package me.serenityline.api.finance.transaction.dto;

import me.serenityline.api.finance.transaction.entity.RecurringTransaction;
import me.serenityline.api.finance.transaction.entity.RecurringTransactionDetailsHistory;
import me.serenityline.api.finance.transaction.entity.RecurringTransactionHistory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RecurringTransactionResponse(
        UUID recurringTransactionId,

        boolean recurringTransactionAmountIsAdjustable,
        LocalDate recurringTransactionFirstPaymentDate,
        boolean recurringTransactionIsSimulated,
        UUID simulationGroupId,
        boolean recurringTransactionReminderEnabled,
        short recurringTransactionReminderDaysBefore,
        OffsetDateTime recurringTransactionCreatedAt,
        OffsetDateTime recurringTransactionUpdatedAt,

        UUID recurringTransactionHistoryId,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        short dayOfUnit,
        short recurrenceInterval,
        String recurrenceUnit,
        String paymentDateAdjustmentPolicy,
        BigDecimal paymentAmount,
        LocalDate recurringTransactionEndDate,
        BigDecimal finalPaymentAmount,

        UUID recurringTransactionDetailsHistoryId,
        String recurringTransactionDescription,
        UUID categoryId,
        UUID financialPriorityId,
        UUID linkedAccountId,
        UUID linkedCreditCardId,
        UUID linkedBucketId,
        boolean recurringTransactionAffectsAccountBalance,
        boolean recurringtransactionAffectsSerenityline,
        LocalDate recurringTransactionDetailsEffectiveFrom
) {

    public static RecurringTransactionResponse from(
            RecurringTransaction recurringTransaction,
            RecurringTransactionHistory history,
            RecurringTransactionDetailsHistory details
    ) {
        return new RecurringTransactionResponse(
                recurringTransaction.getRecurringTransactionId(),
                recurringTransaction.isRecurringTransactionAmountIsAdjustable(),
                recurringTransaction.getRecurringTransactionFirstPaymentDate(),
                recurringTransaction.isRecurringTransactionIsSimulated(),
                recurringTransaction.getSimulationGroup() == null
                        ? null
                        : recurringTransaction.getSimulationGroup().getSimulationGroupId(),
                recurringTransaction.isRecurringTransactionReminderEnabled(),
                recurringTransaction.getRecurringTransactionReminderDaysBefore(),
                recurringTransaction.getRecurringTransactionCreatedAt(),
                recurringTransaction.getRecurringTransactionUpdatedAt(),

                history.getRecurringTransactionHistoryId(),
                history.getEffectiveFrom(),
                history.getEffectiveTo(),
                history.getDayOfUnit(),
                history.getRecurrenceInterval(),
                history.getRecurrenceUnit().name(),
                history.getPaymentDateAdjustmentPolicy().name(),
                history.getPaymentAmount(),
                history.getRecurringTransactionEndDate(),
                history.getFinalPaymentAmount(),

                details.getRecurringTransactionDetailsHistoryId(),
                details.getRecurringTransactionDescription(),
                details.getCategory().getCategoryId(),
                details.getFinancialPriority().getFinancialPriorityId(),
                details.getLinkedAccount().getAccountId(),
                details.getLinkedCreditCard() == null
                        ? null
                        : details.getLinkedCreditCard().getCreditCardId(),
                details.getLinkedBucket() == null
                        ? null
                        : details.getLinkedBucket().getBucketId(),
                details.isRecurringTransactionAffectsAccountBalance(),
                details.isRecurringTransactionAffectsSerenityline(),
                details.getRecurringTransactionDetailsEffectiveFrom()
        );
    }
}