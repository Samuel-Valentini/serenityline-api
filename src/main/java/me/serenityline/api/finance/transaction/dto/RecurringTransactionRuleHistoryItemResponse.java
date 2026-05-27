package me.serenityline.api.finance.transaction.dto;

import me.serenityline.api.finance.transaction.entity.PaymentDateAdjustmentPolicy;
import me.serenityline.api.finance.transaction.entity.RecurrenceUnit;
import me.serenityline.api.finance.transaction.entity.RecurringTransactionHistory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RecurringTransactionRuleHistoryItemResponse(
        UUID recurringTransactionHistoryId,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        short dayOfUnit,
        short recurrenceInterval,
        RecurrenceUnit recurrenceUnit,
        PaymentDateAdjustmentPolicy paymentDateAdjustmentPolicy,
        BigDecimal paymentAmount,
        LocalDate recurringTransactionEndDate,
        BigDecimal finalPaymentAmount,
        OffsetDateTime createdAt
) {

    public static RecurringTransactionRuleHistoryItemResponse from(
            RecurringTransactionHistory history
    ) {
        return new RecurringTransactionRuleHistoryItemResponse(
                history.getRecurringTransactionHistoryId(),
                history.getEffectiveFrom(),
                history.getEffectiveTo(),
                history.getDayOfUnit(),
                history.getRecurrenceInterval(),
                history.getRecurrenceUnit(),
                history.getPaymentDateAdjustmentPolicy(),
                history.getPaymentAmount(),
                history.getRecurringTransactionEndDate(),
                history.getFinalPaymentAmount(),
                history.getRecurringTransactionHistoryCreatedAt()
        );
    }
}