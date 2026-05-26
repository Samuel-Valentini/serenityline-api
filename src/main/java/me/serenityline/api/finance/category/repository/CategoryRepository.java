package me.serenityline.api.finance.category.repository;

import me.serenityline.api.finance.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    boolean existsByUserGroup_UserGroupId(UUID userGroupId);

    long countByUserGroup_UserGroupId(UUID userGroupId);

    Optional<Category> findByCategoryIdAndUserGroup_UserGroupId(
            UUID categoryId,
            UUID userGroupId
    );

    List<Category> findAllByUserGroup_UserGroupIdOrderByCategoryCurrentNameAsc(
            UUID userGroupId
    );

    @Query(value = """
            SELECT category.*
            FROM categories category
            WHERE category.category_id = :categoryId
              AND category.user_group_id = :userGroupId
              AND (
                    SELECT category_status.category_is_active
                    FROM category_status_history category_status
                    WHERE category_status.category_id = category.category_id
                    ORDER BY category_status.category_status_updated_at DESC,
                             category_status.category_status_history_id DESC
                    LIMIT 1
              ) = TRUE
            """, nativeQuery = true)
    Optional<Category> findActiveByCategoryIdAndUserGroupId(
            @Param("categoryId") UUID categoryId,
            @Param("userGroupId") UUID userGroupId
    );
}