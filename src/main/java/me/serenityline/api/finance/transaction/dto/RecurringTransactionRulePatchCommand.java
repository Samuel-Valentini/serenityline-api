package me.serenityline.api.finance.transaction.dto;

import me.serenityline.api.finance.transaction.entity.PaymentDateAdjustmentPolicy;
import me.serenityline.api.finance.transaction.entity.RecurrenceUnit;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecurringTransactionRulePatchCommand(
        PatchField<LocalDate> effectiveFrom,
        PatchField<LocalDate> effectiveTo,
        PatchField<Integer> dayOfUnit,
        PatchField<BigDecimal> paymentAmount,
        PatchField<Integer> recurrenceInterval,
        PatchField<RecurrenceUnit> recurrenceUnit,
        PatchField<PaymentDateAdjustmentPolicy> paymentDateAdjustmentPolicy,
        PatchField<LocalDate> recurringTransactionEndDate,
        PatchField<BigDecimal> finalPaymentAmount
) {

    public boolean hasAnyField() {
        return effectiveFrom.isPresent()
                || effectiveTo.isPresent()
                || dayOfUnit.isPresent()
                || paymentAmount.isPresent()
                || recurrenceInterval.isPresent()
                || recurrenceUnit.isPresent()
                || paymentDateAdjustmentPolicy.isPresent()
                || recurringTransactionEndDate.isPresent()
                || finalPaymentAmount.isPresent();
    }
}