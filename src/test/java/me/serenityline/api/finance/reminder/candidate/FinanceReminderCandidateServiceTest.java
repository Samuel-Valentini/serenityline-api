package me.serenityline.api.finance.reminder.candidate;

import me.serenityline.api.finance.account.entity.Account;
import me.serenityline.api.finance.transaction.service.RecurringTransactionProjectedMovement;
import me.serenityline.api.finance.transaction.service.RecurringTransactionProjectedMovementBatchService;
import me.serenityline.api.finance.transaction.service.RecurringTransactionProjectedMovementSeed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinanceReminderCandidateServiceTest {

    @Mock
    private FinanceReminderCandidateRepository candidateRepository;

    @Mock
    private RecurringTransactionProjectedMovementBatchService recurringProjectionService;

    private FinanceReminderCandidateService service;

    private static RecurringTransactionProjectedMovement projectedMovement(
            UUID recurringTransactionId,
            LocalDate logicalDate,
            LocalDate chargeDate,
            String description,
            BigDecimal amount,
            String currency
    ) {
        Account account = mock(Account.class);
        when(account.getCurrency()).thenReturn(currency);

        return new RecurringTransactionProjectedMovement(
                recurringTransactionId,
                logicalDate,
                chargeDate,
                amount,
                false,
                description,
                mock(me.serenityline.api.finance.category.entity.Category.class),
                mock(me.serenityline.api.finance.financialpriority.entity.FinancialPriority.class),
                account,
                null,
                null,
                true,
                true
        );
    }

    private static RecurringTransactionProjectedMovement projectedMovementWithoutCurrencyStub(
            UUID recurringTransactionId,
            LocalDate logicalDate,
            LocalDate chargeDate,
            String description,
            BigDecimal amount
    ) {
        Account account = mock(Account.class);

        return new RecurringTransactionProjectedMovement(
                recurringTransactionId,
                logicalDate,
                chargeDate,
                amount,
                false,
                description,
                mock(me.serenityline.api.finance.category.entity.Category.class),
                mock(me.serenityline.api.finance.financialpriority.entity.FinancialPriority.class),
                account,
                null,
                null,
                true,
                true
        );
    }

    @BeforeEach
    void setUp() {
        service = new FinanceReminderCandidateService(
                candidateRepository,
                recurringProjectionService
        );
    }

    @Test
    void findDueCandidatesShouldReturnDirectAndRecurringCandidatesSortedByReminderDate() {
        LocalDate today = LocalDate.of(2026, 6, 10);

        UUID userGroupId = UUID.randomUUID();

        UUID directUserId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();

        UUID recurringUserId = UUID.randomUUID();
        UUID recurringTransactionId = UUID.randomUUID();

        FinanceReminderCandidate directCandidate =
                FinanceReminderCandidate.forTransaction(
                        directUserId,
                        userGroupId,
                        transactionId,
                        LocalDate.of(2026, 6, 20),
                        "Affitto",
                        new BigDecimal("-750.00"),
                        "EUR",
                        LocalDate.of(2026, 6, 9)
                );

        RecurringFinanceReminderSeed recurringSeed =
                new RecurringFinanceReminderSeed(
                        recurringTransactionId,
                        userGroupId,
                        LocalDate.of(2026, 1, 10),
                        (short) 7
                );

        RecurringTransactionProjectedMovement projectedMovement =
                projectedMovement(
                        recurringTransactionId,
                        LocalDate.of(2026, 6, 17),
                        LocalDate.of(2026, 6, 17),
                        "Abbonamento palestra",
                        new BigDecimal("-49.90"),
                        "EUR"
                );

        when(candidateRepository.findDueTransactionCandidates(today, 500))
                .thenReturn(List.of(directCandidate));

        when(candidateRepository.findRecurringReminderSeedsPage(today, 500, null))
                .thenReturn(List.of(recurringSeed));

        when(recurringProjectionService.generateProjectedMovementsAcrossRange(
                eq(List.of(new RecurringTransactionProjectedMovementSeed(
                        recurringTransactionId,
                        userGroupId,
                        LocalDate.of(2026, 1, 10)
                ))),
                eq(today),
                eq(today.plusDays(7))
        )).thenReturn(List.of(projectedMovement));

        when(candidateRepository.findReminderEnabledUserIdsByRecurringTransactionId(
                eq(userGroupId),
                anyCollection()
        )).thenReturn(Map.of(
                recurringTransactionId,
                List.of(recurringUserId)
        ));

        List<FinanceReminderCandidate> result = service.findDueCandidates(today);

        assertThat(result)
                .extracting(
                        FinanceReminderCandidate::userId,
                        FinanceReminderCandidate::userGroupId,
                        FinanceReminderCandidate::transactionId,
                        FinanceReminderCandidate::recurringTransactionId,
                        FinanceReminderCandidate::recurringTransactionLogicalDate,
                        FinanceReminderCandidate::chargeDate,
                        FinanceReminderCandidate::notifiedDescription,
                        FinanceReminderCandidate::notifiedAmount,
                        FinanceReminderCandidate::notifiedCurrency,
                        FinanceReminderCandidate::reminderDate
                )
                .containsExactly(
                        tuple(
                                directUserId,
                                userGroupId,
                                transactionId,
                                null,
                                null,
                                LocalDate.of(2026, 6, 20),
                                "Affitto",
                                new BigDecimal("-750.00"),
                                "EUR",
                                LocalDate.of(2026, 6, 9)
                        ),
                        tuple(
                                recurringUserId,
                                userGroupId,
                                null,
                                recurringTransactionId,
                                LocalDate.of(2026, 6, 17),
                                LocalDate.of(2026, 6, 17),
                                "Abbonamento palestra",
                                new BigDecimal("-49.90"),
                                "EUR",
                                LocalDate.of(2026, 6, 10)
                        )
                );
    }

    @Test
    void findDueCandidatesShouldReturnOnlyDirectCandidatesWhenNoRecurringSeedsExist() {
        LocalDate today = LocalDate.of(2026, 6, 10);

        UUID userGroupId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();

        FinanceReminderCandidate directCandidate =
                FinanceReminderCandidate.forTransaction(
                        userId,
                        userGroupId,
                        transactionId,
                        LocalDate.of(2026, 6, 15),
                        "Bolletta luce",
                        new BigDecimal("-120.00"),
                        "EUR",
                        LocalDate.of(2026, 6, 8)
                );

        when(candidateRepository.findDueTransactionCandidates(today, 500))
                .thenReturn(List.of(directCandidate));

        when(candidateRepository.findRecurringReminderSeedsPage(today, 500, null))
                .thenReturn(List.of());

        List<FinanceReminderCandidate> result = service.findDueCandidates(today);

        assertThat(result).containsExactly(directCandidate);

        verifyNoInteractions(recurringProjectionService);
        verify(candidateRepository, never())
                .findReminderEnabledUserIdsByRecurringTransactionId(any(), anyCollection());
    }

    @Test
    void findDueCandidatesShouldIgnoreRecurringProjectedMovementsWhoseReminderDateIsAfterToday() {
        LocalDate today = LocalDate.of(2026, 6, 10);

        UUID userGroupId = UUID.randomUUID();
        UUID recurringTransactionId = UUID.randomUUID();

        RecurringFinanceReminderSeed recurringSeed =
                new RecurringFinanceReminderSeed(
                        recurringTransactionId,
                        userGroupId,
                        LocalDate.of(2026, 1, 10),
                        (short) 3
                );

        RecurringTransactionProjectedMovement projectedMovement =
                projectedMovementWithoutCurrencyStub(
                        recurringTransactionId,
                        LocalDate.of(2026, 6, 20),
                        LocalDate.of(2026, 6, 20),
                        "Rata corso",
                        new BigDecimal("-80.00")
                );

        when(candidateRepository.findDueTransactionCandidates(today, 500))
                .thenReturn(List.of());

        when(candidateRepository.findRecurringReminderSeedsPage(today, 500, null))
                .thenReturn(List.of(recurringSeed));

        when(recurringProjectionService.generateProjectedMovementsAcrossRange(
                eq(List.of(new RecurringTransactionProjectedMovementSeed(
                        recurringTransactionId,
                        userGroupId,
                        LocalDate.of(2026, 1, 10)
                ))),
                eq(today),
                eq(today.plusDays(3))
        )).thenReturn(List.of(projectedMovement));

        List<FinanceReminderCandidate> result = service.findDueCandidates(today);

        assertThat(result).isEmpty();

        verify(candidateRepository, never())
                .findReminderEnabledUserIdsByRecurringTransactionId(any(), anyCollection());
    }

    @Test
    void findDueCandidatesShouldCreateOneRecurringCandidateForEachLinkedReminderEnabledUser() {
        LocalDate today = LocalDate.of(2026, 6, 10);

        UUID userGroupId = UUID.randomUUID();
        UUID recurringTransactionId = UUID.randomUUID();

        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();

        RecurringFinanceReminderSeed recurringSeed =
                new RecurringFinanceReminderSeed(
                        recurringTransactionId,
                        userGroupId,
                        LocalDate.of(2026, 1, 10),
                        (short) 7
                );

        RecurringTransactionProjectedMovement projectedMovement =
                projectedMovement(
                        recurringTransactionId,
                        LocalDate.of(2026, 6, 17),
                        LocalDate.of(2026, 6, 17),
                        "Mutuo",
                        new BigDecimal("-900.00"),
                        "EUR"
                );

        when(candidateRepository.findDueTransactionCandidates(today, 500))
                .thenReturn(List.of());

        when(candidateRepository.findRecurringReminderSeedsPage(today, 500, null))
                .thenReturn(List.of(recurringSeed));

        when(recurringProjectionService.generateProjectedMovementsAcrossRange(
                anyList(),
                eq(today),
                eq(today.plusDays(7))
        )).thenReturn(List.of(projectedMovement));

        when(candidateRepository.findReminderEnabledUserIdsByRecurringTransactionId(
                eq(userGroupId),
                anyCollection()
        )).thenReturn(Map.of(
                recurringTransactionId,
                List.of(firstUserId, secondUserId)
        ));

        List<FinanceReminderCandidate> result = service.findDueCandidates(today);

        assertThat(result)
                .extracting(FinanceReminderCandidate::userId)
                .containsExactlyInAnyOrder(firstUserId, secondUserId);

        assertThat(result)
                .allSatisfy(candidate -> {
                    assertThat(candidate.transactionId()).isNull();
                    assertThat(candidate.recurringTransactionId()).isEqualTo(recurringTransactionId);
                    assertThat(candidate.recurringTransactionLogicalDate()).isEqualTo(LocalDate.of(2026, 6, 17));
                    assertThat(candidate.chargeDate()).isEqualTo(LocalDate.of(2026, 6, 17));
                    assertThat(candidate.reminderDate()).isEqualTo(today);
                    assertThat(candidate.notifiedDescription()).isEqualTo("Mutuo");
                    assertThat(candidate.notifiedAmount()).isEqualByComparingTo("-900.00");
                    assertThat(candidate.notifiedCurrency()).isEqualTo("EUR");
                });
    }

    @Test
    void findDueCandidatesShouldProcessRecurringSeedsGroupedByUserGroup() {
        LocalDate today = LocalDate.of(2026, 6, 10);

        UUID firstUserGroupId = UUID.randomUUID();
        UUID secondUserGroupId = UUID.randomUUID();

        UUID firstRecurringTransactionId = UUID.randomUUID();
        UUID secondRecurringTransactionId = UUID.randomUUID();

        RecurringFinanceReminderSeed firstSeed =
                new RecurringFinanceReminderSeed(
                        firstRecurringTransactionId,
                        firstUserGroupId,
                        LocalDate.of(2026, 1, 10),
                        (short) 7
                );

        RecurringFinanceReminderSeed secondSeed =
                new RecurringFinanceReminderSeed(
                        secondRecurringTransactionId,
                        secondUserGroupId,
                        LocalDate.of(2026, 1, 20),
                        (short) 7
                );

        when(candidateRepository.findDueTransactionCandidates(today, 500))
                .thenReturn(List.of());

        when(candidateRepository.findRecurringReminderSeedsPage(today, 500, null))
                .thenReturn(List.of(firstSeed, secondSeed));

        when(recurringProjectionService.generateProjectedMovementsAcrossRange(
                anyList(),
                eq(today),
                eq(today.plusDays(7))
        )).thenReturn(List.of());

        service.findDueCandidates(today);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RecurringTransactionProjectedMovementSeed>> seedsCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(recurringProjectionService, times(2))
                .generateProjectedMovementsAcrossRange(
                        seedsCaptor.capture(),
                        eq(today),
                        eq(today.plusDays(7))
                );

        assertThat(seedsCaptor.getAllValues())
                .hasSize(2);

        assertThat(seedsCaptor.getAllValues().get(0))
                .containsExactly(new RecurringTransactionProjectedMovementSeed(
                        firstRecurringTransactionId,
                        firstUserGroupId,
                        LocalDate.of(2026, 1, 10)
                ));

        assertThat(seedsCaptor.getAllValues().get(1))
                .containsExactly(new RecurringTransactionProjectedMovementSeed(
                        secondRecurringTransactionId,
                        secondUserGroupId,
                        LocalDate.of(2026, 1, 20)
                ));
    }

    @Test
    void findDueCandidatesShouldRejectNullToday() {
        assertThatThrownBy(() -> service.findDueCandidates(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("today");

        verifyNoInteractions(candidateRepository, recurringProjectionService);
    }

    @Test
    void findDueCandidatesShouldUseConfirmedRecurringOccurrenceSnapshotWhenPresent() {
        LocalDate today = LocalDate.of(2026, 6, 10);

        UUID userGroupId = UUID.randomUUID();
        UUID recurringTransactionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        LocalDate logicalDate = LocalDate.of(2026, 6, 17);

        RecurringFinanceReminderSeed recurringSeed =
                new RecurringFinanceReminderSeed(
                        recurringTransactionId,
                        userGroupId,
                        LocalDate.of(2026, 1, 10),
                        (short) 5
                );

        RecurringTransactionProjectedMovement projectedMovement =
                projectedMovementWithoutCurrencyStub(
                        recurringTransactionId,
                        logicalDate,
                        LocalDate.of(2026, 6, 17),
                        "Descrizione projected",
                        new BigDecimal("-100.00")
                );

        FinanceReminderConfirmedRecurringOccurrenceSnapshot confirmedSnapshot =
                new FinanceReminderConfirmedRecurringOccurrenceSnapshot(
                        recurringTransactionId,
                        logicalDate,
                        LocalDate.of(2026, 6, 15),
                        "Descrizione transaction confermata",
                        new BigDecimal("-95.50"),
                        "EUR"
                );

        when(candidateRepository.findDueTransactionCandidates(today, 500))
                .thenReturn(List.of());

        when(candidateRepository.findRecurringReminderSeedsPage(today, 500, null))
                .thenReturn(List.of(recurringSeed));

        when(recurringProjectionService.generateProjectedMovementsAcrossRange(
                anyList(),
                eq(today),
                eq(today.plusDays(5))
        )).thenReturn(List.of(projectedMovement));

        when(candidateRepository.findConfirmedRecurringOccurrenceSnapshots(
                eq(userGroupId),
                anyCollection(),
                eq(logicalDate),
                eq(logicalDate)
        )).thenReturn(List.of(confirmedSnapshot));

        when(candidateRepository.findReminderEnabledUserIdsByRecurringTransactionId(
                eq(userGroupId),
                anyCollection()
        )).thenReturn(Map.of(
                recurringTransactionId,
                List.of(userId)
        ));

        List<FinanceReminderCandidate> result = service.findDueCandidates(today);

        assertThat(result)
                .extracting(
                        FinanceReminderCandidate::userId,
                        FinanceReminderCandidate::transactionId,
                        FinanceReminderCandidate::recurringTransactionId,
                        FinanceReminderCandidate::recurringTransactionLogicalDate,
                        FinanceReminderCandidate::chargeDate,
                        FinanceReminderCandidate::notifiedDescription,
                        FinanceReminderCandidate::notifiedAmount,
                        FinanceReminderCandidate::notifiedCurrency,
                        FinanceReminderCandidate::reminderDate
                )
                .containsExactly(tuple(
                        userId,
                        null,
                        recurringTransactionId,
                        logicalDate,
                        LocalDate.of(2026, 6, 15),
                        "Descrizione transaction confermata",
                        new BigDecimal("-95.50"),
                        "EUR",
                        today
                ));
    }

    @Test
    void findDueCandidatesShouldGenerateRecurringMovementsOnlyUpToMaxReminderDaysBeforeInGroup() {
        LocalDate today = LocalDate.of(2026, 6, 10);

        UUID userGroupId = UUID.randomUUID();

        UUID firstRecurringTransactionId = UUID.randomUUID();
        UUID secondRecurringTransactionId = UUID.randomUUID();

        RecurringFinanceReminderSeed firstSeed =
                new RecurringFinanceReminderSeed(
                        firstRecurringTransactionId,
                        userGroupId,
                        LocalDate.of(2026, 1, 10),
                        (short) 3
                );

        RecurringFinanceReminderSeed secondSeed =
                new RecurringFinanceReminderSeed(
                        secondRecurringTransactionId,
                        userGroupId,
                        LocalDate.of(2026, 1, 20),
                        (short) 30
                );

        when(candidateRepository.findDueTransactionCandidates(today, 500))
                .thenReturn(List.of());

        when(candidateRepository.findRecurringReminderSeedsPage(today, 500, null))
                .thenReturn(List.of(firstSeed, secondSeed));

        when(recurringProjectionService.generateProjectedMovementsAcrossRange(
                anyList(),
                eq(today),
                eq(today.plusDays(30))
        )).thenReturn(List.of());

        List<FinanceReminderCandidate> result = service.findDueCandidates(today);

        assertThat(result).isEmpty();

        verify(recurringProjectionService)
                .generateProjectedMovementsAcrossRange(
                        anyList(),
                        eq(today),
                        eq(today.plusDays(30))
                );
    }

    @Test
    void findDueCandidatesShouldReadRecurringSeedsAcrossMultiplePages() {
        LocalDate today = LocalDate.of(2026, 6, 10);

        UUID userGroupId = UUID.randomUUID();

        List<RecurringFinanceReminderSeed> firstPage = java.util.stream.IntStream.range(0, 500)
                .mapToObj(index -> new RecurringFinanceReminderSeed(
                        UUID.randomUUID(),
                        userGroupId,
                        LocalDate.of(2026, 1, 10),
                        (short) 7
                ))
                .toList();

        RecurringFinanceReminderSeed extraSeed =
                new RecurringFinanceReminderSeed(
                        UUID.randomUUID(),
                        userGroupId,
                        LocalDate.of(2026, 1, 10),
                        (short) 7
                );

        UUID lastFirstPageRecurringTransactionId =
                firstPage.get(firstPage.size() - 1).recurringTransactionId();

        when(candidateRepository.findDueTransactionCandidates(today, 500))
                .thenReturn(List.of());

        when(candidateRepository.findRecurringReminderSeedsPage(today, 500, null))
                .thenReturn(firstPage);

        when(candidateRepository.findRecurringReminderSeedsPage(
                today,
                500,
                lastFirstPageRecurringTransactionId
        )).thenReturn(List.of(extraSeed));

        when(recurringProjectionService.generateProjectedMovementsAcrossRange(
                anyList(),
                eq(today),
                eq(today.plusDays(7))
        )).thenReturn(List.of());

        List<FinanceReminderCandidate> result = service.findDueCandidates(today);

        assertThat(result).isEmpty();

        verify(candidateRepository).findRecurringReminderSeedsPage(today, 500, null);

        verify(candidateRepository).findRecurringReminderSeedsPage(
                today,
                500,
                lastFirstPageRecurringTransactionId
        );

        verify(recurringProjectionService, times(2))
                .generateProjectedMovementsAcrossRange(
                        anyList(),
                        eq(today),
                        eq(today.plusDays(7))
                );
    }
}