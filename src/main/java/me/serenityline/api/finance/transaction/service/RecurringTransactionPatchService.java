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
import me.serenityline.api.finance.transaction.dto.*;
import me.serenityline.api.finance.transaction.entity.RecurringTransaction;
import me.serenityline.api.finance.transaction.entity.RecurringTransactionDetailsHistory;
import me.serenityline.api.finance.transaction.entity.RecurringTransactionHistory;
import me.serenityline.api.finance.transaction.repository.RecurringTransactionDetailsHistoryRepository;
import me.serenityline.api.finance.transaction.repository.RecurringTransactionHistoryRepository;
import me.serenityline.api.finance.transaction.repository.RecurringTransactionRepository;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Service
public class RecurringTransactionPatchService {

    private final RecurringTransactionPatchParser patchParser;
    private final UserRepository userRepository;
    private final RecurringTransactionAccessService recurringTransactionAccessService;
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

    public RecurringTransactionPatchService(
            RecurringTransactionPatchParser patchParser,
            UserRepository userRepository,
            RecurringTransactionAccessService recurringTransactionAccessService,
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
            RecurringTransactionDetailsHistoryRepository recurringTransactionDetailsHistoryRepository
    ) {
        this.patchParser = Objects.requireNonNull(patchParser, "patchParser");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.recurringTransactionAccessService = Objects.requireNonNull(recurringTransactionAccessService);
        this.transactionAccessService = Objects.requireNonNull(transactionAccessService);
        this.categoryRepository = Objects.requireNonNull(categoryRepository);
        this.financialPriorityRepository = Objects.requireNonNull(financialPriorityRepository);
        this.creditCardRepository = Objects.requireNonNull(creditCardRepository);
        this.bucketRepository = Objects.requireNonNull(bucketRepository);
        this.bucketAccountRepository = Objects.requireNonNull(bucketAccountRepository);
        this.simulationGroupRepository = Objects.requireNonNull(simulationGroupRepository);
        this.simulationGroupAccountRepository = Objects.requireNonNull(simulationGroupAccountRepository);
        this.recurringTransactionRepository = Objects.requireNonNull(recurringTransactionRepository);
        this.recurringTransactionHistoryRepository = Objects.requireNonNull(recurringTransactionHistoryRepository);
        this.recurringTransactionDetailsHistoryRepository = Objects.requireNonNull(recurringTransactionDetailsHistoryRepository);
    }

    @Transactional
    public RecurringTransactionResponse patchRecurringTransaction(
            UUID currentUserId,
            UUID recurringTransactionId,
            JsonNode body
    ) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        Objects.requireNonNull(recurringTransactionId, "recurringTransactionId");

