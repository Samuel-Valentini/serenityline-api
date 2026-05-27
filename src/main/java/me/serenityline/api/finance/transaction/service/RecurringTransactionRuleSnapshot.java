package me.serenityline.api.finance.transaction.service;

import me.serenityline.api.finance.transaction.entity.PaymentDateAdjustmentPolicy;
import me.serenityline.api.finance.transaction.entity.RecurrenceUnit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record RecurringTransactionRuleSnapshot(
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        short dayOfUnit,
        short recurrenceInterval,
        RecurrenceUnit recurrenceUnit,
        PaymentDateAdjustmentPolicy paymentDateAdjustmentPolicy,
        BigDecimal paymentAmount,
        LocalDate recurringTransactionEndDate,
        BigDecimal finalPaymentAmount,
        long precedence
) {
    public RecurringTransactionRuleSnapshot {
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        Objects.requireNonNull(recurrenceUnit, "recurrenceUnit");
        Objects.requireNonNull(paymentDateAdjustmentPolicy, "paymentDateAdjustmentPolicy");
        Objects.requireNonNull(paymentAmount, "paymentAmount");

        if (recurrenceInterval <= 0) {
            throw new IllegalArgumentException("finance.recurringTransaction.recurrenceIntervalInvalid");
        }

        if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
            throw new IllegalArgumentException("finance.recurringTransaction.effectiveDatesInvalid");
        }

        if (recurringTransactionEndDate != null && recurringTransactionEndDate.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("finance.recurringTransaction.endDateInvalid");
        }
    }

    boolean isEffectiveAt(LocalDate date) {
        return !effectiveFrom.isAfter(date)
                && (effectiveTo == null || effectiveTo.isAfter(date));
    }
}