package me.serenityline.api.finance.transaction.service;

import me.serenityline.api.finance.account.entity.Account;
import me.serenityline.api.finance.calendar.CurrencyBusinessCalendarResolver;
import me.serenityline.api.finance.calendar.FinanceCalendarProperties;
import me.serenityline.api.finance.calendar.PaymentAccountCurrencySnapshot;
import me.serenityline.api.finance.calendar.PaymentBusinessCalendarProvider;
import me.serenityline.api.finance.category.entity.Category;
import me.serenityline.api.finance.financialpriority.entity.FinancialPriority;
import me.serenityline.api.finance.transaction.entity.*;
import me.serenityline.api.finance.transaction.repository.RecurringTransactionDetailsHistoryRepository;
import me.serenityline.api.finance.transaction.repository.RecurringTransactionHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecurringTransactionProjectedMovementBatchServiceTest {

    private static final long MAX_RANGE_DAYS = 366L * 5L;
    private static final int MAX_RECURRING_TRANSACTIONS = 500;
    @Mock
    private RecurringTransactionHistoryRepository recurringTransactionHistoryRepository;
    @Mock
    private RecurringTransactionDetailsHistoryRepository recurringTransactionDetailsHistoryRepository;
    @Mock
    private RecurringTransactionRuleSnapshotMapper recurringTransactionRuleSnapshotMapper;
    @Mock
    private RecurringTransactionAccountCurrencySnapshotMapper recurringTransactionAccountCurrencySnapshotMapper;
    @Mock
    private RecurringTransactionProjectedMovementAssembler recurringTransactionProjectedMovementAssembler;
    @Mock
    private CurrencyBusinessCalendarResolver currencyBusinessCalendarResolver;
    @Mock
    private RecurringTransactionOccurrenceGenerator recurringTransactionOccurrenceGenerator;
    @Mock
    private FinanceCalendarProperties financeCalendarProperties;
    @InjectMocks
    private RecurringTransactionProjectedMovementBatchService service;

    private static RecurringTransactionHistory ruleHistoryRow(
            UUID recurringTransactionId
    ) {
        RecurringTransaction recurringTransaction = mock(RecurringTransaction.class);
        when(recurringTransaction.getRecurringTransactionId())
                .thenReturn(recurringTransactionId);

        RecurringTransactionHistory history =
                mock(RecurringTransactionHistory.class);

        when(history.getRecurringTransaction())
                .thenReturn(recurringTransaction);

        return history;
    }

    private static RecurringTransactionDetailsHistory detailsHistoryRow(
            UUID recurringTransactionId
    ) {
        RecurringTransaction recurringTransaction = mock(RecurringTransaction.class);
        when(recurringTransaction.getRecurringTransactionId())
                .thenReturn(recurringTransactionId);

        RecurringTransactionDetailsHistory details =
                mock(RecurringTransactionDetailsHistory.class);

        when(details.getRecurringTransaction())
                .thenReturn(recurringTransaction);

        return details;
    }

    private static RecurringTransactionRuleSnapshot ruleSnapshot(
            LocalDate effectiveFrom,
            String amount,
            long precedence
    ) {
        return new RecurringTransactionRuleSnapshot(
                effectiveFrom,
                null,
                (short) effectiveFrom.getDayOfMonth(),
                (short) 1,
                RecurrenceUnit.MONTH,
                PaymentDateAdjustmentPolicy.NONE,
                new BigDecimal(amount),
                null,
                null,
                precedence
        );
    }

    private static RecurringTransactionOccurrence occurrence(
            UUID recurringTransactionId,
            LocalDate date,
            String amount
    ) {
        return new RecurringTransactionOccurrence(
                recurringTransactionId,
                date,
                date,
                new BigDecimal(amount),
                false
        );
    }

    private static RecurringTransactionProjectedMovement movement(
            UUID recurringTransactionId,
            LocalDate date,
            String amount
    ) {
        return new RecurringTransactionProjectedMovement(
                recurringTransactionId,
                date,
                date,
                new BigDecimal(amount),
                false,
                "Movimento previsto",
                mock(Category.class),
                mock(FinancialPriority.class),
                mock(Account.class),
                null,
                null,
                true,
                true
        );
    }

    private static RecurringTransactionProjectedMovementSeed seed(
            UUID recurringTransactionId,
            UUID userGroupId,
            LocalDate firstPaymentDate
    ) {
        return new RecurringTransactionProjectedMovementSeed(
                recurringTransactionId,
                userGroupId,
                firstPaymentDate
        );
    }

    private void stubMaxRangeDays() {
        when(financeCalendarProperties.getMaxRangeDays())
                .thenReturn(MAX_RANGE_DAYS);
    }

    private void stubCalendarLimits() {
        stubMaxRangeDays();

        when(financeCalendarProperties.getMaxRecurringTransactions())
                .thenReturn(MAX_RECURRING_TRANSACTIONS);
    }

    @Test
    void shouldGenerateProjectedMovementsForMultipleRecurringTransactionsUsingBulkHistoryLoading() {

        stubCalendarLimits();

        UUID userGroupId = UUID.randomUUID();

        UUID firstRecurringTransactionId = UUID.randomUUID();
        UUID secondRecurringTransactionId = UUID.randomUUID();

        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);

        LocalDate firstPaymentDate = LocalDate.of(2026, 1, 10);
        LocalDate secondPaymentDate = LocalDate.of(2026, 1, 5);

        RecurringTransactionProjectedMovementSeed firstRecurringTransaction = seed(
                firstRecurringTransactionId,
                userGroupId,
                firstPaymentDate
        );

        RecurringTransactionProjectedMovementSeed secondRecurringTransaction = seed(
                secondRecurringTransactionId,
                userGroupId,
                secondPaymentDate
        );

        RecurringTransactionHistory firstRuleRow =
                ruleHistoryRow(firstRecurringTransactionId);

        RecurringTransactionHistory secondRuleRow =
                ruleHistoryRow(secondRecurringTransactionId);

        RecurringTransactionDetailsHistory firstDetailsRow =
                detailsHistoryRow(firstRecurringTransactionId);

        RecurringTransactionDetailsHistory secondDetailsRow =
                detailsHistoryRow(secondRecurringTransactionId);

        List<RecurringTransactionHistory> firstRuleRows = List.of(firstRuleRow);
        List<RecurringTransactionHistory> secondRuleRows = List.of(secondRuleRow);

        List<RecurringTransactionDetailsHistory> firstDetailsRows = List.of(firstDetailsRow);
        List<RecurringTransactionDetailsHistory> secondDetailsRows = List.of(secondDetailsRow);

        List<RecurringTransactionRuleSnapshot> firstRuleSnapshots = List.of(
                ruleSnapshot(firstPaymentDate, "-100.00", 1L)
        );

        List<RecurringTransactionRuleSnapshot> secondRuleSnapshots = List.of(
                ruleSnapshot(secondPaymentDate, "-50.00", 1L)
        );

        List<PaymentAccountCurrencySnapshot> firstCurrencySnapshots = List.of(
                new PaymentAccountCurrencySnapshot(
                        firstPaymentDate,
                        UUID.randomUUID(),
                        "EUR",
                        1L
                )
        );

        List<PaymentAccountCurrencySnapshot> secondCurrencySnapshots = List.of(
                new PaymentAccountCurrencySnapshot(
                        secondPaymentDate,
                        UUID.randomUUID(),
                        "EUR",
                        1L
                )
        );

        List<RecurringTransactionOccurrence> firstOccurrences = List.of(
                occurrence(
                        firstRecurringTransactionId,
                        LocalDate.of(2026, 1, 10),
                        "-100.00"
                )
        );

        List<RecurringTransactionOccurrence> secondOccurrences = List.of(
                occurrence(
                        secondRecurringTransactionId,
                        LocalDate.of(2026, 1, 5),
                        "-50.00"
                )
        );

        RecurringTransactionProjectedMovement firstMovement = movement(
                firstRecurringTransactionId,
                LocalDate.of(2026, 1, 10),
                "-100.00"
        );

        RecurringTransactionProjectedMovement secondMovement = movement(
                secondRecurringTransactionId,
                LocalDate.of(2026, 1, 5),
                "-50.00"
        );

        when(recurringTransactionHistoryRepository.findAllHistoryByRecurringTransactionIdsAndUserGroupId(
                anyCollection(), eq(userGroupId)
        )).thenReturn(List.of(firstRuleRow, secondRuleRow));

        when(recurringTransactionDetailsHistoryRepository
                .findAllHistoryWithDetailsByRecurringTransactionIdsAndUserGroupId(
                        anyCollection(),
                        eq(userGroupId)
                ))
                .thenReturn(List.of(firstDetailsRow, secondDetailsRow));

        when(recurringTransactionRuleSnapshotMapper.toSnapshots(firstRuleRows))
                .thenReturn(firstRuleSnapshots);

        when(recurringTransactionRuleSnapshotMapper.toSnapshots(secondRuleRows))
                .thenReturn(secondRuleSnapshots);

        when(recurringTransactionAccountCurrencySnapshotMapper.toSnapshots(firstDetailsRows))
                .thenReturn(firstCurrencySnapshots);

        when(recurringTransactionAccountCurrencySnapshotMapper.toSnapshots(secondDetailsRows))
                .thenReturn(secondCurrencySnapshots);

        when(recurringTransactionOccurrenceGenerator.generateOccurrences(
                eq(firstRecurringTransactionId),
                eq(firstPaymentDate),
                same(firstRuleSnapshots),
                eq(from),
                eq(to),
                any(PaymentBusinessCalendarProvider.class)
        )).thenReturn(firstOccurrences);

        when(recurringTransactionOccurrenceGenerator.generateOccurrences(
                eq(secondRecurringTransactionId),
                eq(secondPaymentDate),
                same(secondRuleSnapshots),
                eq(from),
                eq(to),
                any(PaymentBusinessCalendarProvider.class)
        )).thenReturn(secondOccurrences);

        when(recurringTransactionProjectedMovementAssembler.assemble(
                firstOccurrences,
                firstDetailsRows
        )).thenReturn(List.of(firstMovement));

        when(recurringTransactionProjectedMovementAssembler.assemble(
                secondOccurrences,
                secondDetailsRows
        )).thenReturn(List.of(secondMovement));

        List<RecurringTransactionProjectedMovement> movements =
                service.generateProjectedMovements(
                        List.of(firstRecurringTransaction, secondRecurringTransaction),
                        from,
                        to
                );

        assertThat(movements)
                .containsExactly(secondMovement, firstMovement);

        ArgumentCaptor<Collection<UUID>> idsCaptor =
                ArgumentCaptor.forClass(Collection.class);

        verify(recurringTransactionHistoryRepository)
                .findAllHistoryByRecurringTransactionIdsAndUserGroupId(idsCaptor.capture(), eq(userGroupId));

        assertThat(idsCaptor.getValue())
                .containsExactly(
                        firstRecurringTransactionId,
                        secondRecurringTransactionId
                );

        verify(recurringTransactionDetailsHistoryRepository)
                .findAllHistoryWithDetailsByRecurringTransactionIdsAndUserGroupId(
                        idsCaptor.capture(),
                        eq(userGroupId)
                );

        assertThat(idsCaptor.getValue())
                .containsExactly(
                        firstRecurringTransactionId,
                        secondRecurringTransactionId
                );
    }

    @Test
    void shouldReturnEmptyListWithoutLoadingDataWhenRecurringTransactionsAreEmpty() {

        stubMaxRangeDays();

        List<RecurringTransactionProjectedMovement> movements =
                service.generateProjectedMovements(
                        List.of(),
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 31)
                );

        assertThat(movements).isEmpty();

        verifyNoInteractions(
                recurringTransactionHistoryRepository,
                recurringTransactionDetailsHistoryRepository,
                recurringTransactionRuleSnapshotMapper,
                recurringTransactionAccountCurrencySnapshotMapper,
                recurringTransactionProjectedMovementAssembler,
                currencyBusinessCalendarResolver,
                recurringTransactionOccurrenceGenerator
        );
    }

    @Test
    void shouldRejectInvalidRangeBeforeLoadingData() {
        RecurringTransactionProjectedMovementSeed seed = seed(
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.of(2026, 1, 1)
        );

        assertThatThrownBy(() -> service.generateProjectedMovements(
                List.of(seed),
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 1, 1)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.calendar.dateRangeInvalid");

        verifyNoInteractions(
                recurringTransactionHistoryRepository,
                recurringTransactionDetailsHistoryRepository,
                recurringTransactionRuleSnapshotMapper,
                recurringTransactionAccountCurrencySnapshotMapper,
                recurringTransactionProjectedMovementAssembler,
                currencyBusinessCalendarResolver,
                recurringTransactionOccurrenceGenerator
        );
    }

    @Test
    void shouldRejectRecurringTransactionsFromDifferentUserGroups() {

        stubCalendarLimits();

        RecurringTransactionProjectedMovementSeed first = seed(
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.of(2026, 1, 1)
        );

        RecurringTransactionProjectedMovementSeed second = seed(
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.of(2026, 1, 1)
        );

        assertThatThrownBy(() -> service.generateProjectedMovements(
                List.of(first, second),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.recurringTransaction.userGroupMismatch");

        verifyNoInteractions(
                recurringTransactionHistoryRepository,
                recurringTransactionDetailsHistoryRepository,
                recurringTransactionRuleSnapshotMapper,
                recurringTransactionAccountCurrencySnapshotMapper,
                recurringTransactionProjectedMovementAssembler,
                currencyBusinessCalendarResolver,
                recurringTransactionOccurrenceGenerator
        );
    }

    @Test
    void shouldRejectDuplicatedRecurringTransactions() {

        stubCalendarLimits();

        UUID recurringTransactionId = UUID.randomUUID();
        UUID userGroupId = UUID.randomUUID();

        RecurringTransactionProjectedMovementSeed first = seed(
                recurringTransactionId,
                userGroupId,
                LocalDate.of(2026, 1, 1)
        );

        RecurringTransactionProjectedMovementSeed second = seed(
                recurringTransactionId,
                userGroupId,
                LocalDate.of(2026, 1, 1)
        );

        assertThatThrownBy(() -> service.generateProjectedMovements(
                List.of(first, second),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.recurringTransaction.duplicated");

        verifyNoInteractions(
                recurringTransactionHistoryRepository,
                recurringTransactionDetailsHistoryRepository,
                recurringTransactionRuleSnapshotMapper,
                recurringTransactionAccountCurrencySnapshotMapper,
                recurringTransactionProjectedMovementAssembler,
                currencyBusinessCalendarResolver,
                recurringTransactionOccurrenceGenerator
        );
    }

    @Test
    void shouldRejectMissingRuleHistoryForAnyRecurringTransaction() {

        stubCalendarLimits();

        UUID userGroupId = UUID.randomUUID();
        UUID recurringTransactionId = UUID.randomUUID();

        RecurringTransactionProjectedMovementSeed recurringTransaction = seed(
                recurringTransactionId,
                userGroupId,
                LocalDate.of(2026, 1, 10)
        );

        RecurringTransactionDetailsHistory detailsHistoryRow =
                detailsHistoryRow(recurringTransactionId);

        when(recurringTransactionHistoryRepository.findAllHistoryByRecurringTransactionIdsAndUserGroupId(
                anyCollection(), eq(userGroupId)
        )).thenReturn(List.of());

        when(recurringTransactionDetailsHistoryRepository
                .findAllHistoryWithDetailsByRecurringTransactionIdsAndUserGroupId(
                        anyCollection(),
                        eq(userGroupId)
                ))
                .thenReturn(List.of(detailsHistoryRow));

        assertThatThrownBy(() -> service.generateProjectedMovements(
                List.of(recurringTransaction),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("finance.recurringTransaction.ruleHistoryRequired");
    }

    @Test
    void shouldRejectMissingDetailsHistoryForAnyRecurringTransaction() {

        stubCalendarLimits();

        UUID userGroupId = UUID.randomUUID();
        UUID recurringTransactionId = UUID.randomUUID();

        RecurringTransactionProjectedMovementSeed recurringTransaction = seed(
                recurringTransactionId,
                userGroupId,
                LocalDate.of(2026, 1, 10)
        );

        RecurringTransactionHistory ruleHistoryRow =
                ruleHistoryRow(recurringTransactionId);

        when(recurringTransactionHistoryRepository.findAllHistoryByRecurringTransactionIdsAndUserGroupId(
                anyCollection(), eq(userGroupId)
        )).thenReturn(List.of(ruleHistoryRow));

        when(recurringTransactionDetailsHistoryRepository
                .findAllHistoryWithDetailsByRecurringTransactionIdsAndUserGroupId(
                        anyCollection(),
                        eq(userGroupId)
                ))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.generateProjectedMovements(
                List.of(recurringTransaction),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("finance.recurringTransaction.detailsHistoryRequired");
    }

    @Test
    void shouldRejectRangeGreaterThanMaxRangeDays() {

        stubMaxRangeDays();

        assertThatThrownBy(() -> service.generateProjectedMovements(
                List.of(),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2031, 1, 6)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.calendar.dateRangeTooLarge");

        verifyNoInteractions(
                recurringTransactionHistoryRepository,
                recurringTransactionDetailsHistoryRepository,
                recurringTransactionRuleSnapshotMapper,
                recurringTransactionAccountCurrencySnapshotMapper,
                recurringTransactionProjectedMovementAssembler,
                currencyBusinessCalendarResolver,
                recurringTransactionOccurrenceGenerator
        );
    }

    @Test
    void shouldAcceptRangeEqualToMaxRangeDaysWhenListIsEmpty() {

        stubMaxRangeDays();

        List<RecurringTransactionProjectedMovement> movements =
                service.generateProjectedMovements(
                        List.of(),
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2031, 1, 5)
                );

        assertThat(movements).isEmpty();

        verifyNoInteractions(
                recurringTransactionHistoryRepository,
                recurringTransactionDetailsHistoryRepository,
                recurringTransactionRuleSnapshotMapper,
                recurringTransactionAccountCurrencySnapshotMapper,
                recurringTransactionProjectedMovementAssembler,
                currencyBusinessCalendarResolver,
                recurringTransactionOccurrenceGenerator
        );
    }

    @Test
    void shouldRejectTooManyRecurringTransactions() {

        stubCalendarLimits();

        UUID userGroupId = UUID.randomUUID();

        List<RecurringTransactionProjectedMovementSeed> seeds =
                java.util.stream.IntStream.rangeClosed(1, 501)
                        .mapToObj(index -> seed(
                                UUID.randomUUID(),
                                userGroupId,
                                LocalDate.of(2026, 1, 1)
                        ))
                        .toList();

        assertThatThrownBy(() -> service.generateProjectedMovements(
                seeds,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.recurringTransaction.batchTooLarge");

        verifyNoInteractions(
                recurringTransactionHistoryRepository,
                recurringTransactionDetailsHistoryRepository,
                recurringTransactionRuleSnapshotMapper,
                recurringTransactionAccountCurrencySnapshotMapper,
                recurringTransactionProjectedMovementAssembler,
                currencyBusinessCalendarResolver,
                recurringTransactionOccurrenceGenerator
        );
    }
}