package me.serenityline.api.finance.category.repository;

import me.serenityline.api.finance.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

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
}