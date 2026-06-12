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
import me.serenityline.api.finance.simulation.entity.SimulationGroup;
import me.serenityline.api.finance.simulation.repository.SimulationGroupAccountRepository;
import me.serenityline.api.finance.simulation.repository.SimulationGroupRepository;
import me.serenityline.api.finance.transaction.dto.TransactionResponse;
import me.serenityline.api.finance.transaction.dto.TransactionUpdateRequest;
import me.serenityline.api.finance.transaction.entity.Transaction;
import me.serenityline.api.finance.transaction.repository.TransactionRepository;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class TransactionUpdateService {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final CreditCardRepository creditCardRepository;
    private final BucketRepository bucketRepository;
    private final BucketAccountRepository bucketAccountRepository;
    private final SimulationGroupRepository simulationGroupRepository;
    private final SimulationGroupAccountRepository simulationGroupAccountRepository;
    private final TransactionAccessService transactionAccessService;
    private final FinanceMovementAssociationValidator associationValidator;

    public TransactionUpdateService(
            UserRepository userRepository,
            TransactionRepository transactionRepository,
            CategoryRepository categoryRepository,
            CreditCardRepository creditCardRepository,
            BucketRepository bucketRepository,
            BucketAccountRepository bucketAccountRepository,
            SimulationGroupRepository simulationGroupRepository,
            SimulationGroupAccountRepository simulationGroupAccountRepository,
            TransactionAccessService transactionAccessService,
            FinanceMovementAssociationValidator associationValidator
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.transactionRepository = Objects.requireNonNull(transactionRepository, "transactionRepository");
        this.categoryRepository = Objects.requireNonNull(categoryRepository, "categoryRepository");
        this.creditCardRepository = Objects.requireNonNull(creditCardRepository, "creditCardRepository");
        this.bucketRepository = Objects.requireNonNull(bucketRepository, "bucketRepository");
        this.bucketAccountRepository = Objects.requireNonNull(bucketAccountRepository, "bucketAccountRepository");
        this.simulationGroupRepository = Objects.requireNonNull(simulationGroupRepository, "simulationGroupRepository");
        this.simulationGroupAccountRepository = Objects.requireNonNull(simulationGroupAccountRepository, "simulationGroupAccountRepository");
        this.transactionAccessService = Objects.requireNonNull(transactionAccessService, "transactionAccessService");
        this.associationValidator = Objects.requireNonNull(associationValidator, "associationValidator");
    }

    @Transactional
    public TransactionResponse updateTransaction(
            UUID currentUserId,
            UUID transactionId,
            TransactionUpdateRequest request
    ) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(request, "request");

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("user.notFound"));

        UUID userGroupId = currentUser.getUserGroup().getUserGroupId();

        Transaction transaction = transactionAccessService.findOperableTransaction(
                currentUser,
                userGroupId,
                transactionId
        );

        Account account = transactionAccessService.findOperableAccount(
                currentUser,
                userGroupId,
                request.accountId()
        );

        Category category = resolveCategoryForUpdate(
                transaction,
                request.categoryId(),
                userGroupId
        );

        CreditCard creditCard = resolveCreditCardForUpdate(
                transaction,
                request.creditCardId(),
                account,
                userGroupId
        );

        Bucket bucket = resolveBucketForUpdate(
                transaction,
                request.bucketId(),
                account,
                userGroupId
        );

        associationValidator.validateTransactionCreditCardBucketExclusion(
                creditCard,
                bucket
        );

        SimulationGroup simulationGroup = resolveSimulationGroupForUpdate(
                currentUser,
                userGroupId,
                transaction,
                account,
                request.transactionIsSimulated(),
                request.simulationGroupId()
        );

        transaction.update(
                request.transactionDescription(),
                request.transactionAmount(),
                request.transactionAffectsAccountBalance(),
                request.transactionAffectsSerenityline(),
                category,
                request.transactionChargeDate(),
                request.transactionIsConfirmed(),
                account,
                creditCard,
                bucket,
                request.transactionIsSimulated(),
                simulationGroup,
                request.transactionReminderEnabled(),
                request.transactionReminderDaysBefore().shortValue()
        );

        Transaction saved = transactionRepository.saveAndFlush(transaction);

        return TransactionResponse.from(saved);
    }

    private Category resolveCategoryForUpdate(
            Transaction transaction,
            UUID categoryId,
            UUID userGroupId
    ) {
        UUID currentCategoryId = transaction.getCategory().getCategoryId();

        if (currentCategoryId.equals(categoryId)) {
            return transaction.getCategory();
        }

        return categoryRepository.findActiveByCategoryIdAndUserGroupId(categoryId, userGroupId)
                .orElseThrow(() -> new ResourceNotFoundException("finance.category.notFound"));
    }

    private CreditCard resolveCreditCardForUpdate(
            Transaction transaction,
            UUID creditCardId,
            Account account,
            UUID userGroupId
    ) {
        if (creditCardId == null) {
            return null;
        }

        CreditCard currentCreditCard = transaction.getCreditCard();

        boolean sameCreditCard = currentCreditCard != null
                && currentCreditCard.getCreditCardId().equals(creditCardId);
        boolean sameAccount = transaction.getAccount().getAccountId().equals(account.getAccountId());

        if (sameCreditCard && sameAccount) {
            return currentCreditCard;
        }

        return creditCardRepository.findByCreditCardIdAndAccount_AccountIdAndUserGroup_UserGroupId(
                creditCardId,
                account.getAccountId(),
                userGroupId
        ).orElseThrow(() -> new ResourceNotFoundException("finance.creditCard.notFound"));
    }

    private Bucket resolveBucketForUpdate(
            Transaction transaction,
            UUID bucketId,
            Account account,
            UUID userGroupId
    ) {
        if (bucketId == null) {
            return null;
        }

        Bucket currentBucket = transaction.getBucket();

        boolean sameBucket = currentBucket != null
                && currentBucket.getBucketId().equals(bucketId);
        boolean sameAccount = transaction.getAccount().getAccountId().equals(account.getAccountId());

        if (sameBucket && sameAccount) {
            return currentBucket;
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

    private SimulationGroup resolveSimulationGroupForUpdate(
            User currentUser,
            UUID userGroupId,
            Transaction transaction,
            Account account,
            boolean simulated,
            UUID simulationGroupId
    ) {
        if (!simulated) {
            if (simulationGroupId != null) {
                throw new IllegalArgumentException("finance.transaction.simulationGroupNotAllowed");
            }

            return null;
        }

        if (simulationGroupId == null) {
            throw new IllegalArgumentException("finance.transaction.simulationGroupRequired");
        }

        SimulationGroup currentSimulationGroup = transaction.getSimulationGroup();

        boolean sameSimulationGroup = currentSimulationGroup != null
                && currentSimulationGroup.getSimulationGroupId().equals(simulationGroupId);
        boolean sameAccount = transaction.getAccount().getAccountId().equals(account.getAccountId());

        if (sameSimulationGroup && sameAccount) {
            return currentSimulationGroup;
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
}