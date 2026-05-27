package me.serenityline.api.finance.transaction.dto;

import java.time.LocalDate;
import java.util.UUID;

public record RecurringTransactionPatchCommand(
        PatchField<LocalDate> recurringTransactionFirstPaymentDate,
        PatchField<Boolean> recurringTransactionAmountIsAdjustable,
        PatchField<Boolean> recurringTransactionIsSimulated,
        PatchField<UUID> simulationGroupId,
        PatchField<Boolean> recurringTransactionReminderEnabled,
        PatchField<Integer> recurringTransactionReminderDaysBefore,
        RecurringTransactionRulePatchCommand rule,
        RecurringTransactionDetailsPatchCommand details
) {

    public boolean hasRulePatch() {
        return rule != null;
    }

    public boolean hasDetailsPatch() {
        return details != null;
    }

    public boolean hasRootPatch() {
        return recurringTransactionFirstPaymentDate.isPresent()
                || recurringTransactionAmountIsAdjustable.isPresent()
                || recurringTransactionIsSimulated.isPresent()
                || simulationGroupId.isPresent()
                || recurringTransactionReminderEnabled.isPresent()
                || recurringTransactionReminderDaysBefore.isPresent();
    }

    public boolean hasAnyPatch() {
        return hasRootPatch() || hasRulePatch() || hasDetailsPatch();
    }
}