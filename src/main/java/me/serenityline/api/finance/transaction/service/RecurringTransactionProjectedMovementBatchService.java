package me.serenityline.api.finance.transaction.service;

import me.serenityline.api.finance.calendar.*;
import me.serenityline.api.finance.transaction.entity.RecurringTransaction;
import me.serenityline.api.finance.transaction.entity.RecurringTransactionDetailsHistory;
import me.serenityline.api.finance.transaction.entity.RecurringTransactionHistory;
import me.serenityline.api.finance.transaction.repository.RecurringTransactionDetailsHistoryRepository;
import me.serenityline.api.finance.transaction.repository.RecurringTransactionHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecurringTransactionProjectedMovementBatchService {

    private static final long CHUNK_BOUNDARY_OVERLAP_DAYS = 7L;

    private final RecurringTransactionHistoryRepository recurringTransactionHistoryRepository;
    private final RecurringTransactionDetailsHistoryRepository recurringTransactionDetailsHistoryRepository;
    private final RecurringTransactionRuleSnapshotMapper recurringTransactionRuleSnapshotMapper;
    private final RecurringTransactionAccountCurrencySnapshotMapper recurringTransactionAccountCurrencySnapshotMapper;
    private final RecurringTransactionProjectedMovementAssembler recurringTransactionProjectedMovementAssembler;
    private final CurrencyBusinessCalendarResolver currencyBusinessCalendarResolver;
    private final RecurringTransactionOccurrenceGenerator recurringTransactionOccurrenceGenerator;
    private final FinanceCalendarProperties financeCalendarProperties;

    public RecurringTransactionProjectedMovementBatchService(
            RecurringTransactionHistoryRepository recurringTransactionHistoryRepository,
            RecurringTransactionDetailsHistoryRepository recurringTransactionDetailsHistoryRepository,
            RecurringTransactionRuleSnapshotMapper recurringTransactionRuleSnapshotMapper,
            RecurringTransactionAccountCurrencySnapshotMapper recurringTransactionAccountCurrencySnapshotMapper,
            RecurringTransactionProjectedMovementAssembler recurringTransactionProjectedMovementAssembler,
            CurrencyBusinessCalendarResolver currencyBusinessCalendarResolver,
            RecurringTransactionOccurrenceGenerator recurringTransactionOccurrenceGenerator,
            FinanceCalendarProperties financeCalendarProperties
    ) {
        this.recurringTransactionHistoryRepository = Objects.requireNonNull(
                recurringTransactionHistoryRepository,
                "recurringTransactionHistoryRepository"
        );
        this.recurringTransactionDetailsHistoryRepository = Objects.requireNonNull(
                recurringTransactionDetailsHistoryRepository,
                "recurringTransactionDetailsHistoryRepository"
        );
        this.recurringTransactionRuleSnapshotMapper = Objects.requireNonNull(
                recurringTransactionRuleSnapshotMapper,
                "recurringTransactionRuleSnapshotMapper"
        );
        this.recurringTransactionAccountCurrencySnapshotMapper = Objects.requireNonNull(
                recurringTransactionAccountCurrencySnapshotMapper,
                "recurringTransactionAccountCurrencySnapshotMapper"
        );
        this.recurringTransactionProjectedMovementAssembler = Objects.requireNonNull(
                recurringTransactionProjectedMovementAssembler,
                "recurringTransactionProjectedMovementAssembler"
        );
        this.currencyBusinessCalendarResolver = Objects.requireNonNull(
                currencyBusinessCalendarResolver,
                "currencyBusinessCalendarResolver"
        );
        this.recurringTransactionOccurrenceGenerator = Objects.requireNonNull(
                recurringTransactionOccurrenceGenerator,
                "recurringTransactionOccurrenceGenerator"
        );
        this.financeCalendarProperties = Objects.requireNonNull(
                financeCalendarProperties,
                "financeCalendarProperties"
        );
    }

    @Transactional(readOnly = true)
    public List<RecurringTransactionProjectedMovement> generateProjectedMovements(
            List<RecurringTransactionProjectedMovementSeed> seeds,
            LocalDate from,
            LocalDate to
    ) {
        Objects.requireNonNull(seeds, "seeds");
        validateRange(from, to);

        if (seeds.isEmpty()) {
            return List.of();
        }

        if (seeds.size() > financeCalendarProperties.getMaxRecurringTransactions()) {
            throw new IllegalArgumentException("finance.recurringTransaction.batchTooLarge");
        }

        RecurringTransactionPreparedBatch preparedBatch =
                prepareBatch(seeds);

        return generatePreparedMovements(
                preparedBatch.preparedContexts(),
                from,
                to
        );
    }

    @Transactional(readOnly = true)
    public List<RecurringTransactionProjectedMovement> generateProjectedMovementsAcrossRange(
            List<RecurringTransactionProjectedMovementSeed> seeds,
            LocalDate from,
            LocalDate to
    ) {
        Objects.requireNonNull(seeds, "seeds");
        validateDateOrder(from, to);

        if (seeds.isEmpty()) {
            return List.of();
        }

        if (seeds.size() > financeCalendarProperties.getMaxRecurringTransactions()) {
            throw new IllegalArgumentException("finance.recurringTransaction.batchTooLarge");
        }

        RecurringTransactionPreparedBatch preparedBatch =
                prepareBatch(seeds);

        return generatePreparedMovementsInChunks(
                preparedBatch.preparedContexts(),
                from,
                to
        );
    }

    private Map<UUID, RecurringTransactionBatchContext> toContexts(
            List<RecurringTransactionProjectedMovementSeed> seeds
    ) {
        Map<UUID, RecurringTransactionBatchContext> contexts = new LinkedHashMap<>();

        for (RecurringTransactionProjectedMovementSeed seed : seeds) {
            Objects.requireNonNull(seed, "seed");

            UUID recurringTransactionId = seed.recurringTransactionId();

            if (contexts.containsKey(recurringTransactionId)) {
                throw new IllegalArgumentException("finance.recurringTransaction.duplicated");
            }

            contexts.put(
                    recurringTransactionId,
                    new RecurringTransactionBatchContext(
                            recurringTransactionId,
                            seed.userGroupId(),
                            seed.firstPaymentDate()
                    )
            );
        }

        return contexts;
    }

    private UUID singleUserGroupId(
            Collection<RecurringTransactionBatchContext> contexts
    ) {
        UUID userGroupId = null;

        for (RecurringTransactionBatchContext context : contexts) {
            if (userGroupId == null) {
                userGroupId = context.userGroupId();
                continue;
            }

            if (!userGroupId.equals(context.userGroupId())) {
                throw new IllegalArgumentException("finance.recurringTransaction.userGroupMismatch");
            }
        }

        return Objects.requireNonNull(userGroupId, "userGroupId");
    }

    private Map<UUID, List<RecurringTransactionHistory>> groupRuleHistoryByRecurringTransactionId(
            List<RecurringTransactionHistory> rows
    ) {
        Objects.requireNonNull(rows, "rows");
        return rows.stream()
                .collect(Collectors.groupingBy(
                        this::recurringTransactionIdOfRuleHistory,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private Map<UUID, List<RecurringTransactionDetailsHistory>> groupDetailsHistoryByRecurringTransactionId(
            List<RecurringTransactionDetailsHistory> rows
    ) {
        Objects.requireNonNull(rows, "rows");
        return rows.stream()
                .collect(Collectors.groupingBy(
                        this::recurringTransactionIdOfDetailsHistory,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private UUID recurringTransactionIdOfRuleHistory(
            RecurringTransactionHistory history
    ) {
        Objects.requireNonNull(history, "history");

        RecurringTransaction recurringTransaction = Objects.requireNonNull(
                history.getRecurringTransaction(),
                "recurringTransaction"
        );

        return Objects.requireNonNull(
                recurringTransaction.getRecurringTransactionId(),
                "recurringTransactionId"
        );
    }

    private UUID recurringTransactionIdOfDetailsHistory(
            RecurringTransactionDetailsHistory details
    ) {
        Objects.requireNonNull(details, "details");

        RecurringTransaction recurringTransaction = Objects.requireNonNull(
                details.getRecurringTransaction(),
                "recurringTransaction"
        );

        return Objects.requireNonNull(
                recurringTransaction.getRecurringTransactionId(),
                "recurringTransactionId"
        );
    }

    private void validateRange(LocalDate from, LocalDate to) {
        validateDateOrder(from, to);

        if (from.plusDays(financeCalendarProperties.getMaxRangeDays()).isBefore(to)) {
            throw new IllegalArgumentException("finance.calendar.dateRangeTooLarge");
        }
    }

    private RecurringTransactionPreparedBatch prepareBatch(
            List<RecurringTransactionProjectedMovementSeed> seeds
    ) {
        Map<UUID, RecurringTransactionBatchContext> contexts =
                toContexts(seeds);

        UUID userGroupId = singleUserGroupId(contexts.values());

        List<UUID> recurringTransactionIds = List.copyOf(contexts.keySet());

        Map<UUID, List<RecurringTransactionHistory>> ruleHistoryByRecurringTransactionId =
                groupRuleHistoryByRecurringTransactionId(
                        recurringTransactionHistoryRepository.findAllHistoryByRecurringTransactionIdsAndUserGroupId(
                                recurringTransactionIds,
                                userGroupId
                        )
                );

        Map<UUID, List<RecurringTransactionDetailsHistory>> detailsHistoryByRecurringTransactionId =
                groupDetailsHistoryByRecurringTransactionId(
                        recurringTransactionDetailsHistoryRepository
                                .findAllHistoryWithDetailsByRecurringTransactionIdsAndUserGroupId(
                                        recurringTransactionIds,
                                        userGroupId
                                )
                );

        List<PreparedRecurringTransactionGenerationContext> preparedContexts = new ArrayList<>();

        for (RecurringTransactionBatchContext context : contexts.values()) {
            List<RecurringTransactionHistory> ruleHistoryRows =
                    ruleHistoryByRecurringTransactionId.getOrDefault(
                            context.recurringTransactionId(),
                            List.of()
                    );

            if (ruleHistoryRows.isEmpty()) {
                throw new IllegalStateException("finance.recurringTransaction.ruleHistoryRequired");
            }

            List<RecurringTransactionDetailsHistory> detailsHistoryRows =
                    detailsHistoryByRecurringTransactionId.getOrDefault(
                            context.recurringTransactionId(),
                            List.of()
                    );

            if (detailsHistoryRows.isEmpty()) {
                throw new IllegalStateException("finance.recurringTransaction.detailsHistoryRequired");
            }

            List<RecurringTransactionRuleSnapshot> ruleSnapshots =
                    recurringTransactionRuleSnapshotMapper.toSnapshots(ruleHistoryRows);

            List<PaymentAccountCurrencySnapshot> accountCurrencySnapshots =
                    recurringTransactionAccountCurrencySnapshotMapper.toSnapshots(detailsHistoryRows);

            PaymentBusinessCalendarProvider paymentBusinessCalendarProvider =
                    new AccountCurrencyPaymentBusinessCalendarProvider(
                            accountCurrencySnapshots,
                            currencyBusinessCalendarResolver
                    );

            preparedContexts.add(
                    new PreparedRecurringTransactionGenerationContext(
                            context.recurringTransactionId(),
                            context.firstPaymentDate(),
                            ruleSnapshots,
                            detailsHistoryRows,
                            paymentBusinessCalendarProvider
                    )
            );
        }

        return new RecurringTransactionPreparedBatch(
                List.copyOf(preparedContexts)
        );
    }

    private List<RecurringTransactionProjectedMovement> generatePreparedMovements(
            List<PreparedRecurringTransactionGenerationContext> preparedContexts,
            LocalDate from,
            LocalDate to
    ) {
        List<RecurringTransactionProjectedMovement> movements = new ArrayList<>();

        for (PreparedRecurringTransactionGenerationContext context : preparedContexts) {
            List<RecurringTransactionOccurrence> occurrences =
                    recurringTransactionOccurrenceGenerator.generateOccurrences(
                            context.recurringTransactionId(),
                            context.firstPaymentDate(),
                            context.ruleSnapshots(),
                            from,
                            to,
                            context.paymentBusinessCalendarProvider()
                    );

            movements.addAll(
                    recurringTransactionProjectedMovementAssembler.assemble(
                            occurrences,
                            context.detailsHistoryRows()
                    )
            );
        }

        return sortProjectedMovements(movements);
    }

    private List<RecurringTransactionProjectedMovement> generatePreparedMovementsInChunks(
            List<PreparedRecurringTransactionGenerationContext> preparedContexts,
            LocalDate from,
            LocalDate to
    ) {
        long maxRangeDays = financeCalendarProperties.getMaxRangeDays();

        if (maxRangeDays < 0) {
            throw new IllegalStateException("finance.calendar.maxRangeDaysInvalid");
        }

        long boundaryPaddingDays = Math.min(
                CHUNK_BOUNDARY_OVERLAP_DAYS,
                Math.max(0L, (maxRangeDays - 1) / 2)
        );

        long chunkCoreRangeDays = Math.max(
                1L,
                maxRangeDays - (boundaryPaddingDays * 2)
        );

        List<RecurringTransactionProjectedMovement> movements = new ArrayList<>();

        for (PreparedRecurringTransactionGenerationContext context : preparedContexts) {
            Map<RecurringTransactionOccurrenceKey, RecurringTransactionOccurrence> occurrencesByKey =
                    new LinkedHashMap<>();

            LocalDate chunkFrom = firstChunkFrom(
                    context,
                    from,
                    boundaryPaddingDays
            );

            if (chunkFrom.isAfter(to)) {
                continue;
            }

            while (!chunkFrom.isAfter(to)) {
                LocalDate maxChunkCoreTo = chunkFrom.plusDays(chunkCoreRangeDays);

                LocalDate chunkCoreTo = maxChunkCoreTo.isBefore(to)
                        ? maxChunkCoreTo
                        : to;

                LocalDate generationFrom = chunkFrom.minusDays(boundaryPaddingDays);
                LocalDate generationTo = chunkCoreTo.plusDays(boundaryPaddingDays);

                List<RecurringTransactionOccurrence> occurrences =
                        recurringTransactionOccurrenceGenerator.generateOccurrences(
                                context.recurringTransactionId(),
                                context.firstPaymentDate(),
                                context.ruleSnapshots(),
                                generationFrom,
                                generationTo,
                                context.paymentBusinessCalendarProvider()
                        );

                for (RecurringTransactionOccurrence occurrence : occurrences) {
                    if (!isChargeDateInsideRange(occurrence, from, to)) {
                        continue;
                    }

                    occurrencesByKey.put(
                            new RecurringTransactionOccurrenceKey(
                                    occurrence.recurringTransactionId(),
                                    occurrence.logicalDate(),
                                    occurrence.chargeDate(),
                                    occurrence.finalOccurrence()
                            ),
                            occurrence
                    );
                }

                chunkFrom = chunkCoreTo.plusDays(1);
            }

            movements.addAll(
                    recurringTransactionProjectedMovementAssembler.assemble(
                            sortedOccurrences(occurrencesByKey.values()),
                            context.detailsHistoryRows()
                    )
            );
        }

        return sortProjectedMovements(movements);
    }

    private List<RecurringTransactionOccurrence> sortedOccurrences(
            Collection<RecurringTransactionOccurrence> occurrences
    ) {
        return occurrences.stream()
                .sorted(Comparator
                        .comparing(RecurringTransactionOccurrence::chargeDate)
                        .thenComparing(RecurringTransactionOccurrence::logicalDate)
                        .thenComparing(RecurringTransactionOccurrence::finalOccurrence))
                .toList();
    }

    private List<RecurringTransactionProjectedMovement> sortProjectedMovements(
            List<RecurringTransactionProjectedMovement> movements
    ) {
        return movements.stream()
                .sorted(Comparator
                        .comparing(RecurringTransactionProjectedMovement::chargeDate)
                        .thenComparing(RecurringTransactionProjectedMovement::logicalDate)
                        .thenComparing(RecurringTransactionProjectedMovement::recurringTransactionId))
                .toList();
    }

    private void validateDateOrder(LocalDate from, LocalDate to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");

        if (to.isBefore(from)) {
            throw new IllegalArgumentException("finance.calendar.dateRangeInvalid");
        }
    }

    private LocalDate firstChunkFrom(
            PreparedRecurringTransactionGenerationContext context,
            LocalDate requestedFrom,
            long boundaryPaddingDays
    ) {
        LocalDate firstPaymentDate = Objects.requireNonNull(
                context.firstPaymentDate(),
                "firstPaymentDate"
        );

        LocalDate firstPotentialChargeDate =
                firstPaymentDate.minusDays(boundaryPaddingDays);

        return firstPotentialChargeDate.isAfter(requestedFrom)
                ? firstPotentialChargeDate
                : requestedFrom;
    }

    private boolean isChargeDateInsideRange(
            RecurringTransactionOccurrence occurrence,
            LocalDate from,
            LocalDate to
    ) {
        return !occurrence.chargeDate().isBefore(from)
                && !occurrence.chargeDate().isAfter(to);
    }

    private record RecurringTransactionBatchContext(
            UUID recurringTransactionId,
            UUID userGroupId,
            LocalDate firstPaymentDate
    ) {
    }

    private record RecurringTransactionPreparedBatch(
            List<PreparedRecurringTransactionGenerationContext> preparedContexts
    ) {
    }

    private record PreparedRecurringTransactionGenerationContext(
            UUID recurringTransactionId,
            LocalDate firstPaymentDate,
            List<RecurringTransactionRuleSnapshot> ruleSnapshots,
            List<RecurringTransactionDetailsHistory> detailsHistoryRows,
            PaymentBusinessCalendarProvider paymentBusinessCalendarProvider
    ) {
    }

    private record RecurringTransactionOccurrenceKey(
            UUID recurringTransactionId,
            LocalDate logicalDate,
            LocalDate chargeDate,
            boolean finalOccurrence
    ) {
    }
}