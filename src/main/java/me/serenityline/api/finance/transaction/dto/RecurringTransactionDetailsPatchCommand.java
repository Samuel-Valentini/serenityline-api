package me.serenityline.api.finance.transaction.dto;

import java.time.LocalDate;
import java.util.UUID;

public record RecurringTransactionDetailsPatchCommand(
        PatchField<LocalDate> effectiveFrom,
        PatchField<String> recurringTransactionDescription,
        PatchField<UUID> categoryId,
        PatchField<UUID> financialPriorityId,
        PatchField<UUID> linkedAccountId,
        PatchField<UUID> linkedCreditCardId,
        PatchField<UUID> linkedBucketId,
        PatchField<Boolean> recurringTransactionAffectsAccountBalance,
        PatchField<Boolean> recurringTransactionAffectsLiquidity
) {

    public boolean hasAnyField() {
        return effectiveFrom.isPresent()
                || recurringTransactionDescription.isPresent()
                || categoryId.isPresent()
                || financialPriorityId.isPresent()
                || linkedAccountId.isPresent()
                || linkedCreditCardId.isPresent()
                || linkedBucketId.isPresent()
                || recurringTransactionAffectsAccountBalance.isPresent()
                || recurringTransactionAffectsLiquidity.isPresent();
    }
}