package me.serenityline.api.finance.reminder.worker;

import me.serenityline.api.finance.reminder.candidate.FinanceReminderCandidate;
import me.serenityline.api.finance.reminder.candidate.FinanceReminderCandidateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
public class FinanceReminderNotificationWorkerService {

    private static final Logger log = LoggerFactory.getLogger(
            FinanceReminderNotificationWorkerService.class
    );

    private final FinanceReminderCandidateService candidateService;
    private final FinanceReminderCandidateProcessingService candidateProcessingService;
    private final Clock clock;
    private final long slowRunWarningMs;
    private final int highCandidatesWarningThreshold;

    public FinanceReminderNotificationWorkerService(
            FinanceReminderCandidateService candidateService,
            FinanceReminderCandidateProcessingService candidateProcessingService,
            Clock clock,
            @Value("${serenityline.finance.reminder-worker.slow-run-warning-ms:30000}") long slowRunWarningMs,
            @Value("${serenityline.finance.reminder-worker.high-candidates-warning-threshold:1000}") int highCandidatesWarningThreshold
    ) {
        if (slowRunWarningMs <= 0) {
            throw new IllegalStateException("finance.reminderWorker.slowRunWarningMs.invalid");
        }

        if (highCandidatesWarningThreshold <= 0) {
            throw new IllegalStateException("finance.reminderWorker.highCandidatesWarningThreshold.invalid");
        }

        this.candidateService = Objects.requireNonNull(candidateService, "candidateService");
        this.candidateProcessingService = Objects.requireNonNull(
                candidateProcessingService,
                "candidateProcessingService"
        );
        this.clock = Objects.requireNonNull(clock, "clock");
        this.slowRunWarningMs = slowRunWarningMs;
        this.highCandidatesWarningThreshold = highCandidatesWarningThreshold;
    }

    private static String describeCandidate(FinanceReminderCandidate candidate) {
        if (candidate.isTransactionCandidate()) {
            return "transaction candidate userId=%s userGroupId=%s transactionId=%s chargeDate=%s reminderDate=%s"
                    .formatted(
                            candidate.userId(),
                            candidate.userGroupId(),
                            candidate.transactionId(),
                            candidate.chargeDate(),
                            candidate.reminderDate()
                    );
        }

        return "recurring candidate userId=%s userGroupId=%s recurringTransactionId=%s logicalDate=%s chargeDate=%s reminderDate=%s"
                .formatted(
                        candidate.userId(),
                        candidate.userGroupId(),
                        candidate.recurringTransactionId(),
                        candidate.recurringTransactionLogicalDate(),
                        candidate.chargeDate(),
                        candidate.reminderDate()
                );
    }

    public FinanceReminderWorkerResult processDueReminders() {
        return processDueReminders(LocalDate.now(clock));
    }

    public FinanceReminderWorkerResult processDueReminders(LocalDate today) {
        Objects.requireNonNull(today, "today");

        long startedAtNanos = System.nanoTime();

        List<FinanceReminderCandidate> candidates = candidateService.findDueCandidates(today);

        WorkerCounters counters = new WorkerCounters(candidates.size());

        for (FinanceReminderCandidate candidate : candidates) {
            try {
                FinanceReminderCandidateProcessingResult result =
                        candidateProcessingService.process(candidate);

                counters.record(result);
            } catch (RuntimeException ex) {
                counters.recordFailure();

                log.warn(
                        "Finance reminder candidate processing failed: {}",
                        describeCandidate(candidate),
                        ex
                );
            }
        }

        FinanceReminderWorkerResult result = counters.toResult();

        long durationMs = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedAtNanos
        );

        if (durationMs >= slowRunWarningMs
                || result.failures() > 0
                || result.candidatesFound() >= highCandidatesWarningThreshold) {
            log.warn(
                    "Finance reminder worker completed with warning: today={}, durationMs={}, candidatesFound={}, notificationsCreated={}, emailOutboxesEnsured={}, alreadyNotified={}, failures={}",
                    today,
                    durationMs,
                    result.candidatesFound(),
                    result.notificationsCreated(),
                    result.emailOutboxesEnsured(),
                    result.alreadyNotified(),
                    result.failures()
            );
        } else {
            log.info(
                    "Finance reminder worker completed: today={}, durationMs={}, candidatesFound={}, notificationsCreated={}, emailOutboxesEnsured={}, alreadyNotified={}, failures={}",
                    today,
                    durationMs,
                    result.candidatesFound(),
                    result.notificationsCreated(),
                    result.emailOutboxesEnsured(),
                    result.alreadyNotified(),
                    result.failures()
            );
        }

        return result;
    }

    private static final class WorkerCounters {

        private final int candidatesFound;
        private int notificationsCreated;
        private int emailOutboxesEnsured;
        private int alreadyNotified;
        private int failures;

        private WorkerCounters(int candidatesFound) {
            this.candidatesFound = candidatesFound;
        }

        private void record(FinanceReminderCandidateProcessingResult result) {
            if (result.status() == FinanceReminderCandidateProcessingResult.Status.CREATED) {
                notificationsCreated++;
                emailOutboxesEnsured++;
                return;
            }

            if (result.status() == FinanceReminderCandidateProcessingResult.Status.ALREADY_EXISTS) {
                alreadyNotified++;
                return;
            }

            throw new IllegalStateException("finance.reminderWorker.processingStatus.invalid");
        }

        private void recordFailure() {
            failures++;
        }

        private FinanceReminderWorkerResult toResult() {
            return new FinanceReminderWorkerResult(
                    candidatesFound,
                    notificationsCreated,
                    emailOutboxesEnsured,
                    alreadyNotified,
                    failures
            );
        }
    }
}