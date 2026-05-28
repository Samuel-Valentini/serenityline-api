package me.serenityline.api.finance.transaction.service;

import me.serenityline.api.finance.account.entity.Account;
import me.serenityline.api.finance.bucket.entity.Bucket;
import me.serenityline.api.finance.category.entity.Category;
import me.serenityline.api.finance.creditcard.entity.CreditCard;
import me.serenityline.api.finance.financialpriority.entity.FinancialPriority;
import me.serenityline.api.finance.transaction.entity.RecurringTransactionDetailsHistory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecurringTransactionProjectedMovementAssemblerTest {

    private final RecurringTransactionProjectedMovementAssembler assembler =
            new RecurringTransactionProjectedMovementAssembler();

    private static RecurringTransactionDetailsHistory details(
            LocalDate effectiveFrom,
            String description,
            Category category,
            FinancialPriority financialPriority,
            Account linkedAccount,
            CreditCard linkedCreditCard,
            Bucket linkedBucket,
            boolean affectsAccountBalance,
            boolean affectsLiquidity
    ) {
        RecurringTransactionDetailsHistory details =
                mock(RecurringTransactionDetailsHistory.class);

        when(details.getRecurringTransactionDetailsEffectiveFrom())
                .thenReturn(effectiveFrom);

        when(details.getRecurringTransactionDescription())
                .thenReturn(description);

        when(details.getCategory())
                .thenReturn(category);

        when(details.getFinancialPriority())
                .thenReturn(financialPriority);

        when(details.getLinkedAccount())
                .thenReturn(linkedAccount);

        when(details.getLinkedCreditCard())
                .thenReturn(linkedCreditCard);

        when(details.getLinkedBucket())
                .thenReturn(linkedBucket);

        when(details.isRecurringTransactionAffectsAccountBalance())
                .thenReturn(affectsAccountBalance);

        when(details.isRecurringTransactionAffectsLiquidity())
                .thenReturn(affectsLiquidity);

        return details;
    }

    @Test
    void shouldUseDetailsActiveAtLogicalDateNotChargeDate() {
        UUID recurringTransactionId = UUID.randomUUID();

        Account oldAccount = mock(Account.class);
        Account newAccount = mock(Account.class);

        Category oldCategory = mock(Category.class);
        Category newCategory = mock(Category.class);

        FinancialPriority oldPriority = mock(FinancialPriority.class);
        FinancialPriority newPriority = mock(FinancialPriority.class);

        RecurringTransactionDetailsHistory oldDetails = details(
                LocalDate.of(2026, 1, 1),
                "Vecchia ricorrente",
                oldCategory,
                oldPriority,
                oldAccount,
                null,
                null,
                true,
                true
        );

        RecurringTransactionDetailsHistory newDetails = details(
                LocalDate.of(2026, 7, 1),
                "Nuova ricorrente",
                newCategory,
                newPriority,
                newAccount,
                null,
                null,
                false,
                true
        );

        RecurringTransactionOccurrence occurrence = new RecurringTransactionOccurrence(
                recurringTransactionId,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 6, 30),
                new BigDecimal("-100.00"),
                false
        );

        List<RecurringTransactionProjectedMovement> movements =
                assembler.assemble(
                        List.of(occurrence),
                        List.of(oldDetails, newDetails)
                );

        assertThat(movements)
                .extracting(
                        RecurringTransactionProjectedMovement::recurringTransactionId,
                        RecurringTransactionProjectedMovement::logicalDate,
                        RecurringTransactionProjectedMovement::chargeDate,
                        RecurringTransactionProjectedMovement::amount,
                        RecurringTransactionProjectedMovement::description,
                        RecurringTransactionProjectedMovement::category,
                        RecurringTransactionProjectedMovement::financialPriority,
                        RecurringTransactionProjectedMovement::linkedAccount,
                        RecurringTransactionProjectedMovement::affectsAccountBalance,
                        RecurringTransactionProjectedMovement::affectsLiquidity
                )
                .containsExactly(
                        tuple(
                                recurringTransactionId,
                                LocalDate.of(2026, 7, 1),
                                LocalDate.of(2026, 6, 30),
                                new BigDecimal("-100.00"),
                                "Nuova ricorrente",
                                newCategory,
                                newPriority,
                                newAccount,
                                false,
                                true
                        )
                );
    }

    @Test
    void shouldUseLatestInputPrecedenceWhenDetailsHaveSameEffectiveFrom() {
        UUID recurringTransactionId = UUID.randomUUID();

        Account firstAccount = mock(Account.class);
        Account secondAccount = mock(Account.class);

        Category category = mock(Category.class);
        FinancialPriority priority = mock(FinancialPriority.class);

        RecurringTransactionDetailsHistory firstDetails = details(
                LocalDate.of(2026, 1, 1),
                "Prima versione",
                category,
                priority,
                firstAccount,
                null,
                null,
                true,
                true
        );

        RecurringTransactionDetailsHistory secondDetails = details(
                LocalDate.of(2026, 1, 1),
                "Seconda versione",
                category,
                priority,
                secondAccount,
                null,
                null,
                true,
                false
        );

        RecurringTransactionOccurrence occurrence = new RecurringTransactionOccurrence(
                recurringTransactionId,
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 1, 15),
                new BigDecimal("-50.00"),
                false
        );

        List<RecurringTransactionProjectedMovement> movements =
                assembler.assemble(
                        List.of(occurrence),
                        List.of(firstDetails, secondDetails)
                );

        assertThat(movements).hasSize(1);

        RecurringTransactionProjectedMovement movement = movements.getFirst();

        assertThat(movement.description()).isEqualTo("Seconda versione");
        assertThat(movement.linkedAccount()).isSameAs(secondAccount);
        assertThat(movement.affectsAccountBalance()).isTrue();
        assertThat(movement.affectsLiquidity()).isFalse();
    }

    @Test
    void shouldPreserveOptionalCreditCardAndBucket() {
        UUID recurringTransactionId = UUID.randomUUID();

        Account account = mock(Account.class);
        Category category = mock(Category.class);
        FinancialPriority priority = mock(FinancialPriority.class);
        CreditCard creditCard = mock(CreditCard.class);
        Bucket bucket = mock(Bucket.class);

        RecurringTransactionDetailsHistory details = details(
                LocalDate.of(2026, 1, 1),
                "Ricorrente con carta e bucket",
                category,
                priority,
                account,
                creditCard,
                bucket,
                false,
                true
        );

        RecurringTransactionOccurrence occurrence = new RecurringTransactionOccurrence(
                recurringTransactionId,
                LocalDate.of(2026, 1, 10),
                LocalDate.of(2026, 1, 10),
                new BigDecimal("-25.00"),
                false
        );

        List<RecurringTransactionProjectedMovement> movements =
                assembler.assemble(
                        List.of(occurrence),
                        List.of(details)
                );

        RecurringTransactionProjectedMovement movement = movements.getFirst();

        assertThat(movement.linkedCreditCard()).isSameAs(creditCard);
        assertThat(movement.linkedBucket()).isSameAs(bucket);
    }

    @Test
    void shouldReturnEmptyListWhenOccurrencesAreEmpty() {
        assertThat(assembler.assemble(List.of(), List.of())).isEmpty();
    }

    @Test
    void shouldRejectMissingDetailsHistoryWhenOccurrencesArePresent() {
        RecurringTransactionOccurrence occurrence = new RecurringTransactionOccurrence(
                UUID.randomUUID(),
                LocalDate.of(2026, 1, 10),
                LocalDate.of(2026, 1, 10),
                new BigDecimal("-100.00"),
                false
        );

        assertThatThrownBy(() -> assembler.assemble(
                List.of(occurrence),
                List.of()
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("finance.recurringTransaction.detailsHistoryRequired");
    }

    @Test
    void shouldRejectWhenNoDetailsAreActiveAtLogicalDate() {
        RecurringTransactionDetailsHistory details = details(
                LocalDate.of(2026, 2, 1),
                "Ricorrente futura",
                mock(Category.class),
                mock(FinancialPriority.class),
                mock(Account.class),
                null,
                null,
                true,
                true
        );

        RecurringTransactionOccurrence occurrence = new RecurringTransactionOccurrence(
                UUID.randomUUID(),
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 1, 31),
                new BigDecimal("-100.00"),
                false
        );

        assertThatThrownBy(() -> assembler.assemble(
                List.of(occurrence),
                List.of(details)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("finance.recurringTransaction.detailsNotFound");
    }
}