package me.serenityline.api.finance.transaction.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionUpdateRequest(

        @NotBlank(message = "finance.transaction.descriptionRequired")
        @Size(max = 500, message = "finance.transaction.descriptionTooLong")
        String transactionDescription,

        @NotNull(message = "finance.transaction.amountRequired")
        @Digits(integer = 17, fraction = 2, message = "finance.transaction.amountInvalid")
        BigDecimal transactionAmount,

        @NotNull(message = "finance.transaction.affectsAccountBalanceRequired")
        Boolean transactionAffectsAccountBalance,

        @NotNull(message = "finance.transaction.affectsSerenitylineRequired")
        Boolean transactionAffectsSerenityline,

        @NotNull(message = "finance.transaction.categoryRequired")
        UUID categoryId,

        @NotNull(message = "finance.transaction.chargeDateRequired")
        LocalDate transactionChargeDate,

        @NotNull(message = "finance.transaction.confirmedRequired")
        Boolean transactionIsConfirmed,

        @NotNull(message = "finance.transaction.accountRequired")
        UUID accountId,

        UUID creditCardId,

        UUID bucketId,

        @NotNull(message = "finance.transaction.simulatedRequired")
        Boolean transactionIsSimulated,

        UUID simulationGroupId,

        @NotNull(message = "finance.transaction.reminderEnabledRequired")
        Boolean transactionReminderEnabled,

        @NotNull(message = "finance.transaction.reminderDaysRequired")
        @Min(value = 0, message = "finance.transaction.reminderDaysInvalid")
        @Max(value = 366, message = "finance.transaction.reminderDaysInvalid")
        Integer transactionReminderDaysBefore
) {
}