package me.serenityline.api.finance.category.repository;

import me.serenityline.api.finance.category.entity.CategoryDetailsHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryDetailsHistoryRepository extends JpaRepository<CategoryDetailsHistory, UUID> {

    long countByCategory_CategoryIdIn(Collection<UUID> categoryIds);

    List<CategoryDetailsHistory> findAllByCategory_CategoryIdIn(Collection<UUID> categoryIds);

    List<CategoryDetailsHistory> findAllByCategory_CategoryIdOrderByCategoryDetailsUpdatedAtDesc(
            UUID categoryId
    );

    Optional<CategoryDetailsHistory> findTopByCategory_CategoryIdOrderByCategoryDetailsUpdatedAtDescCategoryDetailsHistoryIdDesc(
            UUID categoryId
    );
}