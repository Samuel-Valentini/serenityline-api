package me.serenityline.api.finance.transaction.service;

import me.serenityline.api.finance.calendar.CurrencyBusinessCalendarResolver;
import me.serenityline.api.finance.calendar.FinanceCalendarProperties;
import me.serenityline.api.finance.calendar.PaymentAccountCurrencySnapshot;
import me.serenityline.api.finance.transaction.entity.RecurringTransaction;
import me.serenityline.api.finance.transaction.entity.RecurringTransactionDetailsHistory;
import me.serenityline.api.finance.transaction.entity.RecurringTransactionHistory;
import me.serenityline.api.finance.transaction.repository.RecurringTransactionDetailsHistoryRepository;
import me.serenityline.api.finance.transaction.repository.RecurringTransactionHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecurringTransactionProjectedMovementNewBatchServiceTest {

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

    private RecurringTransactionProjectedMovementBatchService service;

    @BeforeEach
    void setUp() {
        service = new RecurringTransactionProjectedMovementBatchService(
                recurringTransactionHistoryRepository,
                recurringTransactionDetailsHistoryRepository,
                recurringTransactionRuleSnapshotMapper,
                recurringTransactionAccountCurrencySnapshotMapper,
                recurringTransactionProjectedMovementAssembler,
                currencyBusinessCalendarResolver,
                recurringTransactionOccurrenceGenerator,
                financeCalendarProperties
        );
    }

    @Test
    void generateProjectedMovementsAcrossRangeShouldLoadHistoryOnlyOnceForLongRange() {
        UUID userGroupId = UUID.randomUUID();
        UUID recurringTransactionId = UUID.randomUUID();

        LocalDate firstPaymentDate = LocalDate.of(2026, 1, 1);
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);

        givenCalendarProjectionLimits(10);

        BatchFixture fixture = givenPreparedBatch(
                recurringTransactionId,
                userGroupId,
                firstPaymentDate
        );

        RecurringTransactionProjectedMovement projectedMovement =
                projectedMovement();

        when(recurringTransactionOccurrenceGenerator.generateOccurrences(
                eq(recurringTransactionId),
                eq(firstPaymentDate),
                eq(fixture.ruleSnapshots()),
                any(LocalDate.class),
                any(LocalDate.class),
                any()
        )).thenReturn(List.of());

        when(recurringTransactionProjectedMovementAssembler.assemble(
                anyList(),
                eq(fixture.detailsHistoryRows())
        )).thenReturn(List.of());


        List<RecurringTransactionProjectedMovement> result =
                service.generateProjectedMovementsAcrossRange(
                        List.of(seed(recurringTransactionId, userGroupId, firstPaymentDate)),
                        from,
                        to
                );

        assertThat(result).isEmpty();

        verify(recurringTransactionHistoryRepository, times(1))
                .findAllHistoryByRecurringTransactionIdsAndUserGroupId(
                        List.of(recurringTransactionId),
                        userGroupId
                );

        verify(recurringTransactionDetailsHistoryRepository, times(1))
                .findAllHistoryWithDetailsByRecurringTransactionIdsAndUserGroupId(
                        List.of(recurringTransactionId),
                        userGroupId
                );

        verify(recurringTransactionOccurrenceGenerator, atLeast(2))
                .generateOccurrences(
                        eq(recurringTransactionId),
                        eq(firstPaymentDate),
                        eq(fixture.ruleSnapshots()),
                        any(LocalDate.class),
                        any(LocalDate.class),
                        any()
                );

        verify(recurringTransactionProjectedMovementAssembler, times(1))
                .assemble(anyList(), eq(fixture.detailsHistoryRows()));
    }

    @Test
    void generateProjectedMovementsAcrossRangeShouldIncludePreviousBusinessDayOccurrenceWhenLogicalDateIsAfterRequestedRange() {
        UUID userGroupId = UUID.randomUUID();
        UUID recurringTransactionId = UUID.randomUUID();

        LocalDate logicalDate = LocalDate.of(2026, 2, 1); // domenica
        LocalDate chargeDate = LocalDate.of(2026, 1, 30); // venerdì precedente

        LocalDate firstPaymentDate = logicalDate;
        LocalDate requestedFrom = chargeDate;
        LocalDate requestedTo = chargeDate;

        givenCalendarProjectionLimits(10);

        BatchFixture fixture = givenPreparedBatch(
                recurringTransactionId,
                userGroupId,
                firstPaymentDate
        );

        RecurringTransactionOccurrence occurrence =
                occurrence(recurringTransactionId, logicalDate, chargeDate);

        RecurringTransactionProjectedMovement projectedMovement =
                projectedMovement();

        when(recurringTransactionOccurrenceGenerator.generateOccurrences(
                eq(recurringTransactionId),
                eq(firstPaymentDate),
                eq(fixture.ruleSnapshots()),
                any(LocalDate.class),
                any(LocalDate.class),
                any()
        )).thenAnswer(invocation -> {
            LocalDate generationFrom = invocation.getArgument(3);
            LocalDate generationTo = invocation.getArgument(4);

            if (!generationFrom.isAfter(logicalDate) && !generationTo.isBefore(logicalDate)) {
                return List.of(occurrence);
            }

            return List.of();
        });

        when(recurringTransactionProjectedMovementAssembler.assemble(
                anyList(),
                eq(fixture.detailsHistoryRows())
        )).thenReturn(List.of(projectedMovement));

        List<RecurringTransactionProjectedMovement> result =
                service.generateProjectedMovementsAcrossRange(
                        List.of(seed(recurringTransactionId, userGroupId, firstPaymentDate)),
                        requestedFrom,
                        requestedTo
                );

        assertThat(result).containsExactly(projectedMovement);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RecurringTransactionOccurrence>> occurrencesCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(recurringTransactionProjectedMovementAssembler)
                .assemble(occurrencesCaptor.capture(), eq(fixture.detailsHistoryRows()));

        assertThat(occurrencesCaptor.getValue())
                .containsExactly(occurrence);
    }

    @Test
    void generateProjectedMovementsAcrossRangeShouldIncludeNextBusinessDayOccurrenceWhenLogicalDateIsBeforeRequestedRange() {
        UUID userGroupId = UUID.randomUUID();
        UUID recurringTransactionId = UUID.randomUUID();

        LocalDate logicalDate = LocalDate.of(2026, 1, 31); // sabato
        LocalDate chargeDate = LocalDate.of(2026, 2, 2); // lunedì successivo

        LocalDate firstPaymentDate = logicalDate;
        LocalDate requestedFrom = chargeDate;
        LocalDate requestedTo = chargeDate;

        givenCalendarProjectionLimits(10);

        BatchFixture fixture = givenPreparedBatch(
                recurringTransactionId,
                userGroupId,
                firstPaymentDate
        );

        RecurringTransactionOccurrence occurrence =
                occurrence(recurringTransactionId, logicalDate, chargeDate);

        RecurringTransactionProjectedMovement projectedMovement =
                projectedMovement();

        when(recurringTransactionOccurrenceGenerator.generateOccurrences(
                eq(recurringTransactionId),
                eq(firstPaymentDate),
                eq(fixture.ruleSnapshots()),
                any(LocalDate.class),
                any(LocalDate.class),
                any()
        )).thenAnswer(invocation -> {
            LocalDate generationFrom = invocation.getArgument(3);
            LocalDate generationTo = invocation.getArgument(4);

            if (!generationFrom.isAfter(logicalDate) && !generationTo.isBefore(logicalDate)) {
                return List.of(occurrence);
            }

            return List.of();
        });

        when(recurringTransactionProjectedMovementAssembler.assemble(
                anyList(),
                eq(fixture.detailsHistoryRows())
        )).thenReturn(List.of(projectedMovement));

        List<RecurringTransactionProjectedMovement> result =
                service.generateProjectedMovementsAcrossRange(
                        List.of(seed(recurringTransactionId, userGroupId, firstPaymentDate)),
                        requestedFrom,
                        requestedTo
                );

        assertThat(result).containsExactly(projectedMovement);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RecurringTransactionOccurrence>> occurrencesCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(recurringTransactionProjectedMovementAssembler)
                .assemble(occurrencesCaptor.capture(), eq(fixture.detailsHistoryRows()));

        assertThat(occurrencesCaptor.getValue())
                .containsExactly(occurrence);
    }

    @Test
    void generateProjectedMovementsAcrossRangeShouldDeduplicateOccurrencesGeneratedByOverlappingChunks() {
        UUID userGroupId = UUID.randomUUID();
        UUID recurringTransactionId = UUID.randomUUID();

        LocalDate firstPaymentDate = LocalDate.of(2026, 1, 1);
        LocalDate requestedFrom = LocalDate.of(2026, 1, 1);
        LocalDate requestedTo = LocalDate.of(2026, 1, 31);

        LocalDate logicalDate = LocalDate.of(2026, 1, 15);
        LocalDate chargeDate = LocalDate.of(2026, 1, 15);

        givenCalendarProjectionLimits(10);

        BatchFixture fixture = givenPreparedBatch(
                recurringTransactionId,
                userGroupId,
                firstPaymentDate
        );

        RecurringTransactionOccurrence occurrence =
                occurrence(recurringTransactionId, logicalDate, chargeDate);

        RecurringTransactionProjectedMovement projectedMovement =
                projectedMovement();

        when(recurringTransactionOccurrenceGenerator.generateOccurrences(
                eq(recurringTransactionId),
                eq(firstPaymentDate),
                eq(fixture.ruleSnapshots()),
                any(LocalDate.class),
                any(LocalDate.class),
                any()
        )).thenReturn(List.of(occurrence));

        when(recurringTransactionProjectedMovementAssembler.assemble(
                anyList(),
                eq(fixture.detailsHistoryRows())
        )).thenReturn(List.of(projectedMovement));

        List<RecurringTransactionProjectedMovement> result =
                service.generateProjectedMovementsAcrossRange(
                        List.of(seed(recurringTransactionId, userGroupId, firstPaymentDate)),
                        requestedFrom,
                        requestedTo
                );

        assertThat(result).containsExactly(projectedMovement);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RecurringTransactionOccurrence>> occurrencesCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(recurringTransactionProjectedMovementAssembler)
                .assemble(occurrencesCaptor.capture(), eq(fixture.detailsHistoryRows()));

        assertThat(occurrencesCaptor.getValue())
                .containsExactly(occurrence);
    }

    @Test
    void generateProjectedMovementsAcrossRangeShouldFilterOccurrencesOutsideRequestedChargeDateRange() {
        UUID userGroupId = UUID.randomUUID();
        UUID recurringTransactionId = UUID.randomUUID();

        LocalDate firstPaymentDate = LocalDate.of(2026, 1, 1);
        LocalDate requestedFrom = LocalDate.of(2026, 1, 10);
        LocalDate requestedTo = LocalDate.of(2026, 1, 20);

        RecurringTransactionOccurrence beforeRequestedRange =
                occurrence(
                        recurringTransactionId,
                        LocalDate.of(2026, 1, 9),
                        LocalDate.of(2026, 1, 9)
                );

        RecurringTransactionOccurrence insideRequestedRange =
                occurrence(
                        recurringTransactionId,
                        LocalDate.of(2026, 1, 15),
                        LocalDate.of(2026, 1, 15)
                );

        RecurringTransactionOccurrence afterRequestedRange =
                occurrence(
                        recurringTransactionId,
                        LocalDate.of(2026, 1, 21),
                        LocalDate.of(2026, 1, 21)
                );

        givenCalendarProjectionLimits(10);

        BatchFixture fixture = givenPreparedBatch(
                recurringTransactionId,
                userGroupId,
                firstPaymentDate
        );

        RecurringTransactionProjectedMovement projectedMovement =
                projectedMovement();

        when(recurringTransactionOccurrenceGenerator.generateOccurrences(
                eq(recurringTransactionId),
                eq(firstPaymentDate),
                eq(fixture.ruleSnapshots()),
                any(LocalDate.class),
                any(LocalDate.class),
                any()
        )).thenReturn(List.of(
                beforeRequestedRange,
                insideRequestedRange,
                afterRequestedRange
        ));

        when(recurringTransactionProjectedMovementAssembler.assemble(
                anyList(),
                eq(fixture.detailsHistoryRows())
        )).thenReturn(List.of(projectedMovement));

        List<RecurringTransactionProjectedMovement> result =
                service.generateProjectedMovementsAcrossRange(
                        List.of(seed(recurringTransactionId, userGroupId, firstPaymentDate)),
                        requestedFrom,
                        requestedTo
                );

        assertThat(result).containsExactly(projectedMovement);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RecurringTransactionOccurrence>> occurrencesCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(recurringTransactionProjectedMovementAssembler)
                .assemble(occurrencesCaptor.capture(), eq(fixture.detailsHistoryRows()));

        assertThat(occurrencesCaptor.getValue())
                .containsExactly(insideRequestedRange);
    }

    private void givenMaxRangeDays(long maxRangeDays) {
        when(financeCalendarProperties.getMaxRangeDays())
                .thenReturn(maxRangeDays);
    }

    private void givenMaxRecurringTransactions(int maxRecurringTransactions) {
        when(financeCalendarProperties.getMaxRecurringTransactions())
                .thenReturn(maxRecurringTransactions);
    }

    private void givenCalendarProjectionLimits(long maxRangeDays) {
        givenMaxRangeDays(maxRangeDays);
        givenMaxRecurringTransactions(100);
    }

    private BatchFixture givenPreparedBatch(
            UUID recurringTransactionId,
            UUID userGroupId,
            LocalDate firstPaymentDate
    ) {
        RecurringTransaction recurringTransaction = mock(RecurringTransaction.class);
        when(recurringTransaction.getRecurringTransactionId())
                .thenReturn(recurringTransactionId);

        RecurringTransactionHistory ruleHistory = mock(RecurringTransactionHistory.class);
        when(ruleHistory.getRecurringTransaction())
                .thenReturn(recurringTransaction);

        RecurringTransactionDetailsHistory detailsHistory =
                mock(RecurringTransactionDetailsHistory.class);
        when(detailsHistory.getRecurringTransaction())
                .thenReturn(recurringTransaction);

        List<RecurringTransactionHistory> ruleHistoryRows = List.of(ruleHistory);
        List<RecurringTransactionDetailsHistory> detailsHistoryRows = List.of(detailsHistory);

        when(recurringTransactionHistoryRepository.findAllHistoryByRecurringTransactionIdsAndUserGroupId(
                List.of(recurringTransactionId),
                userGroupId
        )).thenReturn(ruleHistoryRows);

        when(recurringTransactionDetailsHistoryRepository.findAllHistoryWithDetailsByRecurringTransactionIdsAndUserGroupId(
                List.of(recurringTransactionId),
                userGroupId
        )).thenReturn(detailsHistoryRows);

        RecurringTransactionRuleSnapshot ruleSnapshot =
                mock(RecurringTransactionRuleSnapshot.class);

        PaymentAccountCurrencySnapshot accountCurrencySnapshot =
                mock(PaymentAccountCurrencySnapshot.class);

        List<RecurringTransactionRuleSnapshot> ruleSnapshots =
                List.of(ruleSnapshot);

        List<PaymentAccountCurrencySnapshot> accountCurrencySnapshots =
                List.of(accountCurrencySnapshot);

        when(recurringTransactionRuleSnapshotMapper.toSnapshots(ruleHistoryRows))
                .thenReturn(ruleSnapshots);

        when(recurringTransactionAccountCurrencySnapshotMapper.toSnapshots(detailsHistoryRows))
                .thenReturn(accountCurrencySnapshots);

        return new BatchFixture(
                ruleSnapshots,
                detailsHistoryRows
        );
    }

    @Test
    void generateProjectedMovementsShouldRejectRangeOverMaxRangeDays() {
        UUID userGroupId = UUID.randomUUID();
        UUID recurringTransactionId = UUID.randomUUID();

        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 20);

        when(financeCalendarProperties.getMaxRangeDays())
                .thenReturn(10L);

        assertThatThrownBy(() -> service.generateProjectedMovements(
                List.of(seed(recurringTransactionId, userGroupId, from)),
                from,
                to
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.calendar.dateRangeTooLarge");

        verifyNoInteractions(
                recurringTransactionHistoryRepository,
                recurringTransactionDetailsHistoryRepository,
                recurringTransactionOccurrenceGenerator,
                recurringTransactionProjectedMovementAssembler
        );
    }

    @Test
    void generateProjectedMovementsAcrossRangeShouldAllowRangeOverMaxRangeDays() {
        UUID userGroupId = UUID.randomUUID();
        UUID recurringTransactionId = UUID.randomUUID();

        LocalDate firstPaymentDate = LocalDate.of(2026, 1, 1);
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);

        givenCalendarProjectionLimits(10);

        BatchFixture fixture = givenPreparedBatch(
                recurringTransactionId,
                userGroupId,
                firstPaymentDate
        );

        when(recurringTransactionOccurrenceGenerator.generateOccurrences(
                eq(recurringTransactionId),
                eq(firstPaymentDate),
                eq(fixture.ruleSnapshots()),
                any(LocalDate.class),
                any(LocalDate.class),
                any()
        )).thenReturn(List.of());

        when(recurringTransactionProjectedMovementAssembler.assemble(
                anyList(),
                eq(fixture.detailsHistoryRows())
        )).thenReturn(List.of());

        List<RecurringTransactionProjectedMovement> result =
                service.generateProjectedMovementsAcrossRange(
                        List.of(seed(recurringTransactionId, userGroupId, firstPaymentDate)),
                        from,
                        to
                );

        assertThat(result).isEmpty();

        verify(recurringTransactionOccurrenceGenerator, atLeast(2))
                .generateOccurrences(
                        eq(recurringTransactionId),
                        eq(firstPaymentDate),
                        eq(fixture.ruleSnapshots()),
                        any(LocalDate.class),
                        any(LocalDate.class),
                        any()
                );
    }

    @Test
    void generateProjectedMovementsAcrossRangeShouldReturnEmptyWhenSeedsAreEmpty() {
        List<RecurringTransactionProjectedMovement> result =
                service.generateProjectedMovementsAcrossRange(
                        List.of(),
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 12, 31)
                );

        assertThat(result).isEmpty();

        verifyNoInteractions(
                financeCalendarProperties,
                recurringTransactionHistoryRepository,
                recurringTransactionDetailsHistoryRepository,
                recurringTransactionOccurrenceGenerator,
                recurringTransactionProjectedMovementAssembler
        );
    }

    @Test
    void generateProjectedMovementsAcrossRangeShouldRejectDuplicatedRecurringTransactionSeed() {
        UUID userGroupId = UUID.randomUUID();
        UUID recurringTransactionId = UUID.randomUUID();

        LocalDate firstPaymentDate = LocalDate.of(2026, 1, 1);

        givenMaxRecurringTransactions(100);

        RecurringTransactionProjectedMovementSeed seed =
                seed(recurringTransactionId, userGroupId, firstPaymentDate);

        assertThatThrownBy(() -> service.generateProjectedMovementsAcrossRange(
                List.of(seed, seed),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.recurringTransaction.duplicated");

        verifyNoInteractions(
                recurringTransactionHistoryRepository,
                recurringTransactionDetailsHistoryRepository,
                recurringTransactionOccurrenceGenerator,
                recurringTransactionProjectedMovementAssembler
        );
    }

    @Test
    void generateProjectedMovementsAcrossRangeShouldRejectSeedsFromDifferentUserGroups() {
        UUID firstUserGroupId = UUID.randomUUID();
        UUID secondUserGroupId = UUID.randomUUID();

        LocalDate firstPaymentDate = LocalDate.of(2026, 1, 1);

        givenMaxRecurringTransactions(100);

        assertThatThrownBy(() -> service.generateProjectedMovementsAcrossRange(
                List.of(
                        seed(UUID.randomUUID(), firstUserGroupId, firstPaymentDate),
                        seed(UUID.randomUUID(), secondUserGroupId, firstPaymentDate)
                ),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.recurringTransaction.userGroupMismatch");

        verifyNoInteractions(
                recurringTransactionHistoryRepository,
                recurringTransactionDetailsHistoryRepository,
                recurringTransactionOccurrenceGenerator,
                recurringTransactionProjectedMovementAssembler
        );
    }

    @Test
    void generateProjectedMovementsAcrossRangeShouldRejectMissingRuleHistory() {
        UUID userGroupId = UUID.randomUUID();
        UUID recurringTransactionId = UUID.randomUUID();
        LocalDate firstPaymentDate = LocalDate.of(2026, 1, 1);

        givenMaxRecurringTransactions(100);

        when(recurringTransactionHistoryRepository.findAllHistoryByRecurringTransactionIdsAndUserGroupId(
                List.of(recurringTransactionId),
                userGroupId
        )).thenReturn(List.of());

        when(recurringTransactionDetailsHistoryRepository.findAllHistoryWithDetailsByRecurringTransactionIdsAndUserGroupId(
                List.of(recurringTransactionId),
                userGroupId
        )).thenReturn(List.of());

        assertThatThrownBy(() -> service.generateProjectedMovementsAcrossRange(
                List.of(seed(recurringTransactionId, userGroupId, firstPaymentDate)),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("finance.recurringTransaction.ruleHistoryRequired");

        verifyNoInteractions(
                recurringTransactionOccurrenceGenerator,
                recurringTransactionProjectedMovementAssembler
        );
    }

    @Test
    void generateProjectedMovementsAcrossRangeShouldRejectMissingDetailsHistory() {
        UUID userGroupId = UUID.randomUUID();
        UUID recurringTransactionId = UUID.randomUUID();
        LocalDate firstPaymentDate = LocalDate.of(2026, 1, 1);

        givenMaxRecurringTransactions(100);

        RecurringTransaction recurringTransaction = mock(RecurringTransaction.class);
        when(recurringTransaction.getRecurringTransactionId())
                .thenReturn(recurringTransactionId);

        RecurringTransactionHistory ruleHistory = mock(RecurringTransactionHistory.class);
        when(ruleHistory.getRecurringTransaction())
                .thenReturn(recurringTransaction);

        when(recurringTransactionHistoryRepository.findAllHistoryByRecurringTransactionIdsAndUserGroupId(
                List.of(recurringTransactionId),
                userGroupId
        )).thenReturn(List.of(ruleHistory));

        when(recurringTransactionDetailsHistoryRepository.findAllHistoryWithDetailsByRecurringTransactionIdsAndUserGroupId(
                List.of(recurringTransactionId),
                userGroupId
        )).thenReturn(List.of());

        assertThatThrownBy(() -> service.generateProjectedMovementsAcrossRange(
                List.of(seed(recurringTransactionId, userGroupId, firstPaymentDate)),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("finance.recurringTransaction.detailsHistoryRequired");

        verifyNoInteractions(
                recurringTransactionOccurrenceGenerator,
                recurringTransactionProjectedMovementAssembler
        );
    }

    @Test
    void generateProjectedMovementsAcrossRangeShouldRejectTooManySeeds() {
        UUID userGroupId = UUID.randomUUID();

        LocalDate firstPaymentDate = LocalDate.of(2026, 1, 1);

        givenMaxRecurringTransactions(1);

        assertThatThrownBy(() -> service.generateProjectedMovementsAcrossRange(
                List.of(
                        seed(UUID.randomUUID(), userGroupId, firstPaymentDate),
                        seed(UUID.randomUUID(), userGroupId, firstPaymentDate)
                ),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.recurringTransaction.batchTooLarge");

        verifyNoInteractions(
                recurringTransactionHistoryRepository,
                recurringTransactionDetailsHistoryRepository,
                recurringTransactionOccurrenceGenerator,
                recurringTransactionProjectedMovementAssembler
        );
    }

    @Test
    void generateProjectedMovementsAcrossRangeShouldRejectInvalidDateOrder() {
        UUID userGroupId = UUID.randomUUID();
        UUID recurringTransactionId = UUID.randomUUID();

        assertThatThrownBy(() -> service.generateProjectedMovementsAcrossRange(
                List.of(seed(
                        recurringTransactionId,
                        userGroupId,
                        LocalDate.of(2026, 1, 1)
                )),
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 1, 31)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.calendar.dateRangeInvalid");

        verifyNoInteractions(
                financeCalendarProperties,
                recurringTransactionHistoryRepository,
                recurringTransactionDetailsHistoryRepository,
                recurringTransactionOccurrenceGenerator,
                recurringTransactionProjectedMovementAssembler
        );
    }

    private RecurringTransactionProjectedMovementSeed seed(
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

    private RecurringTransactionOccurrence occurrence(
            UUID recurringTransactionId,
            LocalDate logicalDate,
            LocalDate chargeDate
    ) {
        return new RecurringTransactionOccurrence(
                recurringTransactionId,
                logicalDate,
                chargeDate,
                BigDecimal.valueOf(-100),
                false
        );
    }

    private RecurringTransactionProjectedMovement projectedMovement() {
        return mock(RecurringTransactionProjectedMovement.class);
    }

    private record BatchFixture(
            List<RecurringTransactionRuleSnapshot> ruleSnapshots,
            List<RecurringTransactionDetailsHistory> detailsHistoryRows
    ) {
    }
}