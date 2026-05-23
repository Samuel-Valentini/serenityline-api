package me.serenityline.api.finance.category.service;

import jakarta.persistence.EntityManager;
import me.serenityline.api.finance.category.entity.Category;
import me.serenityline.api.finance.category.entity.CategoryDetailsHistory;
import me.serenityline.api.finance.category.entity.CategoryStatusHistory;
import me.serenityline.api.finance.category.repository.CategoryDetailsHistoryRepository;
import me.serenityline.api.finance.category.repository.CategoryRepository;
import me.serenityline.api.finance.category.repository.CategoryStatusHistoryRepository;
import me.serenityline.api.user.entity.UserGroup;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class DefaultCategoryCreationService {

    private final CategoryRepository categoryRepository;
    private final CategoryDetailsHistoryRepository categoryDetailsHistoryRepository;
    private final CategoryStatusHistoryRepository categoryStatusHistoryRepository;
    private final MessageSource messageSource;
    private final EntityManager entityManager;

    public DefaultCategoryCreationService(
            CategoryRepository categoryRepository,
            CategoryDetailsHistoryRepository categoryDetailsHistoryRepository,
            CategoryStatusHistoryRepository categoryStatusHistoryRepository,
            MessageSource messageSource,
            EntityManager entityManager
    ) {
        this.categoryRepository = categoryRepository;
        this.categoryDetailsHistoryRepository = categoryDetailsHistoryRepository;
        this.categoryStatusHistoryRepository = categoryStatusHistoryRepository;
        this.messageSource = messageSource;
        this.entityManager = entityManager;
    }

    @Transactional
    public void createDefaultCategoriesIfMissing(UUID userGroupId, UUID createdByUserId, Locale locale) {
        Objects.requireNonNull(userGroupId, "userGroupId");
        Objects.requireNonNull(createdByUserId, "createdByUserId");

        Locale resolvedLocale = locale == null ? Locale.ITALY : locale;

        if (categoryRepository.existsByUserGroup_UserGroupId(userGroupId)) {
            return;
        }

        UserGroup userGroup = entityManager.getReference(UserGroup.class, userGroupId);

        for (DefaultCategoryDefinition definition : DefaultCategoryDefinitions.all()) {
            String name = messageSource.getMessage(definition.nameMessageCode(), null, resolvedLocale);
            String description = messageSource.getMessage(definition.descriptionMessageCode(), null, resolvedLocale);

            Category category = categoryRepository.save(Category.create(
                    userGroup,
                    createdByUserId,
                    name
            ));

            categoryDetailsHistoryRepository.save(CategoryDetailsHistory.create(
                    category,
                    name,
                    description
            ));

            categoryStatusHistoryRepository.save(CategoryStatusHistory.active(category));
        }
    }
}