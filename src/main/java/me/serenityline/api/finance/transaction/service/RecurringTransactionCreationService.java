package me.serenityline.api.finance.transaction.service;

import me.serenityline.api.common.error.ResourceNotFoundException;
import me.serenityline.api.finance.account.entity.Account;
import me.serenityline.api.finance.bucket.entity.Bucket;
import me.serenityline.api.finance.bucket.repository.BucketAccountRepository;
import me.serenityline.api.finance.bucket.repository.BucketRepository;
import me.serenityline.api.finance.category.entity.Category;
import me.serenityline.api.finance.category.repository.CategoryRepository;
import me.serenityline.api.finance.creditcard.entity.CreditCard;
import me.serenityline.api.finance.creditcard.repository.CreditCardRepository;
import me.serenityline.api.finance.financialpriority.entity.FinancialPriority;
import me.serenityline.api.finance.financialpriority.repository.FinancialPriorityRepository;
import me.serenityline.api.finance.simulation.entity.SimulationGroup;
import me.serenityline.api.finance.simulation.repository.SimulationGroupAccountRepository;
import me.serenityline.api.finance.simulation.repository.SimulationGroupRepository;
import me.serenityline.api.finance.transaction.dto.RecurringTransactionCreateRequest;
import me.serenityline.api.finance.transaction.dto.RecurringTransactionResponse;
import me.serenityline.api.finance.transaction.entity.*;
import me.serenityline.api.finance.transaction.repository.RecurringTransactionDetailsHistoryRepository;
import me.serenityline.api.finance.transaction.repository.RecurringTransactionHistoryRepository;
import me.serenityline.api.finance.transaction.repository.RecurringTransactionRepository;
import me.serenityline.api.finance.transaction.repository.RecurringTransactionUserRepository;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Service
public class RecurringTransactionCreationService {

    private static final short DEFAULT_REMINDER_DAYS_BEFORE = 7;

    private final UserRepository userRepository;
    private final TransactionAccessService transactionAccessService;
    private final CategoryRepository categoryRepository;
    private final FinancialPriorityRepository financialPriorityRepository;
    private final CreditCardRepository creditCardRepository;
    private final BucketRepository bucketRepository;
    private final BucketAccountRepository bucketAccountRepository;
    private final SimulationGroupRepository simulationGroupRepository;
    private final SimulationGroupAccountRepository simulationGroupAccountRepository;
    private final RecurringTransactionRepository recurringTransactionRepository;
    private final RecurringTransactionHistoryRepository recurringTransactionHistoryRepository;
    private final RecurringTransactionDetailsHistoryRepository recurringTransactionDetailsHistoryRepository;
    private final RecurringTransactionUserRepository recurringTransactionUserRepository;
    private final FinanceMovementAssociationValidator associationValidator;

    public RecurringTransactionCreationService(
            UserRepository userRepository,
            TransactionAccessService transactionAccessService,
            CategoryRepository categoryRepository,
            FinancialPriorityRepository financialPriorityRepository,
            CreditCardRepository creditCardRepository,
            BucketRepository bucketRepository,
            BucketAccountRepository bucketAccountRepository,
            SimulationGroupRepository simulationGroupRepository,
            SimulationGroupAccountRepository simulationGroupAccountRepository,
            RecurringTransactionRepository recurringTransactionRepository,
            RecurringTransactionHistoryRepository recurringTransactionHistoryRepository,
            RecurringTransactionDetailsHistoryRepository recurringTransactionDetailsHistoryRepository,
            RecurringTransactionUserRepository recurringTransactionUserRepository,
            FinanceMovementAssociationValidator associationValidator
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.transactionAccessService = Objects.requireNonNull(transactionAccessService, "transactionAccessService");
        this.categoryRepository = Objects.requireNonNull(categoryRepository, "categoryRepository");
        this.financialPriorityRepository = Objects.requireNonNull(financialPriorityRepository, "financialPriorityRepository");
        this.creditCardRepository = Objects.requireNonNull(creditCardRepository, "creditCardRepository");
        this.bucketRepository = Objects.requireNonNull(bucketRepository, "bucketRepository");
        this.bucketAccountRepository = Objects.requireNonNull(bucketAccountRepository, "bucketAccountRepository");
        this.simulationGroupRepository = Objects.requireNonNull(simulationGroupRepository, "simulationGroupRepository");
        this.simulationGroupAccountRepository = Objects.requireNonNull(simulationGroupAccountRepository, "simulationGroupAccountRepository");
        this.recurringTransactionRepository = Objects.requireNonNull(recurringTransactionRepository, "recurringTransactionRepository");
        this.recurringTransactionHistoryRepository = Objects.requireNonNull(recurringTransactionHistoryRepository, "recurringTransactionHistoryRepository");
        this.recurringTransactionDetailsHistoryRepository = Objects.requireNonNull(recurringTransactionDetailsHistoryRepository, "recurringTransactionDetailsHistoryRepository");
        this.recurringTransactionUserRepository = Objects.requireNonNull(recurringTransactionUserRepository, "recurringTransactionUserRepository");
        this.associationValidator = Objects.requireNonNull(associationValidator, "associationValidator");
    }

