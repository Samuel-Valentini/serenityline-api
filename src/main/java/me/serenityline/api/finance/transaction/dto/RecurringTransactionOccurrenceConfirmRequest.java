package me.serenityline.api.finance.transaction.dto;

import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecurringTransactionOccurrenceConfirmRequest(

        LocalDate logicalDate,

        @Digits(integer = 17, fraction = 2, message = "finance.transaction.amountInvalid")
        BigDecimal transactionAmount,

        LocalDate transactionChargeDate
) {
}