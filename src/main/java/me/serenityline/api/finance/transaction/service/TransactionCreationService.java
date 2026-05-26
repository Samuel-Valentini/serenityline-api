package me.serenityline.api.finance.transaction.service;

import me.serenityline.api.common.error.ResourceNotFoundException;
import me.serenityline.api.finance.account.entity.Account;
import me.serenityline.api.finance.account.repository.AccountRepository;
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
import me.serenityline.api.finance.transaction.dto.TransactionCreateRequest;
import me.serenityline.api.finance.transaction.dto.TransactionResponse;
import me.serenityline.api.finance.transaction.entity.Transaction;
import me.serenityline.api.finance.transaction.entity.TransactionUser;
import me.serenityline.api.finance.transaction.repository.TransactionRepository;
import me.serenityline.api.finance.transaction.repository.TransactionUserRepository;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.entity.UserRole;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class TransactionCreationService {

    private static final short DEFAULT_REMINDER_DAYS_BEFORE = 7;

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final CreditCardRepository creditCardRepository;
    private final BucketRepository bucketRepository;
    private final BucketAccountRepository bucketAccountRepository;
    private final SimulationGroupRepository simulationGroupRepository;
    private final SimulationGroupAccountRepository simulationGroupAccountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionUserRepository transactionUserRepository;

    public TransactionCreationService(
            UserRepository userRepository,
            AccountRepository accountRepository,
            CategoryRepository categoryRepository,
            CreditCardRepository creditCardRepository,
            BucketRepository bucketRepository,
            BucketAccountRepository bucketAccountRepository,
            SimulationGroupRepository simulationGroupRepository,
            SimulationGroupAccountRepository simulationGroupAccountRepository,
            TransactionRepository transactionRepository,
            TransactionUserRepository transactionUserRepository
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
        this.categoryRepository = Objects.requireNonNull(categoryRepository, "categoryRepository");
        this.creditCardRepository = Objects.requireNonNull(creditCardRepository, "creditCardRepository");
        this.bucketRepository = Objects.requireNonNull(bucketRepository, "bucketRepository");
        this.bucketAccountRepository = Objects.requireNonNull(bucketAccountRepository, "bucketAccountRepository");
        this.simulationGroupRepository = Objects.requireNonNull(simulationGroupRepository, "simulationGroupRepository");
        this.simulationGroupAccountRepository = Objects.requireNonNull(simulationGroupAccountRepository, "simulationGroupAccountRepository");
        this.transactionRepository = Objects.requireNonNull(transactionRepository, "transactionRepository");
        this.transactionUserRepository = Objects.requireNonNull(transactionUserRepository, "transactionUserRepository");
    }

    @Transactional
    public TransactionResponse createTransaction(
            UUID currentUserId,
            TransactionCreateRequest request
    ) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        Objects.requireNonNull(request, "request");

        User currentUser = findCurrentUser(currentUserId);
        UUID userGroupId = currentUser.getUserGroup().getUserGroupId();

        Account account = resolveOperableAccount(
                currentUser,
                userGroupId,
                request.accountId()
        );

        Category category = resolveActiveCategory(
                request.categoryId(),
                userGroupId
        );

        CreditCard creditCard = resolveCreditCard(
                request.creditCardId(),
                account,
                userGroupId
        );

        Bucket bucket = resolveBucket(
                request.bucketId(),
                account,
                userGroupId
        );

        boolean simulated = defaultFalse(request.transactionIsSimulated());

        SimulationGroup simulationGroup = resolveSimulationGroup(
                currentUser,
                userGroupId,
                account,
                simulated,
                request.simulationGroupId()
        );

        Transaction transaction = Transaction.createUserEntered(
                request.transactionDescription(),
                request.transactionAmount(),
                request.transactionAffectsAccountBalance(),
                request.transactionAffectsLiquidity(),
                category,
                request.transactionChargeDate(),
                request.transactionIsConfirmed(),
                account,
                creditCard,
                bucket,
                simulated,
                simulationGroup,
                request.transactionReminderEnabled(),
                normalizeReminderDaysBefore(request.transactionReminderDaysBefore()),
                currentUser.getUserGroup()
        );

        transaction = transactionRepository.saveAndFlush(transaction);

        transactionUserRepository.save(TransactionUser.link(
                transaction,
                currentUser,
                currentUser.getUserGroup()
        ));

        return TransactionResponse.from(transaction);
    }

    private User findCurrentUser(UUID currentUserId) {
        return userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("user.notFound"));
    }

    private Account resolveOperableAccount(
            User currentUser,
            UUID userGroupId,
            UUID accountId
    ) {
        if (accountId == null) {
            throw new IllegalArgumentException("finance.transaction.accountRequired");
        }

        if (canOperateOnAllAccounts(currentUser)) {
            return accountRepository.findByAccountIdAndUserGroup_UserGroupId(accountId, userGroupId)
                    .orElseThrow(() -> new ResourceNotFoundException("finance.account.notFound"));
        }

        if (currentUser.getUserRole() == UserRole.VIEWER_COLLABORATOR) {
            Account account = accountRepository.findByAccountIdAndUserGroup_UserGroupId(accountId, userGroupId)
                    .orElseThrow(() -> new ResourceNotFoundException("finance.account.notFound"));

            accountRepository.findVisibleAccountForLinkedUser(
                            accountId,
                            userGroupId,
                            currentUser.getUserId()
                    )
                    .orElseThrow(() -> new AccessDeniedException("finance.account.operationNotAllowed"));

            return account;
        }

        return accountRepository.findVisibleAccountForLinkedUser(
                        accountId,
                        userGroupId,
                        currentUser.getUserId()
                )
                .orElseThrow(() -> new ResourceNotFoundException("finance.account.notFound"));
    }

    private Category resolveActiveCategory(
            UUID categoryId,
            UUID userGroupId
    ) {
        if (categoryId == null) {
            throw new IllegalArgumentException("finance.transaction.categoryRequired");
        }

        return categoryRepository.findActiveByCategoryIdAndUserGroupId(categoryId, userGroupId)
                .orElseThrow(() -> new ResourceNotFoundException("finance.category.notFound"));
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
                )
                .orElseThrow(() -> new ResourceNotFoundException("finance.creditCard.notFound"));
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
                )
                .orElseThrow(() -> new ResourceNotFoundException("finance.bucket.notFound"));

        boolean bucketLinkedToAccount = bucketAccountRepository.existsLink(
                bucket.getBucketId(),
                account.getAccountId(),
                userGroupId
        );

        if (!bucketLinkedToAccount) {
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
                throw new IllegalArgumentException("finance.transaction.simulationGroupNotAllowed");
            }

            return null;
        }

        if (simulationGroupId == null) {
            throw new IllegalArgumentException("finance.transaction.simulationGroupRequired");
        }

        SimulationGroup simulationGroup = findOperableActiveSimulationGroup(
                currentUser,
                userGroupId,
                simulationGroupId
        );

        boolean accountLinkedToSimulationGroup = simulationGroupAccountRepository.existsLink(
                simulationGroup.getSimulationGroupId(),
                account.getAccountId(),
                userGroupId
        );

        if (!accountLinkedToSimulationGroup) {
            throw new ResourceNotFoundException("finance.simulationGroup.notFound");
        }

        return simulationGroup;
    }

    private SimulationGroup findOperableActiveSimulationGroup(
            User currentUser,
            UUID userGroupId,
            UUID simulationGroupId
    ) {
        if (canOperateOnAllAccounts(currentUser)) {
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
                )
                .orElseThrow(() -> new ResourceNotFoundException("finance.simulationGroup.notFound"));
    }

    private Short normalizeReminderDaysBefore(Integer value) {
        if (value == null) {
            return DEFAULT_REMINDER_DAYS_BEFORE;
        }

        if (value < 0 || value > 366) {
            throw new IllegalArgumentException("finance.transaction.reminderDaysInvalid");
        }

        return value.shortValue();
    }

    private boolean defaultFalse(Boolean value) {
        return value != null && value;
    }

    private boolean canOperateOnAllAccounts(User user) {
        return user.getUserRole() == UserRole.OWNER
                || user.getUserRole() == UserRole.SUPER_COLLABORATOR;
    }
}