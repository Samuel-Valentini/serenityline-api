package me.serenityline.api.finance.bucket.service;

import me.serenityline.api.common.error.ResourceNotFoundException;
import me.serenityline.api.finance.account.entity.Account;
import me.serenityline.api.finance.account.repository.AccountRepository;
import me.serenityline.api.finance.bucket.entity.Bucket;
import me.serenityline.api.finance.bucket.entity.BucketAccount;
import me.serenityline.api.finance.bucket.repository.BucketAccountRepository;
import me.serenityline.api.finance.bucket.repository.BucketRepository;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.entity.UserRole;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class BucketCreationService {

    private static final int MAX_BUCKET_NAME_LENGTH = 255;
    private static final int MAX_BUCKET_DESCRIPTION_LENGTH = 2000;

    private final BucketRepository bucketRepository;
    private final BucketAccountRepository bucketAccountRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public BucketCreationService(
            BucketRepository bucketRepository,
            BucketAccountRepository bucketAccountRepository,
            AccountRepository accountRepository,
            UserRepository userRepository
    ) {
        this.bucketRepository = Objects.requireNonNull(bucketRepository, "bucketRepository");
        this.bucketAccountRepository = Objects.requireNonNull(bucketAccountRepository, "bucketAccountRepository");
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
    }

    @Transactional
    public BucketCreationResult createBucket(UUID currentUserId, CreateBucketCommand command) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        Objects.requireNonNull(command, "command");

        User currentUser = findCurrentUser(currentUserId);
        UUID userGroupId = currentUser.getUserGroup().getUserGroupId();

        List<UUID> accountIds = normalizeAccountIds(command.accountIds());

        if (currentUser.getUserRole() == UserRole.COLLABORATOR && accountIds.isEmpty()) {
            throw new IllegalArgumentException("finance.bucket.accountRequiredForCollaborator");
        }

        List<Account> linkedAccounts = findLinkableAccounts(currentUser, accountIds);

        String bucketName = normalizeBucketNameForStorage(command.bucketName());
        String normalizedBucketName = normalizeBucketNameForComparison(bucketName);

        if (bucketRepository.existsActiveByUserGroupIdAndNormalizedBucketName(
                userGroupId,
                normalizedBucketName
        )) {
            throw duplicateBucketNameException(currentUser);
        }

        String bucketDescription = normalizeOptionalText(
                command.bucketDescription(),
                MAX_BUCKET_DESCRIPTION_LENGTH,
                "finance.bucket.description.tooLong"
        );

        try {
            Bucket bucket = bucketRepository.saveAndFlush(Bucket.create(
                    bucketName,
                    bucketDescription,
                    currentUser.getUserGroup()
            ));

            List<BucketAccount> bucketAccounts = linkedAccounts.stream()
                    .map(account -> BucketAccount.link(
                            bucket,
                            account,
                            currentUser.getUserGroup()
                    ))
                    .toList();

            bucketAccountRepository.saveAllAndFlush(bucketAccounts);

            List<UUID> linkedAccountIds = linkedAccounts.stream()
                    .map(Account::getAccountId)
                    .toList();

            return new BucketCreationResult(
                    bucket,
                    linkedAccountIds
            );
        } catch (DataIntegrityViolationException exception) {
            throw duplicateBucketNameException(currentUser, exception);
        }
    }

    private User findCurrentUser(UUID currentUserId) {
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("finance.bucket.notFound"));

        if (!currentUser.isUserIsEnabled() || currentUser.isPendingDeletion()) {
            throw new ResourceNotFoundException("finance.bucket.notFound");
        }

        return currentUser;
    }

    private List<Account> findLinkableAccounts(User currentUser, List<UUID> accountIds) {
        List<Account> linkedAccounts = new ArrayList<>();

        for (UUID accountId : accountIds) {
            linkedAccounts.add(findLinkableAccount(currentUser, accountId));
        }

        return linkedAccounts;
    }

    private Account findLinkableAccount(User currentUser, UUID accountId) {
        UUID userGroupId = currentUser.getUserGroup().getUserGroupId();

        if (canLinkAllGroupAccounts(currentUser)) {
            return accountRepository.findByAccountIdAndUserGroup_UserGroupId(
                    accountId,
                    userGroupId
            ).orElseThrow(() -> new ResourceNotFoundException("finance.account.notFound"));
        }

        return accountRepository.findVisibleAccountForLinkedUser(
                accountId,
                userGroupId,
                currentUser.getUserId()
        ).orElseThrow(() -> new ResourceNotFoundException("finance.account.notFound"));
    }

    private boolean canLinkAllGroupAccounts(User user) {
        UserRole userRole = user.getUserRole();

        return userRole == UserRole.OWNER
                || userRole == UserRole.SUPER_COLLABORATOR;
    }

    private List<UUID> normalizeAccountIds(List<UUID> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<UUID> uniqueAccountIds = new LinkedHashSet<>();

        for (UUID accountId : accountIds) {
            if (accountId == null) {
                throw new IllegalArgumentException("finance.bucket.accountId.required");
            }

            uniqueAccountIds.add(accountId);
        }

        return List.copyOf(uniqueAccountIds);
    }

    private String normalizeBucketNameForStorage(String value) {
        String normalized = normalizeRequiredText(
                value,
                "finance.bucket.name.required"
        );

        validateMaxLength(
                normalized,
                MAX_BUCKET_NAME_LENGTH,
                "finance.bucket.name.tooLong"
        );

        return normalized;
    }

    private String normalizeBucketNameForComparison(String value) {
        return value
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private String normalizeOptionalText(String value, int maxLength, String tooLongMessageKey) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        if (normalized.isBlank()) {
            return null;
        }

        validateMaxLength(normalized, maxLength, tooLongMessageKey);

        return normalized;
    }

    private String normalizeRequiredText(String value, String requiredMessageKey) {
        if (value == null) {
            throw new IllegalArgumentException(requiredMessageKey);
        }

        String normalized = value.trim();

        if (normalized.isBlank()) {
            throw new IllegalArgumentException(requiredMessageKey);
        }

        return normalized;
    }

    private void validateMaxLength(String value, int maxLength, String tooLongMessageKey) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(tooLongMessageKey);
        }
    }

    private RuntimeException duplicateBucketNameException(User currentUser) {
        return duplicateBucketNameException(currentUser, null);
    }

    private RuntimeException duplicateBucketNameException(User currentUser, Throwable cause) {
        if (currentUser.getUserRole() == UserRole.COLLABORATOR) {
            if (cause == null) {
                return new IllegalArgumentException("finance.bucket.nameNotAllowed");
            }

            return new IllegalArgumentException("finance.bucket.nameNotAllowed", cause);
        }

        if (cause == null) {
            return new IllegalStateException("finance.bucket.nameAlreadyExists");
        }

        return new IllegalStateException("finance.bucket.nameAlreadyExists", cause);
    }
}