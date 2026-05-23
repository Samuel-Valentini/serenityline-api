package me.serenityline.api.finance.category.repository;

import me.serenityline.api.finance.category.entity.CategoryStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryStatusHistoryRepository extends JpaRepository<CategoryStatusHistory, UUID> {

    long countByCategory_CategoryIdIn(Collection<UUID> categoryIds);

    List<CategoryStatusHistory> findAllByCategory_CategoryIdIn(Collection<UUID> categoryIds);

    List<CategoryStatusHistory> findAllByCategory_CategoryIdOrderByCategoryStatusUpdatedAtDesc(
            UUID categoryId
    );

    Optional<CategoryStatusHistory> findTopByCategory_CategoryIdOrderByCategoryStatusUpdatedAtDescCategoryStatusHistoryIdDesc(
            UUID categoryId
    );
}