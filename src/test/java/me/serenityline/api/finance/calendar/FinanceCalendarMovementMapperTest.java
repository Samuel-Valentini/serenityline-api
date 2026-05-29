package me.serenityline.api.finance.calendar;

import me.serenityline.api.finance.account.entity.Account;
import me.serenityline.api.finance.bucket.entity.Bucket;
import me.serenityline.api.finance.category.entity.Category;
import me.serenityline.api.finance.creditcard.entity.CreditCard;
import me.serenityline.api.finance.financialpriority.entity.FinancialPriority;
import me.serenityline.api.finance.simulation.entity.SimulationGroup;
import me.serenityline.api.finance.transaction.entity.RecurringTransaction;
import me.serenityline.api.finance.transaction.entity.Transaction;
import me.serenityline.api.finance.transaction.service.RecurringTransactionProjectedMovement;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinanceCalendarMovementMapperTest {

    private final FinanceCalendarMovementMapper mapper =
            new FinanceCalendarMovementMapper();

    @Test
    void shouldMapPersistedTransaction() {
        UUID transactionId = UUID.randomUUID();
        UUID recurringTransactionId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID creditCardId = UUID.randomUUID();
        UUID bucketId = UUID.randomUUID();
        UUID simulationGroupId = UUID.randomUUID();

        Category category = mock(Category.class);
        when(category.getCategoryId()).thenReturn(categoryId);

        Account account = mock(Account.class);
        when(account.getAccountId()).thenReturn(accountId);

        CreditCard creditCard = mock(CreditCard.class);
        when(creditCard.getCreditCardId()).thenReturn(creditCardId);

        Bucket bucket = mock(Bucket.class);
        when(bucket.getBucketId()).thenReturn(bucketId);

        SimulationGroup simulationGroup = mock(SimulationGroup.class);
        when(simulationGroup.getSimulationGroupId()).thenReturn(simulationGroupId);

        RecurringTransaction recurringTransaction = mock(RecurringTransaction.class);
        when(recurringTransaction.getRecurringTransactionId())
                .thenReturn(recurringTransactionId);

        Transaction transaction = mock(Transaction.class);
        when(transaction.getTransactionId()).thenReturn(transactionId);
        when(transaction.getRecurringTransaction()).thenReturn(recurringTransaction);
        when(transaction.getTransactionChargeDate()).thenReturn(LocalDate.of(2026, 1, 10));
        when(transaction.getTransactionDescription()).thenReturn(" Transazione persistita ");
        when(transaction.getTransactionAmount()).thenReturn(new BigDecimal("-100.00"));
        when(transaction.isTransactionAffectsAccountBalance()).thenReturn(true);
        when(transaction.isTransactionAffectsSerenityline()).thenReturn(false);
        when(transaction.getCategory()).thenReturn(category);
        when(transaction.getAccount()).thenReturn(account);
        when(transaction.getCreditCard()).thenReturn(creditCard);
        when(transaction.getBucket()).thenReturn(bucket);
        when(transaction.isTransactionIsConfirmed()).thenReturn(true);
        when(transaction.isTransactionIsSimulated()).thenReturn(true);
        when(transaction.getSimulationGroup()).thenReturn(simulationGroup);
        when(transaction.isTransactionIsUserEntered()).thenReturn(false);

        FinanceCalendarMovement movement =
                mapper.fromPersistedTransaction(transaction);

        assertThat(movement.type())
                .isEqualTo(FinanceCalendarMovementType.PERSISTED_TRANSACTION);
        assertThat(movement.transactionId()).isEqualTo(transactionId);
        assertThat(movement.recurringTransactionId()).isEqualTo(recurringTransactionId);
        assertThat(movement.logicalDate()).isEqualTo(LocalDate.of(2026, 1, 10));
        assertThat(movement.chargeDate()).isEqualTo(LocalDate.of(2026, 1, 10));
        assertThat(movement.description()).isEqualTo("Transazione persistita");
        assertThat(movement.amount()).isEqualByComparingTo(new BigDecimal("-100.00"));
        assertThat(movement.affectsAccountBalance()).isTrue();
        assertThat(movement.affectsSerenityline()).isFalse();
        assertThat(movement.categoryId()).isEqualTo(categoryId);
        assertThat(movement.financialPriorityId()).isNull();
        assertThat(movement.accountId()).isEqualTo(accountId);
        assertThat(movement.creditCardId()).isEqualTo(creditCardId);
        assertThat(movement.bucketId()).isEqualTo(bucketId);
        assertThat(movement.confirmed()).isTrue();
        assertThat(movement.simulated()).isTrue();
        assertThat(movement.simulationGroupId()).isEqualTo(simulationGroupId);
        assertThat(movement.userEntered()).isFalse();
        assertThat(movement.finalOccurrence()).isFalse();
    }

    @Test
    void shouldMapProjectedRecurringMovement() {
        UUID recurringTransactionId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID financialPriorityId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID creditCardId = UUID.randomUUID();
        UUID bucketId = UUID.randomUUID();
        UUID simulationGroupId = UUID.randomUUID();

        Category category = mock(Category.class);
        when(category.getCategoryId()).thenReturn(categoryId);

        FinancialPriority financialPriority = mock(FinancialPriority.class);
        when(financialPriority.getFinancialPriorityId())
                .thenReturn(financialPriorityId);

        Account account = mock(Account.class);
        when(account.getAccountId()).thenReturn(accountId);

        CreditCard creditCard = mock(CreditCard.class);
        when(creditCard.getCreditCardId()).thenReturn(creditCardId);

        Bucket bucket = mock(Bucket.class);
        when(bucket.getBucketId()).thenReturn(bucketId);

        RecurringTransactionProjectedMovement projected =
                new RecurringTransactionProjectedMovement(
                        recurringTransactionId,
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 1, 30),
                        new BigDecimal("-80.00"),
                        true,
                        " Movimento ricorrente previsto ",
                        category,
                        financialPriority,
                        account,
                        creditCard,
                        bucket,
                        false,
                        true
                );

        FinanceCalendarMovement movement =
                mapper.fromProjectedRecurringMovement(
                        projected,
                        true,
                        simulationGroupId
                );

        assertThat(movement.type())
                .isEqualTo(FinanceCalendarMovementType.PROJECTED_RECURRING_TRANSACTION);
        assertThat(movement.transactionId()).isNull();
        assertThat(movement.recurringTransactionId()).isEqualTo(recurringTransactionId);
        assertThat(movement.logicalDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(movement.chargeDate()).isEqualTo(LocalDate.of(2026, 1, 30));
        assertThat(movement.description()).isEqualTo("Movimento ricorrente previsto");
        assertThat(movement.amount()).isEqualByComparingTo(new BigDecimal("-80.00"));
        assertThat(movement.affectsAccountBalance()).isFalse();
        assertThat(movement.affectsSerenityline()).isTrue();
        assertThat(movement.categoryId()).isEqualTo(categoryId);
        assertThat(movement.financialPriorityId()).isEqualTo(financialPriorityId);
        assertThat(movement.accountId()).isEqualTo(accountId);
        assertThat(movement.creditCardId()).isEqualTo(creditCardId);
        assertThat(movement.bucketId()).isEqualTo(bucketId);
        assertThat(movement.confirmed()).isFalse();
        assertThat(movement.simulated()).isTrue();
        assertThat(movement.simulationGroupId()).isEqualTo(simulationGroupId);
        assertThat(movement.userEntered()).isFalse();
        assertThat(movement.finalOccurrence()).isTrue();
    }

    @Test
    void shouldMapProjectedRecurringMovementWithoutOptionalLinks() {
        UUID recurringTransactionId = UUID.randomUUID();

        Category category = mock(Category.class);
        when(category.getCategoryId()).thenReturn(UUID.randomUUID());

        FinancialPriority financialPriority = mock(FinancialPriority.class);
        when(financialPriority.getFinancialPriorityId())
                .thenReturn(UUID.randomUUID());

        Account account = mock(Account.class);
        when(account.getAccountId()).thenReturn(UUID.randomUUID());

        RecurringTransactionProjectedMovement projected =
                new RecurringTransactionProjectedMovement(
                        recurringTransactionId,
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 2, 1),
                        new BigDecimal("-80.00"),
                        false,
                        "Movimento ricorrente previsto",
                        category,
                        financialPriority,
                        account,
                        null,
                        null,
                        true,
                        true
                );

        FinanceCalendarMovement movement =
                mapper.fromProjectedRecurringMovement(
                        projected,
                        false,
                        null
                );

        assertThat(movement.creditCardId()).isNull();
        assertThat(movement.bucketId()).isNull();
        assertThat(movement.simulated()).isFalse();
        assertThat(movement.simulationGroupId()).isNull();
    }

    @Test
    void shouldMapPersistedTransactionWithoutOptionalLinks() {
        UUID transactionId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        Category category = mock(Category.class);
        when(category.getCategoryId()).thenReturn(categoryId);

        Account account = mock(Account.class);
        when(account.getAccountId()).thenReturn(accountId);

        Transaction transaction = mock(Transaction.class);
        when(transaction.getTransactionId()).thenReturn(transactionId);
        when(transaction.getRecurringTransaction()).thenReturn(null);
        when(transaction.getTransactionChargeDate()).thenReturn(LocalDate.of(2026, 1, 10));
        when(transaction.getTransactionDescription()).thenReturn("Transazione");
        when(transaction.getTransactionAmount()).thenReturn(new BigDecimal("-100.00"));
        when(transaction.isTransactionAffectsAccountBalance()).thenReturn(true);
        when(transaction.isTransactionAffectsSerenityline()).thenReturn(true);
        when(transaction.getCategory()).thenReturn(category);
        when(transaction.getAccount()).thenReturn(account);
        when(transaction.getCreditCard()).thenReturn(null);
        when(transaction.getBucket()).thenReturn(null);
        when(transaction.isTransactionIsConfirmed()).thenReturn(false);
        when(transaction.isTransactionIsSimulated()).thenReturn(false);
        when(transaction.getSimulationGroup()).thenReturn(null);
        when(transaction.isTransactionIsUserEntered()).thenReturn(true);

        FinanceCalendarMovement movement =
                mapper.fromPersistedTransaction(transaction);

        assertThat(movement.transactionId()).isEqualTo(transactionId);
        assertThat(movement.recurringTransactionId()).isNull();
        assertThat(movement.creditCardId()).isNull();
        assertThat(movement.bucketId()).isNull();
        assertThat(movement.simulated()).isFalse();
        assertThat(movement.simulationGroupId()).isNull();
        assertThat(movement.userEntered()).isTrue();
    }

    @Test
    void shouldRejectPersistedMovementWithoutTransactionId() {
        assertThatThrownBy(() -> new FinanceCalendarMovement(
                FinanceCalendarMovementType.PERSISTED_TRANSACTION,
                null,
                null,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 1),
                "Movimento",
                new BigDecimal("-10.00"),
                true,
                true,
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                null,
                null,
                false,
                false,
                null,
                true,
                false
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.calendar.transactionIdRequired");
    }

    @Test
    void shouldRejectProjectedMovementWithoutRecurringTransactionId() {
        assertThatThrownBy(() -> new FinanceCalendarMovement(
                FinanceCalendarMovementType.PROJECTED_RECURRING_TRANSACTION,
                null,
                null,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 1),
                "Movimento",
                new BigDecimal("-10.00"),
                true,
                true,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                false,
                false,
                null,
                false,
                false
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.calendar.recurringTransactionIdRequired");
    }

    @Test
    void shouldRejectFinalOccurrenceForPersistedMovement() {
        assertThatThrownBy(() -> new FinanceCalendarMovement(
                FinanceCalendarMovementType.PERSISTED_TRANSACTION,
                UUID.randomUUID(),
                null,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 1),
                "Movimento",
                new BigDecimal("-10.00"),
                true,
                true,
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                null,
                null,
                true,
                false,
                null,
                true,
                true
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.calendar.finalOccurrenceNotAllowed");
    }

    @Test
    void shouldRejectSimulationGroupWhenMovementIsNotSimulated() {
        assertThatThrownBy(() -> new FinanceCalendarMovement(
                FinanceCalendarMovementType.PERSISTED_TRANSACTION,
                UUID.randomUUID(),
                null,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 1),
                "Movimento",
                new BigDecimal("-10.00"),
                true,
                true,
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                null,
                null,
                false,
                false,
                UUID.randomUUID(),
                true,
                false
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.calendar.simulationGroupNotAllowed");
    }

    @Test
    void shouldRejectMissingSimulationGroupWhenMovementIsSimulated() {
        assertThatThrownBy(() -> new FinanceCalendarMovement(
                FinanceCalendarMovementType.PERSISTED_TRANSACTION,
                UUID.randomUUID(),
                null,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 1),
                "Movimento",
                new BigDecimal("-10.00"),
                true,
                true,
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                null,
                null,
                false,
                true,
                null,
                true,
                false
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.calendar.simulationGroupRequired");
    }

    @Test
    void shouldRejectMovementThatAffectsNothing() {
        assertThatThrownBy(() -> new FinanceCalendarMovement(
                FinanceCalendarMovementType.PERSISTED_TRANSACTION,
                UUID.randomUUID(),
                null,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 1),
                "Movimento",
                new BigDecimal("-10.00"),
                false,
                false,
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                null,
                null,
                false,
                false,
                null,
                true,
                false
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.calendar.affectsSomethingRequired");
    }
}