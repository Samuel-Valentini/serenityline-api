package me.serenityline.api.finance.bucket.service;

import me.serenityline.api.common.error.ResourceNotFoundException;
import me.serenityline.api.finance.bucket.entity.Bucket;
import me.serenityline.api.finance.bucket.repository.BucketAccountRepository;
import me.serenityline.api.finance.bucket.repository.BucketRepository;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.entity.UserRole;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class BucketQueryService {

    private final BucketRepository bucketRepository;
    private final BucketAccountRepository bucketAccountRepository;
    private final UserRepository userRepository;

    public BucketQueryService(
            BucketRepository bucketRepository,
            BucketAccountRepository bucketAccountRepository,
            UserRepository userRepository
    ) {
        this.bucketRepository = Objects.requireNonNull(bucketRepository, "bucketRepository");
        this.bucketAccountRepository = Objects.requireNonNull(bucketAccountRepository, "bucketAccountRepository");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
    }

    @Transactional(readOnly = true)
    public List<BucketDetails> findVisibleBuckets(UUID currentUserId) {
        Objects.requireNonNull(currentUserId, "currentUserId");

        User currentUser = findCurrentUser(currentUserId);
        UUID userGroupId = currentUser.getUserGroup().getUserGroupId();

        List<Bucket> buckets = canReadAllGroupBuckets(currentUser)
                ? bucketRepository.findAllActiveByUserGroupId(userGroupId)
                : bucketRepository.findAllActiveVisibleForCollaborator(userGroupId, currentUser.getUserId());

        return buckets.stream()
                .map(bucket -> toBucketDetails(currentUser, bucket))
                .toList();
    }

    @Transactional(readOnly = true)
    public BucketDetails findVisibleBucket(UUID currentUserId, UUID bucketId) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        Objects.requireNonNull(bucketId, "bucketId");

        User currentUser = findCurrentUser(currentUserId);
        UUID userGroupId = currentUser.getUserGroup().getUserGroupId();

        Bucket bucket = canReadAllGroupBuckets(currentUser)
                ? bucketRepository.findByBucketIdAndUserGroup_UserGroupIdAndBucketClosedAtIsNull(
                bucketId,
                userGroupId
        ).orElseThrow(() -> new ResourceNotFoundException("finance.bucket.notFound"))
                : bucketRepository.findActiveVisibleForCollaborator(
                bucketId,
                userGroupId,
                currentUser.getUserId()
        ).orElseThrow(() -> new ResourceNotFoundException("finance.bucket.notFound"));

        return toBucketDetails(currentUser, bucket);
    }

    private BucketDetails toBucketDetails(User currentUser, Bucket bucket) {
        List<UUID> linkedAccountIds = canReadAllGroupBuckets(currentUser)
                ? bucketAccountRepository.findAccountIdsByBucketId(bucket.getBucketId())
                : bucketAccountRepository.findVisibleAccountIdsByBucketIdForCollaborator(
                bucket.getBucketId(),
                currentUser.getUserGroup().getUserGroupId(),
                currentUser.getUserId()
        );

        return new BucketDetails(
                bucket,
                linkedAccountIds
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

    private boolean canReadAllGroupBuckets(User user) {
        UserRole userRole = user.getUserRole();

        return userRole == UserRole.OWNER
                || userRole == UserRole.SUPER_COLLABORATOR
                || userRole == UserRole.VIEWER_COLLABORATOR;
    }
}