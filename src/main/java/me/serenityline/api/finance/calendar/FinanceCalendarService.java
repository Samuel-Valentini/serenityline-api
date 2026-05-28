package me.serenityline.api.finance.calendar;

import me.serenityline.api.common.error.ResourceNotFoundException;
import me.serenityline.api.finance.account.entity.Account;
import me.serenityline.api.finance.account.repository.AccountUserRepository;
import me.serenityline.api.finance.common.FinanceProperties;
import me.serenityline.api.finance.simulation.entity.SimulationGroup;
import me.serenityline.api.finance.simulation.repository.SimulationGroupRepository;
import me.serenityline.api.finance.transaction.entity.RecurringTransaction;
import me.serenityline.api.finance.transaction.entity.Transaction;
import me.serenityline.api.finance.transaction.repository.RecurringTransactionRepository;
import me.serenityline.api.finance.transaction.repository.TransactionRepository;
import me.serenityline.api.finance.transaction.service.*;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FinanceCalendarService {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final RecurringTransactionRepository recurringTransactionRepository;
    private final SimulationGroupRepository simulationGroupRepository;
    private final AccountUserRepository accountUserRepository;
    private final TransactionAccessService transactionAccessService;
    private final RecurringTransactionAccessService recurringTransactionAccessService;
    private final RecurringTransactionProjectedMovementBatchService recurringTransactionProjectedMovementBatchService;
    private final FinanceCalendarMovementMapper financeCalendarMovementMapper;
    private final FinanceCalendarProperties financeCalendarProperties;
    private final FinanceProperties financeProperties;

    public FinanceCalendarService(
            UserRepository userRepository,
            TransactionRepository transactionRepository,
            RecurringTransactionRepository recurringTransactionRepository,
            SimulationGroupRepository simulationGroupRepository,
            AccountUserRepository accountUserRepository,
            TransactionAccessService transactionAccessService,
            RecurringTransactionAccessService recurringTransactionAccessService,
            RecurringTransactionProjectedMovementBatchService recurringTransactionProjectedMovementBatchService,
            FinanceCalendarMovementMapper financeCalendarMovementMapper,
            FinanceCalendarProperties financeCalendarProperties,
            FinanceProperties financeProperties
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.transactionRepository = Objects.requireNonNull(transactionRepository, "transactionRepository");
        this.recurringTransactionRepository = Objects.requireNonNull(recurringTransactionRepository, "recurringTransactionRepository");
        this.simulationGroupRepository = Objects.requireNonNull(simulationGroupRepository, "simulationGroupRepository");
        this.accountUserRepository = Objects.requireNonNull(accountUserRepository, "accountUserRepository");
        this.transactionAccessService = Objects.requireNonNull(transactionAccessService, "transactionAccessService");
        this.recurringTransactionAccessService = Objects.requireNonNull(recurringTransactionAccessService, "recurringTransactionAccessService");
        this.recurringTransactionProjectedMovementBatchService = Objects.requireNonNull(recurringTransactionProjectedMovementBatchService, "recurringTransactionProjectedMovementBatchService");
        this.financeCalendarMovementMapper = Objects.requireNonNull(financeCalendarMovementMapper, "financeCalendarMovementMapper");
        this.financeCalendarProperties = Objects.requireNonNull(financeCalendarProperties, "financeCalendarProperties");
        this.financeProperties = Objects.requireNonNull(financeProperties, "financeProperties");
    }

    @Transactional(readOnly = true)
    public List<FinanceCalendarMovement> getCalendarMovements(
            UUID currentUserId,
            FinanceCalendarSearchRequest request
    ) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        Objects.requireNonNull(request, "request");

        validateRange(request.from(), request.to());

        List<UUID> simulationGroupIds = normalizeSimulationGroupIds(
                request.simulationGroupIds()
        );

        List<UUID> accountIds = normalizeAccountIds(
                request.accountIds()
        );

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("user.notFound"));

        UUID userGroupId = currentUser.getUserGroup().getUserGroupId();

        boolean canReadAllGroupTransactions =
                transactionAccessService.canReadAllGroupTransactions(currentUser);

        boolean canReadAllGroupRecurringTransactions =
                recurringTransactionAccessService.canReadAllGroupRecurringTransactions(currentUser);

        assertReadableAccountFilters(
                currentUser,
                userGroupId,
                accountIds
        );

        assertReadableSimulationGroups(
                currentUser,
                userGroupId,
                simulationGroupIds,
                canReadAllGroupTransactions
        );

        List<Transaction> persistedTransactions = findPersistedTransactions(
                currentUser,
                userGroupId,
                request.from(),
                request.to(),
                accountIds,
                simulationGroupIds,
                canReadAllGroupTransactions
        );

        List<RecurringTransaction> recurringTransactions = findReadableRecurringTransactions(
                currentUser,
                userGroupId,
                accountIds,
                simulationGroupIds,
                canReadAllGroupRecurringTransactions
        );

        Map<UUID, RecurringTransaction> recurringTransactionById =
                recurringTransactions.stream()
                        .collect(Collectors.toMap(
                                RecurringTransaction::getRecurringTransactionId,
                                recurringTransaction -> recurringTransaction,
                                (first, second) -> first,
                                LinkedHashMap::new
                        ));

        Map<UUID, RecurringTransactionProjectionContext> recurringContextById =
                recurringTransactionById.values()
                        .stream()
                        .collect(Collectors.toMap(
                                RecurringTransaction::getRecurringTransactionId,
                                this::toProjectionContext,
                                (first, second) -> first,
                                LinkedHashMap::new
                        ));

        List<RecurringTransactionProjectedMovementSeed> seeds =
                recurringTransactionById.values()
                        .stream()
                        .map(recurringTransaction -> new RecurringTransactionProjectedMovementSeed(
                                recurringTransaction.getRecurringTransactionId(),
                                userGroupId,
                                recurringTransaction.getRecurringTransactionFirstPaymentDate()
                        ))
                        .toList();

        List<RecurringTransactionProjectedMovement> projectedMovements =
                recurringTransactionProjectedMovementBatchService.generateProjectedMovements(
                        seeds,
                        request.from(),
                        request.to()
                );

        assertProjectedMovementsHaveReadableRecurringContext(
                projectedMovements,
                recurringContextById
        );

        Set<UUID> readableRecurringAccountIds = projectedMovements.isEmpty()
                ? Set.of()
                : readableProjectedAccountIds(
                currentUser,
                userGroupId,
                accountIds,
                canReadAllGroupRecurringTransactions
        );

        Set<UUID> requestedAccountIdSet = Set.copyOf(accountIds);

        List<RecurringTransactionProjectedMovement> visibleProjectedMovements =
                projectedMovements.stream()
                        .filter(projectedMovement -> canReadProjectedMovementAccount(
                                projectedMovement,
                                requestedAccountIdSet,
                                canReadAllGroupRecurringTransactions,
                                readableRecurringAccountIds
                        ))
                        .toList();

        Set<ConfirmedRecurringOccurrenceKey> confirmedRecurringOccurrenceKeys =
                findConfirmedRecurringOccurrenceKeysForProjectedMovements(
                        userGroupId,
                        visibleProjectedMovements,
                        recurringContextById.keySet()
                );

        List<FinanceCalendarMovement> movements = new ArrayList<>();

        persistedTransactions.stream()
                .map(financeCalendarMovementMapper::fromPersistedTransaction)
                .forEach(movements::add);

        visibleProjectedMovements.stream()
                .filter(projectedMovement -> !isAlreadyConfirmed(
                        projectedMovement,
                        confirmedRecurringOccurrenceKeys
                ))
                .map(projectedMovement -> toFinanceCalendarMovement(
                        projectedMovement,
                        recurringContextById
                ))
                .forEach(movements::add);

        return movements.stream()
                .sorted(Comparator
                        .comparing(FinanceCalendarMovement::chargeDate)
                        .thenComparing(FinanceCalendarMovement::logicalDate)
                        .thenComparing(FinanceCalendarMovement::type)
                        .thenComparing(this::stableMovementId))
                .toList();
    }

    private List<Transaction> findPersistedTransactions(
            User currentUser,
            UUID userGroupId,
            LocalDate from,
            LocalDate to,
            List<UUID> accountIds,
            List<UUID> simulationGroupIds,
            boolean canReadAllGroupTransactions
    ) {
        if (accountIds.isEmpty()) {
            return findPersistedTransactionsForAccount(
                    currentUser,
                    userGroupId,
                    from,
                    to,
                    null,
                    simulationGroupIds,
                    canReadAllGroupTransactions
            );
        }

        if (canReadAllGroupTransactions) {
            if (simulationGroupIds.isEmpty()) {
                return transactionRepository.findBaseGroupTransactionsInRangeForAccounts(
                        userGroupId,
                        from,
                        to,
                        accountIds
                );
            }

            return transactionRepository.findBaseAndSimulatedGroupTransactionsInRangeForAccounts(
                    userGroupId,
                    from,
                    to,
                    accountIds,
                    simulationGroupIds
            );
        }

        if (simulationGroupIds.isEmpty()) {
            return transactionRepository.findBaseLinkedUserTransactionsInRangeForAccounts(
                    userGroupId,
                    currentUser.getUserId(),
                    from,
                    to,
                    accountIds
            );
        }

        return transactionRepository.findBaseAndSimulatedLinkedUserTransactionsInRangeForAccounts(
                userGroupId,
                currentUser.getUserId(),
                from,
                to,
                accountIds,
                simulationGroupIds
        );
    }

    private List<Transaction> findPersistedTransactionsForAccount(
            User currentUser,
            UUID userGroupId,
            LocalDate from,
            LocalDate to,
            UUID accountId,
            List<UUID> simulationGroupIds,
            boolean canReadAllGroupTransactions
    ) {
        if (canReadAllGroupTransactions) {
            if (simulationGroupIds.isEmpty()) {
                return transactionRepository.findBaseGroupTransactionsInRange(
                        userGroupId,
                        from,
                        to,
                        accountId
                );
            }

            return transactionRepository.findBaseAndSimulatedGroupTransactionsInRange(
                    userGroupId,
                    from,
                    to,
                    accountId,
                    simulationGroupIds
            );
        }

        if (simulationGroupIds.isEmpty()) {
            return transactionRepository.findBaseLinkedUserTransactionsInRange(
                    userGroupId,
                    currentUser.getUserId(),
                    from,
                    to,
                    accountId
            );
        }

        return transactionRepository.findBaseAndSimulatedLinkedUserTransactionsInRange(
                userGroupId,
                currentUser.getUserId(),
                from,
                to,
                accountId,
                simulationGroupIds
        );
    }

    private List<RecurringTransaction> findReadableRecurringTransactions(
            User currentUser,
            UUID userGroupId,
            List<UUID> accountIds,
            List<UUID> simulationGroupIds,
            boolean canReadAllGroupRecurringTransactions
    ) {
        if (accountIds.isEmpty()) {
            return findReadableRecurringTransactionsForAccount(
                    currentUser,
                    userGroupId,
                    null,
                    simulationGroupIds,
                    canReadAllGroupRecurringTransactions
            );
        }

        if (canReadAllGroupRecurringTransactions) {
            if (simulationGroupIds.isEmpty()) {
                return recurringTransactionRepository.findCalendarReadableBaseByUserGroupForAccounts(
                        userGroupId,
                        accountIds
                );
            }

            return recurringTransactionRepository.findCalendarReadableBaseAndSimulatedByUserGroupForAccounts(
                    userGroupId,
                    accountIds,
                    simulationGroupIds
            );
        }

        if (simulationGroupIds.isEmpty()) {
            return recurringTransactionRepository.findCalendarReadableBaseByLinkedUserAccessForAccounts(
                    userGroupId,
                    currentUser.getUserId(),
                    accountIds
            );
        }

        return recurringTransactionRepository.findCalendarReadableBaseAndSimulatedByLinkedUserAccessForAccounts(
                userGroupId,
                currentUser.getUserId(),
                accountIds,
                simulationGroupIds
        );
    }

    private List<RecurringTransaction> findReadableRecurringTransactionsForAccount(
            User currentUser,
            UUID userGroupId,
            UUID accountId,
            List<UUID> simulationGroupIds,
            boolean canReadAllGroupRecurringTransactions
    ) {
        if (canReadAllGroupRecurringTransactions) {
            if (simulationGroupIds.isEmpty()) {
                return recurringTransactionRepository.findCalendarReadableBaseByUserGroup(
                        userGroupId,
                        accountId
                );
            }

            return recurringTransactionRepository.findCalendarReadableBaseAndSimulatedByUserGroup(
                    userGroupId,
                    accountId,
                    simulationGroupIds
            );
        }

        if (simulationGroupIds.isEmpty()) {
            return recurringTransactionRepository.findCalendarReadableBaseByLinkedUserAccess(
                    userGroupId,
                    currentUser.getUserId(),
                    accountId
            );
        }

        return recurringTransactionRepository.findCalendarReadableBaseAndSimulatedByLinkedUserAccess(
                userGroupId,
                currentUser.getUserId(),
                accountId,
                simulationGroupIds
        );
    }


    private void assertReadableSimulationGroups(
            User currentUser,
            UUID userGroupId,
            List<UUID> simulationGroupIds,
            boolean canReadAllGroupSimulationGroups
    ) {
        if (simulationGroupIds.isEmpty()) {
            return;
        }

        List<UUID> foundIds;

        if (canReadAllGroupSimulationGroups) {
            foundIds = simulationGroupRepository.findActiveIdsByUserGroupId(
                    simulationGroupIds,
                    userGroupId
            );
        } else {
            foundIds = simulationGroupRepository.findActiveIdsReadableByLinkedUser(
                    simulationGroupIds,
                    userGroupId,
                    currentUser.getUserId()
            );
        }

        if (!new HashSet<>(foundIds).containsAll(simulationGroupIds)) {
            throw new ResourceNotFoundException("finance.simulationGroup.notFound");
        }
    }

    private Set<UUID> readableProjectedAccountIds(
            User currentUser,
            UUID userGroupId,
            List<UUID> requestedAccountIds,
            boolean canReadAllGroupRecurringTransactions
    ) {
        if (canReadAllGroupRecurringTransactions) {
            return Set.of();
        }

        if (!requestedAccountIds.isEmpty()) {
            return Set.copyOf(requestedAccountIds);
        }

        return Set.copyOf(accountUserRepository.findVisibleAccountIdsForUser(
                userGroupId,
                currentUser.getUserId()
        ));
    }

    private List<UUID> normalizeSimulationGroupIds(
            List<UUID> simulationGroupIds
    ) {
        if (simulationGroupIds == null || simulationGroupIds.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<UUID> normalized = new LinkedHashSet<>();

        for (UUID simulationGroupId : simulationGroupIds) {
            if (simulationGroupId == null) {
                throw new IllegalArgumentException("finance.simulationGroup.idRequired");
            }

            normalized.add(simulationGroupId);
        }

        if (normalized.size() > financeProperties.getMaxSimulationGroupIds()) {
            throw new IllegalArgumentException("finance.simulationGroup.idsTooMany");
        }

        return List.copyOf(normalized);
    }

    private boolean isAlreadyConfirmed(
            RecurringTransactionProjectedMovement projectedMovement,
            Set<ConfirmedRecurringOccurrenceKey> confirmedRecurringOccurrenceKeys
    ) {
        return confirmedRecurringOccurrenceKeys.contains(
                new ConfirmedRecurringOccurrenceKey(
                        projectedMovement.recurringTransactionId(),
                        projectedMovement.logicalDate()
                )
        );
    }

    private FinanceCalendarMovement toFinanceCalendarMovement(
            RecurringTransactionProjectedMovement projectedMovement,
            Map<UUID, RecurringTransactionProjectionContext> recurringContextById
    ) {
        RecurringTransactionProjectionContext context =
                recurringContextById.get(projectedMovement.recurringTransactionId());

        if (context == null) {
            throw new IllegalStateException("finance.calendar.recurringContextMissing");
        }

        return financeCalendarMovementMapper.fromProjectedRecurringMovement(
                projectedMovement,
                context.simulated(),
                context.simulationGroupId()
        );
    }

    private RecurringTransactionProjectionContext toProjectionContext(
            RecurringTransaction recurringTransaction
    ) {
        boolean simulated = recurringTransaction.isRecurringTransactionIsSimulated();

        if (!simulated) {
            return new RecurringTransactionProjectionContext(false, null);
        }

        SimulationGroup simulationGroup = recurringTransaction.getSimulationGroup();

        if (simulationGroup == null || simulationGroup.getSimulationGroupId() == null) {
            throw new IllegalStateException("finance.calendar.simulationGroupRequired");
        }

        UUID simulationGroupId = simulationGroup.getSimulationGroupId();

        return new RecurringTransactionProjectionContext(
                true,
                simulationGroupId
        );
    }

    private UUID stableMovementId(FinanceCalendarMovement movement) {
        if (movement.transactionId() != null) {
            return movement.transactionId();
        }

        return movement.recurringTransactionId();
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null) {
            throw new IllegalArgumentException("finance.calendar.fromRequired");
        }

        if (to == null) {
            throw new IllegalArgumentException("finance.calendar.toRequired");
        }

        if (to.isBefore(from)) {
            throw new IllegalArgumentException("finance.calendar.dateRangeInvalid");
        }

        if (from.plusDays(financeCalendarProperties.getMaxRangeDays()).isBefore(to)) {
            throw new IllegalArgumentException("finance.calendar.dateRangeTooLarge");
        }
    }

    private Set<ConfirmedRecurringOccurrenceKey> findConfirmedRecurringOccurrenceKeysForProjectedMovements(
            UUID userGroupId,
            List<RecurringTransactionProjectedMovement> projectedMovements,
            Set<UUID> authorizedRecurringTransactionIds
    ) {
        if (projectedMovements.isEmpty() || authorizedRecurringTransactionIds.isEmpty()) {
            return Set.of();
        }

        List<RecurringTransactionProjectedMovement> authorizedProjectedMovements =
                projectedMovements.stream()
                        .filter(projectedMovement -> authorizedRecurringTransactionIds.contains(
                                projectedMovement.recurringTransactionId()
                        ))
                        .toList();

        if (authorizedProjectedMovements.isEmpty()) {
            return Set.of();
        }

        Set<UUID> projectedRecurringTransactionIds =
                authorizedProjectedMovements.stream()
                        .map(RecurringTransactionProjectedMovement::recurringTransactionId)
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        LocalDate minLogicalDate = authorizedProjectedMovements.stream()
                .map(RecurringTransactionProjectedMovement::logicalDate)
                .min(LocalDate::compareTo)
                .orElseThrow();

        LocalDate maxLogicalDate = authorizedProjectedMovements.stream()
                .map(RecurringTransactionProjectedMovement::logicalDate)
                .max(LocalDate::compareTo)
                .orElseThrow();

        return transactionRepository.findConfirmedRecurringOccurrenceKeysForRecurringTransactions(
                        userGroupId,
                        projectedRecurringTransactionIds,
                        minLogicalDate,
                        maxLogicalDate
                )
                .stream()
                .map(row -> new ConfirmedRecurringOccurrenceKey(
                        row.getRecurringTransactionId(),
                        row.getRecurringTransactionLogicalDate()
                ))
                .collect(Collectors.toUnmodifiableSet());
    }

    private void assertProjectedMovementsHaveReadableRecurringContext(
            List<RecurringTransactionProjectedMovement> projectedMovements,
            Map<UUID, RecurringTransactionProjectionContext> recurringContextById
    ) {
        for (RecurringTransactionProjectedMovement projectedMovement : projectedMovements) {
            if (!recurringContextById.containsKey(projectedMovement.recurringTransactionId())) {
                throw new IllegalStateException("finance.calendar.recurringContextMissing");
            }
        }
    }

    private boolean canReadProjectedMovementAccount(
            RecurringTransactionProjectedMovement projectedMovement,
            Set<UUID> requestedAccountIds,
            boolean canReadAllGroupRecurringTransactions,
            Set<UUID> readableAccountIds
    ) {
        Account linkedAccount = projectedMovement.linkedAccount();

        if (linkedAccount == null || linkedAccount.getAccountId() == null) {
            throw new IllegalStateException("finance.calendar.projectedMovementAccountRequired");
        }

        UUID projectedAccountId = linkedAccount.getAccountId();

        if (!requestedAccountIds.isEmpty() && !requestedAccountIds.contains(projectedAccountId)) {
            return false;
        }

        return canReadAllGroupRecurringTransactions || readableAccountIds.contains(projectedAccountId);
    }

    private List<UUID> normalizeAccountIds(
            List<UUID> accountIds
    ) {
        if (accountIds == null || accountIds.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<UUID> normalized = new LinkedHashSet<>();

        for (UUID accountId : accountIds) {
            if (accountId == null) {
                throw new IllegalArgumentException("finance.account.idRequired");
            }

            normalized.add(accountId);
        }

        if (normalized.size() > financeCalendarProperties.getMaxAccountIds()) {
            throw new IllegalArgumentException("finance.calendar.accountIdsTooMany");
        }

        return List.copyOf(normalized);
    }

    private void assertReadableAccountFilters(
            User currentUser,
            UUID userGroupId,
            List<UUID> accountIds
    ) {
        if (accountIds.isEmpty()) {
            recurringTransactionAccessService.assertReadableAccountFilter(
                    currentUser,
                    userGroupId,
                    null
            );
            return;
        }

        for (UUID accountId : accountIds) {
            recurringTransactionAccessService.assertReadableAccountFilter(
                    currentUser,
                    userGroupId,
                    accountId
            );
        }
    }

    private record RecurringTransactionProjectionContext(
            boolean simulated,
            UUID simulationGroupId
    ) {
    }

    private record ConfirmedRecurringOccurrenceKey(
            UUID recurringTransactionId,
            LocalDate logicalDate
    ) {
    }
}