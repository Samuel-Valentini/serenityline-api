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

        List<RecurringTransactionProjectedMovement> movements = new ArrayList<>();

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

            List<RecurringTransactionOccurrence> occurrences =
                    recurringTransactionOccurrenceGenerator.generateOccurrences(
                            context.recurringTransactionId(),
                            context.firstPaymentDate(),
                            ruleSnapshots,
                            from,
                            to,
                            paymentBusinessCalendarProvider
                    );

            movements.addAll(
                    recurringTransactionProjectedMovementAssembler.assemble(
                            occurrences,
                            detailsHistoryRows
                    )
            );
        }

        return movements.stream()
                .sorted(Comparator
                        .comparing(RecurringTransactionProjectedMovement::chargeDate)
                        .thenComparing(RecurringTransactionProjectedMovement::logicalDate)
                        .thenComparing(RecurringTransactionProjectedMovement::recurringTransactionId))
                .toList();
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
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");

        if (to.isBefore(from)) {
            throw new IllegalArgumentException("finance.calendar.dateRangeInvalid");
        }

        if (from.plusDays(financeCalendarProperties.getMaxRangeDays()).isBefore(to)) {
            throw new IllegalArgumentException("finance.calendar.dateRangeTooLarge");
        }
    }

    private record RecurringTransactionBatchContext(
            UUID recurringTransactionId,
            UUID userGroupId,
            LocalDate firstPaymentDate
    ) {
    }
}