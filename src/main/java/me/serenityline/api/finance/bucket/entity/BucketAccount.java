package me.serenityline.api.finance.bucket.entity;

import jakarta.persistence.*;
import me.serenityline.api.finance.account.entity.Account;
import me.serenityline.api.user.entity.UserGroup;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "buckets_accounts")
public class BucketAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "bucket_account_id", nullable = false)
    private UUID bucketAccountId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bucket_id", nullable = false, updatable = false)
    private Bucket bucket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_group_id", nullable = false, updatable = false)
    private UserGroup userGroup;

    @Column(name = "bucket_account_created_at", nullable = false, updatable = false)
    private OffsetDateTime bucketAccountCreatedAt;

    protected BucketAccount() {
    }

    private BucketAccount(
            Bucket bucket,
            Account account,
            UserGroup userGroup
    ) {
        this.bucket = Objects.requireNonNull(bucket, "bucket");
        this.account = Objects.requireNonNull(account, "account");
        this.userGroup = Objects.requireNonNull(userGroup, "userGroup");
    }

    public static BucketAccount link(
            Bucket bucket,
            Account account,
            UserGroup userGroup
    ) {
        if (!bucket.getUserGroupId().equals(userGroup.getUserGroupId())) {
            throw new IllegalArgumentException("finance.bucket.userGroupMismatch");
        }

        if (!account.getUserGroupId().equals(userGroup.getUserGroupId())) {
            throw new IllegalArgumentException("finance.bucket.userGroupMismatch");
        }

        return new BucketAccount(
                bucket,
                account,
                userGroup
        );
    }

    @PrePersist
    void onCreate() {
        this.bucketAccountCreatedAt = OffsetDateTime.now();
    }

    public UUID getBucketAccountId() {
        return bucketAccountId;
    }

    public Bucket getBucket() {
        return bucket;
    }

    public UUID getBucketId() {
        return bucket.getBucketId();
    }

    public Account getAccount() {
        return account;
    }

    public UUID getAccountId() {
        return account.getAccountId();
    }

    public UserGroup getUserGroup() {
        return userGroup;
    }

    public UUID getUserGroupId() {
        return userGroup.getUserGroupId();
    }

    public OffsetDateTime getBucketAccountCreatedAt() {
        return bucketAccountCreatedAt;
    }
}