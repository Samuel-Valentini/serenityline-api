package me.serenityline.api.finance.calendar;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record FinanceCalendarMovementResponse(
        FinanceCalendarMovementType movementType,
        UUID transactionId,
        UUID recurringTransactionId,
        LocalDate logicalDate,
        LocalDate chargeDate,
        String description,
        BigDecimal amount,
        boolean affectsAccountBalance,
        boolean affectsSerenityline,
        UUID categoryId,
        UUID financialPriorityId,
        UUID accountId,
        UUID creditCardId,
        UUID bucketId,
        boolean confirmed,
        boolean simulated,
        UUID simulationGroupId,
        boolean userEntered,
        boolean finalOccurrence
) {

    public static FinanceCalendarMovementResponse from(
            FinanceCalendarMovement movement
    ) {
        return new FinanceCalendarMovementResponse(
                movement.type(),
                movement.transactionId(),
                movement.recurringTransactionId(),
                movement.logicalDate(),
                movement.chargeDate(),
                movement.description(),
                movement.amount(),
                movement.affectsAccountBalance(),
                movement.affectsSerenityline(),
                movement.categoryId(),
                movement.financialPriorityId(),
                movement.accountId(),
                movement.creditCardId(),
                movement.bucketId(),
                movement.confirmed(),
                movement.simulated(),
                movement.simulationGroupId(),
                movement.userEntered(),
                movement.finalOccurrence()
        );
    }
}