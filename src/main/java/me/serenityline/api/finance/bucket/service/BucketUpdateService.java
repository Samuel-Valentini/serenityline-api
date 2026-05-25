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

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class BucketUpdateService {

    private final BucketRepository bucketRepository;
    private final BucketAccountRepository bucketAccountRepository;
    private final UserRepository userRepository;

    public BucketUpdateService(
            BucketRepository bucketRepository,
            BucketAccountRepository bucketAccountRepository,
            UserRepository userRepository
    ) {
        this.bucketRepository = Objects.requireNonNull(bucketRepository, "bucketRepository");
        this.bucketAccountRepository = Objects.requireNonNull(bucketAccountRepository, "bucketAccountRepository");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
    }

    @Transactional
    public BucketDetails updateBucket(
            UUID currentUserId,
            UUID bucketId,
            UpdateBucketCommand command
    ) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        Objects.requireNonNull(bucketId, "bucketId");
        Objects.requireNonNull(command, "command");

        User currentUser = findCurrentUser(currentUserId);
        UUID userGroupId = currentUser.getUserGroup().getUserGroupId();

        Bucket bucket = findUpdatableBucket(currentUser, bucketId, userGroupId);

        String bucketName = trimToNull(command.bucketName());
        String bucketDescription = command.bucketDescription() == null
                ? null
                : trimToNull(command.bucketDescription());

        if (command.bucketName() != null && bucketName == null) {
            throw new IllegalArgumentException("finance.bucket.name.required");
        }

        try {
            if (bucketName != null) {
                updateNameIfNeeded(currentUser, userGroupId, bucket, bucketName);
            }

            if (command.bucketDescription() != null) {
                bucket.updateBucketDescription(bucketDescription);
            }

            bucketRepository.saveAndFlush(bucket);

            return new BucketDetails(
                    bucket,
                    linkedAccountIdsFor(currentUser, bucket)
            );
        } catch (DataIntegrityViolationException exception) {
            throw duplicateBucketNameException(currentUser, exception);
        }
    }

    private void updateNameIfNeeded(
            User currentUser,
            UUID userGroupId,
            Bucket bucket,
            String bucketName
    ) {
        String currentNormalizedName = normalizeName(bucket.getBucketName());
        String requestedNormalizedName = normalizeName(bucketName);

        if (!requestedNormalizedName.equals(currentNormalizedName)
                && bucketRepository.existsActiveByUserGroupIdAndNormalizedBucketName(
                userGroupId,
                requestedNormalizedName
        )) {
            throw duplicateBucketNameException(currentUser);
        }

        bucket.updateBucketName(bucketName);
    }

    private Bucket findUpdatableBucket(
            User currentUser,
            UUID bucketId,
            UUID userGroupId
    ) {
        if (canUpdateAllGroupBuckets(currentUser)) {
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

    private User findCurrentUser(UUID currentUserId) {
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("finance.bucket.notFound"));

        if (!currentUser.isUserIsEnabled() || currentUser.isPendingDeletion()) {
            throw new ResourceNotFoundException("finance.bucket.notFound");
        }

        return currentUser;
    }

    private boolean canUpdateAllGroupBuckets(User user) {
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        if (trimmed.isEmpty()) {
            return null;
        }

        return trimmed;
    }

    private String normalizeName(String value) {
        return value.trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }
}