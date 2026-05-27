package me.serenityline.api.finance.transaction.dto;

import jakarta.validation.constraints.*;
import me.serenityline.api.finance.transaction.entity.PaymentDateAdjustmentPolicy;
import me.serenityline.api.finance.transaction.entity.RecurrenceUnit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RecurringTransactionCreateRequest(

        @NotBlank(message = "finance.recurringTransaction.descriptionRequired")
        @Size(max = 500, message = "finance.recurringTransaction.descriptionTooLong")
        String recurringTransactionDescription,

        @NotNull(message = "finance.recurringTransaction.paymentAmountRequired")
        @Digits(integer = 17, fraction = 2, message = "finance.recurringTransaction.paymentAmountInvalid")
        BigDecimal paymentAmount,

        Boolean recurringTransactionAmountIsAdjustable,

        @NotNull(message = "finance.recurringTransaction.firstPaymentDateRequired")
        LocalDate recurringTransactionFirstPaymentDate,

        @NotNull(message = "finance.recurringTransaction.recurrenceIntervalRequired")
        @Min(value = 1, message = "finance.recurringTransaction.recurrenceIntervalInvalid")
        @Max(value = 32767, message = "finance.recurringTransaction.recurrenceIntervalInvalid")
        Integer recurrenceInterval,

        @NotNull(message = "finance.recurringTransaction.recurrenceUnitRequired")
        RecurrenceUnit recurrenceUnit,

        PaymentDateAdjustmentPolicy paymentDateAdjustmentPolicy,

        LocalDate recurringTransactionEndDate,

        @Digits(integer = 17, fraction = 2, message = "finance.recurringTransaction.finalPaymentAmountInvalid")
        BigDecimal finalPaymentAmount,

        @NotNull(message = "finance.recurringTransaction.categoryRequired")
        UUID categoryId,

        @NotNull(message = "finance.recurringTransaction.financialPriorityRequired")
        UUID financialPriorityId,

        @NotNull(message = "finance.recurringTransaction.accountRequired")
        UUID linkedAccountId,

        UUID linkedCreditCardId,

        UUID linkedBucketId,

        Boolean recurringTransactionAffectsAccountBalance,

        Boolean recurringTransactionAffectsLiquidity,

        Boolean recurringTransactionIsSimulated,

        UUID simulationGroupId,

        Boolean recurringTransactionReminderEnabled,

        @Min(value = 0, message = "finance.recurringTransaction.reminderDaysInvalid")
        @Max(value = 366, message = "finance.recurringTransaction.reminderDaysInvalid")
        Integer recurringTransactionReminderDaysBefore
) {
}