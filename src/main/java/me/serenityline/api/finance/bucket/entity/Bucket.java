package me.serenityline.api.finance.bucket.entity;

import jakarta.persistence.*;
import me.serenityline.api.user.entity.UserGroup;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "buckets")
public class Bucket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "bucket_id", nullable = false)
    private UUID bucketId;

    @Column(name = "bucket_name", nullable = false, length = 255)
    private String bucketName;

    @Column(name = "bucket_description", length = 2000)
    private String bucketDescription;

    @Column(name = "bucket_created_at", nullable = false, updatable = false)
    private OffsetDateTime bucketCreatedAt;

    @Column(name = "bucket_updated_at", nullable = false)
    private OffsetDateTime bucketUpdatedAt;

    @Column(name = "bucket_closed_at")
    private OffsetDateTime bucketClosedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_group_id", nullable = false, updatable = false)
    private UserGroup userGroup;

    protected Bucket() {
    }

    private Bucket(
            String bucketName,
            String bucketDescription,
            UserGroup userGroup
    ) {
        this.bucketName = Objects.requireNonNull(bucketName, "bucketName");
        this.bucketDescription = bucketDescription;
        this.userGroup = Objects.requireNonNull(userGroup, "userGroup");
    }

    public static Bucket create(
            String bucketName,
            String bucketDescription,
            UserGroup userGroup
    ) {
        return new Bucket(
                bucketName,
                bucketDescription,
                userGroup
        );
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.bucketCreatedAt = now;
        this.bucketUpdatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.bucketUpdatedAt = OffsetDateTime.now();
    }

    public void updateBucketName(String bucketName) {
        if (bucketName == null || bucketName.isBlank()) {
            throw new IllegalArgumentException("finance.bucket.name.required");
        }

        this.bucketName = bucketName;
        touch();
    }

    public void updateBucketDescription(String bucketDescription) {
        if (bucketDescription != null && bucketDescription.isBlank()) {
            throw new IllegalArgumentException("finance.bucket.description.blank");
        }

        this.bucketDescription = bucketDescription;
        touch();
    }

    private void touch() {
        this.bucketUpdatedAt = OffsetDateTime.now();
    }

    public void close() {
        this.bucketClosedAt = OffsetDateTime.now();
        touch();
    }

    public void reopen() {
        this.bucketClosedAt = null;
        touch();
    }

    public UUID getBucketId() {
        return bucketId;
    }

    public String getBucketName() {
        return bucketName;
    }

    public String getBucketDescription() {
        return bucketDescription;
    }

    public OffsetDateTime getBucketCreatedAt() {
        return bucketCreatedAt;
    }

    public OffsetDateTime getBucketUpdatedAt() {
        return bucketUpdatedAt;
    }

    public OffsetDateTime getBucketClosedAt() {
        return bucketClosedAt;
    }

    public UserGroup getUserGroup() {
        return userGroup;
    }

    public UUID getUserGroupId() {
        return userGroup.getUserGroupId();
    }

    public boolean isClosed() {
        return bucketClosedAt != null;
    }
}