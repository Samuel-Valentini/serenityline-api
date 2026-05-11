package me.serenityline.api.user.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_groups")
public class UserGroup {

    private static final int USER_GROUP_NAME_MAX_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_group_id", nullable = false)
    private UUID userGroupId;

    @NotBlank(message = "{userGroup.name.required}")
    @Column(name = "user_group_name", nullable = false, length = 255)
    private String userGroupName;

    @Column(name = "user_group_created_at", nullable = false, updatable = false)
    private OffsetDateTime userGroupCreatedAt;

    protected UserGroup() {
    }

    public UserGroup(String userGroupName) {
        setUserGroupName(userGroupName);
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }

        return value.trim();
    }

    private static String requireNotBlankAndMaxLength(
            String value,
            int maxLength,
            String requiredMessageKey,
            String tooLongMessageKey
    ) {
        String normalized = normalizeText(value);

        if (normalized == null || normalized.isBlank()) {
            throw validationError(requiredMessageKey);
        }

        if (normalized.length() > maxLength) {
            throw validationError(tooLongMessageKey);
        }

        return normalized;
    }

    private static IllegalArgumentException validationError(String messageKey) {
        return new IllegalArgumentException(messageKey);
    }

    @PrePersist
    protected void onCreate() {
        if (this.userGroupCreatedAt == null) {
            this.userGroupCreatedAt = OffsetDateTime.now();
        }

        validateForPersistence();
    }

    @PreUpdate
    protected void onUpdate() {
        validateForPersistence();
    }

    private void validateForPersistence() {
        this.userGroupName = requireNotBlankAndMaxLength(
                this.userGroupName,
                USER_GROUP_NAME_MAX_LENGTH,
                "userGroup.name.required",
                "userGroup.name.tooLong"
        );
    }


    public UUID getUserGroupId() {
        return userGroupId;
    }

    public String getUserGroupName() {
        return userGroupName;
    }

    public void setUserGroupName(String userGroupName) {
        this.userGroupName = requireNotBlankAndMaxLength(
                userGroupName,
                USER_GROUP_NAME_MAX_LENGTH,
                "userGroup.name.required",
                "userGroup.name.tooLong"
        );
    }

    public OffsetDateTime getUserGroupCreatedAt() {
        return userGroupCreatedAt;
    }
}