        RecurringTransactionPatchCommand command = patchParser.parse(body);

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("user.notFound"));

        UUID userGroupId = currentUser.getUserGroup().getUserGroupId();

        RecurringTransaction recurringTransaction = recurringTransactionAccessService
                .findOperableRecurringTransaction(
                        currentUser,
                        userGroupId,
                        recurringTransactionId
                );

        RecurringTransactionHistory currentOpenHistory = recurringTransactionHistoryRepository
                .findCurrentOpenByRecurringTransactionId(recurringTransaction.getRecurringTransactionId())
                .orElseThrow(() -> new ResourceNotFoundException("finance.recurringTransaction.historyNotFound"));

        RecurringTransactionDetailsHistory currentDetails = recurringTransactionDetailsHistoryRepository
                .findCurrentByRecurringTransactionIdAndUserGroupId(
                        recurringTransaction.getRecurringTransactionId(),
                        userGroupId
                )
                .orElseThrow(() -> new ResourceNotFoundException("finance.recurringTransaction.detailsNotFound"));

        RecurringTransactionDetailsHistory responseDetails = currentDetails;
        Account accountForSimulationValidation = currentDetails.getLinkedAccount();

        if (command.hasDetailsPatch()) {
            responseDetails = insertDetailsHistory(
                    currentUser,
                    userGroupId,
                    recurringTransaction,
                    currentDetails,
                    command.details()
            );

            accountForSimulationValidation = responseDetails.getLinkedAccount();
        }

        SimulationGroup simulationGroup = resolveSimulationGroup(
                currentUser,
                userGroupId,
                accountForSimulationValidation,
                command,
                recurringTransaction
        );

        recurringTransaction.updateMutableSettings(
                valueOr(
                        command.recurringTransactionFirstPaymentDate(),
                        recurringTransaction.getRecurringTransactionFirstPaymentDate()
                ),
                valueOr(
                        command.recurringTransactionAmountIsAdjustable(),
                        recurringTransaction.isRecurringTransactionAmountIsAdjustable()
                ),
                valueOr(
                        command.recurringTransactionIsSimulated(),
                        recurringTransaction.isRecurringTransactionIsSimulated()
                ),
                simulationGroup,
                valueOr(
                        command.recurringTransactionReminderEnabled(),
                        recurringTransaction.isRecurringTransactionReminderEnabled()
                ),
                valueOr(
                        command.recurringTransactionReminderDaysBefore(),
                        (int) recurringTransaction.getRecurringTransactionReminderDaysBefore()
                ).shortValue()
        );

        RecurringTransactionHistory responseHistory = currentOpenHistory;

        if (command.hasRulePatch()) {
            responseHistory = insertRuleHistory(
                    recurringTransaction,
                    currentOpenHistory,
                    command.rule()
            );
        }

        recurringTransactionRepository.save(recurringTransaction);

        return RecurringTransactionResponse.from(
                recurringTransaction,
                responseHistory,
                responseDetails
        );
    }

    private RecurringTransactionHistory insertRuleHistory(
            RecurringTransaction recurringTransaction,
            RecurringTransactionHistory currentOpenHistory,
            RecurringTransactionRulePatchCommand rulePatch
    ) {
        LocalDate effectiveFrom = valueOr(
                rulePatch.effectiveFrom(),
                currentOpenHistory.getEffectiveFrom()
        );

        RecurringTransactionHistory baseHistory = recurringTransactionHistoryRepository
                .findEffectiveAt(
                        recurringTransaction.getRecurringTransactionId(),
                        effectiveFrom
                )
                .orElse(currentOpenHistory);

        LocalDate effectiveTo = valueOr(rulePatch.effectiveTo(), baseHistory.getEffectiveTo());

        short recurrenceInterval = valueOr(
                rulePatch.recurrenceInterval(),
                (int) baseHistory.getRecurrenceInterval()
        ).shortValue();

        var recurrenceUnit = valueOr(
                rulePatch.recurrenceUnit(),
                baseHistory.getRecurrenceUnit()
        );

        short dayOfUnit = valueOr(
                rulePatch.dayOfUnit(),
                (int) baseHistory.getDayOfUnit()
        ).shortValue();

        RecurringTransactionHistory newHistory = RecurringTransactionHistory.create(
                recurringTransaction,
                effectiveFrom,
                effectiveTo,
                dayOfUnit,
                recurrenceInterval,
                recurrenceUnit,
                valueOr(
                        rulePatch.paymentDateAdjustmentPolicy(),
                        baseHistory.getPaymentDateAdjustmentPolicy()
                ),
                valueOr(rulePatch.paymentAmount(), baseHistory.getPaymentAmount()),
                valueOr(
                        rulePatch.recurringTransactionEndDate(),
                        baseHistory.getRecurringTransactionEndDate()
                ),
                valueOr(rulePatch.finalPaymentAmount(), baseHistory.getFinalPaymentAmount())
        );

        if (effectiveTo == null && effectiveFrom.isAfter(currentOpenHistory.getEffectiveFrom())) {
            currentOpenHistory.closeAt(effectiveFrom);
        }

        return recurringTransactionHistoryRepository.save(newHistory);
    }

    private RecurringTransactionDetailsHistory insertDetailsHistory(
            User currentUser,
            UUID userGroupId,
            RecurringTransaction recurringTransaction,
            RecurringTransactionDetailsHistory currentDetails,
            RecurringTransactionDetailsPatchCommand detailsPatch
    ) {
        LocalDate effectiveFrom = valueOr(
                detailsPatch.effectiveFrom(),
                currentDetails.getRecurringTransactionDetailsEffectiveFrom()
        );

        RecurringTransactionDetailsHistory baseDetails = recurringTransactionDetailsHistoryRepository
                .findEffectiveAt(
                        recurringTransaction.getRecurringTransactionId(),
                        userGroupId,
                        effectiveFrom
                )
                .orElse(currentDetails);

        Account linkedAccount = resolveLinkedAccount(
                currentUser,
                userGroupId,
                detailsPatch.linkedAccountId(),
                baseDetails
        );

        Category category = resolveCategory(
                userGroupId,
                detailsPatch.categoryId(),
                baseDetails
        );

        FinancialPriority financialPriority = resolveFinancialPriority(
                detailsPatch.financialPriorityId(),
                baseDetails
        );

        CreditCard linkedCreditCard = resolveCreditCard(
                detailsPatch.linkedCreditCardId(),
                baseDetails,
                linkedAccount,
                userGroupId
        );

        Bucket linkedBucket = resolveBucket(
                detailsPatch.linkedBucketId(),
                baseDetails,
                linkedAccount,
                userGroupId
        );

        RecurringTransactionDetailsHistory newDetails = RecurringTransactionDetailsHistory.create(
                recurringTransaction,
                valueOr(
                        detailsPatch.recurringTransactionDescription(),
                        baseDetails.getRecurringTransactionDescription()
                ),
                category,
                financialPriority,
                linkedAccount,
                linkedCreditCard,
                linkedBucket,
                valueOr(
                        detailsPatch.recurringTransactionAffectsAccountBalance(),
                        baseDetails.isRecurringTransactionAffectsAccountBalance()
                ),
                valueOr(
                        detailsPatch.recurringTransactionAffectsLiquidity(),
                        baseDetails.isRecurringTransactionAffectsLiquidity()
                ),
                effectiveFrom,
                recurringTransaction.getUserGroup()
        );

        return recurringTransactionDetailsHistoryRepository.save(newDetails);
    }

    private Account resolveLinkedAccount(
            User currentUser,
            UUID userGroupId,
            PatchField<UUID> linkedAccountPatch,
            RecurringTransactionDetailsHistory baseDetails
    ) {
        UUID linkedAccountId = valueOr(
                linkedAccountPatch,
                baseDetails.getLinkedAccount().getAccountId()
        );

        return transactionAccessService.findOperableAccount(
                currentUser,
                userGroupId,
                linkedAccountId
        );
    }

    private Category resolveCategory(
            UUID userGroupId,
            PatchField<UUID> categoryPatch,
            RecurringTransactionDetailsHistory baseDetails
    ) {
        if (!categoryPatch.isPresent()) {
            return baseDetails.getCategory();
        }

        return categoryRepository.findActiveByCategoryIdAndUserGroupId(
                categoryPatch.value(),
                userGroupId
        ).orElseThrow(() -> new ResourceNotFoundException("finance.category.notFound"));
    }

    private FinancialPriority resolveFinancialPriority(
            PatchField<UUID> financialPriorityPatch,
            RecurringTransactionDetailsHistory baseDetails
    ) {
        if (!financialPriorityPatch.isPresent()) {
            return baseDetails.getFinancialPriority();
        }

        return financialPriorityRepository.findById(financialPriorityPatch.value())
                .orElseThrow(() -> new ResourceNotFoundException("finance.financialPriority.notFound"));
    }

    private CreditCard resolveCreditCard(
            PatchField<UUID> creditCardPatch,
            RecurringTransactionDetailsHistory baseDetails,
            Account linkedAccount,
            UUID userGroupId
    ) {
        UUID creditCardId;

        if (creditCardPatch.isPresent()) {
            creditCardId = creditCardPatch.value();
        } else {
            creditCardId = baseDetails.getLinkedCreditCard() == null
                    ? null
                    : baseDetails.getLinkedCreditCard().getCreditCardId();
        }

        if (creditCardId == null) {
            return null;
        }

        return creditCardRepository
                .findByCreditCardIdAndAccount_AccountIdAndUserGroup_UserGroupId(
                        creditCardId,
                        linkedAccount.getAccountId(),
                        userGroupId
                )
                .orElseThrow(() -> new ResourceNotFoundException("finance.creditCard.notFound"));
    }

    private Bucket resolveBucket(
            PatchField<UUID> bucketPatch,
            RecurringTransactionDetailsHistory baseDetails,
            Account linkedAccount,
            UUID userGroupId
    ) {
        UUID bucketId;

        if (bucketPatch.isPresent()) {
            bucketId = bucketPatch.value();
        } else {
            bucketId = baseDetails.getLinkedBucket() == null
                    ? null
                    : baseDetails.getLinkedBucket().getBucketId();
        }

        if (bucketId == null) {
            return null;
        }

        Bucket bucket = bucketRepository
                .findByBucketIdAndUserGroup_UserGroupIdAndBucketClosedAtIsNull(
                        bucketId,
                        userGroupId
                )
                .orElseThrow(() -> new ResourceNotFoundException("finance.bucket.notFound"));

        boolean linkedToAccount = bucketAccountRepository.existsLink(
                bucket.getBucketId(),
                linkedAccount.getAccountId(),
                userGroupId
        );

        if (!linkedToAccount) {
            throw new ResourceNotFoundException("finance.bucket.notFound");
        }

        return bucket;
    }

    private SimulationGroup resolveSimulationGroup(
            User currentUser,
            UUID userGroupId,
            Account linkedAccount,
            RecurringTransactionPatchCommand command,
            RecurringTransaction recurringTransaction
    ) {
        boolean simulated = valueOr(
                command.recurringTransactionIsSimulated(),
                recurringTransaction.isRecurringTransactionIsSimulated()
        );

        if (!simulated) {
            if (command.simulationGroupId().isPresent()
                    && command.simulationGroupId().value() != null) {
                throw new IllegalArgumentException("finance.recurringTransaction.simulationGroupNotAllowed");
            }

            return null;
        }

        UUID simulationGroupId;

        if (command.simulationGroupId().isPresent()) {
            simulationGroupId = command.simulationGroupId().value();
        } else {
            simulationGroupId = recurringTransaction.getSimulationGroup() == null
                    ? null
                    : recurringTransaction.getSimulationGroup().getSimulationGroupId();
        }

        if (simulationGroupId == null) {
            throw new IllegalArgumentException("finance.recurringTransaction.simulationGroupRequired");
        }

        SimulationGroup simulationGroup;

        if (recurringTransactionAccessService.canOperateOnAllGroupRecurringTransactions(currentUser)) {
            simulationGroup = simulationGroupRepository
                    .findBySimulationGroupIdAndUserGroup_UserGroupIdAndSimulationGroupArchivedAtIsNull(
                            simulationGroupId,
                            userGroupId
                    )
                    .orElseThrow(() -> new ResourceNotFoundException("finance.simulationGroup.notFound"));
        } else {
            simulationGroup = simulationGroupRepository
                    .findActiveOperableToLinkedUserById(
                            simulationGroupId,
                            userGroupId,
                            currentUser.getUserId()
                    )
                    .orElseThrow(() -> new ResourceNotFoundException("finance.simulationGroup.notFound"));
        }

        boolean linkedToAccount = simulationGroupAccountRepository.existsLink(
                simulationGroup.getSimulationGroupId(),
                linkedAccount.getAccountId(),
                userGroupId
        );

        if (!linkedToAccount) {
            throw new ResourceNotFoundException("finance.simulationGroup.notFound");
        }

        return simulationGroup;
    }

    private short deriveDayOfUnit(
            LocalDate effectiveFrom,
            me.serenityline.api.finance.transaction.entity.RecurrenceUnit recurrenceUnit
    ) {
        return switch (recurrenceUnit) {
            case DAY -> 1;
            case WEEK -> (short) effectiveFrom.getDayOfWeek().getValue();
            case MONTH -> (short) effectiveFrom.getDayOfMonth();
            case YEAR -> (short) effectiveFrom.getDayOfYear();
        };
    }

    private <T> T valueOr(PatchField<T> patchField, T fallback) {
        return patchField.isPresent()
                ? patchField.value()
                : fallback;
    }
}