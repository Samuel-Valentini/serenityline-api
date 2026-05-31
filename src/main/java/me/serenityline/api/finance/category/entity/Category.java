package me.serenityline.api.finance.category.entity;

import jakarta.persistence.*;
import me.serenityline.api.user.entity.UserGroup;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_group_id", nullable = false, updatable = false)
    private UserGroup userGroup;

    @Column(name = "category_created_by_user_id", nullable = false, updatable = false)
    private UUID categoryCreatedByUserId;

    @Column(name = "category_current_name", nullable = false, length = 255)
    private String categoryCurrentName;

    @Column(name = "category_created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime categoryCreatedAt;

    protected Category() {
    }

    private Category(UserGroup userGroup, UUID categoryCreatedByUserId, String categoryCurrentName) {
        this.userGroup = userGroup;
        this.categoryCreatedByUserId = categoryCreatedByUserId;
        this.categoryCurrentName = categoryCurrentName;
    }

    public static Category create(UserGroup userGroup, UUID categoryCreatedByUserId, String categoryCurrentName) {
        return new Category(userGroup, categoryCreatedByUserId, categoryCurrentName);
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public UserGroup getUserGroup() {
        return userGroup;
    }

    public UUID getUserGroupId() {
        return userGroup.getUserGroupId();
    }

    public UUID getCategoryCreatedByUserId() {
        return categoryCreatedByUserId;
    }

    public String getCategoryCurrentName() {
        return categoryCurrentName;
    }

    public OffsetDateTime getCategoryCreatedAt() {
        return categoryCreatedAt;
    }

    public boolean wasCreatedBy(UUID userId) {
        return categoryCreatedByUserId.equals(userId);
    }

    public void updateCurrentName(String categoryCurrentName) {
        this.categoryCurrentName = categoryCurrentName;
    }
}