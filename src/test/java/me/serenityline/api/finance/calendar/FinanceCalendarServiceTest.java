package me.serenityline.api.finance.calendar;

import me.serenityline.api.common.error.ResourceNotFoundException;
import me.serenityline.api.finance.account.entity.Account;
import me.serenityline.api.finance.account.repository.AccountRepository;
import me.serenityline.api.finance.account.repository.AccountUserRepository;
import me.serenityline.api.finance.category.entity.Category;
import me.serenityline.api.finance.common.FinanceProperties;
import me.serenityline.api.finance.financialpriority.entity.FinancialPriority;
import me.serenityline.api.finance.simulation.entity.SimulationGroup;
import me.serenityline.api.finance.simulation.repository.SimulationGroupRepository;
import me.serenityline.api.finance.transaction.entity.RecurringTransaction;
import me.serenityline.api.finance.transaction.entity.Transaction;
import me.serenityline.api.finance.transaction.repository.RecurringTransactionRepository;
import me.serenityline.api.finance.transaction.repository.TransactionRepository;
import me.serenityline.api.finance.transaction.service.*;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.entity.UserGroup;
import me.serenityline.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;

@ExtendWith(MockitoExtension.class)
class FinanceCalendarServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private RecurringTransactionRepository recurringTransactionRepository;

    @Mock
    private SimulationGroupRepository simulationGroupRepository;

    @Mock
    private TransactionAccessService transactionAccessService;

    @Mock
    private RecurringTransactionAccessService recurringTransactionAccessService;

    @Mock
    private RecurringTransactionProjectedMovementBatchService recurringTransactionProjectedMovementBatchService;

    @Mock
    private FinanceCalendarMovementMapper financeCalendarMovementMapper;

    @Mock
    private FinanceCalendarProperties financeCalendarProperties;

    @Mock
    private FinanceProperties financeProperties;

    @Mock
    private AccountUserRepository accountUserRepository;

    @Mock
    private AccountRepository accountRepository;

    private FinanceCalendarService service;

    private static FinanceCalendarSearchRequest request(
            LocalDate from,
            LocalDate to,
            UUID accountId,
            List<UUID> simulationGroupIds
    ) {
        return new FinanceCalendarSearchRequest(
                from,
                to,
                accountId == null ? null : List.of(accountId),
                simulationGroupIds
        );
    }

    private static FinanceCalendarSearchRequest requestForAccounts(
            LocalDate from,
            LocalDate to,
            List<UUID> accountIds,
            List<UUID> simulationGroupIds
    ) {
        return new FinanceCalendarSearchRequest(
                from,
                to,
                accountIds,
                simulationGroupIds
        );
    }

    private static User user(
            UUID userId,
            UUID userGroupId
    ) {
        UserGroup userGroup = mock(UserGroup.class);
        lenient().when(userGroup.getUserGroupId())
                .thenReturn(userGroupId);

        User user = mock(User.class);
        lenient().when(user.getUserId())
                .thenReturn(userId);
        lenient().when(user.getUserGroup())
                .thenReturn(userGroup);

        return user;
    }

    private static Transaction manualTransaction() {
        Transaction transaction = mock(Transaction.class);
        lenient().when(transaction.getRecurringTransaction())
                .thenReturn(null);

        return transaction;
    }

    private static Transaction confirmedRecurringTransaction(
            UUID recurringTransactionId,
            LocalDate recurringTransactionLogicalDate
    ) {
        RecurringTransaction recurringTransaction = mock(RecurringTransaction.class);
        lenient().when(recurringTransaction.getRecurringTransactionId())
                .thenReturn(recurringTransactionId);

        Transaction transaction = mock(Transaction.class);
        lenient().when(transaction.getRecurringTransaction())
                .thenReturn(recurringTransaction);
        lenient().when(transaction.getRecurringTransactionLogicalDate())
                .thenReturn(recurringTransactionLogicalDate);

        return transaction;
    }

    private static RecurringTransaction recurringTransaction(
            UUID recurringTransactionId,
            LocalDate firstPaymentDate,
            boolean simulated,
            UUID simulationGroupId
    ) {
        RecurringTransaction recurringTransaction = mock(RecurringTransaction.class);

        lenient().when(recurringTransaction.getRecurringTransactionId())
                .thenReturn(recurringTransactionId);
        lenient().when(recurringTransaction.getRecurringTransactionFirstPaymentDate())
                .thenReturn(firstPaymentDate);
        lenient().when(recurringTransaction.isRecurringTransactionIsSimulated())
                .thenReturn(simulated);

        if (simulationGroupId == null) {
            lenient().when(recurringTransaction.getSimulationGroup())
                    .thenReturn(null);
        } else {
            SimulationGroup simulationGroup = mock(SimulationGroup.class);
            lenient().when(simulationGroup.getSimulationGroupId())
                    .thenReturn(simulationGroupId);
            lenient().when(recurringTransaction.getSimulationGroup())
                    .thenReturn(simulationGroup);
        }

        return recurringTransaction;
    }

    private static RecurringTransactionProjectedMovement projectedMovement(
            UUID recurringTransactionId,
            LocalDate logicalDate,
            LocalDate chargeDate
    ) {
        return projectedMovement(
                recurringTransactionId,
                logicalDate,
                chargeDate,
                UUID.randomUUID()
        );
    }

    private static RecurringTransactionProjectedMovement projectedMovement(
            UUID recurringTransactionId,
            LocalDate logicalDate,
            LocalDate chargeDate,
            UUID accountId
    ) {
        Account account = mock(Account.class);
        lenient().when(account.getAccountId())
                .thenReturn(accountId);

        return new RecurringTransactionProjectedMovement(
                recurringTransactionId,
                logicalDate,
                chargeDate,
                new BigDecimal("-100.00"),
                false,
                "Movimento previsto",
                mock(Category.class),
                mock(FinancialPriority.class),
                account,
                null,
                null,
                true,
                true
        );
    }

    private static FinanceCalendarMovement persistedCalendarMovement(
            UUID transactionId,
            LocalDate chargeDate,
            UUID accountId
    ) {
        return new FinanceCalendarMovement(
                FinanceCalendarMovementType.PERSISTED_TRANSACTION,
                transactionId,
                null,
                chargeDate,
                chargeDate,
                "Movimento persistito",
                new BigDecimal("-50.00"),
                true,
                true,
                UUID.randomUUID(),
                null,
                accountId,
                null,
                null,
                true,
                false,
                null,
                true,
                false
        );
    }

    private static FinanceCalendarMovement projectedCalendarMovement(
            UUID recurringTransactionId,
            LocalDate logicalDate,
            LocalDate chargeDate,
            UUID accountId,
            boolean simulated,
            UUID simulationGroupId
    ) {
        return new FinanceCalendarMovement(
                FinanceCalendarMovementType.PROJECTED_RECURRING_TRANSACTION,
                null,
                recurringTransactionId,
                logicalDate,
                chargeDate,
                "Movimento ricorrente previsto",
                new BigDecimal("-100.00"),
                true,
                true,
                UUID.randomUUID(),
                UUID.randomUUID(),
                accountId,
                null,
                null,
                false,
                simulated,
                simulationGroupId,
                false,
                false
        );
    }

    private static TransactionRepository.ConfirmedRecurringOccurrenceKeyView confirmedKey(
            UUID recurringTransactionId,
            LocalDate logicalDate
    ) {
        return new TransactionRepository.ConfirmedRecurringOccurrenceKeyView() {
            @Override
            public UUID getRecurringTransactionId() {
                return recurringTransactionId;
            }

            @Override
            public LocalDate getRecurringTransactionLogicalDate() {
                return logicalDate;
            }
        };
    }

    @BeforeEach
    void setUp() {
        lenient().when(financeCalendarProperties.getMaxRangeDays())
                .thenReturn(1830L);

        lenient().when(financeCalendarProperties.getMaxAccountIds())
                .thenReturn(50);

        lenient().when(financeProperties.getMaxSimulationGroupIds())
                .thenReturn(50);

        service = new FinanceCalendarService(
                userRepository,
                transactionRepository,
                recurringTransactionRepository,
                simulationGroupRepository,
                accountUserRepository,
                transactionAccessService,
                recurringTransactionAccessService,
                recurringTransactionProjectedMovementBatchService,
                financeCalendarMovementMapper,
                financeCalendarProperties,
                financeProperties,
                accountRepository
        );
    }

    @Test
    void shouldRequireCurrentUserId() {
        FinanceCalendarSearchRequest request = request(
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30),
                null,
                null
        );

        assertThatThrownBy(() -> service.getCalendarMovements(null, request))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("currentUserId");
    }

    @Test
    void shouldRequireRequest() {
        assertThatThrownBy(() -> service.getCalendarMovements(UUID.randomUUID(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("request");
    }

    @Test
    void shouldRequireFromDate() {
        FinanceCalendarSearchRequest request = request(
                null,
                LocalDate.of(2026, 6, 30),
                null,
                null
        );

        assertThatThrownBy(() -> service.getCalendarMovements(UUID.randomUUID(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.calendar.fromRequired");

        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldRequireToDate() {
        FinanceCalendarSearchRequest request = request(
                LocalDate.of(2026, 6, 1),
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.getCalendarMovements(UUID.randomUUID(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.calendar.toRequired");

        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldRejectInvalidDateRange() {
        FinanceCalendarSearchRequest request = request(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 6, 30),
                null,
                null
        );

        assertThatThrownBy(() -> service.getCalendarMovements(UUID.randomUUID(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.calendar.dateRangeInvalid");

        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldRejectTooLargeDateRange() {
        when(financeCalendarProperties.getMaxRangeDays())
                .thenReturn(30L);

        FinanceCalendarSearchRequest request = request(
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 7, 2),
                null,
                null
        );

        assertThatThrownBy(() -> service.getCalendarMovements(UUID.randomUUID(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.calendar.dateRangeTooLarge");

        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldRejectNullSimulationGroupIdBeforeLoadingUser() {
        FinanceCalendarSearchRequest request = request(
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30),
                null,
                Arrays.asList(UUID.randomUUID(), null)
        );

        assertThatThrownBy(() -> service.getCalendarMovements(UUID.randomUUID(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.simulationGroup.idRequired");

        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldRejectTooManySimulationGroupIdsBeforeLoadingUser() {
        when(financeProperties.getMaxSimulationGroupIds())
                .thenReturn(1);

        FinanceCalendarSearchRequest request = request(
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30),
                null,
                List.of(UUID.randomUUID(), UUID.randomUUID())
        );

        assertThatThrownBy(() -> service.getCalendarMovements(UUID.randomUUID(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.simulationGroup.idsTooMany");

        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldReturnNotFoundWhenCurrentUserDoesNotExist() {
        UUID currentUserId = UUID.randomUUID();

        when(userRepository.findById(currentUserId))
                .thenReturn(Optional.empty());

        FinanceCalendarSearchRequest request = request(
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30),
                null,
                null
        );

        assertThatThrownBy(() -> service.getCalendarMovements(currentUserId, request))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(transactionRepository);
        verifyNoInteractions(recurringTransactionRepository);
        verifyNoInteractions(recurringTransactionProjectedMovementBatchService);
    }

    @Test
    void ownerShouldReturnPersistedAndProjectedMovementsOrderedByChargeDate() {
        UUID currentUserId = UUID.randomUUID();
        UUID userGroupId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UUID recurringTransactionId = UUID.randomUUID();

        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);

        User owner = user(currentUserId, userGroupId);

        Transaction transaction = manualTransaction();
        RecurringTransaction recurringTransaction = recurringTransaction(
                recurringTransactionId,
                LocalDate.of(2026, 6, 1),
                false,
                null
        );

        RecurringTransactionProjectedMovement projectedMovement =
                projectedMovement(
                        recurringTransactionId,
                        LocalDate.of(2026, 6, 10),
                        LocalDate.of(2026, 6, 10)
                );

        FinanceCalendarMovement persistedCalendarMovement =
                persistedCalendarMovement(
                        transactionId,
                        LocalDate.of(2026, 6, 15),
                        accountId
                );

        FinanceCalendarMovement projectedCalendarMovement =
                projectedCalendarMovement(
                        recurringTransactionId,
                        LocalDate.of(2026, 6, 10),
                        LocalDate.of(2026, 6, 10),
                        accountId,
                        false,
                        null
                );

        when(userRepository.findById(currentUserId))
                .thenReturn(Optional.of(owner));
        when(transactionAccessService.canReadAllGroupTransactions(owner))
                .thenReturn(true);
        when(recurringTransactionAccessService.canReadAllGroupRecurringTransactions(owner))
                .thenReturn(true);
        when(transactionRepository.findBaseGroupTransactionsInRange(userGroupId, from, to, null))
                .thenReturn(List.of(transaction));
        when(recurringTransactionRepository.findCalendarReadableBaseByUserGroup(userGroupId, null))
                .thenReturn(List.of(recurringTransaction));
        when(recurringTransactionProjectedMovementBatchService.generateProjectedMovementsAcrossRange(anyList(), eq(from), eq(to)))
                .thenReturn(List.of(projectedMovement));
        stubNoConfirmedKeys(
                userGroupId,
                LocalDate.of(2026, 6, 10),
                LocalDate.of(2026, 6, 10)
        );
        when(financeCalendarMovementMapper.fromPersistedTransaction(transaction))
                .thenReturn(persistedCalendarMovement);
        when(financeCalendarMovementMapper.fromProjectedRecurringMovement(projectedMovement, false, null))
                .thenReturn(projectedCalendarMovement);

        List<FinanceCalendarMovement> result = service.getCalendarMovements(
                currentUserId,
                request(from, to, null, null)
        );

        assertThat(result)
                .containsExactly(projectedCalendarMovement, persistedCalendarMovement);

        verify(recurringTransactionAccessService)
                .assertReadableAccountFilter(owner, userGroupId, null);
        verifyNoInteractions(simulationGroupRepository);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RecurringTransactionProjectedMovementSeed>> seedsCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(recurringTransactionProjectedMovementBatchService)
                .generateProjectedMovementsAcrossRange(seedsCaptor.capture(), eq(from), eq(to));

        assertThat(seedsCaptor.getValue())
                .singleElement()
                .satisfies(seed -> {
                    assertThat(seed.recurringTransactionId()).isEqualTo(recurringTransactionId);
                    assertThat(seed.userGroupId()).isEqualTo(userGroupId);
                    assertThat(seed.firstPaymentDate()).isEqualTo(LocalDate.of(2026, 6, 1));
                });
    }

    @Test
    void shouldDeduplicateProjectedRecurringMovementUsingLogicalDateEvenWhenChargeDateChanged() {
        UUID currentUserId = UUID.randomUUID();
        UUID userGroupId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UUID recurringTransactionId = UUID.randomUUID();

        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);
        LocalDate logicalDate = LocalDate.of(2026, 6, 10);

        User owner = user(currentUserId, userGroupId);

        Transaction confirmedTransaction = confirmedRecurringTransaction(
                recurringTransactionId,
                logicalDate
        );

        RecurringTransaction recurringTransaction = recurringTransaction(
                recurringTransactionId,
                LocalDate.of(2026, 6, 1),
                false,
                null
        );

        RecurringTransactionProjectedMovement projectedMovement =
                projectedMovement(
                        recurringTransactionId,
                        logicalDate,
                        LocalDate.of(2026, 6, 10)
                );

        FinanceCalendarMovement persistedCalendarMovement =
                persistedCalendarMovement(
                        transactionId,
                        LocalDate.of(2026, 6, 12),
                        accountId
                );

        TransactionRepository.ConfirmedRecurringOccurrenceKeyView confirmedKey =
                confirmedKey(recurringTransactionId, logicalDate);

        when(userRepository.findById(currentUserId))
                .thenReturn(Optional.of(owner));
        when(transactionAccessService.canReadAllGroupTransactions(owner))
                .thenReturn(true);
        when(recurringTransactionAccessService.canReadAllGroupRecurringTransactions(owner))
                .thenReturn(true);
        when(transactionRepository.findBaseGroupTransactionsInRange(userGroupId, from, to, null))
                .thenReturn(List.of(confirmedTransaction));
        when(recurringTransactionRepository.findCalendarReadableBaseByUserGroup(userGroupId, null))
                .thenReturn(List.of(recurringTransaction));
        when(transactionRepository.findConfirmedRecurringOccurrenceKeysForRecurringTransactions(
                eq(userGroupId),
                anyCollection(),
                eq(logicalDate),
                eq(logicalDate)
        )).thenReturn(List.of(confirmedKey));
        when(recurringTransactionProjectedMovementBatchService.generateProjectedMovementsAcrossRange(anyList(), eq(from), eq(to)))
                .thenReturn(List.of(projectedMovement));
        when(financeCalendarMovementMapper.fromPersistedTransaction(confirmedTransaction))
                .thenReturn(persistedCalendarMovement);

        List<FinanceCalendarMovement> result = service.getCalendarMovements(
                currentUserId,
                request(from, to, null, null)
        );

        assertThat(result)
                .containsExactly(persistedCalendarMovement);

        verify(financeCalendarMovementMapper, never())
                .fromProjectedRecurringMovement(eq(projectedMovement), anyBoolean(), any());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> recurringIdsCaptor =
                ArgumentCaptor.forClass(Collection.class);

        verify(transactionRepository).findConfirmedRecurringOccurrenceKeysForRecurringTransactions(
                eq(userGroupId),
                recurringIdsCaptor.capture(),
                eq(logicalDate),
                eq(logicalDate)
        );

        assertThat(recurringIdsCaptor.getValue())
                .containsExactly(recurringTransactionId);
    }

    @Test
    void shouldNotDeduplicateProjectedRecurringMovementWithDifferentLogicalDate() {
        UUID currentUserId = UUID.randomUUID();
        UUID userGroupId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UUID recurringTransactionId = UUID.randomUUID();

        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);

        User owner = user(currentUserId, userGroupId);

        Transaction confirmedTransaction = confirmedRecurringTransaction(
                recurringTransactionId,
                LocalDate.of(2026, 6, 10)
        );

        RecurringTransaction recurringTransaction = recurringTransaction(
                recurringTransactionId,
                LocalDate.of(2026, 6, 1),
                false,
                null
        );

        RecurringTransactionProjectedMovement projectedMovement =
                projectedMovement(
                        recurringTransactionId,
                        LocalDate.of(2026, 6, 15),
                        LocalDate.of(2026, 6, 15)
                );

        FinanceCalendarMovement persistedCalendarMovement =
                persistedCalendarMovement(
                        transactionId,
                        LocalDate.of(2026, 6, 12),
                        accountId
                );

        FinanceCalendarMovement projectedCalendarMovement =
                projectedCalendarMovement(
                        recurringTransactionId,
                        LocalDate.of(2026, 6, 15),
                        LocalDate.of(2026, 6, 15),
                        accountId,
                        false,
                        null
                );

        when(userRepository.findById(currentUserId))
                .thenReturn(Optional.of(owner));
        when(transactionAccessService.canReadAllGroupTransactions(owner))
                .thenReturn(true);
        when(recurringTransactionAccessService.canReadAllGroupRecurringTransactions(owner))
                .thenReturn(true);
        when(transactionRepository.findBaseGroupTransactionsInRange(userGroupId, from, to, null))
                .thenReturn(List.of(confirmedTransaction));
        when(recurringTransactionRepository.findCalendarReadableBaseByUserGroup(userGroupId, null))
                .thenReturn(List.of(recurringTransaction));
        when(recurringTransactionProjectedMovementBatchService.generateProjectedMovementsAcrossRange(anyList(), eq(from), eq(to)))
                .thenReturn(List.of(projectedMovement));
        stubNoConfirmedKeys(
                userGroupId,
                LocalDate.of(2026, 6, 15),
                LocalDate.of(2026, 6, 15)
        );
        when(financeCalendarMovementMapper.fromPersistedTransaction(confirmedTransaction))
                .thenReturn(persistedCalendarMovement);
        when(financeCalendarMovementMapper.fromProjectedRecurringMovement(projectedMovement, false, null))
                .thenReturn(projectedCalendarMovement);

        List<FinanceCalendarMovement> result = service.getCalendarMovements(
                currentUserId,
                request(from, to, null, null)
        );

        assertThat(result)
                .containsExactly(persistedCalendarMovement, projectedCalendarMovement);
    }

    @Test
    void ownerShouldUseBaseAndSimulatedQueriesWhenSimulationGroupsAreSelected() {
        UUID currentUserId = UUID.randomUUID();
        UUID userGroupId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID firstSimulationGroupId = UUID.randomUUID();
        UUID secondSimulationGroupId = UUID.randomUUID();
        UUID recurringTransactionId = UUID.randomUUID();

        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);

        User owner = user(currentUserId, userGroupId);

        RecurringTransaction simulatedRecurringTransaction = recurringTransaction(
                recurringTransactionId,
                LocalDate.of(2026, 6, 1),
                true,
                firstSimulationGroupId
        );

        RecurringTransactionProjectedMovement projectedMovement =
                projectedMovement(
                        recurringTransactionId,
                        LocalDate.of(2026, 6, 10),
                        LocalDate.of(2026, 6, 10)
                );

        FinanceCalendarMovement projectedCalendarMovement =
                projectedCalendarMovement(
                        recurringTransactionId,
                        LocalDate.of(2026, 6, 10),
                        LocalDate.of(2026, 6, 10),
                        accountId,
                        true,
                        firstSimulationGroupId
                );

        when(userRepository.findById(currentUserId))
                .thenReturn(Optional.of(owner));
        when(transactionAccessService.canReadAllGroupTransactions(owner))
                .thenReturn(true);
        when(recurringTransactionAccessService.canReadAllGroupRecurringTransactions(owner))
                .thenReturn(true);
        when(simulationGroupRepository.findActiveIdsByUserGroupId(anyCollection(), eq(userGroupId)))
                .thenReturn(List.of(firstSimulationGroupId, secondSimulationGroupId));
        when(transactionRepository.findBaseAndSimulatedGroupTransactionsInRange(
                userGroupId,
                from,
                to,
                null,
                List.of(firstSimulationGroupId, secondSimulationGroupId)
        )).thenReturn(List.of());
        when(recurringTransactionRepository.findCalendarReadableBaseAndSimulatedByUserGroup(
                userGroupId,
                null,
                List.of(firstSimulationGroupId, secondSimulationGroupId)
        )).thenReturn(List.of(simulatedRecurringTransaction));
        when(recurringTransactionProjectedMovementBatchService.generateProjectedMovementsAcrossRange(anyList(), eq(from), eq(to)))
                .thenReturn(List.of(projectedMovement));
        stubNoConfirmedKeys(
                userGroupId,
                LocalDate.of(2026, 6, 10),
                LocalDate.of(2026, 6, 10)
        );
        when(financeCalendarMovementMapper.fromProjectedRecurringMovement(
                projectedMovement,
                true,
                firstSimulationGroupId
        )).thenReturn(projectedCalendarMovement);

        List<FinanceCalendarMovement> result = service.getCalendarMovements(
                currentUserId,
                request(
                        from,
                        to,
                        null,
                        List.of(firstSimulationGroupId, secondSimulationGroupId)
                )
        );

        assertThat(result)
                .containsExactly(projectedCalendarMovement);

        verify(simulationGroupRepository)
                .findActiveIdsByUserGroupId(
                        eq(List.of(firstSimulationGroupId, secondSimulationGroupId)),
                        eq(userGroupId)
                );
    }

    @Test
    void shouldNormalizeDuplicateSimulationGroupIds() {
        UUID currentUserId = UUID.randomUUID();
        UUID userGroupId = UUID.randomUUID();
        UUID simulationGroupId = UUID.randomUUID();

        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);

        User owner = user(currentUserId, userGroupId);

        when(userRepository.findById(currentUserId))
                .thenReturn(Optional.of(owner));
        when(transactionAccessService.canReadAllGroupTransactions(owner))
                .thenReturn(true);
        when(recurringTransactionAccessService.canReadAllGroupRecurringTransactions(owner))
                .thenReturn(true);
        when(simulationGroupRepository.findActiveIdsByUserGroupId(anyCollection(), eq(userGroupId)))
                .thenReturn(List.of(simulationGroupId));
        when(transactionRepository.findBaseAndSimulatedGroupTransactionsInRange(
                userGroupId,
                from,
                to,
                null,
                List.of(simulationGroupId)
        )).thenReturn(List.of());
        when(recurringTransactionRepository.findCalendarReadableBaseAndSimulatedByUserGroup(
                userGroupId,
                null,
                List.of(simulationGroupId)
        )).thenReturn(List.of());

        List<FinanceCalendarMovement> result = service.getCalendarMovements(
                currentUserId,
                request(from, to, null, List.of(simulationGroupId, simulationGroupId))
        );

        assertThat(result).isEmpty();

        verify(simulationGroupRepository)
                .findActiveIdsByUserGroupId(
                        eq(List.of(simulationGroupId)),
                        eq(userGroupId)
                );
    }

    @Test
    void ownerShouldReceiveNotFoundWhenAnySelectedSimulationGroupIsNotReadable() {
        UUID currentUserId = UUID.randomUUID();
        UUID userGroupId = UUID.randomUUID();
        UUID foundSimulationGroupId = UUID.randomUUID();
        UUID missingSimulationGroupId = UUID.randomUUID();

        User owner = user(currentUserId, userGroupId);

        when(userRepository.findById(currentUserId))
                .thenReturn(Optional.of(owner));
        when(transactionAccessService.canReadAllGroupTransactions(owner))
                .thenReturn(true);
        when(simulationGroupRepository.findActiveIdsByUserGroupId(anyCollection(), eq(userGroupId)))
                .thenReturn(List.of(foundSimulationGroupId));

        FinanceCalendarSearchRequest request = request(
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30),
                null,
                List.of(foundSimulationGroupId, missingSimulationGroupId)
        );

        assertThatThrownBy(() -> service.getCalendarMovements(currentUserId, request))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(transactionRepository);
        verifyNoInteractions(recurringTransactionRepository);
        verifyNoInteractions(recurringTransactionProjectedMovementBatchService);
    }

    @Test
    void collaboratorShouldUseLinkedUserQueriesAndLinkedSimulationValidation() {
        UUID currentUserId = UUID.randomUUID();
        UUID userGroupId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID simulationGroupId = UUID.randomUUID();

        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);

        User collaborator = user(currentUserId, userGroupId);

        when(userRepository.findById(currentUserId))
                .thenReturn(Optional.of(collaborator));
        when(transactionAccessService.canReadAllGroupTransactions(collaborator))
                .thenReturn(false);
        when(recurringTransactionAccessService.canReadAllGroupRecurringTransactions(collaborator))
                .thenReturn(false);
        when(simulationGroupRepository.findActiveIdsReadableByLinkedUser(
                anyCollection(),
                eq(userGroupId),
                eq(currentUserId)
        )).thenReturn(List.of(simulationGroupId));
        when(transactionRepository.findBaseAndSimulatedLinkedUserTransactionsInRangeForAccounts(
                userGroupId,
                currentUserId,
                from,
                to,
                List.of(accountId),
                List.of(simulationGroupId)
        )).thenReturn(List.of());

        when(recurringTransactionRepository.findCalendarReadableBaseAndSimulatedByLinkedUserAccessForAccounts(
                userGroupId,
                currentUserId,
                List.of(accountId),
                List.of(simulationGroupId)
        )).thenReturn(List.of());

        List<FinanceCalendarMovement> result = service.getCalendarMovements(
                currentUserId,
                request(from, to, accountId, List.of(simulationGroupId))
        );

        assertThat(result).isEmpty();

        verify(recurringTransactionAccessService)
                .assertReadableAccountFilter(collaborator, userGroupId, accountId);
        verify(simulationGroupRepository)
                .findActiveIdsReadableByLinkedUser(
                        eq(List.of(simulationGroupId)),
                        eq(userGroupId),
                        eq(currentUserId)
                );
    }

    @Test
    void collaboratorShouldUseBaseLinkedQueriesWhenNoSimulationGroupIsSelected() {
        UUID currentUserId = UUID.randomUUID();
        UUID userGroupId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);

        User collaborator = user(currentUserId, userGroupId);

        when(userRepository.findById(currentUserId))
                .thenReturn(Optional.of(collaborator));
        when(transactionAccessService.canReadAllGroupTransactions(collaborator))
                .thenReturn(false);
        when(recurringTransactionAccessService.canReadAllGroupRecurringTransactions(collaborator))
                .thenReturn(false);
        when(transactionRepository.findBaseLinkedUserTransactionsInRangeForAccounts(
                userGroupId,
                currentUserId,
                from,
                to,
                List.of(accountId)
        )).thenReturn(List.of());

        when(recurringTransactionRepository.findCalendarReadableBaseByLinkedUserAccessForAccounts(
                userGroupId,
                currentUserId,
                List.of(accountId)
        )).thenReturn(List.of());

        List<FinanceCalendarMovement> result = service.getCalendarMovements(
                currentUserId,
                request(from, to, accountId, null)
        );

        assertThat(result).isEmpty();

        verifyNoInteractions(simulationGroupRepository);
    }

    @Test
    void shouldFilterProjectedMovementsByRequestedAccountBeforeMapping() {
        UUID currentUserId = UUID.randomUUID();
        UUID userGroupId = UUID.randomUUID();
        UUID requestedAccountId = UUID.randomUUID();
        UUID otherAccountId = UUID.randomUUID();

        UUID firstRecurringTransactionId = UUID.randomUUID();
        UUID secondRecurringTransactionId = UUID.randomUUID();

        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);

        User owner = user(currentUserId, userGroupId);

        RecurringTransaction firstRecurringTransaction = recurringTransaction(
                firstRecurringTransactionId,
                LocalDate.of(2026, 6, 1),
                false,
                null
        );

        RecurringTransaction secondRecurringTransaction = recurringTransaction(
                secondRecurringTransactionId,
                LocalDate.of(2026, 6, 1),
                false,
                null
        );

        RecurringTransactionProjectedMovement firstProjectedMovement =
                projectedMovement(
                        firstRecurringTransactionId,
                        LocalDate.of(2026, 6, 10),
                        LocalDate.of(2026, 6, 10),
                        requestedAccountId
                );

        RecurringTransactionProjectedMovement secondProjectedMovement =
                projectedMovement(
                        secondRecurringTransactionId,
                        LocalDate.of(2026, 6, 11),
                        LocalDate.of(2026, 6, 11),
                        otherAccountId
                );

        FinanceCalendarMovement includedMovement =
                projectedCalendarMovement(
                        firstRecurringTransactionId,
                        LocalDate.of(2026, 6, 10),
                        LocalDate.of(2026, 6, 10),
                        requestedAccountId,
                        false,
                        null
                );

        when(userRepository.findById(currentUserId))
                .thenReturn(Optional.of(owner));
        when(transactionAccessService.canReadAllGroupTransactions(owner))
                .thenReturn(true);
        when(recurringTransactionAccessService.canReadAllGroupRecurringTransactions(owner))
                .thenReturn(true);
        when(transactionRepository.findBaseGroupTransactionsInRangeForAccounts(
                userGroupId,
                from,
                to,
                List.of(requestedAccountId)
        )).thenReturn(List.of());

        when(recurringTransactionRepository.findCalendarReadableBaseByUserGroupForAccounts(
                userGroupId,
                List.of(requestedAccountId)
        )).thenReturn(List.of(firstRecurringTransaction, secondRecurringTransaction));
        when(recurringTransactionProjectedMovementBatchService.generateProjectedMovementsAcrossRange(anyList(), eq(from), eq(to)))
                .thenReturn(List.of(firstProjectedMovement, secondProjectedMovement));
        stubNoConfirmedKeys(
                userGroupId,
                LocalDate.of(2026, 6, 10),
                LocalDate.of(2026, 6, 10)
        );
        when(financeCalendarMovementMapper.fromProjectedRecurringMovement(firstProjectedMovement, false, null))
                .thenReturn(includedMovement);

        List<FinanceCalendarMovement> result = service.getCalendarMovements(
                currentUserId,
                request(from, to, requestedAccountId, null)
        );

        assertThat(result)
                .containsExactly(includedMovement);

        verify(financeCalendarMovementMapper, never())
                .fromProjectedRecurringMovement(eq(secondProjectedMovement), anyBoolean(), any());
    }

    @Test
    void shouldReturnOnlyPersistedMovementsWhenThereAreNoRecurringTransactions() {
        UUID currentUserId = UUID.randomUUID();
        UUID userGroupId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();

        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);

        User owner = user(currentUserId, userGroupId);

        Transaction transaction = manualTransaction();

        FinanceCalendarMovement persistedCalendarMovement =
                persistedCalendarMovement(
                        transactionId,
                        LocalDate.of(2026, 6, 10),
                        accountId
                );

        when(userRepository.findById(currentUserId))
                .thenReturn(Optional.of(owner));
        when(transactionAccessService.canReadAllGroupTransactions(owner))
                .thenReturn(true);
        when(recurringTransactionAccessService.canReadAllGroupRecurringTransactions(owner))
                .thenReturn(true);
        when(transactionRepository.findBaseGroupTransactionsInRange(userGroupId, from, to, null))
                .thenReturn(List.of(transaction));
        when(recurringTransactionRepository.findCalendarReadableBaseByUserGroup(userGroupId, null))
                .thenReturn(List.of());
        when(financeCalendarMovementMapper.fromPersistedTransaction(transaction))
                .thenReturn(persistedCalendarMovement);

        List<FinanceCalendarMovement> result = service.getCalendarMovements(
                currentUserId,
                request(from, to, null, null)
        );

        assertThat(result)
                .containsExactly(persistedCalendarMovement);
    }

    @Test
    void shouldReturnEmptyListWhenNoPersistedTransactionsAndNoRecurringTransactionsExist() {
        UUID currentUserId = UUID.randomUUID();
        UUID userGroupId = UUID.randomUUID();

        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);

        User owner = user(currentUserId, userGroupId);

        when(userRepository.findById(currentUserId))
                .thenReturn(Optional.of(owner));
        when(transactionAccessService.canReadAllGroupTransactions(owner))
                .thenReturn(true);
        when(recurringTransactionAccessService.canReadAllGroupRecurringTransactions(owner))
                .thenReturn(true);
        when(transactionRepository.findBaseGroupTransactionsInRange(userGroupId, from, to, null))
                .thenReturn(List.of());
        when(recurringTransactionRepository.findCalendarReadableBaseByUserGroup(userGroupId, null))
                .thenReturn(List.of());

        verify(transactionRepository, never())
                .findConfirmedRecurringOccurrenceKeysForRecurringTransactions(
                        any(),
                        anyCollection(),
                        any(),
                        any()
                );

        List<FinanceCalendarMovement> result = service.getCalendarMovements(
                currentUserId,
                request(from, to, null, null)
        );

        assertThat(result).isEmpty();
    }

    @Test
    void shouldStopWhenAccountFilterIsNotReadable() {
        UUID currentUserId = UUID.randomUUID();
        UUID userGroupId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        User owner = user(currentUserId, userGroupId);

        when(userRepository.findById(currentUserId))
                .thenReturn(Optional.of(owner));

        doThrow(new ResourceNotFoundException("finance.account.notFound"))
                .when(recurringTransactionAccessService)
                .assertReadableAccountFilter(owner, userGroupId, accountId);

        FinanceCalendarSearchRequest request = request(
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30),
                accountId,
                null
        );

        assertThatThrownBy(() -> service.getCalendarMovements(currentUserId, request))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(simulationGroupRepository);
        verifyNoInteractions(transactionRepository);
        verifyNoInteractions(recurringTransactionRepository);
        verifyNoInteractions(recurringTransactionProjectedMovementBatchService);
    }

    @Test
    void shouldFailWhenProjectedMovementHasNoReadableRecurringContext() {
        UUID currentUserId = UUID.randomUUID();
        UUID userGroupId = UUID.randomUUID();
        UUID recurringTransactionId = UUID.randomUUID();
        UUID unexpectedRecurringTransactionId = UUID.randomUUID();

        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);

        User owner = user(currentUserId, userGroupId);

        RecurringTransaction recurringTransaction = recurringTransaction(
                recurringTransactionId,
                LocalDate.of(2026, 6, 1),
                false,
                null
        );

        RecurringTransactionProjectedMovement unexpectedMovement = projectedMovement(
                unexpectedRecurringTransactionId,
                LocalDate.of(2026, 6, 10),
                LocalDate.of(2026, 6, 10)
        );

        when(userRepository.findById(currentUserId))
                .thenReturn(Optional.of(owner));
        when(transactionAccessService.canReadAllGroupTransactions(owner))
                .thenReturn(true);
        when(recurringTransactionAccessService.canReadAllGroupRecurringTransactions(owner))
                .thenReturn(true);
        when(transactionRepository.findBaseGroupTransactionsInRange(userGroupId, from, to, null))
                .thenReturn(List.of());
        when(recurringTransactionRepository.findCalendarReadableBaseByUserGroup(userGroupId, null))
                .thenReturn(List.of(recurringTransaction));
        when(recurringTransactionProjectedMovementBatchService.generateProjectedMovementsAcrossRange(anyList(), eq(from), eq(to)))
                .thenReturn(List.of(unexpectedMovement));

        assertThatThrownBy(() -> service.getCalendarMovements(
                currentUserId,
                request(from, to, null, null)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("finance.calendar.recurringContextMissing");
    }

    @Test
    void shouldOrderMovementsStablyWhenDatesAreEqual() {
        UUID currentUserId = UUID.randomUUID();
        UUID userGroupId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        UUID firstTransactionId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondTransactionId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);
        LocalDate sameDate = LocalDate.of(2026, 6, 10);

        User owner = user(currentUserId, userGroupId);

        Transaction firstTransaction = manualTransaction();
        Transaction secondTransaction = manualTransaction();

        FinanceCalendarMovement secondMovement = persistedCalendarMovement(
                secondTransactionId,
                sameDate,
                accountId
        );

        FinanceCalendarMovement firstMovement = persistedCalendarMovement(
                firstTransactionId,
                sameDate,
                accountId
        );

        when(userRepository.findById(currentUserId))
                .thenReturn(Optional.of(owner));
        when(transactionAccessService.canReadAllGroupTransactions(owner))
                .thenReturn(true);
        when(recurringTransactionAccessService.canReadAllGroupRecurringTransactions(owner))
                .thenReturn(true);
        when(transactionRepository.findBaseGroupTransactionsInRange(userGroupId, from, to, null))
                .thenReturn(List.of(secondTransaction, firstTransaction));
        when(recurringTransactionRepository.findCalendarReadableBaseByUserGroup(userGroupId, null))
                .thenReturn(List.of());
        when(financeCalendarMovementMapper.fromPersistedTransaction(secondTransaction))
                .thenReturn(secondMovement);
        when(financeCalendarMovementMapper.fromPersistedTransaction(firstTransaction))
                .thenReturn(firstMovement);

        List<FinanceCalendarMovement> result = service.getCalendarMovements(
                currentUserId,
                request(from, to, null, null)
        );

        assertThat(result)
                .containsExactly(firstMovement, secondMovement);
    }

    @Test
    void shouldDeduplicateConfirmedRecurringOccurrenceEvenWhenConfirmedChargeDateIsOutsideRequestedRange() {
        UUID currentUserId = UUID.randomUUID();
        UUID userGroupId = UUID.randomUUID();
        UUID recurringTransactionId = UUID.randomUUID();

        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);
        LocalDate logicalDate = LocalDate.of(2026, 6, 10);

        User owner = user(currentUserId, userGroupId);

        RecurringTransaction recurringTransaction = recurringTransaction(
                recurringTransactionId,
                LocalDate.of(2026, 6, 1),
                false,
                null
        );

        RecurringTransactionProjectedMovement projectedMovement =
                projectedMovement(
                        recurringTransactionId,
                        logicalDate,
                        logicalDate
                );

        TransactionRepository.ConfirmedRecurringOccurrenceKeyView confirmedKey =
                confirmedKey(recurringTransactionId, logicalDate);

        when(userRepository.findById(currentUserId))
                .thenReturn(Optional.of(owner));

        when(transactionAccessService.canReadAllGroupTransactions(owner))
                .thenReturn(true);

        when(recurringTransactionAccessService.canReadAllGroupRecurringTransactions(owner))
                .thenReturn(true);

        when(transactionRepository.findBaseGroupTransactionsInRange(
                userGroupId,
                from,
                to,
                null
        )).thenReturn(List.of());

        when(recurringTransactionRepository.findCalendarReadableBaseByUserGroup(
                userGroupId,
                null
        )).thenReturn(List.of(recurringTransaction));

        when(recurringTransactionProjectedMovementBatchService.generateProjectedMovementsAcrossRange(
                anyList(),
                eq(from),
                eq(to)
        )).thenReturn(List.of(projectedMovement));

        when(transactionRepository.findConfirmedRecurringOccurrenceKeysForRecurringTransactions(
                eq(userGroupId),
                anyCollection(),
                eq(logicalDate),
                eq(logicalDate)
        )).thenReturn(List.of(confirmedKey));

        List<FinanceCalendarMovement> result = service.getCalendarMovements(
                currentUserId,
                request(from, to, null, null)
        );

        assertThat(result).isEmpty();

        verify(financeCalendarMovementMapper, never())
                .fromProjectedRecurringMovement(eq(projectedMovement), anyBoolean(), any());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> recurringIdsCaptor =
                ArgumentCaptor.forClass(Collection.class);

        verify(transactionRepository).findConfirmedRecurringOccurrenceKeysForRecurringTransactions(
                eq(userGroupId),
                recurringIdsCaptor.capture(),
                eq(logicalDate),
                eq(logicalDate)
        );

        assertThat(recurringIdsCaptor.getValue())
                .containsExactly(recurringTransactionId);
    }

    @Test
    void collaboratorShouldFilterProjectedMovementsOnUnreadableAccounts() {
        UUID currentUserId = UUID.randomUUID();
        UUID userGroupId = UUID.randomUUID();
        UUID readableAccountId = UUID.randomUUID();
        UUID unreadableAccountId = UUID.randomUUID();

        UUID firstRecurringTransactionId = UUID.randomUUID();
        UUID secondRecurringTransactionId = UUID.randomUUID();

        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);

        LocalDate firstLogicalDate = LocalDate.of(2026, 6, 10);
        LocalDate secondLogicalDate = LocalDate.of(2026, 6, 11);

        User collaborator = user(currentUserId, userGroupId);

        RecurringTransaction firstRecurringTransaction = recurringTransaction(
                firstRecurringTransactionId,
                LocalDate.of(2026, 6, 1),
                false,
                null
        );

        RecurringTransaction secondRecurringTransaction = recurringTransaction(
                secondRecurringTransactionId,
                LocalDate.of(2026, 6, 1),
                false,
                null
        );

        RecurringTransactionProjectedMovement firstProjectedMovement =
                projectedMovement(
                        firstRecurringTransactionId,
                        firstLogicalDate,
                        firstLogicalDate,
                        readableAccountId
                );

        RecurringTransactionProjectedMovement secondProjectedMovement =
                projectedMovement(
                        secondRecurringTransactionId,
                        secondLogicalDate,
                        secondLogicalDate,
                        unreadableAccountId
                );

        FinanceCalendarMovement includedMovement =
                projectedCalendarMovement(
                        firstRecurringTransactionId,
                        firstLogicalDate,
                        firstLogicalDate,
                        readableAccountId,
                        false,
                        null
                );

        when(userRepository.findById(currentUserId))
                .thenReturn(Optional.of(collaborator));

        when(transactionAccessService.canReadAllGroupTransactions(collaborator))
                .thenReturn(false);

        when(recurringTransactionAccessService.canReadAllGroupRecurringTransactions(collaborator))
                .thenReturn(false);

        when(accountUserRepository.findVisibleAccountIdsForUser(
                userGroupId,
                currentUserId
        )).thenReturn(List.of(readableAccountId));

        when(transactionRepository.findBaseLinkedUserTransactionsInRange(
                userGroupId,
                currentUserId,
                from,
                to,
                null
        )).thenReturn(List.of());

        when(recurringTransactionRepository.findCalendarReadableBaseByLinkedUserAccess(
                userGroupId,
                currentUserId,
                null
        )).thenReturn(List.of(firstRecurringTransaction, secondRecurringTransaction));

        when(recurringTransactionProjectedMovementBatchService.generateProjectedMovementsAcrossRange(
                anyList(),
                eq(from),
                eq(to)
        )).thenReturn(List.of(firstProjectedMovement, secondProjectedMovement));

        when(transactionRepository.findConfirmedRecurringOccurrenceKeysForRecurringTransactions(
                eq(userGroupId),
                anyCollection(),
                eq(firstLogicalDate),
                eq(firstLogicalDate)
        )).thenReturn(List.of());

        when(financeCalendarMovementMapper.fromProjectedRecurringMovement(
                firstProjectedMovement,
                false,
                null
        )).thenReturn(includedMovement);

        List<FinanceCalendarMovement> result = service.getCalendarMovements(
                currentUserId,
                request(from, to, null, null)
        );

        assertThat(result)
                .containsExactly(includedMovement);

        verify(accountUserRepository).findVisibleAccountIdsForUser(
                userGroupId,
                currentUserId
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> recurringIdsCaptor =
                ArgumentCaptor.forClass(Collection.class);

        verify(transactionRepository).findConfirmedRecurringOccurrenceKeysForRecurringTransactions(
                eq(userGroupId),
                recurringIdsCaptor.capture(),
                eq(firstLogicalDate),
                eq(firstLogicalDate)
        );

        assertThat(recurringIdsCaptor.getValue())
                .containsExactly(firstRecurringTransactionId);

        verify(financeCalendarMovementMapper, never())
                .fromProjectedRecurringMovement(eq(secondProjectedMovement), anyBoolean(), any());
    }

    @Test
    void shouldDeduplicateProjectedMovementWhenLogicalDateIsBeforeRequestedRangeButChargeDateIsInsideRange() {
        UUID currentUserId = UUID.randomUUID();
        UUID userGroupId = UUID.randomUUID();
        UUID recurringTransactionId = UUID.randomUUID();

        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);

        LocalDate logicalDate = LocalDate.of(2026, 5, 31);
        LocalDate chargeDate = LocalDate.of(2026, 6, 1);

        User owner = user(currentUserId, userGroupId);

        RecurringTransaction recurringTransaction = recurringTransaction(
                recurringTransactionId,
                LocalDate.of(2026, 1, 1),
                false,
                null
        );

        RecurringTransactionProjectedMovement projectedMovement = projectedMovement(
                recurringTransactionId,
                logicalDate,
                chargeDate
        );

        when(userRepository.findById(currentUserId))
                .thenReturn(Optional.of(owner));
        when(transactionAccessService.canReadAllGroupTransactions(owner))
                .thenReturn(true);
        when(recurringTransactionAccessService.canReadAllGroupRecurringTransactions(owner))
                .thenReturn(true);

        when(transactionRepository.findBaseGroupTransactionsInRange(
                userGroupId,
                from,
                to,
                null
        )).thenReturn(List.of());

        when(recurringTransactionRepository.findCalendarReadableBaseByUserGroup(
                userGroupId,
                null
        )).thenReturn(List.of(recurringTransaction));

        when(recurringTransactionProjectedMovementBatchService.generateProjectedMovementsAcrossRange(
                anyList(),
                eq(from),
                eq(to)
        )).thenReturn(List.of(projectedMovement));

        TransactionRepository.ConfirmedRecurringOccurrenceKeyView confirmedKey =
                confirmedKey(recurringTransactionId, logicalDate);

        when(transactionRepository.findConfirmedRecurringOccurrenceKeysForRecurringTransactions(
                eq(userGroupId),
                anyCollection(),
                eq(logicalDate),
                eq(logicalDate)
        )).thenReturn(List.of(confirmedKey));

        List<FinanceCalendarMovement> result = service.getCalendarMovements(
                currentUserId,
                request(from, to, null, null)
        );

        assertThat(result).isEmpty();

        verify(financeCalendarMovementMapper, never())
                .fromProjectedRecurringMovement(eq(projectedMovement), anyBoolean(), any());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> recurringIdsCaptor =
                ArgumentCaptor.forClass(Collection.class);

        verify(transactionRepository).findConfirmedRecurringOccurrenceKeysForRecurringTransactions(
                eq(userGroupId),
                recurringIdsCaptor.capture(),
                eq(logicalDate),
                eq(logicalDate)
        );

        assertThat(recurringIdsCaptor.getValue())
                .containsExactly(recurringTransactionId);
    }

    @Test
    void collaboratorShouldDeduplicateConfirmedRecurringOccurrenceEvenWhenConfirmedTransactionIsOnUnreadableAccount() {
        UUID currentUserId = UUID.randomUUID();
        UUID userGroupId = UUID.randomUUID();
        UUID readableAccountId = UUID.randomUUID();
        UUID recurringTransactionId = UUID.randomUUID();

        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);
        LocalDate logicalDate = LocalDate.of(2026, 6, 10);

        User collaborator = user(currentUserId, userGroupId);

        RecurringTransaction recurringTransaction = recurringTransaction(
                recurringTransactionId,
                LocalDate.of(2026, 6, 1),
                false,
                null
        );

        RecurringTransactionProjectedMovement projectedMovement = projectedMovement(
                recurringTransactionId,
                logicalDate,
                logicalDate,
                readableAccountId
        );

        TransactionRepository.ConfirmedRecurringOccurrenceKeyView confirmedKey =
                confirmedKey(recurringTransactionId, logicalDate);

        when(userRepository.findById(currentUserId))
                .thenReturn(Optional.of(collaborator));

        when(transactionAccessService.canReadAllGroupTransactions(collaborator))
                .thenReturn(false);

        when(recurringTransactionAccessService.canReadAllGroupRecurringTransactions(collaborator))
                .thenReturn(false);

        when(accountUserRepository.findVisibleAccountIdsForUser(
                userGroupId,
                currentUserId
        )).thenReturn(List.of(readableAccountId));

        when(transactionRepository.findBaseLinkedUserTransactionsInRange(
                userGroupId,
                currentUserId,
                from,
                to,
                null
        )).thenReturn(List.of());

        when(recurringTransactionRepository.findCalendarReadableBaseByLinkedUserAccess(
                userGroupId,
                currentUserId,
                null
        )).thenReturn(List.of(recurringTransaction));

        when(recurringTransactionProjectedMovementBatchService.generateProjectedMovementsAcrossRange(
                anyList(),
                eq(from),
                eq(to)
        )).thenReturn(List.of(projectedMovement));

        when(transactionRepository.findConfirmedRecurringOccurrenceKeysForRecurringTransactions(
                eq(userGroupId),
                anyCollection(),
                eq(logicalDate),
                eq(logicalDate)
        )).thenReturn(List.of(confirmedKey));

        List<FinanceCalendarMovement> result = service.getCalendarMovements(
                currentUserId,
                request(from, to, null, null)
        );

        assertThat(result).isEmpty();

        verify(financeCalendarMovementMapper, never())
                .fromProjectedRecurringMovement(eq(projectedMovement), anyBoolean(), any());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> recurringIdsCaptor =
                ArgumentCaptor.forClass(Collection.class);

        verify(transactionRepository)
                .findConfirmedRecurringOccurrenceKeysForRecurringTransactions(
                        eq(userGroupId),
                        recurringIdsCaptor.capture(),
                        eq(logicalDate),
                        eq(logicalDate)
                );

        assertThat(recurringIdsCaptor.getValue())
                .containsExactly(recurringTransactionId);
    }

    @Test
    void ownerShouldUseCalendarAccountFilterAndStillFilterProjectedMovementsAfterGeneration() {
        UUID currentUserId = UUID.randomUUID();
        UUID userGroupId = UUID.randomUUID();
        UUID requestedAccountId = UUID.randomUUID();
        UUID recurringTransactionId = UUID.randomUUID();

        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);
        LocalDate logicalDate = LocalDate.of(2026, 6, 10);

        User owner = user(currentUserId, userGroupId);

        RecurringTransaction recurringTransaction = recurringTransaction(
                recurringTransactionId,
                LocalDate.of(2026, 1, 1),
                false,
                null
        );

        RecurringTransactionProjectedMovement projectedMovement = projectedMovement(
                recurringTransactionId,
                logicalDate,
                logicalDate,
                requestedAccountId
        );

        FinanceCalendarMovement calendarMovement = projectedCalendarMovement(
                recurringTransactionId,
                logicalDate,
                logicalDate,
                requestedAccountId,
                false,
                null
        );

        when(userRepository.findById(currentUserId))
                .thenReturn(Optional.of(owner));
        when(transactionAccessService.canReadAllGroupTransactions(owner))
                .thenReturn(true);
        when(recurringTransactionAccessService.canReadAllGroupRecurringTransactions(owner))
                .thenReturn(true);
        when(transactionRepository.findBaseGroupTransactionsInRangeForAccounts(
                userGroupId,
                from,
                to,
                List.of(requestedAccountId)
        )).thenReturn(List.of());

        when(recurringTransactionRepository.findCalendarReadableBaseByUserGroupForAccounts(
                userGroupId,
                List.of(requestedAccountId)
        )).thenReturn(List.of(recurringTransaction));
        when(recurringTransactionProjectedMovementBatchService.generateProjectedMovementsAcrossRange(
                anyList(),
                eq(from),
                eq(to)
        )).thenReturn(List.of(projectedMovement));

        stubNoConfirmedKeys(userGroupId, logicalDate, logicalDate);

        when(financeCalendarMovementMapper.fromProjectedRecurringMovement(
                projectedMovement,
                false,
                null
        )).thenReturn(calendarMovement);

        List<FinanceCalendarMovement> result = service.getCalendarMovements(
                currentUserId,
                request(from, to, requestedAccountId, null)
        );

        assertThat(result)
                .containsExactly(calendarMovement);

        verify(recurringTransactionRepository).findCalendarReadableBaseByUserGroupForAccounts(
                userGroupId,
                List.of(requestedAccountId)
        );

        verify(recurringTransactionRepository, never()).findCalendarReadableBaseByUserGroup(
                any(),
                any()
        );
    }

    @Test
    void collaboratorWithRequestedAccountShouldNotLoadAllReadableAccountIds() {
        UUID currentUserId = UUID.randomUUID();
        UUID userGroupId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID recurringTransactionId = UUID.randomUUID();

        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);
        LocalDate logicalDate = LocalDate.of(2026, 6, 10);

        User collaborator = user(currentUserId, userGroupId);

        RecurringTransaction recurringTransaction = recurringTransaction(
                recurringTransactionId,
                LocalDate.of(2026, 1, 1),
                false,
                null
        );

        RecurringTransactionProjectedMovement projectedMovement = projectedMovement(
                recurringTransactionId,
                logicalDate,
                logicalDate,
                accountId
        );

        FinanceCalendarMovement calendarMovement = projectedCalendarMovement(
                recurringTransactionId,
                logicalDate,
                logicalDate,
                accountId,
                false,
                null
        );

        when(userRepository.findById(currentUserId))
                .thenReturn(Optional.of(collaborator));
        when(transactionAccessService.canReadAllGroupTransactions(collaborator))
                .thenReturn(false);
        when(recurringTransactionAccessService.canReadAllGroupRecurringTransactions(collaborator))
                .thenReturn(false);

        when(transactionRepository.findBaseLinkedUserTransactionsInRangeForAccounts(
                userGroupId,
                currentUserId,
                from,
                to,
                List.of(accountId)
        )).thenReturn(List.of());

        when(recurringTransactionRepository.findCalendarReadableBaseByLinkedUserAccessForAccounts(
                userGroupId,
                currentUserId,
                List.of(accountId)
        )).thenReturn(List.of(recurringTransaction));

        when(recurringTransactionProjectedMovementBatchService.generateProjectedMovementsAcrossRange(
                anyList(),
                eq(from),
                eq(to)
        )).thenReturn(List.of(projectedMovement));

        stubNoConfirmedKeys(userGroupId, logicalDate, logicalDate);

        when(financeCalendarMovementMapper.fromProjectedRecurringMovement(
                projectedMovement,
                false,
                null
        )).thenReturn(calendarMovement);

        List<FinanceCalendarMovement> result = service.getCalendarMovements(
                currentUserId,
                request(from, to, accountId, null)
        );

        assertThat(result)
                .containsExactly(calendarMovement);

        verify(recurringTransactionAccessService)
                .assertReadableAccountFilter(collaborator, userGroupId, accountId);

        verify(accountUserRepository, never())
                .findVisibleAccountIdsForUser(any(), any());
    }

    @Test
    void shouldFailWhenProjectedMovementLinkedAccountHasNoId() {
        UUID currentUserId = UUID.randomUUID();
        UUID userGroupId = UUID.randomUUID();
        UUID recurringTransactionId = UUID.randomUUID();

        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);
        LocalDate logicalDate = LocalDate.of(2026, 6, 10);

        User owner = user(currentUserId, userGroupId);

        RecurringTransaction recurringTransaction = recurringTransaction(
                recurringTransactionId,
                LocalDate.of(2026, 6, 1),
                false,
                null
        );

        Account accountWithoutId = mock(Account.class);
        when(accountWithoutId.getAccountId())
                .thenReturn(null);

        RecurringTransactionProjectedMovement projectedMovement =
                new RecurringTransactionProjectedMovement(
                        recurringTransactionId,
                        logicalDate,
                        logicalDate,
                        new BigDecimal("-100.00"),
                        false,
                        "Movimento previsto",
                        mock(Category.class),
                        mock(FinancialPriority.class),
                        accountWithoutId,
                        null,
                        null,
                        true,
                        true
                );

        when(userRepository.findById(currentUserId))
                .thenReturn(Optional.of(owner));
        when(transactionAccessService.canReadAllGroupTransactions(owner))
                .thenReturn(true);
        when(recurringTransactionAccessService.canReadAllGroupRecurringTransactions(owner))
                .thenReturn(true);
        when(transactionRepository.findBaseGroupTransactionsInRange(
                userGroupId,
                from,
                to,
                null
        )).thenReturn(List.of());
        when(recurringTransactionRepository.findCalendarReadableBaseByUserGroup(
                userGroupId,
                null
        )).thenReturn(List.of(recurringTransaction));
        when(recurringTransactionProjectedMovementBatchService.generateProjectedMovementsAcrossRange(
                anyList(),
                eq(from),
                eq(to)
        )).thenReturn(List.of(projectedMovement));

        assertThatThrownBy(() -> service.getCalendarMovements(
                currentUserId,
                request(from, to, null, null)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("finance.calendar.projectedMovementAccountRequired");

        verify(financeCalendarMovementMapper, never())
                .fromProjectedRecurringMovement(any(), anyBoolean(), any());
    }

    @Test
    void shouldFailWhenSimulatedRecurringTransactionHasNoSimulationGroup() {
        UUID currentUserId = UUID.randomUUID();
        UUID userGroupId = UUID.randomUUID();
        UUID recurringTransactionId = UUID.randomUUID();
        UUID simulationGroupId = UUID.randomUUID();

        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);

        User owner = user(currentUserId, userGroupId);

        RecurringTransaction recurringTransaction = recurringTransaction(
                recurringTransactionId,
                LocalDate.of(2026, 6, 1),
                true,
                null
        );

        when(userRepository.findById(currentUserId))
                .thenReturn(Optional.of(owner));
        when(transactionAccessService.canReadAllGroupTransactions(owner))
                .thenReturn(true);
        when(recurringTransactionAccessService.canReadAllGroupRecurringTransactions(owner))
                .thenReturn(true);
        when(simulationGroupRepository.findActiveIdsByUserGroupId(
                anyCollection(),
                eq(userGroupId)
        )).thenReturn(List.of(simulationGroupId));
        when(transactionRepository.findBaseAndSimulatedGroupTransactionsInRange(
                userGroupId,
                from,
                to,
                null,
                List.of(simulationGroupId)
        )).thenReturn(List.of());
        when(recurringTransactionRepository.findCalendarReadableBaseAndSimulatedByUserGroup(
                userGroupId,
                null,
                List.of(simulationGroupId)
        )).thenReturn(List.of(recurringTransaction));

        assertThatThrownBy(() -> service.getCalendarMovements(
                currentUserId,
                request(from, to, null, List.of(simulationGroupId))
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("finance.calendar.simulationGroupRequired");

        verifyNoInteractions(recurringTransactionProjectedMovementBatchService);
    }

    @Test
    void shouldDeduplicateRecurringTransactionsBeforeCreatingProjectionSeeds() {
        UUID currentUserId = UUID.randomUUID();
        UUID userGroupId = UUID.randomUUID();
        UUID recurringTransactionId = UUID.randomUUID();

        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);
        LocalDate firstPaymentDate = LocalDate.of(2026, 1, 1);

        User owner = user(currentUserId, userGroupId);

        RecurringTransaction recurringTransaction = recurringTransaction(
                recurringTransactionId,
                firstPaymentDate,
                false,
                null
        );

        when(userRepository.findById(currentUserId))
                .thenReturn(Optional.of(owner));
        when(transactionAccessService.canReadAllGroupTransactions(owner))
                .thenReturn(true);
        when(recurringTransactionAccessService.canReadAllGroupRecurringTransactions(owner))
                .thenReturn(true);
        when(transactionRepository.findBaseGroupTransactionsInRange(
                userGroupId,
                from,
                to,
                null
        )).thenReturn(List.of());

        RecurringTransaction duplicatedRecurringTransaction = recurringTransaction(
                recurringTransactionId,
                firstPaymentDate,
                false,
                null
        );

        when(recurringTransactionRepository.findCalendarReadableBaseByUserGroup(
                userGroupId,
                null
        )).thenReturn(List.of(recurringTransaction, duplicatedRecurringTransaction));
        when(recurringTransactionProjectedMovementBatchService.generateProjectedMovementsAcrossRange(
                anyList(),
                eq(from),
                eq(to)
        )).thenReturn(List.of());

        List<FinanceCalendarMovement> result = service.getCalendarMovements(
                currentUserId,
                request(from, to, null, null)
        );

        assertThat(result).isEmpty();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RecurringTransactionProjectedMovementSeed>> seedsCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(recurringTransactionProjectedMovementBatchService)
                .generateProjectedMovementsAcrossRange(seedsCaptor.capture(), eq(from), eq(to));

        assertThat(seedsCaptor.getValue())
                .singleElement()
                .satisfies(seed -> {
                    assertThat(seed.recurringTransactionId()).isEqualTo(recurringTransactionId);
                    assertThat(seed.userGroupId()).isEqualTo(userGroupId);
                    assertThat(seed.firstPaymentDate()).isEqualTo(firstPaymentDate);
                });
    }

    @Test
    void collaboratorShouldNotLoadReadableAccountIdsWhenThereAreNoProjectedMovements() {
        UUID currentUserId = UUID.randomUUID();
        UUID userGroupId = UUID.randomUUID();

        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);

        User collaborator = user(currentUserId, userGroupId);

        when(userRepository.findById(currentUserId))
                .thenReturn(Optional.of(collaborator));
        when(transactionAccessService.canReadAllGroupTransactions(collaborator))
                .thenReturn(false);
        when(recurringTransactionAccessService.canReadAllGroupRecurringTransactions(collaborator))
                .thenReturn(false);

        when(transactionRepository.findBaseLinkedUserTransactionsInRange(
                userGroupId,
                currentUserId,
                from,
                to,
                null
        )).thenReturn(List.of());

        when(recurringTransactionRepository.findCalendarReadableBaseByLinkedUserAccess(
                userGroupId,
                currentUserId,
                null
        )).thenReturn(List.of());

        List<FinanceCalendarMovement> result = service.getCalendarMovements(
                currentUserId,
                request(from, to, null, null)
        );

        assertThat(result).isEmpty();

        verify(accountUserRepository, never())
                .findVisibleAccountIdsForUser(any(), any());
    }

    @Test
    void ownerShouldNotLoadReadableAccountIdsWhenFilteringProjectedMovements() {
        UUID currentUserId = UUID.randomUUID();
        UUID userGroupId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID recurringTransactionId = UUID.randomUUID();

        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);
        LocalDate logicalDate = LocalDate.of(2026, 6, 10);

        User owner = user(currentUserId, userGroupId);

        RecurringTransaction recurringTransaction = recurringTransaction(
                recurringTransactionId,
                LocalDate.of(2026, 1, 1),
                false,
                null
        );

        RecurringTransactionProjectedMovement projectedMovement = projectedMovement(
                recurringTransactionId,
                logicalDate,
                logicalDate,
                accountId
        );

        FinanceCalendarMovement calendarMovement = projectedCalendarMovement(
                recurringTransactionId,
                logicalDate,
                logicalDate,
                accountId,
                false,
                null
        );

        when(userRepository.findById(currentUserId))
                .thenReturn(Optional.of(owner));
        when(transactionAccessService.canReadAllGroupTransactions(owner))
                .thenReturn(true);
        when(recurringTransactionAccessService.canReadAllGroupRecurringTransactions(owner))
                .thenReturn(true);
        when(transactionRepository.findBaseGroupTransactionsInRange(
                userGroupId,
                from,
                to,
                null
        )).thenReturn(List.of());
        when(recurringTransactionRepository.findCalendarReadableBaseByUserGroup(
                userGroupId,
                null
        )).thenReturn(List.of(recurringTransaction));
        when(recurringTransactionProjectedMovementBatchService.generateProjectedMovementsAcrossRange(
                anyList(),
                eq(from),
                eq(to)
        )).thenReturn(List.of(projectedMovement));

        stubNoConfirmedKeys(userGroupId, logicalDate, logicalDate);

        when(financeCalendarMovementMapper.fromProjectedRecurringMovement(
                projectedMovement,
                false,
                null
        )).thenReturn(calendarMovement);

        List<FinanceCalendarMovement> result = service.getCalendarMovements(
                currentUserId,
                request(from, to, null, null)
        );

        assertThat(result)
                .containsExactly(calendarMovement);

        verify(accountUserRepository, never())
                .findVisibleAccountIdsForUser(any(), any());
    }

    @Test
    void ownerShouldUseSingleRepositoryQueryForMultipleAccountIds() {
        UUID currentUserId = UUID.randomUUID();
        UUID userGroupId = UUID.randomUUID();
        UUID firstAccountId = UUID.randomUUID();
        UUID secondAccountId = UUID.randomUUID();

        UUID firstRecurringTransactionId = UUID.randomUUID();
        UUID secondRecurringTransactionId = UUID.randomUUID();

        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);

        User owner = user(currentUserId, userGroupId);

        RecurringTransaction firstRecurringTransaction = recurringTransaction(
                firstRecurringTransactionId,
                LocalDate.of(2026, 1, 1),
                false,
                null
        );

        RecurringTransaction secondRecurringTransaction = recurringTransaction(
                secondRecurringTransactionId,
                LocalDate.of(2026, 1, 1),
                false,
                null
        );

        when(userRepository.findById(currentUserId))
                .thenReturn(Optional.of(owner));
        when(transactionAccessService.canReadAllGroupTransactions(owner))
                .thenReturn(true);
        when(recurringTransactionAccessService.canReadAllGroupRecurringTransactions(owner))
                .thenReturn(true);

        when(transactionRepository.findBaseGroupTransactionsInRangeForAccounts(
                userGroupId,
                from,
                to,
                List.of(firstAccountId, secondAccountId)
        )).thenReturn(List.of());

        when(recurringTransactionRepository.findCalendarReadableBaseByUserGroupForAccounts(
                userGroupId,
                List.of(firstAccountId, secondAccountId)
        )).thenReturn(List.of(firstRecurringTransaction, secondRecurringTransaction));

        when(recurringTransactionProjectedMovementBatchService.generateProjectedMovementsAcrossRange(
                anyList(),
                eq(from),
                eq(to)
        )).thenReturn(List.of());

        List<FinanceCalendarMovement> result = service.getCalendarMovements(
                currentUserId,
                requestForAccounts(
                        from,
                        to,
                        List.of(firstAccountId, secondAccountId),
                        null
                )
        );

        assertThat(result).isEmpty();

        verify(recurringTransactionAccessService)
                .assertReadableAccountFilter(owner, userGroupId, firstAccountId);
        verify(recurringTransactionAccessService)
                .assertReadableAccountFilter(owner, userGroupId, secondAccountId);

        verify(transactionRepository).findBaseGroupTransactionsInRangeForAccounts(
                userGroupId,
                from,
                to,
                List.of(firstAccountId, secondAccountId)
        );

        verify(recurringTransactionRepository).findCalendarReadableBaseByUserGroupForAccounts(
                userGroupId,
                List.of(firstAccountId, secondAccountId)
        );

        verify(transactionRepository, never()).findBaseGroupTransactionsInRange(
                any(),
                any(),
                any(),
                any()
        );

        verify(recurringTransactionRepository, never()).findCalendarReadableBaseByUserGroup(
                any(),
                any()
        );
    }

    @Test
    void collaboratorShouldUseSingleRepositoryQueryForMultipleAccountIds() {
        UUID currentUserId = UUID.randomUUID();
        UUID userGroupId = UUID.randomUUID();
        UUID firstAccountId = UUID.randomUUID();
        UUID secondAccountId = UUID.randomUUID();

        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);

        User collaborator = user(currentUserId, userGroupId);

        when(userRepository.findById(currentUserId))
                .thenReturn(Optional.of(collaborator));
        when(transactionAccessService.canReadAllGroupTransactions(collaborator))
                .thenReturn(false);
        when(recurringTransactionAccessService.canReadAllGroupRecurringTransactions(collaborator))
                .thenReturn(false);

        when(transactionRepository.findBaseLinkedUserTransactionsInRangeForAccounts(
                userGroupId,
                currentUserId,
                from,
                to,
                List.of(firstAccountId, secondAccountId)
        )).thenReturn(List.of());

        when(recurringTransactionRepository.findCalendarReadableBaseByLinkedUserAccessForAccounts(
                userGroupId,
                currentUserId,
                List.of(firstAccountId, secondAccountId)
        )).thenReturn(List.of());

        List<FinanceCalendarMovement> result = service.getCalendarMovements(
                currentUserId,
                requestForAccounts(
                        from,
                        to,
                        List.of(firstAccountId, secondAccountId),
                        null
                )
        );

        assertThat(result).isEmpty();

        verify(recurringTransactionAccessService)
                .assertReadableAccountFilter(collaborator, userGroupId, firstAccountId);
        verify(recurringTransactionAccessService)
                .assertReadableAccountFilter(collaborator, userGroupId, secondAccountId);

        verify(transactionRepository).findBaseLinkedUserTransactionsInRangeForAccounts(
                userGroupId,
                currentUserId,
                from,
                to,
                List.of(firstAccountId, secondAccountId)
        );

        verify(recurringTransactionRepository).findCalendarReadableBaseByLinkedUserAccessForAccounts(
                userGroupId,
                currentUserId,
                List.of(firstAccountId, secondAccountId)
        );

        verify(transactionRepository, never()).findBaseLinkedUserTransactionsInRange(
                any(),
                any(),
                any(),
                any(),
                any()
        );

        verify(recurringTransactionRepository, never()).findCalendarReadableBaseByLinkedUserAccess(
                any(),
                any(),
                any()
        );
    }


    private void stubNoConfirmedKeys(
            UUID userGroupId,
            LocalDate from,
            LocalDate to
    ) {
        when(transactionRepository.findConfirmedRecurringOccurrenceKeysForRecurringTransactions(
                eq(userGroupId),
                anyCollection(),
                eq(from),
                eq(to)
        )).thenReturn(List.of());
    }
}