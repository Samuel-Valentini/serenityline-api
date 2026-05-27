package me.serenityline.api.finance.transaction.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecurringTransactionDeleteCommand(
        PatchField<LocalDate> endDate,
        PatchField<BigDecimal> finalPaymentAmount
) {
}