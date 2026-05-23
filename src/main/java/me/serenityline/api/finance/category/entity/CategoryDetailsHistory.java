package me.serenityline.api.finance.category.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "category_details_history")
public class CategoryDetailsHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "category_details_history_id", nullable = false)
    private UUID categoryDetailsHistoryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false, updatable = false)
    private Category category;

    @Column(name = "category_name", nullable = false, length = 255)
    private String categoryName;

    @Column(name = "category_description", length = 500)
    private String categoryDescription;

    @Column(name = "category_details_updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime categoryDetailsUpdatedAt;

    protected CategoryDetailsHistory() {
    }

    private CategoryDetailsHistory(Category category, String categoryName, String categoryDescription) {
        this.category = category;
        this.categoryName = categoryName;
        this.categoryDescription = categoryDescription;
    }

    public static CategoryDetailsHistory create(Category category, String categoryName, String categoryDescription) {
        return new CategoryDetailsHistory(category, categoryName, categoryDescription);
    }

    public UUID getCategoryDetailsHistoryId() {
        return categoryDetailsHistoryId;
    }

    public Category getCategory() {
        return category;
    }

    public UUID getCategoryId() {
        return category.getCategoryId();
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getCategoryDescription() {
        return categoryDescription;
    }

    public OffsetDateTime getCategoryDetailsUpdatedAt() {
        return categoryDetailsUpdatedAt;
    }
}