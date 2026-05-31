package me.serenityline.api.finance.bucket.service;

import me.serenityline.api.finance.bucket.entity.Bucket;
import me.serenityline.api.finance.calendar.FinanceCalendarProperties;
import me.serenityline.api.finance.transaction.entity.RecurringTransaction;
import me.serenityline.api.finance.transaction.repository.RecurringTransactionRepository;
import me.serenityline.api.finance.transaction.repository.TransactionRepository;
import me.serenityline.api.finance.transaction.service.RecurringTransactionProjectedMovement;
import me.serenityline.api.finance.transaction.service.RecurringTransactionProjectedMovementBatchService;
import me.serenityline.api.finance.transaction.service.RecurringTransactionProjectedMovementSeed;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BucketBalanceCalculator {

    private static final long PROJECTED_MOVEMENT_BOUNDARY_DAYS = 7L;

    private final TransactionRepository transactionRepository;
    private final RecurringTransactionRepository recurringTransactionRepository;
    private final RecurringTransactionProjectedMovementBatchService recurringTransactionProjectedMovementBatchService;
    private final FinanceCalendarProperties financeCalendarProperties;
    private final Clock clock;

    public BucketBalanceCalculator(
            TransactionRepository transactionRepository,
            RecurringTransactionRepository recurringTransactionRepository,
            RecurringTransactionProjectedMovementBatchService recurringTransactionProjectedMovementBatchService,
            FinanceCalendarProperties financeCalendarProperties,
            Clock clock
    ) {
        this.transactionRepository = Objects.requireNonNull(transactionRepository, "transactionRepository");
        this.recurringTransactionRepository = Objects.requireNonNull(
                recurringTransactionRepository,
                "recurringTransactionRepository"
        );
        this.recurringTransactionProjectedMovementBatchService = Objects.requireNonNull(
                recurringTransactionProjectedMovementBatchService,
                "recurringTransactionProjectedMovementBatchService"
        );
        this.financeCalendarProperties = Objects.requireNonNull(
                financeCalendarProperties,
                "financeCalendarProperties"
        );
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateCurrentBalance(UUID bucketId, UUID userGroupId) {
        return calculateBalanceAt(
                bucketId,
                userGroupId,
                LocalDate.now(clock)
        );
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateBalanceAt(
            UUID bucketId,
            UUID userGroupId,
            LocalDate asOfDate
    ) {
        Objects.requireNonNull(bucketId, "bucketId");
        Objects.requireNonNull(userGroupId, "userGroupId");
        Objects.requireNonNull(asOfDate, "asOfDate");

        BigDecimal persistedBalance = transactionRepository.calculatePersistedBaseBucketBalanceAt(
                bucketId,
                userGroupId,
                asOfDate
        );

        if (persistedBalance == null) {
            persistedBalance = BigDecimal.ZERO;
        }

        BigDecimal projectedBalance = calculateProjectedRecurringBalanceAt(
                bucketId,
                userGroupId,
                asOfDate
        );

        return persistedBalance.add(projectedBalance);
    }

    private BigDecimal calculateProjectedRecurringBalanceAt(
            UUID bucketId,
            UUID userGroupId,
            LocalDate asOfDate
    ) {
        LocalDate latestRelevantDate = asOfDate.plusDays(PROJECTED_MOVEMENT_BOUNDARY_DAYS);

        List<RecurringTransaction> recurringTransactions =
                recurringTransactionRepository.findBaseOpenRecurringTransactionsEverLinkedToBucket(
                        userGroupId,
                        bucketId,
                        latestRelevantDate
                );

        if (recurringTransactions.isEmpty()) {
            return BigDecimal.ZERO;
        }

        LocalDate from = recurringTransactions.stream()
                .map(RecurringTransaction::getRecurringTransactionFirstPaymentDate)
                .min(LocalDate::compareTo)
                .orElse(asOfDate)
                .minusDays(PROJECTED_MOVEMENT_BOUNDARY_DAYS);

        if (from.isAfter(asOfDate)) {
            return BigDecimal.ZERO;
        }

        List<RecurringTransactionProjectedMovementSeed> seeds = recurringTransactions.stream()
                .map(recurringTransaction -> new RecurringTransactionProjectedMovementSeed(
                        recurringTransaction.getRecurringTransactionId(),
                        userGroupId,
                        recurringTransaction.getRecurringTransactionFirstPaymentDate()
                ))
                .toList();

        List<RecurringTransactionProjectedMovement> projectedBucketMovements =
                generateProjectedMovementsInChunks(seeds, from, asOfDate)
                        .stream()
                        .filter(projectedMovement -> isForBucket(projectedMovement, bucketId))
                        .toList();

        if (projectedBucketMovements.isEmpty()) {
            return BigDecimal.ZERO;
        }

        Set<ConfirmedRecurringOccurrenceKey> confirmedOccurrences =
                findConfirmedOccurrences(
                        userGroupId,
                        projectedBucketMovements
                );

        return projectedBucketMovements.stream()
                .filter(projectedMovement -> !isAlreadyConfirmed(
                        projectedMovement,
                        confirmedOccurrences
                ))
                .map(this::bucketDelta)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<RecurringTransactionProjectedMovement> generateProjectedMovementsInChunks(
            List<RecurringTransactionProjectedMovementSeed> seeds,
            LocalDate from,
            LocalDate to
    ) {
        int chunkSize = financeCalendarProperties.getMaxRecurringTransactions();

        if (chunkSize <= 0) {
            throw new IllegalStateException("finance.recurringTransaction.maxBatchSizeInvalid");
        }

        List<RecurringTransactionProjectedMovement> movements = new ArrayList<>();

        for (int start = 0; start < seeds.size(); start += chunkSize) {
            int end = Math.min(start + chunkSize, seeds.size());

            movements.addAll(
                    recurringTransactionProjectedMovementBatchService.generateProjectedMovementsAcrossRange(
                            seeds.subList(start, end),
                            from,
                            to
                    )
            );
        }

        return movements;
    }

    private boolean isForBucket(
            RecurringTransactionProjectedMovement projectedMovement,
            UUID bucketId
    ) {
        Bucket linkedBucket = projectedMovement.linkedBucket();

        return linkedBucket != null
                && bucketId.equals(linkedBucket.getBucketId());
    }

    private Set<ConfirmedRecurringOccurrenceKey> findConfirmedOccurrences(
            UUID userGroupId,
            List<RecurringTransactionProjectedMovement> projectedMovements
    ) {
        Set<UUID> recurringTransactionIds = projectedMovements.stream()
                .map(RecurringTransactionProjectedMovement::recurringTransactionId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        LocalDate minLogicalDate = projectedMovements.stream()
                .map(RecurringTransactionProjectedMovement::logicalDate)
                .min(LocalDate::compareTo)
                .orElseThrow();

        LocalDate maxLogicalDate = projectedMovements.stream()
                .map(RecurringTransactionProjectedMovement::logicalDate)
                .max(LocalDate::compareTo)
                .orElseThrow();

        return transactionRepository.findConfirmedRecurringOccurrenceKeysForRecurringTransactions(
                        userGroupId,
                        recurringTransactionIds,
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

    private boolean isAlreadyConfirmed(
            RecurringTransactionProjectedMovement projectedMovement,
            Set<ConfirmedRecurringOccurrenceKey> confirmedOccurrences
    ) {
        return confirmedOccurrences.contains(
                new ConfirmedRecurringOccurrenceKey(
                        projectedMovement.recurringTransactionId(),
                        projectedMovement.logicalDate()
                )
        );
    }

    private BigDecimal bucketDelta(RecurringTransactionProjectedMovement projectedMovement) {
        if (!projectedMovement.affectsAccountBalance()
                && projectedMovement.affectsSerenityline()) {
            return projectedMovement.amount().negate();
        }

        return projectedMovement.amount();
    }

    private record ConfirmedRecurringOccurrenceKey(
            UUID recurringTransactionId,
            LocalDate logicalDate
    ) {
    }
}