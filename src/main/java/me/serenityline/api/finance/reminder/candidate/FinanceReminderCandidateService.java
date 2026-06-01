package me.serenityline.api.finance.reminder.candidate;

import me.serenityline.api.finance.transaction.service.RecurringTransactionProjectedMovement;
import me.serenityline.api.finance.transaction.service.RecurringTransactionProjectedMovementBatchService;
import me.serenityline.api.finance.transaction.service.RecurringTransactionProjectedMovementSeed;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FinanceReminderCandidateService {

    private static final int DEFAULT_TRANSACTION_LIMIT = 500;
    private static final int DEFAULT_RECURRING_SEED_PAGE_SIZE = 500;

    private final FinanceReminderCandidateRepository candidateRepository;
    private final RecurringTransactionProjectedMovementBatchService recurringProjectionService;

    public FinanceReminderCandidateService(
            FinanceReminderCandidateRepository candidateRepository,
            RecurringTransactionProjectedMovementBatchService recurringProjectionService
    ) {
        this.candidateRepository = Objects.requireNonNull(candidateRepository, "candidateRepository");
        this.recurringProjectionService = Objects.requireNonNull(recurringProjectionService, "recurringProjectionService");
    }

    private static int maxReminderDaysBefore(List<RecurringFinanceReminderSeed> seeds) {
        return seeds.stream()
                .mapToInt(RecurringFinanceReminderSeed::reminderDaysBefore)
                .max()
                .orElse(0);
    }

    @Transactional(readOnly = true)
    public List<FinanceReminderCandidate> findDueCandidates(LocalDate today) {
        Objects.requireNonNull(today, "today");

        List<FinanceReminderCandidate> transactionCandidates =
                candidateRepository.findDueTransactionCandidates(
                        today,
                        DEFAULT_TRANSACTION_LIMIT
                );

        List<FinanceReminderCandidate> recurringCandidates =
                findDueRecurringCandidates(
                        today,
                        DEFAULT_RECURRING_SEED_PAGE_SIZE
                );

        List<FinanceReminderCandidate> result = new ArrayList<>(
                transactionCandidates.size() + recurringCandidates.size()
        );

        result.addAll(transactionCandidates);
        result.addAll(recurringCandidates);

        return result.stream()
                .sorted(Comparator
                        .comparing(FinanceReminderCandidate::reminderDate)
                        .thenComparing(FinanceReminderCandidate::chargeDate)
                        .thenComparing(candidate -> candidate.transactionId() == null
                                ? candidate.recurringTransactionId()
                                : candidate.transactionId())
                        .thenComparing(FinanceReminderCandidate::userId))
                .toList();
    }

    private List<FinanceReminderCandidate> findDueRecurringCandidates(
            LocalDate today,
            int pageSize
    ) {
        List<FinanceReminderCandidate> result = new ArrayList<>();

        UUID afterRecurringTransactionId = null;

        while (true) {
            List<RecurringFinanceReminderSeed> seeds =
                    candidateRepository.findRecurringReminderSeedsPage(
                            today,
                            pageSize,
                            afterRecurringTransactionId
                    );

            if (seeds.isEmpty()) {
                return result;
            }

            Map<UUID, List<RecurringFinanceReminderSeed>> seedsByUserGroup =
                    seeds.stream()
                            .collect(Collectors.groupingBy(
                                    RecurringFinanceReminderSeed::userGroupId,
                                    LinkedHashMap::new,
                                    Collectors.toList()
                            ));

            for (Map.Entry<UUID, List<RecurringFinanceReminderSeed>> entry : seedsByUserGroup.entrySet()) {
                result.addAll(findDueRecurringCandidatesForGroup(
                        entry.getKey(),
                        entry.getValue(),
                        today
                ));
            }

            afterRecurringTransactionId = seeds.get(seeds.size() - 1)
                    .recurringTransactionId();

            if (seeds.size() < pageSize) {
                return result;
            }
        }
    }

    private List<FinanceReminderCandidate> findDueRecurringCandidatesForGroup(
            UUID userGroupId,
            List<RecurringFinanceReminderSeed> seeds,
            LocalDate today
    ) {
        Map<UUID, RecurringFinanceReminderSeed> seedByRecurringTransactionId =
                seeds.stream()
                        .collect(Collectors.toMap(
                                RecurringFinanceReminderSeed::recurringTransactionId,
                                Function.identity(),
                                (first, second) -> first,
                                LinkedHashMap::new
                        ));

        List<RecurringTransactionProjectedMovementSeed> projectionSeeds =
                seedByRecurringTransactionId.values()
                        .stream()
                        .map(seed -> new RecurringTransactionProjectedMovementSeed(
                                seed.recurringTransactionId(),
                                seed.userGroupId(),
                                seed.firstPaymentDate()
                        ))
                        .toList();

        LocalDate generationFrom = today;
        LocalDate generationTo = today.plusDays(maxReminderDaysBefore(seeds));

        List<RecurringTransactionProjectedMovement> projectedMovements =
                recurringProjectionService.generateProjectedMovementsAcrossRange(
                        projectionSeeds,
                        generationFrom,
                        generationTo
                );

        if (projectedMovements.isEmpty()) {
            return List.of();
        }

        Map<RecurringOccurrenceKey, FinanceReminderConfirmedRecurringOccurrenceSnapshot> confirmedSnapshotByKey =
                confirmedSnapshotByKey(
                        userGroupId,
                        seedByRecurringTransactionId.keySet(),
                        projectedMovements
                );

        List<RecurringTransactionProjectedMovement> dueProjectedMovements =
                projectedMovements.stream()
                        .filter(projectedMovement -> {
                            RecurringFinanceReminderSeed seed = seedByRecurringTransactionId.get(
                                    projectedMovement.recurringTransactionId()
                            );

                            if (seed == null) {
                                return false;
                            }

                            FinanceReminderConfirmedRecurringOccurrenceSnapshot confirmedSnapshot =
                                    confirmedSnapshotByKey.get(RecurringOccurrenceKey.from(projectedMovement));

                            LocalDate effectiveChargeDate = confirmedSnapshot == null
                                    ? projectedMovement.chargeDate()
                                    : confirmedSnapshot.chargeDate();

                            if (effectiveChargeDate.isBefore(today)) {
                                return false;
                            }

                            LocalDate reminderDate = effectiveChargeDate
                                    .minusDays(seed.reminderDaysBefore());

                            return !reminderDate.isAfter(today);
                        })
                        .toList();

        if (dueProjectedMovements.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<UUID>> userIdsByRecurringTransactionId =
                candidateRepository.findReminderEnabledUserIdsByRecurringTransactionId(
                        userGroupId,
                        seedByRecurringTransactionId.keySet()
                );

        List<FinanceReminderCandidate> result = new ArrayList<>();

        for (RecurringTransactionProjectedMovement projectedMovement : dueProjectedMovements) {
            RecurringFinanceReminderSeed seed = seedByRecurringTransactionId.get(
                    projectedMovement.recurringTransactionId()
            );

            FinanceReminderConfirmedRecurringOccurrenceSnapshot confirmedSnapshot =
                    confirmedSnapshotByKey.get(RecurringOccurrenceKey.from(projectedMovement));

            LocalDate chargeDate = confirmedSnapshot == null
                    ? projectedMovement.chargeDate()
                    : confirmedSnapshot.chargeDate();

            String notifiedDescription = confirmedSnapshot == null
                    ? projectedMovement.description()
                    : confirmedSnapshot.notifiedDescription();

            BigDecimal notifiedAmount = confirmedSnapshot == null
                    ? projectedMovement.amount()
                    : confirmedSnapshot.notifiedAmount();

            String notifiedCurrency = confirmedSnapshot == null
                    ? projectedMovement.linkedAccount().getCurrency()
                    : confirmedSnapshot.notifiedCurrency();

            LocalDate reminderDate = chargeDate.minusDays(seed.reminderDaysBefore());

            List<UUID> userIds = userIdsByRecurringTransactionId.getOrDefault(
                    projectedMovement.recurringTransactionId(),
                    List.of()
            );

            for (UUID userId : userIds) {
                result.add(FinanceReminderCandidate.forRecurringOccurrence(
                        userId,
                        userGroupId,
                        projectedMovement.recurringTransactionId(),
                        projectedMovement.logicalDate(),
                        chargeDate,
                        notifiedDescription,
                        notifiedAmount,
                        notifiedCurrency,
                        reminderDate
                ));
            }
        }

        return result;
    }

    private Map<RecurringOccurrenceKey, FinanceReminderConfirmedRecurringOccurrenceSnapshot> confirmedSnapshotByKey(
            UUID userGroupId,
            Collection<UUID> recurringTransactionIds,
            List<RecurringTransactionProjectedMovement> projectedMovements
    ) {
        if (projectedMovements.isEmpty()) {
            return Map.of();
        }

        LocalDate minLogicalDate = projectedMovements.stream()
                .map(RecurringTransactionProjectedMovement::logicalDate)
                .min(LocalDate::compareTo)
                .orElseThrow();

        LocalDate maxLogicalDate = projectedMovements.stream()
                .map(RecurringTransactionProjectedMovement::logicalDate)
                .max(LocalDate::compareTo)
                .orElseThrow();

        return candidateRepository.findConfirmedRecurringOccurrenceSnapshots(
                        userGroupId,
                        recurringTransactionIds,
                        minLogicalDate,
                        maxLogicalDate
                )
                .stream()
                .collect(Collectors.toMap(
                        snapshot -> new RecurringOccurrenceKey(
                                snapshot.recurringTransactionId(),
                                snapshot.recurringTransactionLogicalDate()
                        ),
                        Function.identity(),
                        (first, second) -> first,
                        LinkedHashMap::new
                ));
    }

    private record RecurringOccurrenceKey(
            UUID recurringTransactionId,
            LocalDate logicalDate
    ) {

        private static RecurringOccurrenceKey from(RecurringTransactionProjectedMovement projectedMovement) {
            return new RecurringOccurrenceKey(
                    projectedMovement.recurringTransactionId(),
                    projectedMovement.logicalDate()
            );
        }
    }
}