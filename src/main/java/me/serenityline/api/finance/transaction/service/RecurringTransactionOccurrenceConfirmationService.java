package me.serenityline.api.finance.transaction.service;

import me.serenityline.api.common.error.ResourceNotFoundException;
import me.serenityline.api.finance.transaction.dto.RecurringTransactionOccurrenceConfirmRequest;
import me.serenityline.api.finance.transaction.dto.TransactionResponse;
import me.serenityline.api.finance.transaction.entity.RecurringTransaction;
import me.serenityline.api.finance.transaction.entity.Transaction;
import me.serenityline.api.finance.transaction.entity.TransactionUser;
import me.serenityline.api.finance.transaction.repository.TransactionRepository;
import me.serenityline.api.finance.transaction.repository.TransactionUserRepository;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
public class RecurringTransactionOccurrenceConfirmationService {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionUserRepository transactionUserRepository;
    private final RecurringTransactionAccessService recurringTransactionAccessService;
    private final TransactionAccessService transactionAccessService;
    private final RecurringTransactionProjectedMovementBatchService recurringTransactionProjectedMovementBatchService;

    public RecurringTransactionOccurrenceConfirmationService(
            UserRepository userRepository,
            TransactionRepository transactionRepository,
            TransactionUserRepository transactionUserRepository,
            RecurringTransactionAccessService recurringTransactionAccessService,
            TransactionAccessService transactionAccessService,
            RecurringTransactionProjectedMovementBatchService recurringTransactionProjectedMovementBatchService
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.transactionRepository = Objects.requireNonNull(transactionRepository, "transactionRepository");
        this.transactionUserRepository = Objects.requireNonNull(transactionUserRepository, "transactionUserRepository");
        this.recurringTransactionAccessService = Objects.requireNonNull(
                recurringTransactionAccessService,
                "recurringTransactionAccessService"
        );
        this.transactionAccessService = Objects.requireNonNull(
                transactionAccessService,
                "transactionAccessService"
        );
        this.recurringTransactionProjectedMovementBatchService = Objects.requireNonNull(
                recurringTransactionProjectedMovementBatchService,
                "recurringTransactionProjectedMovementBatchService"
        );
    }

    @Transactional
    public TransactionResponse confirmOccurrence(
            UUID currentUserId,
            UUID recurringTransactionId,
            RecurringTransactionOccurrenceConfirmRequest request
    ) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        Objects.requireNonNull(recurringTransactionId, "recurringTransactionId");
        Objects.requireNonNull(request, "request");

        LocalDate logicalDate = requireLogicalDate(request.logicalDate());

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("user.notFound"));

        UUID userGroupId = currentUser.getUserGroup().getUserGroupId();

        RecurringTransaction recurringTransaction =
                recurringTransactionAccessService.findOperableRecurringTransaction(
                        currentUser,
                        userGroupId,
                        recurringTransactionId
                );

        assertOccurrenceNotAlreadyConfirmed(
                userGroupId,
                recurringTransactionId,
                logicalDate
        );

        RecurringTransactionProjectedMovement projectedMovement =
                recurringTransactionProjectedMovementBatchService
                        .generateProjectedMovementForLogicalDate(
                                new RecurringTransactionProjectedMovementSeed(
                                        recurringTransaction.getRecurringTransactionId(),
                                        userGroupId,
                                        recurringTransaction.getRecurringTransactionFirstPaymentDate()
                                ),
                                logicalDate
                        )
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "finance.recurringTransaction.occurrenceNotFound"
                        ));

        transactionAccessService.findOperableAccount(
                currentUser,
                userGroupId,
                projectedMovement.linkedAccount().getAccountId()
        );

        BigDecimal amount = resolveAmount(
                recurringTransaction,
                projectedMovement,
                request.transactionAmount()
        );

        LocalDate chargeDate = request.transactionChargeDate() == null
                ? projectedMovement.chargeDate()
                : request.transactionChargeDate();

        Transaction transaction = Transaction.createConfirmedRecurringOccurrence(
                projectedMovement.description(),
                amount,
                projectedMovement.affectsAccountBalance(),
                projectedMovement.affectsSerenityline(),
                projectedMovement.category(),
                chargeDate,
                projectedMovement.linkedAccount(),
                projectedMovement.linkedCreditCard(),
                projectedMovement.linkedBucket(),
                recurringTransaction.isRecurringTransactionIsSimulated(),
                recurringTransaction.getSimulationGroup(),
                recurringTransaction,
                logicalDate,
                OffsetDateTime.now(),
                recurringTransaction.isRecurringTransactionReminderEnabled(),
                recurringTransaction.getRecurringTransactionReminderDaysBefore(),
                currentUser.getUserGroup()
        );

        try {
            transaction = transactionRepository.saveAndFlush(transaction);
        } catch (DataIntegrityViolationException exception) {
            if (transactionRepository.existsConfirmedRecurringOccurrence(
                    userGroupId,
                    recurringTransactionId,
                    logicalDate
            )) {
                throw new IllegalArgumentException(
                        "finance.recurringTransaction.occurrenceAlreadyConfirmed",
                        exception
                );
            }

            throw exception;
        }

        transactionUserRepository.save(TransactionUser.link(
                transaction,
                currentUser,
                currentUser.getUserGroup()
        ));

        return TransactionResponse.from(transaction);
    }

    private LocalDate requireLogicalDate(LocalDate logicalDate) {
        if (logicalDate == null) {
            throw new IllegalArgumentException(
                    "finance.recurringTransaction.occurrenceLogicalDateRequired"
            );
        }

        return logicalDate;
    }

    private void assertOccurrenceNotAlreadyConfirmed(
            UUID userGroupId,
            UUID recurringTransactionId,
            LocalDate logicalDate
    ) {
        boolean alreadyConfirmed = transactionRepository.existsConfirmedRecurringOccurrence(
                userGroupId,
                recurringTransactionId,
                logicalDate
        );

        if (alreadyConfirmed) {
            throw new IllegalArgumentException(
                    "finance.recurringTransaction.occurrenceAlreadyConfirmed"
            );
        }
    }

    private BigDecimal resolveAmount(
            RecurringTransaction recurringTransaction,
            RecurringTransactionProjectedMovement projectedMovement,
            BigDecimal requestedAmount
    ) {
        if (requestedAmount == null) {
            return projectedMovement.amount();
        }

        BigDecimal normalizedAmount = normalizeAmount(requestedAmount);

        if (!recurringTransaction.isRecurringTransactionAmountIsAdjustable()
                && normalizedAmount.compareTo(projectedMovement.amount()) != 0) {
            throw new IllegalArgumentException(
                    "finance.recurringTransaction.amountNotAdjustable"
            );
        }

        return normalizedAmount;
    }

    private BigDecimal normalizeAmount(BigDecimal value) {
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "finance.transaction.amountInvalid",
                    exception
            );
        }
    }
}