package me.serenityline.api.finance.transaction.service;

import me.serenityline.api.finance.calendar.BusinessCalendar;
import me.serenityline.api.finance.calendar.CurrencyBusinessCalendarResolver;
import me.serenityline.api.finance.calendar.PaymentAccountCurrencySnapshot;
import me.serenityline.api.finance.calendar.PaymentBusinessCalendarProvider;
import me.serenityline.api.finance.transaction.entity.*;
import me.serenityline.api.finance.transaction.repository.RecurringTransactionDetailsHistoryRepository;
import me.serenityline.api.finance.transaction.repository.RecurringTransactionHistoryRepository;
import me.serenityline.api.user.entity.UserGroup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecurringTransactionOccurrenceServiceTest {

    @Mock
    private RecurringTransactionHistoryRepository recurringTransactionHistoryRepository;

    @Mock
    private RecurringTransactionDetailsHistoryRepository recurringTransactionDetailsHistoryRepository;

    @Mock
    private RecurringTransactionRuleSnapshotMapper recurringTransactionRuleSnapshotMapper;

    @Mock
    private RecurringTransactionAccountCurrencySnapshotMapper recurringTransactionAccountCurrencySnapshotMapper;

    @Mock
    private CurrencyBusinessCalendarResolver currencyBusinessCalendarResolver;

    @Mock
    private RecurringTransactionOccurrenceGenerator recurringTransactionOccurrenceGenerator;

    @InjectMocks
    private RecurringTransactionOccurrenceService service;

    private static RecurringTransaction recurringTransaction(
            UUID recurringTransactionId,
            UUID userGroupId,
            LocalDate firstPaymentDate
    ) {
        UserGroup userGroup = mock(UserGroup.class);
        when(userGroup.getUserGroupId()).thenReturn(userGroupId);

        RecurringTransaction recurringTransaction = mock(RecurringTransaction.class);
        when(recurringTransaction.getRecurringTransactionId()).thenReturn(recurringTransactionId);
        when(recurringTransaction.getUserGroup()).thenReturn(userGroup);
        when(recurringTransaction.getRecurringTransactionFirstPaymentDate())
                .thenReturn(firstPaymentDate);

        return recurringTransaction;
    }

    @Test
    void shouldGenerateOccurrencesUsingLoadedHistoryAndCurrencyProvider() {
        UUID recurringTransactionId = UUID.randomUUID();
        UUID userGroupId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        LocalDate firstPaymentDate = LocalDate.of(2026, 1, 10);
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);

        RecurringTransaction recurringTransaction = recurringTransaction(
                recurringTransactionId,
                userGroupId,
                firstPaymentDate
        );

        RecurringTransactionHistory ruleHistoryRow = mock(RecurringTransactionHistory.class);
        RecurringTransactionDetailsHistory detailsHistoryRow =
                mock(RecurringTransactionDetailsHistory.class);

        List<RecurringTransactionHistory> ruleHistoryRows = List.of(ruleHistoryRow);
        List<RecurringTransactionDetailsHistory> detailsHistoryRows = List.of(detailsHistoryRow);

        List<RecurringTransactionRuleSnapshot> ruleSnapshots = List.of(
                new RecurringTransactionRuleSnapshot(
                        firstPaymentDate,
                        null,
                        (short) 10,
                        (short) 1,
                        RecurrenceUnit.MONTH,
                        PaymentDateAdjustmentPolicy.NONE,
                        new BigDecimal("-100.00"),
                        null,
                        null,
                        1L
                )
        );

        List<PaymentAccountCurrencySnapshot> accountCurrencySnapshots = List.of(
                new PaymentAccountCurrencySnapshot(
                        firstPaymentDate,
                        accountId,
                        "EUR",
                        1L
                )
        );

        List<RecurringTransactionOccurrence> expectedOccurrences = List.of(
                new RecurringTransactionOccurrence(
                        recurringTransactionId,
                        LocalDate.of(2026, 1, 10),
                        LocalDate.of(2026, 1, 10),
                        new BigDecimal("-100.00"),
                        false
                )
        );

        BusinessCalendar eurCalendar = mock(BusinessCalendar.class);

        when(recurringTransactionHistoryRepository.findAllHistoryByRecurringTransactionId(
                recurringTransactionId
        )).thenReturn(ruleHistoryRows);

        when(recurringTransactionDetailsHistoryRepository
                .findAllHistoryWithLinkedAccountByRecurringTransactionIdAndUserGroupId(
                        recurringTransactionId,
                        userGroupId
                ))
                .thenReturn(detailsHistoryRows);

        when(recurringTransactionRuleSnapshotMapper.toSnapshots(ruleHistoryRows))
                .thenReturn(ruleSnapshots);

        when(recurringTransactionAccountCurrencySnapshotMapper.toSnapshots(detailsHistoryRows))
                .thenReturn(accountCurrencySnapshots);

        when(currencyBusinessCalendarResolver.resolveByCurrency("EUR"))
                .thenReturn(eurCalendar);

        when(currencyBusinessCalendarResolver.adjustmentWindowDays())
                .thenReturn(14);

        when(recurringTransactionOccurrenceGenerator.generateOccurrences(
                eq(recurringTransactionId),
                eq(firstPaymentDate),
                same(ruleSnapshots),
                eq(from),
                eq(to),
                any(PaymentBusinessCalendarProvider.class)
        )).thenReturn(expectedOccurrences);

        List<RecurringTransactionOccurrence> occurrences =
                service.generateOccurrences(recurringTransaction, from, to);

        assertThat(occurrences).isSameAs(expectedOccurrences);

        ArgumentCaptor<PaymentBusinessCalendarProvider> providerCaptor =
                ArgumentCaptor.forClass(PaymentBusinessCalendarProvider.class);

        verify(recurringTransactionOccurrenceGenerator).generateOccurrences(
                eq(recurringTransactionId),
                eq(firstPaymentDate),
                same(ruleSnapshots),
                eq(from),
                eq(to),
                providerCaptor.capture()
        );

        PaymentBusinessCalendarProvider provider = providerCaptor.getValue();

        assertThat(provider.adjustmentWindowDays()).isEqualTo(14);
        assertThat(provider.calendarAt(firstPaymentDate)).isSameAs(eurCalendar);
    }

    @Test
    void shouldRejectInvalidRangeBeforeLoadingData() {
        RecurringTransaction recurringTransaction = mock(RecurringTransaction.class);

        assertThatThrownBy(() -> service.generateOccurrences(
                recurringTransaction,
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
                currencyBusinessCalendarResolver,
                recurringTransactionOccurrenceGenerator
        );
    }

    @Test
    void shouldRejectNullRecurringTransaction() {
        assertThatThrownBy(() -> service.generateOccurrences(
                null,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31)
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("recurringTransaction");
    }

    @Test
    void shouldRejectNullFrom() {
        assertThatThrownBy(() -> service.generateOccurrences(
                mock(RecurringTransaction.class),
                null,
                LocalDate.of(2026, 1, 31)
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("from");
    }

    @Test
    void shouldRejectNullTo() {
        assertThatThrownBy(() -> service.generateOccurrences(
                mock(RecurringTransaction.class),
                LocalDate.of(2026, 1, 1),
                null
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("to");
    }

    @Test
    void shouldRejectRecurringTransactionWithoutId() {
        RecurringTransaction recurringTransaction = mock(RecurringTransaction.class);

        when(recurringTransaction.getRecurringTransactionId()).thenReturn(null);

        assertThatThrownBy(() -> service.generateOccurrences(
                recurringTransaction,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31)
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("recurringTransactionId");
    }

    @Test
    void shouldRejectRecurringTransactionWithoutUserGroup() {
        RecurringTransaction recurringTransaction = mock(RecurringTransaction.class);

        when(recurringTransaction.getRecurringTransactionId()).thenReturn(UUID.randomUUID());
        when(recurringTransaction.getUserGroup()).thenReturn(null);

        assertThatThrownBy(() -> service.generateOccurrences(
                recurringTransaction,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31)
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("userGroup");
    }

    @Test
    void shouldRejectRecurringTransactionWithoutUserGroupId() {
        RecurringTransaction recurringTransaction = mock(RecurringTransaction.class);
        UserGroup userGroup = mock(UserGroup.class);

        when(recurringTransaction.getRecurringTransactionId()).thenReturn(UUID.randomUUID());
        when(recurringTransaction.getUserGroup()).thenReturn(userGroup);
        when(userGroup.getUserGroupId()).thenReturn(null);

        assertThatThrownBy(() -> service.generateOccurrences(
                recurringTransaction,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31)
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("userGroupId");
    }

    @Test
    void shouldRejectRecurringTransactionWithoutFirstPaymentDate() {
        UUID recurringTransactionId = UUID.randomUUID();
        UUID userGroupId = UUID.randomUUID();

        RecurringTransaction recurringTransaction = recurringTransaction(
                recurringTransactionId,
                userGroupId,
                null
        );

        assertThatThrownBy(() -> service.generateOccurrences(
                recurringTransaction,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31)
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("recurringTransactionFirstPaymentDate");
    }

    @Test
    void shouldRejectMissingRuleHistory() {
        UUID recurringTransactionId = UUID.randomUUID();
        UUID userGroupId = UUID.randomUUID();

        RecurringTransaction recurringTransaction = recurringTransaction(
                recurringTransactionId,
                userGroupId,
                LocalDate.of(2026, 1, 10)
        );

        when(recurringTransactionHistoryRepository.findAllHistoryByRecurringTransactionId(
                recurringTransactionId
        )).thenReturn(List.of());

        assertThatThrownBy(() -> service.generateOccurrences(
                recurringTransaction,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("finance.recurringTransaction.ruleHistoryRequired");

        verifyNoInteractions(
                recurringTransactionDetailsHistoryRepository,
                recurringTransactionRuleSnapshotMapper,
                recurringTransactionAccountCurrencySnapshotMapper,
                currencyBusinessCalendarResolver,
                recurringTransactionOccurrenceGenerator
        );
    }

    @Test
    void shouldRejectMissingDetailsHistory() {
        UUID recurringTransactionId = UUID.randomUUID();
        UUID userGroupId = UUID.randomUUID();

        RecurringTransaction recurringTransaction = recurringTransaction(
                recurringTransactionId,
                userGroupId,
                LocalDate.of(2026, 1, 10)
        );

        RecurringTransactionHistory ruleHistoryRow = mock(RecurringTransactionHistory.class);

        when(recurringTransactionHistoryRepository.findAllHistoryByRecurringTransactionId(
                recurringTransactionId
        )).thenReturn(List.of(ruleHistoryRow));

        when(recurringTransactionDetailsHistoryRepository
                .findAllHistoryWithLinkedAccountByRecurringTransactionIdAndUserGroupId(
                        recurringTransactionId,
                        userGroupId
                ))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.generateOccurrences(
                recurringTransaction,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("finance.recurringTransaction.detailsHistoryRequired");

        verifyNoInteractions(
                recurringTransactionRuleSnapshotMapper,
                recurringTransactionAccountCurrencySnapshotMapper,
                currencyBusinessCalendarResolver,
                recurringTransactionOccurrenceGenerator
        );
    }

    @Test
    void shouldBuildPaymentCalendarProviderUsingCurrencyActiveAtLogicalDate() {
        UUID recurringTransactionId = UUID.randomUUID();
        UUID userGroupId = UUID.randomUUID();
        UUID eurAccountId = UUID.randomUUID();
        UUID usdAccountId = UUID.randomUUID();

        LocalDate firstPaymentDate = LocalDate.of(2026, 1, 10);
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 12, 31);

        RecurringTransaction recurringTransaction = recurringTransaction(
                recurringTransactionId,
                userGroupId,
                firstPaymentDate
        );

        RecurringTransactionHistory ruleHistoryRow = mock(RecurringTransactionHistory.class);
        RecurringTransactionDetailsHistory firstDetails = mock(RecurringTransactionDetailsHistory.class);
        RecurringTransactionDetailsHistory secondDetails = mock(RecurringTransactionDetailsHistory.class);

        List<RecurringTransactionHistory> ruleHistoryRows = List.of(ruleHistoryRow);
        List<RecurringTransactionDetailsHistory> detailsHistoryRows = List.of(firstDetails, secondDetails);

        List<RecurringTransactionRuleSnapshot> ruleSnapshots = List.of(
                new RecurringTransactionRuleSnapshot(
                        firstPaymentDate,
                        null,
                        (short) 10,
                        (short) 1,
                        RecurrenceUnit.MONTH,
                        PaymentDateAdjustmentPolicy.PREVIOUS_BUSINESS_DAY,
                        new BigDecimal("-100.00"),
                        null,
                        null,
                        1L
                )
        );

        List<PaymentAccountCurrencySnapshot> accountCurrencySnapshots = List.of(
                new PaymentAccountCurrencySnapshot(
                        LocalDate.of(2026, 1, 1),
                        eurAccountId,
                        "EUR",
                        1L
                ),
                new PaymentAccountCurrencySnapshot(
                        LocalDate.of(2026, 7, 1),
                        usdAccountId,
                        "USD",
                        2L
                )
        );

        BusinessCalendar eurCalendar = mock(BusinessCalendar.class);
        BusinessCalendar usdCalendar = mock(BusinessCalendar.class);

        when(recurringTransactionHistoryRepository.findAllHistoryByRecurringTransactionId(
                recurringTransactionId
        )).thenReturn(ruleHistoryRows);

        when(recurringTransactionDetailsHistoryRepository
                .findAllHistoryWithLinkedAccountByRecurringTransactionIdAndUserGroupId(
                        recurringTransactionId,
                        userGroupId
                ))
                .thenReturn(detailsHistoryRows);

        when(recurringTransactionRuleSnapshotMapper.toSnapshots(ruleHistoryRows))
                .thenReturn(ruleSnapshots);

        when(recurringTransactionAccountCurrencySnapshotMapper.toSnapshots(detailsHistoryRows))
                .thenReturn(accountCurrencySnapshots);

        when(currencyBusinessCalendarResolver.resolveByCurrency("EUR"))
                .thenReturn(eurCalendar);

        when(currencyBusinessCalendarResolver.resolveByCurrency("USD"))
                .thenReturn(usdCalendar);

        when(currencyBusinessCalendarResolver.adjustmentWindowDays())
                .thenReturn(14);

        when(recurringTransactionOccurrenceGenerator.generateOccurrences(
                eq(recurringTransactionId),
                eq(firstPaymentDate),
                same(ruleSnapshots),
                eq(from),
                eq(to),
                any(PaymentBusinessCalendarProvider.class)
        )).thenReturn(List.of());

        service.generateOccurrences(recurringTransaction, from, to);

        ArgumentCaptor<PaymentBusinessCalendarProvider> providerCaptor =
                ArgumentCaptor.forClass(PaymentBusinessCalendarProvider.class);

        verify(recurringTransactionOccurrenceGenerator).generateOccurrences(
                eq(recurringTransactionId),
                eq(firstPaymentDate),
                same(ruleSnapshots),
                eq(from),
                eq(to),
                providerCaptor.capture()
        );

        PaymentBusinessCalendarProvider provider = providerCaptor.getValue();

        assertThat(provider.calendarAt(LocalDate.of(2026, 6, 30))).isSameAs(eurCalendar);
        assertThat(provider.calendarAt(LocalDate.of(2026, 7, 1))).isSameAs(usdCalendar);
        assertThat(provider.adjustmentWindowDays()).isEqualTo(14);
    }

    @Test
    void shouldRejectEmptyAccountCurrencySnapshots() {
        UUID recurringTransactionId = UUID.randomUUID();
        UUID userGroupId = UUID.randomUUID();

        LocalDate firstPaymentDate = LocalDate.of(2026, 1, 10);

        RecurringTransaction recurringTransaction = recurringTransaction(
                recurringTransactionId,
                userGroupId,
                firstPaymentDate
        );

        RecurringTransactionHistory ruleHistoryRow = mock(RecurringTransactionHistory.class);
        RecurringTransactionDetailsHistory detailsHistoryRow =
                mock(RecurringTransactionDetailsHistory.class);

        List<RecurringTransactionHistory> ruleHistoryRows = List.of(ruleHistoryRow);
        List<RecurringTransactionDetailsHistory> detailsHistoryRows = List.of(detailsHistoryRow);

        List<RecurringTransactionRuleSnapshot> ruleSnapshots = List.of(
                new RecurringTransactionRuleSnapshot(
                        firstPaymentDate,
                        null,
                        (short) 10,
                        (short) 1,
                        RecurrenceUnit.MONTH,
                        PaymentDateAdjustmentPolicy.NONE,
                        new BigDecimal("-100.00"),
                        null,
                        null,
                        1L
                )
        );

        when(recurringTransactionHistoryRepository.findAllHistoryByRecurringTransactionId(
                recurringTransactionId
        )).thenReturn(ruleHistoryRows);

        when(recurringTransactionDetailsHistoryRepository
                .findAllHistoryWithLinkedAccountByRecurringTransactionIdAndUserGroupId(
                        recurringTransactionId,
                        userGroupId
                ))
                .thenReturn(detailsHistoryRows);

        when(recurringTransactionRuleSnapshotMapper.toSnapshots(ruleHistoryRows))
                .thenReturn(ruleSnapshots);

        when(recurringTransactionAccountCurrencySnapshotMapper.toSnapshots(detailsHistoryRows))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.generateOccurrences(
                recurringTransaction,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.paymentCalendar.snapshotsRequired");

        verifyNoInteractions(recurringTransactionOccurrenceGenerator);
    }
}