package me.serenityline.api.finance.calendar;

import me.serenityline.api.finance.transaction.entity.Transaction;
import me.serenityline.api.finance.transaction.service.RecurringTransactionProjectedMovement;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

@Component
public class FinanceCalendarMovementMapper {

    public FinanceCalendarMovement fromPersistedTransaction(Transaction transaction) {
        Objects.requireNonNull(transaction, "transaction");

        return new FinanceCalendarMovement(
                FinanceCalendarMovementType.PERSISTED_TRANSACTION,
                transaction.getTransactionId(),
                transaction.getRecurringTransaction() == null
                        ? null
                        : transaction.getRecurringTransaction().getRecurringTransactionId(),
                transaction.getTransactionChargeDate(),
                transaction.getTransactionChargeDate(),
                transaction.getTransactionDescription(),
                transaction.getTransactionAmount(),
                transaction.isTransactionAffectsAccountBalance(),
                transaction.isTransactionAffectsSerenityline(),
                transaction.getCategory().getCategoryId(),
                null,
                transaction.getAccount().getAccountId(),
                transaction.getCreditCard() == null
                        ? null
                        : transaction.getCreditCard().getCreditCardId(),
                transaction.getBucket() == null
                        ? null
                        : transaction.getBucket().getBucketId(),
                transaction.isTransactionIsConfirmed(),
                transaction.isTransactionIsSimulated(),
                transaction.getSimulationGroup() == null
                        ? null
                        : transaction.getSimulationGroup().getSimulationGroupId(),
                transaction.isTransactionIsUserEntered(),
                false
        );
    }

    public FinanceCalendarMovement fromProjectedRecurringMovement(
            RecurringTransactionProjectedMovement movement,
            boolean simulated,
            UUID simulationGroupId
    ) {
        Objects.requireNonNull(movement, "movement");

        return new FinanceCalendarMovement(
                FinanceCalendarMovementType.PROJECTED_RECURRING_TRANSACTION,
                null,
                movement.recurringTransactionId(),
                movement.logicalDate(),
                movement.chargeDate(),
                movement.description(),
                movement.amount(),
                movement.affectsAccountBalance(),
                movement.affectsSerenityline(),
                movement.category().getCategoryId(),
                movement.financialPriority().getFinancialPriorityId(),
                movement.linkedAccount().getAccountId(),
                movement.linkedCreditCard() == null
                        ? null
                        : movement.linkedCreditCard().getCreditCardId(),
                movement.linkedBucket() == null
                        ? null
                        : movement.linkedBucket().getBucketId(),
                false,
                simulated,
                simulationGroupId,
                false,
                movement.finalOccurrence()
        );
    }
}