    @Transactional
    public RecurringTransactionResponse createRecurringTransaction(
            UUID currentUserId,
            RecurringTransactionCreateRequest request
    ) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        Objects.requireNonNull(request, "request");

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("user.notFound"));

        UUID userGroupId = currentUser.getUserGroup().getUserGroupId();

        Account linkedAccount = transactionAccessService.findOperableAccount(
                currentUser,
                userGroupId,
                request.linkedAccountId()
        );

        Category category = resolveActiveCategory(
                request.categoryId(),
                userGroupId
        );

        FinancialPriority financialPriority = resolveFinancialPriority(
                request.financialPriorityId()
        );

        CreditCard linkedCreditCard = resolveCreditCard(
                request.linkedCreditCardId(),
                linkedAccount,
                userGroupId
        );

        Bucket linkedBucket = resolveBucket(
                request.linkedBucketId(),
                linkedAccount,
                userGroupId
        );

        associationValidator.validateRecurringCreditCardBucketExclusion(
                linkedCreditCard,
                linkedBucket
        );

        boolean simulated = defaultFalse(request.recurringTransactionIsSimulated());

        SimulationGroup simulationGroup = resolveSimulationGroup(
                currentUser,
                userGroupId,
                linkedAccount,
                simulated,
                request.simulationGroupId()
        );

        RecurringTransaction recurringTransaction = RecurringTransaction.create(
                defaultFalse(request.recurringTransactionAmountIsAdjustable()),
                request.recurringTransactionFirstPaymentDate(),
                simulated,
                simulationGroup,
                request.recurringTransactionReminderEnabled(),
                normalizeReminderDaysBefore(request.recurringTransactionReminderDaysBefore()),
                currentUser.getUserGroup()
        );

        recurringTransaction = recurringTransactionRepository.saveAndFlush(recurringTransaction);

        LocalDate effectiveFrom = request.recurringTransactionFirstPaymentDate();

        RecurringTransactionHistory history = RecurringTransactionHistory.create(
                recurringTransaction,
                effectiveFrom,
                null,
                deriveDayOfUnit(
                        request.recurrenceUnit(),
                        request.recurringTransactionFirstPaymentDate()
                ),
                normalizeRecurrenceInterval(request.recurrenceInterval()),
                request.recurrenceUnit(),
                request.paymentDateAdjustmentPolicy(),
                request.paymentAmount(),
                request.recurringTransactionEndDate(),
                request.finalPaymentAmount()
        );

        history = recurringTransactionHistoryRepository.saveAndFlush(history);

        RecurringTransactionDetailsHistory details = RecurringTransactionDetailsHistory.create(
                recurringTransaction,
                request.recurringTransactionDescription(),
                category,
                financialPriority,
                linkedAccount,
                linkedCreditCard,
                linkedBucket,
                request.recurringTransactionAffectsAccountBalance(),
                request.recurringtransactionAffectsSerenityline(),
                effectiveFrom,
                currentUser.getUserGroup()
        );

        details = recurringTransactionDetailsHistoryRepository.saveAndFlush(details);

        recurringTransactionUserRepository.saveAndFlush(RecurringTransactionUser.link(
                recurringTransaction,
                currentUser,
                currentUser.getUserGroup()
        ));

        return RecurringTransactionResponse.from(
                recurringTransaction,
                history,
                details
        );
    }

    private Category resolveActiveCategory(
            UUID categoryId,
            UUID userGroupId
    ) {
        if (categoryId == null) {
            throw new IllegalArgumentException("finance.recurringTransaction.categoryRequired");
        }

        return categoryRepository.findActiveByCategoryIdAndUserGroupId(categoryId, userGroupId)
                .orElseThrow(() -> new ResourceNotFoundException("finance.category.notFound"));
    }

    private FinancialPriority resolveFinancialPriority(UUID financialPriorityId) {
        if (financialPriorityId == null) {
            throw new IllegalArgumentException("finance.recurringTransaction.financialPriorityRequired");
        }

        return financialPriorityRepository.findById(financialPriorityId)
                .orElseThrow(() -> new ResourceNotFoundException("finance.financialPriority.notFound"));
    }

    private CreditCard resolveCreditCard(
            UUID creditCardId,
            Account account,
            UUID userGroupId
    ) {
        if (creditCardId == null) {
            return null;
        }

        return creditCardRepository.findByCreditCardIdAndAccount_AccountIdAndUserGroup_UserGroupId(
                creditCardId,
                account.getAccountId(),
                userGroupId
        ).orElseThrow(() -> new ResourceNotFoundException("finance.creditCard.notFound"));
    }

    private Bucket resolveBucket(
            UUID bucketId,
            Account account,
            UUID userGroupId
    ) {
        if (bucketId == null) {
            return null;
        }

        Bucket bucket = bucketRepository.findByBucketIdAndUserGroup_UserGroupIdAndBucketClosedAtIsNull(
                bucketId,
                userGroupId
        ).orElseThrow(() -> new ResourceNotFoundException("finance.bucket.notFound"));

        boolean linked = bucketAccountRepository.existsLink(
                bucket.getBucketId(),
                account.getAccountId(),
                userGroupId
        );

        if (!linked) {
            throw new ResourceNotFoundException("finance.bucket.notFound");
        }

        return bucket;
    }

    private SimulationGroup resolveSimulationGroup(
            User currentUser,
            UUID userGroupId,
            Account account,
            boolean simulated,
            UUID simulationGroupId
    ) {
        if (!simulated) {
            if (simulationGroupId != null) {
                throw new IllegalArgumentException("finance.recurringTransaction.simulationGroupNotAllowed");
            }

            return null;
        }

        if (simulationGroupId == null) {
            throw new IllegalArgumentException("finance.recurringTransaction.simulationGroupRequired");
        }

        SimulationGroup simulationGroup = findOperableActiveSimulationGroup(
                currentUser,
                userGroupId,
                simulationGroupId
        );

        boolean linked = simulationGroupAccountRepository.existsLink(
                simulationGroup.getSimulationGroupId(),
                account.getAccountId(),
                userGroupId
        );

        if (!linked) {
            throw new ResourceNotFoundException("finance.simulationGroup.notFound");
        }

        return simulationGroup;
    }

    private SimulationGroup findOperableActiveSimulationGroup(
            User currentUser,
            UUID userGroupId,
            UUID simulationGroupId
    ) {
        if (transactionAccessService.canOperateOnAllAccounts(currentUser)) {
            return simulationGroupRepository
                    .findBySimulationGroupIdAndUserGroup_UserGroupIdAndSimulationGroupArchivedAtIsNull(
                            simulationGroupId,
                            userGroupId
                    )
                    .orElseThrow(() -> new ResourceNotFoundException("finance.simulationGroup.notFound"));
        }

        return simulationGroupRepository.findActiveOperableToLinkedUserById(
                simulationGroupId,
                userGroupId,
                currentUser.getUserId()
        ).orElseThrow(() -> new ResourceNotFoundException("finance.simulationGroup.notFound"));
    }

    private Short normalizeReminderDaysBefore(Integer value) {
        if (value == null) {
            return DEFAULT_REMINDER_DAYS_BEFORE;
        }

        if (value < 0 || value > 366) {
            throw new IllegalArgumentException("finance.recurringTransaction.reminderDaysInvalid");
        }

        return value.shortValue();
    }

    private Short normalizeRecurrenceInterval(Integer value) {
        if (value == null) {
            throw new IllegalArgumentException("finance.recurringTransaction.recurrenceIntervalRequired");
        }

        if (value < 1 || value > Short.MAX_VALUE) {
            throw new IllegalArgumentException("finance.recurringTransaction.recurrenceIntervalInvalid");
        }

        return value.shortValue();
    }

    private Short deriveDayOfUnit(
            RecurrenceUnit recurrenceUnit,
            LocalDate firstPaymentDate
    ) {
        if (recurrenceUnit == null) {
            throw new IllegalArgumentException("finance.recurringTransaction.recurrenceUnitRequired");
        }

        if (firstPaymentDate == null) {
            throw new IllegalArgumentException("finance.recurringTransaction.firstPaymentDateRequired");
        }

        return switch (recurrenceUnit) {
            case DAY -> (short) 1;
            case WEEK -> (short) firstPaymentDate.getDayOfWeek().getValue();
            case MONTH -> (short) firstPaymentDate.getDayOfMonth();
            case YEAR -> (short) firstPaymentDate.getDayOfYear();
        };
    }

    private boolean defaultFalse(Boolean value) {
        return value != null && value;
    }
}