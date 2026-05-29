package me.serenityline.api.finance.transaction.service;

import me.serenityline.api.finance.account.entity.Account;
import me.serenityline.api.finance.bucket.entity.Bucket;
import me.serenityline.api.finance.category.entity.Category;
import me.serenityline.api.finance.creditcard.entity.CreditCard;
import me.serenityline.api.finance.financialpriority.entity.FinancialPriority;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record RecurringTransactionProjectedMovement(
        UUID recurringTransactionId,
        LocalDate logicalDate,
        LocalDate chargeDate,
        BigDecimal amount,
        boolean finalOccurrence,
        String description,
        Category category,
        FinancialPriority financialPriority,
        Account linkedAccount,
        CreditCard linkedCreditCard,
        Bucket linkedBucket,
        boolean affectsAccountBalance,
        boolean affectsSerenityline
) {
    public RecurringTransactionProjectedMovement {
        Objects.requireNonNull(recurringTransactionId, "recurringTransactionId");
        Objects.requireNonNull(logicalDate, "logicalDate");
        Objects.requireNonNull(chargeDate, "chargeDate");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(financialPriority, "financialPriority");
        Objects.requireNonNull(linkedAccount, "linkedAccount");

        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("finance.recurringTransaction.descriptionRequired");
        }

        description = description.trim();

        if (!affectsAccountBalance && !affectsSerenityline) {
            throw new IllegalArgumentException("finance.recurringTransaction.affectsSomethingRequired");
        }
    }
}