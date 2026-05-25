package me.serenityline.api.finance.bucket.service;

import me.serenityline.api.common.error.ResourceNotFoundException;
import me.serenityline.api.finance.account.entity.Account;
import me.serenityline.api.finance.account.repository.AccountRepository;
import me.serenityline.api.finance.bucket.entity.Bucket;
import me.serenityline.api.finance.bucket.repository.BucketAccountRepository;
import me.serenityline.api.finance.bucket.repository.BucketRepository;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.entity.UserRole;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class BucketAccountLinkService {

    private final BucketRepository bucketRepository;
    private final BucketAccountRepository bucketAccountRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public BucketAccountLinkService(
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
    public void linkAccount(
            UUID currentUserId,
            UUID bucketId,
            UUID accountId
    ) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        Objects.requireNonNull(bucketId, "bucketId");
        Objects.requireNonNull(accountId, "accountId");

        User currentUser = findCurrentUser(currentUserId);
        UUID userGroupId = currentUser.getUserGroup().getUserGroupId();

        findLinkableBucket(currentUser, bucketId, userGroupId);
        findLinkableAccount(currentUser, accountId, userGroupId);

        bucketAccountRepository.insertIfMissing(
                bucketId,
                accountId,
                userGroupId
        );
    }

    @Transactional
    public void unlinkAccount(
            UUID currentUserId,
            UUID bucketId,
            UUID accountId
    ) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        Objects.requireNonNull(bucketId, "bucketId");
        Objects.requireNonNull(accountId, "accountId");

        User currentUser = findCurrentUser(currentUserId);
        UUID userGroupId = currentUser.getUserGroup().getUserGroupId();

        findLinkableBucket(currentUser, bucketId, userGroupId);
        findLinkableAccount(currentUser, accountId, userGroupId);

        boolean linkExists = bucketAccountRepository.existsByBucketIdAndAccountIdAndUserGroupId(
                bucketId,
                accountId,
                userGroupId
        );

        if (!linkExists) {
            return;
        }

        ensureBucketAccountCanBeDeleted(bucketId, accountId, userGroupId);

        bucketAccountRepository.deleteByBucketIdAndAccountIdAndUserGroupId(
                bucketId,
                accountId,
                userGroupId
        );
    }

    private Bucket findLinkableBucket(
            User currentUser,
            UUID bucketId,
            UUID userGroupId
    ) {
        if (canReadAllGroupBuckets(currentUser)) {
            return bucketRepository.findByBucketIdAndUserGroup_UserGroupIdAndBucketClosedAtIsNull(
                    bucketId,
                    userGroupId
            ).orElseThrow(() -> new ResourceNotFoundException("finance.bucket.notFound"));
        }

        return bucketRepository.findActiveVisibleForCollaborator(
                bucketId,
                userGroupId,
                currentUser.getUserId()
        ).orElseThrow(() -> new ResourceNotFoundException("finance.bucket.notFound"));
    }

    private Account findLinkableAccount(
            User currentUser,
            UUID accountId,
            UUID userGroupId
    ) {
        if (canManageAllGroupAccounts(currentUser)) {
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

    private void ensureBucketAccountCanBeDeleted(
            UUID bucketId,
            UUID accountId,
            UUID userGroupId
    ) {
        // TODO: quando implementiamo transactions / recurring transactions,
        // verificare se la coppia bucket-account è già stata usata.
        // Se è usata, non eliminare fisicamente il link e lanciare:
        // throw new IllegalStateException("finance.bucketAccount.alreadyUsed");
    }

    private User findCurrentUser(UUID currentUserId) {
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("finance.bucket.notFound"));

        if (!currentUser.isUserIsEnabled() || currentUser.isPendingDeletion()) {
            throw new ResourceNotFoundException("finance.bucket.notFound");
        }

        return currentUser;
    }

    private boolean canReadAllGroupBuckets(User user) {
        UserRole userRole = user.getUserRole();

        return userRole == UserRole.OWNER
                || userRole == UserRole.SUPER_COLLABORATOR
                || userRole == UserRole.VIEWER_COLLABORATOR;
    }

    private boolean canManageAllGroupAccounts(User user) {
        UserRole userRole = user.getUserRole();

        return userRole == UserRole.OWNER
                || userRole == UserRole.SUPER_COLLABORATOR;
    }
}