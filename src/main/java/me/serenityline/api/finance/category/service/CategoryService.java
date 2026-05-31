package me.serenityline.api.finance.category.service;

import me.serenityline.api.finance.category.dto.CategoryCreateRequest;
import me.serenityline.api.finance.category.dto.CategoryResponse;
import me.serenityline.api.finance.category.dto.CategoryUpdateRequest;
import me.serenityline.api.finance.category.entity.Category;
import me.serenityline.api.finance.category.entity.CategoryDetailsHistory;
import me.serenityline.api.finance.category.entity.CategoryStatusHistory;
import me.serenityline.api.finance.category.repository.CategoryDetailsHistoryRepository;
import me.serenityline.api.finance.category.repository.CategoryRepository;
import me.serenityline.api.finance.category.repository.CategoryStatusHistoryRepository;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.entity.UserGroup;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CategoryService {

    private static final int CATEGORY_NAME_MAX_LENGTH = 255;
    private static final int CATEGORY_DESCRIPTION_MAX_LENGTH = 500;

    private static final Comparator<CategoryDetailsHistory> DETAILS_COMPARATOR = Comparator
            .comparing(
                    CategoryDetailsHistory::getCategoryDetailsUpdatedAt,
                    Comparator.nullsFirst(Comparator.naturalOrder())
            )
            .thenComparing(
                    CategoryDetailsHistory::getCategoryDetailsHistoryId,
                    Comparator.nullsFirst(Comparator.naturalOrder())
            );

    private static final Comparator<CategoryStatusHistory> STATUS_COMPARATOR = Comparator
            .comparing(
                    CategoryStatusHistory::getCategoryStatusUpdatedAt,
                    Comparator.nullsFirst(Comparator.naturalOrder())
            )
            .thenComparing(
                    CategoryStatusHistory::getCategoryStatusHistoryId,
                    Comparator.nullsFirst(Comparator.naturalOrder())
            );

    private final CategoryRepository categoryRepository;
    private final CategoryDetailsHistoryRepository categoryDetailsHistoryRepository;
    private final CategoryStatusHistoryRepository categoryStatusHistoryRepository;
    private final UserRepository userRepository;

    public CategoryService(
            CategoryRepository categoryRepository,
            CategoryDetailsHistoryRepository categoryDetailsHistoryRepository,
            CategoryStatusHistoryRepository categoryStatusHistoryRepository,
            UserRepository userRepository
    ) {
        this.categoryRepository = Objects.requireNonNull(categoryRepository, "categoryRepository");
        this.categoryDetailsHistoryRepository = Objects.requireNonNull(
                categoryDetailsHistoryRepository,
                "categoryDetailsHistoryRepository"
        );
        this.categoryStatusHistoryRepository = Objects.requireNonNull(
                categoryStatusHistoryRepository,
                "categoryStatusHistoryRepository"
        );
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories(UUID userId) {
        Objects.requireNonNull(userId, "userId");

        UUID userGroupId = userGroupIdOf(userId);

        List<Category> categories = categoryRepository
                .findAllByUserGroup_UserGroupIdOrderByCategoryCurrentNameAsc(userGroupId);

        if (categories.isEmpty()) {
            return List.of();
        }

        List<UUID> categoryIds = categories.stream()
                .map(Category::getCategoryId)
                .toList();

        Map<UUID, CategoryDetailsHistory> latestDetailsByCategoryId = categoryDetailsHistoryRepository
                .findAllByCategory_CategoryIdIn(categoryIds)
                .stream()
                .collect(Collectors.toMap(
                        CategoryDetailsHistory::getCategoryId,
                        Function.identity(),
                        this::newerDetails
                ));

        Map<UUID, CategoryStatusHistory> latestStatusByCategoryId = categoryStatusHistoryRepository
                .findAllByCategory_CategoryIdIn(categoryIds)
                .stream()
                .collect(Collectors.toMap(
                        CategoryStatusHistory::getCategoryId,
                        Function.identity(),
                        this::newerStatus
                ));

        return categories.stream()
                .map(category -> toResponse(
                        category,
                        latestDetailsByCategoryId.get(category.getCategoryId()),
                        latestStatusByCategoryId.get(category.getCategoryId())
                ))
                .toList();
    }

    @Transactional
    public CategoryResponse createCategory(
            UUID userId,
            CategoryCreateRequest request
    ) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(request, "request");

        User user = userById(userId);
        UserGroup userGroup = userGroupOf(user);
        UUID userGroupId = userGroupIdOf(userGroup);

        String categoryName = normalizeName(request.categoryName());
        String categoryDescription = normalizeDescription(request.categoryDescription());

        if (categoryRepository.existsByUserGroupIdAndNormalizedCurrentName(userGroupId, categoryName)) {
            throw new IllegalArgumentException("finance.category.nameAlreadyExists");
        }

        Category category = categoryRepository.save(Category.create(
                userGroup,
                userId,
                categoryName
        ));

        CategoryDetailsHistory detailsHistory = categoryDetailsHistoryRepository.save(CategoryDetailsHistory.create(
                category,
                categoryName,
                categoryDescription
        ));

        CategoryStatusHistory statusHistory = categoryStatusHistoryRepository.save(
                CategoryStatusHistory.active(category)
        );

        return toResponse(category, detailsHistory, statusHistory);
    }

    @Transactional
    public CategoryResponse updateCategory(
            UUID userId,
            UUID categoryId,
            CategoryUpdateRequest request
    ) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(categoryId, "categoryId");
        Objects.requireNonNull(request, "request");

        UUID userGroupId = userGroupIdOf(userId);

        String categoryName = normalizeName(request.categoryName());
        String categoryDescription = normalizeDescription(request.categoryDescription());

        Category category = categoryRepository.findByCategoryIdAndUserGroup_UserGroupId(categoryId, userGroupId)
                .orElseThrow(() -> new IllegalArgumentException("finance.category.notFound"));

        if (categoryRepository.existsByUserGroupIdAndNormalizedCurrentNameExcludingCategoryId(
                userGroupId,
                categoryId,
                categoryName
        )) {
            throw new IllegalArgumentException("finance.category.nameAlreadyExists");
        }

        category.updateCurrentName(categoryName);

        CategoryDetailsHistory detailsHistory = categoryDetailsHistoryRepository.save(CategoryDetailsHistory.create(
                category,
                categoryName,
                categoryDescription
        ));

        CategoryStatusHistory statusHistory = latestStatus(categoryId);

        return toResponse(category, detailsHistory, statusHistory);
    }

    @Transactional
    public CategoryResponse deactivateCategory(UUID userId, UUID categoryId) {
        return changeCategoryStatus(userId, categoryId, false);
    }

    @Transactional
    public CategoryResponse reactivateCategory(UUID userId, UUID categoryId) {
        return changeCategoryStatus(userId, categoryId, true);
    }

    private CategoryResponse changeCategoryStatus(UUID userId, UUID categoryId, boolean active) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(categoryId, "categoryId");

        UUID userGroupId = userGroupIdOf(userId);

        Category category = categoryRepository.findByCategoryIdAndUserGroup_UserGroupId(categoryId, userGroupId)
                .orElseThrow(() -> new IllegalArgumentException("finance.category.notFound"));

        CategoryDetailsHistory detailsHistory = latestDetails(categoryId);
        CategoryStatusHistory currentStatus = latestStatus(categoryId);

        if (currentStatus.isActive() == active) {
            return toResponse(category, detailsHistory, currentStatus);
        }

        CategoryStatusHistory newStatus = active
                ? CategoryStatusHistory.active(category)
                : CategoryStatusHistory.inactive(category);

        CategoryStatusHistory savedStatus = categoryStatusHistoryRepository.save(newStatus);

        return toResponse(category, detailsHistory, savedStatus);
    }

    private UUID userGroupIdOf(UUID userId) {
        User user = userById(userId);
        UserGroup userGroup = userGroupOf(user);

        return userGroupIdOf(userGroup);
    }

    private User userById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("auth.userNotFound"));
    }

    private UserGroup userGroupOf(User user) {
        return Objects.requireNonNull(user.getUserGroup(), "userGroup");
    }

    private UUID userGroupIdOf(UserGroup userGroup) {
        return Objects.requireNonNull(userGroup.getUserGroupId(), "userGroupId");
    }

    private CategoryDetailsHistory latestDetails(UUID categoryId) {
        return categoryDetailsHistoryRepository
                .findTopByCategory_CategoryIdOrderByCategoryDetailsUpdatedAtDescCategoryDetailsHistoryIdDesc(categoryId)
                .orElseThrow(() -> new IllegalStateException("finance.category.detailsHistoryMissing"));
    }

    private CategoryStatusHistory latestStatus(UUID categoryId) {
        return categoryStatusHistoryRepository
                .findTopByCategory_CategoryIdOrderByCategoryStatusUpdatedAtDescCategoryStatusHistoryIdDesc(categoryId)
                .orElseThrow(() -> new IllegalStateException("finance.category.statusHistoryMissing"));
    }

    private CategoryResponse toResponse(
            Category category,
            CategoryDetailsHistory detailsHistory,
            CategoryStatusHistory statusHistory
    ) {
        if (detailsHistory == null) {
            throw new IllegalStateException("finance.category.detailsHistoryMissing");
        }

        if (statusHistory == null) {
            throw new IllegalStateException("finance.category.statusHistoryMissing");
        }

        return new CategoryResponse(
                category.getCategoryId(),
                category.getCategoryCurrentName(),
                detailsHistory.getCategoryDescription(),
                statusHistory.isActive()
        );
    }

    private CategoryDetailsHistory newerDetails(
            CategoryDetailsHistory first,
            CategoryDetailsHistory second
    ) {
        return DETAILS_COMPARATOR.compare(first, second) >= 0 ? first : second;
    }

    private CategoryStatusHistory newerStatus(
            CategoryStatusHistory first,
            CategoryStatusHistory second
    ) {
        return STATUS_COMPARATOR.compare(first, second) >= 0 ? first : second;
    }

    private String normalizeName(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            throw new IllegalArgumentException("finance.category.nameRequired");
        }

        String normalized = categoryName.strip();

        if (normalized.length() > CATEGORY_NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("finance.category.nameTooLong");
        }

        return normalized;
    }

    private String normalizeDescription(String categoryDescription) {
        if (categoryDescription == null || categoryDescription.isBlank()) {
            return null;
        }

        String normalized = categoryDescription.strip();

        if (normalized.length() > CATEGORY_DESCRIPTION_MAX_LENGTH) {
            throw new IllegalArgumentException("finance.category.descriptionTooLong");
        }

        return normalized;
    }
}