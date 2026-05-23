package me.serenityline.api.finance.category.service;

import me.serenityline.api.finance.category.entity.Category;
import me.serenityline.api.finance.category.entity.CategoryDetailsHistory;
import me.serenityline.api.finance.category.entity.CategoryStatusHistory;
import me.serenityline.api.finance.category.repository.CategoryDetailsHistoryRepository;
import me.serenityline.api.finance.category.repository.CategoryRepository;
import me.serenityline.api.finance.category.repository.CategoryStatusHistoryRepository;
import me.serenityline.api.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultCategoryCreationServiceIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private DefaultCategoryCreationService defaultCategoryCreationService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CategoryDetailsHistoryRepository categoryDetailsHistoryRepository;

    @Autowired
    private CategoryStatusHistoryRepository categoryStatusHistoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCreateItalianDefaultCategoriesWithDetailsAndActiveStatus() {
        UUID userGroupId = createUserGroup();
        UUID createdByUserId = UUID.randomUUID();

        defaultCategoryCreationService.createDefaultCategoriesIfMissing(
                userGroupId,
                createdByUserId,
                Locale.forLanguageTag("it-IT")
        );

        List<Category> categories =
                categoryRepository.findAllByUserGroup_UserGroupIdOrderByCategoryCurrentNameAsc(userGroupId);

        List<UUID> categoryIds = categories.stream()
                .map(Category::getCategoryId)
                .toList();

        assertThat(categories).hasSize(20);

        assertThat(categories)
                .extracting(Category::getCategoryCurrentName)
                .containsExactlyInAnyOrder(
                        "Affitti",
                        "Alimentari e altre spese domestiche",
                        "Altre entrate",
                        "Assicurazioni",
                        "Auto",
                        "Beneficenza",
                        "Bollette e altri servizi ricorrenti di prima necessità",
                        "Casa",
                        "Famiglia",
                        "Fondi per piccole spese",
                        "Formazione e istruzione",
                        "Investimenti",
                        "Lavoro",
                        "Mutui e prestiti",
                        "Pensione integrativa",
                        "Piccole spese personali",
                        "Salute",
                        "Tasse e prelievi diretti",
                        "Tasse e prelievi indiretti",
                        "Vacanze"
                );

        assertThat(categories)
                .allSatisfy(category -> {
                    assertThat(category.getUserGroupId()).isEqualTo(userGroupId);
                    assertThat(category.getCategoryCreatedByUserId()).isEqualTo(createdByUserId);
                    assertThat(category.getCategoryCurrentName()).isNotBlank();
                });

        List<CategoryDetailsHistory> details =
                categoryDetailsHistoryRepository.findAllByCategory_CategoryIdIn(categoryIds);

        assertThat(details).hasSize(20);

        assertThat(details)
                .allSatisfy(detail -> {
                    assertThat(detail.getCategoryId()).isIn(categoryIds);
                    assertThat(detail.getCategoryName()).isNotBlank();
                    assertThat(detail.getCategoryDescription()).isNotBlank();
                    assertThat(detail.getCategoryDescription()).hasSizeLessThanOrEqualTo(500);
                });

        assertThat(details)
                .anySatisfy(detail -> {
                    assertThat(detail.getCategoryName()).isEqualTo("Mutui e prestiti");
                    assertThat(detail.getCategoryDescription()).contains("mutuo", "prestito");
                });

        assertThat(details)
                .anySatisfy(detail -> {
                    assertThat(detail.getCategoryName())
                            .isEqualTo("Bollette e altri servizi ricorrenti di prima necessità");
                    assertThat(detail.getCategoryDescription()).contains("luce", "gas", "internet");
                });

        List<CategoryStatusHistory> statuses =
                categoryStatusHistoryRepository.findAllByCategory_CategoryIdIn(categoryIds);

        assertThat(statuses).hasSize(20);

        assertThat(statuses)
                .allSatisfy(status -> {
                    assertThat(status.getCategoryId()).isIn(categoryIds);
                    assertThat(status.isActive()).isTrue();
                });
    }

    @Test
    void shouldNotCreateDefaultCategoriesTwiceForTheSameUserGroup() {
        UUID userGroupId = createUserGroup();
        UUID createdByUserId = UUID.randomUUID();

        defaultCategoryCreationService.createDefaultCategoriesIfMissing(
                userGroupId,
                createdByUserId,
                Locale.forLanguageTag("it-IT")
        );

        defaultCategoryCreationService.createDefaultCategoriesIfMissing(
                userGroupId,
                createdByUserId,
                Locale.forLanguageTag("it-IT")
        );

        List<Category> categories =
                categoryRepository.findAllByUserGroup_UserGroupIdOrderByCategoryCurrentNameAsc(userGroupId);

        List<UUID> categoryIds = categories.stream()
                .map(Category::getCategoryId)
                .toList();

        assertThat(categories).hasSize(20);
        assertThat(categoryDetailsHistoryRepository.countByCategory_CategoryIdIn(categoryIds)).isEqualTo(20);
        assertThat(categoryStatusHistoryRepository.countByCategory_CategoryIdIn(categoryIds)).isEqualTo(20);
    }

    @Test
    void shouldCreateEnglishDefaultCategoriesWhenEnglishLocaleIsUsed() {
        UUID userGroupId = createUserGroup();
        UUID createdByUserId = UUID.randomUUID();

        defaultCategoryCreationService.createDefaultCategoriesIfMissing(
                userGroupId,
                createdByUserId,
                Locale.US
        );

        List<Category> categories =
                categoryRepository.findAllByUserGroup_UserGroupIdOrderByCategoryCurrentNameAsc(userGroupId);

        assertThat(categories).hasSize(20);

        assertThat(categories)
                .extracting(Category::getCategoryCurrentName)
                .contains(
                        "Rent",
                        "Work",
                        "Loans and mortgages",
                        "Bills and other essential recurring services"
                );
    }

    @Test
    void shouldCreateIndependentDefaultCategoriesForDifferentUserGroups() {
        UUID firstUserGroupId = createUserGroup();
        UUID secondUserGroupId = createUserGroup();

        UUID firstCreatedByUserId = UUID.randomUUID();
        UUID secondCreatedByUserId = UUID.randomUUID();

        defaultCategoryCreationService.createDefaultCategoriesIfMissing(
                firstUserGroupId,
                firstCreatedByUserId,
                Locale.forLanguageTag("it-IT")
        );

        defaultCategoryCreationService.createDefaultCategoriesIfMissing(
                secondUserGroupId,
                secondCreatedByUserId,
                Locale.forLanguageTag("it-IT")
        );

        List<Category> firstGroupCategories =
                categoryRepository.findAllByUserGroup_UserGroupIdOrderByCategoryCurrentNameAsc(firstUserGroupId);

        List<Category> secondGroupCategories =
                categoryRepository.findAllByUserGroup_UserGroupIdOrderByCategoryCurrentNameAsc(secondUserGroupId);

        assertThat(firstGroupCategories).hasSize(20);
        assertThat(secondGroupCategories).hasSize(20);

        assertThat(firstGroupCategories)
                .allSatisfy(category -> {
                    assertThat(category.getUserGroupId()).isEqualTo(firstUserGroupId);
                    assertThat(category.getCategoryCreatedByUserId()).isEqualTo(firstCreatedByUserId);
                });

        assertThat(secondGroupCategories)
                .allSatisfy(category -> {
                    assertThat(category.getUserGroupId()).isEqualTo(secondUserGroupId);
                    assertThat(category.getCategoryCreatedByUserId()).isEqualTo(secondCreatedByUserId);
                });
    }

    private UUID createUserGroup() {
        UUID userGroupId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                        INSERT INTO user_groups (user_group_id, user_group_name)
                        VALUES (?, ?)
                        """,
                userGroupId,
                "Test group " + userGroupId
        );

        return userGroupId;
    }
}