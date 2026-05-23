package me.serenityline.api.finance.category.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "category_status_history")
public class CategoryStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "category_status_history_id", nullable = false)
    private UUID categoryStatusHistoryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false, updatable = false)
    private Category category;

    @Column(name = "category_is_active", nullable = false)
    private boolean active;

    @Column(name = "category_status_updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime categoryStatusUpdatedAt;

    protected CategoryStatusHistory() {
    }

    private CategoryStatusHistory(Category category, boolean active) {
        this.category = category;
        this.active = active;
    }

    public static CategoryStatusHistory active(Category category) {
        return new CategoryStatusHistory(category, true);
    }

    public static CategoryStatusHistory inactive(Category category) {
        return new CategoryStatusHistory(category, false);
    }

    public UUID getCategoryStatusHistoryId() {
        return categoryStatusHistoryId;
    }

    public Category getCategory() {
        return category;
    }

    public UUID getCategoryId() {
        return category.getCategoryId();
    }

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getCategoryStatusUpdatedAt() {
        return categoryStatusUpdatedAt;
    }
}