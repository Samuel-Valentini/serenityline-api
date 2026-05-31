package me.serenityline.api.finance.bucket.service;

import me.serenityline.api.common.error.ResourceNotFoundException;
import me.serenityline.api.finance.bucket.entity.Bucket;
import me.serenityline.api.finance.bucket.repository.BucketAccountRepository;
import me.serenityline.api.finance.bucket.repository.BucketRepository;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.entity.UserRole;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class BucketLifecycleService {

    private final BucketRepository bucketRepository;
    private final BucketAccountRepository bucketAccountRepository;
    private final UserRepository userRepository;
    private final BucketBalanceCalculator bucketBalanceCalculator;

    public BucketLifecycleService(
            BucketRepository bucketRepository,
            BucketAccountRepository bucketAccountRepository,
            UserRepository userRepository,
            BucketBalanceCalculator bucketBalanceCalculator
    ) {
        this.bucketRepository = Objects.requireNonNull(bucketRepository, "bucketRepository");
        this.bucketAccountRepository = Objects.requireNonNull(bucketAccountRepository, "bucketAccountRepository");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.bucketBalanceCalculator = Objects.requireNonNull(bucketBalanceCalculator, "bucketBalanceCalculator");
    }

    @Transactional
    public BucketDetails closeBucket(UUID currentUserId, UUID bucketId) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        Objects.requireNonNull(bucketId, "bucketId");

        User currentUser = findCurrentUser(currentUserId);
        UUID userGroupId = currentUser.getUserGroup().getUserGroupId();

        Bucket bucket = findClosableBucket(currentUser, bucketId, userGroupId);

        BigDecimal currentBucketBalance = calculateCurrentBucketBalance(
                bucket.getBucketId(),
                userGroupId
        );

        if (currentBucketBalance.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException("finance.bucket.balanceMustBeZeroOrNegative");
        }

        bucket.close();

        bucketRepository.saveAndFlush(bucket);

        return new BucketDetails(
                bucket,
                linkedAccountIdsFor(currentUser, bucket)
        );
    }

    @Transactional
    public BucketDetails reopenBucket(UUID currentUserId, UUID bucketId) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        Objects.requireNonNull(bucketId, "bucketId");

        User currentUser = findCurrentUser(currentUserId);
        UUID userGroupId = currentUser.getUserGroup().getUserGroupId();

        Bucket bucket = findReopenableBucket(currentUser, bucketId, userGroupId);

        String normalizedBucketName = normalizeName(bucket.getBucketName());

        if (bucketRepository.existsActiveByUserGroupIdAndNormalizedBucketName(
                userGroupId,
                normalizedBucketName
        )) {
            throw duplicateBucketNameException(currentUser);
        }

        try {
            bucket.reopen();

            bucketRepository.saveAndFlush(bucket);

            return new BucketDetails(
                    bucket,
                    linkedAccountIdsFor(currentUser, bucket)
            );
        } catch (DataIntegrityViolationException exception) {
            throw duplicateBucketNameException(currentUser, exception);
        }
    }

    private Bucket findClosableBucket(
            User currentUser,
            UUID bucketId,
            UUID userGroupId
    ) {
        if (canOperateAllGroupBuckets(currentUser)) {
            return bucketRepository.findByBucketIdAndUserGroup_UserGroupIdAndBucketClosedAtIsNull(
                    bucketId,
                    userGroupId
            ).orElseThrow(() -> new ResourceNotFoundException("finance.bucket.notFound"));
        }

        return bucketRepository.findActiveLinkedToAccessibleAccount(
                bucketId,
                userGroupId,
                currentUser.getUserId()
        ).orElseThrow(() -> new ResourceNotFoundException("finance.bucket.notFound"));
    }

    private Bucket findReopenableBucket(
            User currentUser,
            UUID bucketId,
            UUID userGroupId
    ) {
        if (canOperateAllGroupBuckets(currentUser)) {
            return bucketRepository.findByBucketIdAndUserGroup_UserGroupIdAndBucketClosedAtIsNotNull(
                    bucketId,
                    userGroupId
            ).orElseThrow(() -> new ResourceNotFoundException("finance.bucket.notFound"));
        }

        return bucketRepository.findClosedLinkedToAccessibleAccount(
                bucketId,
                userGroupId,
                currentUser.getUserId()
        ).orElseThrow(() -> new ResourceNotFoundException("finance.bucket.notFound"));
    }

    private List<UUID> linkedAccountIdsFor(User currentUser, Bucket bucket) {
        if (canReadAllLinkedAccounts(currentUser)) {
            return bucketAccountRepository.findAccountIdsByBucketId(bucket.getBucketId());
        }

        return bucketAccountRepository.findVisibleAccountIdsByBucketIdForCollaborator(
                bucket.getBucketId(),
                currentUser.getUserGroup().getUserGroupId(),
                currentUser.getUserId()
        );
    }

    private BigDecimal calculateCurrentBucketBalance(UUID bucketId, UUID userGroupId) {
        Objects.requireNonNull(bucketId, "bucketId");
        Objects.requireNonNull(userGroupId, "userGroupId");

        return bucketBalanceCalculator.calculateCurrentBalance(
                bucketId,
                userGroupId
        );
    }

    private User findCurrentUser(UUID currentUserId) {
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("finance.bucket.notFound"));

        if (!currentUser.isUserIsEnabled() || currentUser.isPendingDeletion()) {
            throw new ResourceNotFoundException("finance.bucket.notFound");
        }

        return currentUser;
    }

    private boolean canOperateAllGroupBuckets(User user) {
        UserRole userRole = user.getUserRole();

        return userRole == UserRole.OWNER
                || userRole == UserRole.SUPER_COLLABORATOR;
    }

    private boolean canReadAllLinkedAccounts(User user) {
        UserRole userRole = user.getUserRole();

        return userRole == UserRole.OWNER
                || userRole == UserRole.SUPER_COLLABORATOR
                || userRole == UserRole.VIEWER_COLLABORATOR;
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

    private String normalizeName(String value) {
        return value.trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }
}