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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_GROUP_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");

    private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID SECOND_CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

    private static final UUID DETAILS_HISTORY_ID_1 = UUID.fromString("00000000-0000-0000-0000-000000001001");
    private static final UUID DETAILS_HISTORY_ID_2 = UUID.fromString("00000000-0000-0000-0000-000000001002");
    private static final UUID DETAILS_HISTORY_ID_3 = UUID.fromString("00000000-0000-0000-0000-000000001003");

    private static final UUID STATUS_HISTORY_ID_1 = UUID.fromString("00000000-0000-0000-0000-000000002001");
    private static final UUID STATUS_HISTORY_ID_2 = UUID.fromString("00000000-0000-0000-0000-000000002002");
    private static final UUID STATUS_HISTORY_ID_3 = UUID.fromString("00000000-0000-0000-0000-000000002003");

    private static final OffsetDateTime OLDER = OffsetDateTime.parse("2026-01-01T10:00:00+01:00");
    private static final OffsetDateTime NEWER = OffsetDateTime.parse("2026-02-01T10:00:00+01:00");

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryDetailsHistoryRepository categoryDetailsHistoryRepository;

    @Mock
    private CategoryStatusHistoryRepository categoryStatusHistoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private User user;

    @Mock
    private UserGroup userGroup;

    private CategoryService categoryService;

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to set field <%s>".formatted(fieldName), exception);
        }
    }

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(
                categoryRepository,
                categoryDetailsHistoryRepository,
                categoryStatusHistoryRepository,
                userRepository
        );
    }

    @Test
    void listCategoriesShouldReturnCategoriesUsingLatestDetailsAndLatestStatus() {
        givenExistingUserInGroup();

        Category rent = category(CATEGORY_ID, "Affitto");
        Category holidays = category(SECOND_CATEGORY_ID, "Vacanze");

        CategoryDetailsHistory oldRentDetails = details(
                rent,
                DETAILS_HISTORY_ID_1,
                OLDER,
                "Vecchia descrizione affitto"
        );

        CategoryDetailsHistory latestRentDetails = details(
                rent,
                DETAILS_HISTORY_ID_2,
                NEWER,
                "Canone mensile di locazione"
        );

        CategoryDetailsHistory holidaysDetails = details(
                holidays,
                DETAILS_HISTORY_ID_3,
                NEWER,
                "Viaggi e ferie"
        );

        CategoryStatusHistory oldRentStatus = status(
                rent,
                STATUS_HISTORY_ID_1,
                OLDER,
                false
        );

        CategoryStatusHistory latestRentStatus = status(
                rent,
                STATUS_HISTORY_ID_2,
                NEWER,
                true
        );

        CategoryStatusHistory holidaysStatus = status(
                holidays,
                STATUS_HISTORY_ID_3,
                NEWER,
                false
        );

        given(categoryRepository.findAllByUserGroup_UserGroupIdOrderByCategoryCurrentNameAsc(USER_GROUP_ID))
                .willReturn(List.of(rent, holidays));

        given(categoryDetailsHistoryRepository.findAllByCategory_CategoryIdIn(List.of(CATEGORY_ID, SECOND_CATEGORY_ID)))
                .willReturn(List.of(oldRentDetails, latestRentDetails, holidaysDetails));

        given(categoryStatusHistoryRepository.findAllByCategory_CategoryIdIn(List.of(CATEGORY_ID, SECOND_CATEGORY_ID)))
                .willReturn(List.of(oldRentStatus, latestRentStatus, holidaysStatus));

        List<CategoryResponse> response = categoryService.listCategories(USER_ID);

        assertThat(response).containsExactly(
                new CategoryResponse(
                        CATEGORY_ID,
                        "Affitto",
                        "Canone mensile di locazione",
                        true
                ),
                new CategoryResponse(
                        SECOND_CATEGORY_ID,
                        "Vacanze",
                        "Viaggi e ferie",
                        false
                )
        );

        verify(categoryRepository).findAllByUserGroup_UserGroupIdOrderByCategoryCurrentNameAsc(USER_GROUP_ID);
        verify(categoryDetailsHistoryRepository).findAllByCategory_CategoryIdIn(List.of(CATEGORY_ID, SECOND_CATEGORY_ID));
        verify(categoryStatusHistoryRepository).findAllByCategory_CategoryIdIn(List.of(CATEGORY_ID, SECOND_CATEGORY_ID));
    }

    @Test
    void listCategoriesShouldReturnEmptyListWithoutLoadingHistoriesWhenNoCategoriesExist() {
        givenExistingUserInGroup();

        given(categoryRepository.findAllByUserGroup_UserGroupIdOrderByCategoryCurrentNameAsc(USER_GROUP_ID))
                .willReturn(List.of());

        List<CategoryResponse> response = categoryService.listCategories(USER_ID);

        assertThat(response).isEmpty();

        verify(categoryRepository).findAllByUserGroup_UserGroupIdOrderByCategoryCurrentNameAsc(USER_GROUP_ID);
        verifyNoInteractions(categoryDetailsHistoryRepository, categoryStatusHistoryRepository);
    }

    @Test
    void listCategoriesShouldFailWhenUserDoesNotExist() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.listCategories(USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("auth.userNotFound");

        verifyNoInteractions(categoryRepository, categoryDetailsHistoryRepository, categoryStatusHistoryRepository);
    }

    @Test
    void listCategoriesShouldFailFastWhenDetailsHistoryIsMissing() {
        givenExistingUserInGroup();

        Category category = category(CATEGORY_ID, "Casa");

        given(categoryRepository.findAllByUserGroup_UserGroupIdOrderByCategoryCurrentNameAsc(USER_GROUP_ID))
                .willReturn(List.of(category));

        given(categoryDetailsHistoryRepository.findAllByCategory_CategoryIdIn(List.of(CATEGORY_ID)))
                .willReturn(List.of());

        given(categoryStatusHistoryRepository.findAllByCategory_CategoryIdIn(List.of(CATEGORY_ID)))
                .willReturn(List.of(status(category, STATUS_HISTORY_ID_1, NEWER, true)));

        assertThatThrownBy(() -> categoryService.listCategories(USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("finance.category.detailsHistoryMissing");
    }

    @Test
    void listCategoriesShouldFailFastWhenStatusHistoryIsMissing() {
        givenExistingUserInGroup();

        Category category = category(CATEGORY_ID, "Casa");

        given(categoryRepository.findAllByUserGroup_UserGroupIdOrderByCategoryCurrentNameAsc(USER_GROUP_ID))
                .willReturn(List.of(category));

        given(categoryDetailsHistoryRepository.findAllByCategory_CategoryIdIn(List.of(CATEGORY_ID)))
                .willReturn(List.of(details(category, DETAILS_HISTORY_ID_1, NEWER, "Spese casa")));

        given(categoryStatusHistoryRepository.findAllByCategory_CategoryIdIn(List.of(CATEGORY_ID)))
                .willReturn(List.of());

        assertThatThrownBy(() -> categoryService.listCategories(USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("finance.category.statusHistoryMissing");
    }

    @Test
    void createCategoryShouldNormalizeInputPersistCategoryDetailsAndActiveStatus() {
        givenExistingUserInGroup();

        Category savedCategory = category(CATEGORY_ID, "Viaggi");

        given(categoryRepository.existsByUserGroupIdAndNormalizedCurrentName(USER_GROUP_ID, "Viaggi"))
                .willReturn(false);

        given(categoryRepository.save(any(Category.class)))
                .willReturn(savedCategory);

        given(categoryDetailsHistoryRepository.save(any(CategoryDetailsHistory.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        given(categoryStatusHistoryRepository.save(any(CategoryStatusHistory.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response = categoryService.createCategory(
                USER_ID,
                new CategoryCreateRequest(
                        "  Viaggi  ",
                        "  Spese per viaggi  "
                )
        );

        assertThat(response).isEqualTo(new CategoryResponse(
                CATEGORY_ID,
                "Viaggi",
                "Spese per viaggi",
                true
        ));

        ArgumentCaptor<Category> categoryCaptor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(categoryCaptor.capture());

        Category categoryToSave = categoryCaptor.getValue();

        assertThat(categoryToSave.getUserGroup()).isSameAs(userGroup);
        assertThat(categoryToSave.getCategoryCreatedByUserId()).isEqualTo(USER_ID);
        assertThat(categoryToSave.getCategoryCurrentName()).isEqualTo("Viaggi");

        ArgumentCaptor<CategoryDetailsHistory> detailsCaptor =
                ArgumentCaptor.forClass(CategoryDetailsHistory.class);

        verify(categoryDetailsHistoryRepository).save(detailsCaptor.capture());

        CategoryDetailsHistory detailsToSave = detailsCaptor.getValue();

        assertThat(detailsToSave.getCategory()).isSameAs(savedCategory);
        assertThat(detailsToSave.getCategoryName()).isEqualTo("Viaggi");
        assertThat(detailsToSave.getCategoryDescription()).isEqualTo("Spese per viaggi");

        ArgumentCaptor<CategoryStatusHistory> statusCaptor =
                ArgumentCaptor.forClass(CategoryStatusHistory.class);

        verify(categoryStatusHistoryRepository).save(statusCaptor.capture());

        CategoryStatusHistory statusToSave = statusCaptor.getValue();

        assertThat(statusToSave.getCategory()).isSameAs(savedCategory);
        assertThat(statusToSave.isActive()).isTrue();
    }

    @Test
    void createCategoryShouldNormalizeBlankDescriptionToNull() {
        givenExistingUserInGroup();

        Category savedCategory = category(CATEGORY_ID, "Altro");

        given(categoryRepository.existsByUserGroupIdAndNormalizedCurrentName(USER_GROUP_ID, "Altro"))
                .willReturn(false);

        given(categoryRepository.save(any(Category.class)))
                .willReturn(savedCategory);

        given(categoryDetailsHistoryRepository.save(any(CategoryDetailsHistory.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        given(categoryStatusHistoryRepository.save(any(CategoryStatusHistory.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response = categoryService.createCategory(
                USER_ID,
                new CategoryCreateRequest(
                        "Altro",
                        "   "
                )
        );

        assertThat(response).isEqualTo(new CategoryResponse(
                CATEGORY_ID,
                "Altro",
                null,
                true
        ));

        ArgumentCaptor<CategoryDetailsHistory> detailsCaptor =
                ArgumentCaptor.forClass(CategoryDetailsHistory.class);

        verify(categoryDetailsHistoryRepository).save(detailsCaptor.capture());

        assertThat(detailsCaptor.getValue().getCategoryDescription()).isNull();
    }

    @Test
    void createCategoryShouldRejectDuplicateNormalizedName() {
        givenExistingUserInGroup();

        given(categoryRepository.existsByUserGroupIdAndNormalizedCurrentName(USER_GROUP_ID, "Casa"))
                .willReturn(true);

        assertThatThrownBy(() -> categoryService.createCategory(
                USER_ID,
                new CategoryCreateRequest(
                        "  Casa  ",
                        "Spese casa"
                )
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.category.nameAlreadyExists");

        verify(categoryRepository, never()).save(any(Category.class));
        verifyNoInteractions(categoryDetailsHistoryRepository, categoryStatusHistoryRepository);
    }

    @Test
    void createCategoryShouldRejectBlankName() {
        givenExistingUserInGroup();

        assertThatThrownBy(() -> categoryService.createCategory(
                USER_ID,
                new CategoryCreateRequest(
                        "   ",
                        "Descrizione"
                )
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.category.nameRequired");

        verifyNoInteractions(categoryRepository, categoryDetailsHistoryRepository, categoryStatusHistoryRepository);
    }

    @Test
    void createCategoryShouldRejectTooLongName() {
        givenExistingUserInGroup();

        assertThatThrownBy(() -> categoryService.createCategory(
                USER_ID,
                new CategoryCreateRequest(
                        "a".repeat(256),
                        "Descrizione"
                )
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.category.nameTooLong");

        verifyNoInteractions(categoryRepository, categoryDetailsHistoryRepository, categoryStatusHistoryRepository);
    }

    @Test
    void createCategoryShouldRejectTooLongDescription() {
        givenExistingUserInGroup();

        assertThatThrownBy(() -> categoryService.createCategory(
                USER_ID,
                new CategoryCreateRequest(
                        "Casa",
                        "a".repeat(501)
                )
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.category.descriptionTooLong");

        verifyNoInteractions(categoryRepository, categoryDetailsHistoryRepository, categoryStatusHistoryRepository);
    }

    @Test
    void updateCategoryShouldNormalizeInputUpdateCurrentNameAndAppendDetailsHistory() {
        givenExistingUserInGroup();

        Category category = category(CATEGORY_ID, "Vecchio nome");
        CategoryStatusHistory currentStatus = status(category, STATUS_HISTORY_ID_1, NEWER, true);

        given(categoryRepository.findByCategoryIdAndUserGroup_UserGroupId(CATEGORY_ID, USER_GROUP_ID))
                .willReturn(Optional.of(category));

        given(categoryRepository.existsByUserGroupIdAndNormalizedCurrentNameExcludingCategoryId(
                USER_GROUP_ID,
                CATEGORY_ID,
                "Casa"
        )).willReturn(false);

        given(categoryDetailsHistoryRepository.save(any(CategoryDetailsHistory.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        given(categoryStatusHistoryRepository.findTopByCategory_CategoryIdOrderByCategoryStatusUpdatedAtDescCategoryStatusHistoryIdDesc(CATEGORY_ID))
                .willReturn(Optional.of(currentStatus));

        CategoryResponse response = categoryService.updateCategory(
                USER_ID,
                CATEGORY_ID,
                new CategoryUpdateRequest(
                        "  Casa  ",
                        "  Spese casa aggiornate  "
                )
        );

        assertThat(response).isEqualTo(new CategoryResponse(
                CATEGORY_ID,
                "Casa",
                "Spese casa aggiornate",
                true
        ));

        assertThat(category.getCategoryCurrentName()).isEqualTo("Casa");

        ArgumentCaptor<CategoryDetailsHistory> detailsCaptor =
                ArgumentCaptor.forClass(CategoryDetailsHistory.class);

        verify(categoryDetailsHistoryRepository).save(detailsCaptor.capture());

        CategoryDetailsHistory detailsToSave = detailsCaptor.getValue();

        assertThat(detailsToSave.getCategory()).isSameAs(category);
        assertThat(detailsToSave.getCategoryName()).isEqualTo("Casa");
        assertThat(detailsToSave.getCategoryDescription()).isEqualTo("Spese casa aggiornate");
    }

    @Test
    void updateCategoryShouldNormalizeBlankDescriptionToNull() {
        givenExistingUserInGroup();

        Category category = category(CATEGORY_ID, "Casa");
        CategoryStatusHistory currentStatus = status(category, STATUS_HISTORY_ID_1, NEWER, true);

        given(categoryRepository.findByCategoryIdAndUserGroup_UserGroupId(CATEGORY_ID, USER_GROUP_ID))
                .willReturn(Optional.of(category));

        given(categoryRepository.existsByUserGroupIdAndNormalizedCurrentNameExcludingCategoryId(
                USER_GROUP_ID,
                CATEGORY_ID,
                "Casa"
        )).willReturn(false);

        given(categoryDetailsHistoryRepository.save(any(CategoryDetailsHistory.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        given(categoryStatusHistoryRepository.findTopByCategory_CategoryIdOrderByCategoryStatusUpdatedAtDescCategoryStatusHistoryIdDesc(CATEGORY_ID))
                .willReturn(Optional.of(currentStatus));

        CategoryResponse response = categoryService.updateCategory(
                USER_ID,
                CATEGORY_ID,
                new CategoryUpdateRequest(
                        "Casa",
                        "   "
                )
        );

        assertThat(response).isEqualTo(new CategoryResponse(
                CATEGORY_ID,
                "Casa",
                null,
                true
        ));

        ArgumentCaptor<CategoryDetailsHistory> detailsCaptor =
                ArgumentCaptor.forClass(CategoryDetailsHistory.class);

        verify(categoryDetailsHistoryRepository).save(detailsCaptor.capture());

        assertThat(detailsCaptor.getValue().getCategoryDescription()).isNull();
    }

    @Test
    void updateCategoryShouldRejectCategoryOutsideUserGroupAsNotFound() {
        givenExistingUserInGroup();

        given(categoryRepository.findByCategoryIdAndUserGroup_UserGroupId(CATEGORY_ID, USER_GROUP_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.updateCategory(
                USER_ID,
                CATEGORY_ID,
                new CategoryUpdateRequest(
                        "Casa",
                        "Spese casa"
                )
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.category.notFound");

        verify(categoryRepository, never()).existsByUserGroupIdAndNormalizedCurrentNameExcludingCategoryId(
                any(),
                any(),
                any()
        );

        verifyNoInteractions(categoryDetailsHistoryRepository, categoryStatusHistoryRepository);
    }

    @Test
    void updateCategoryShouldRejectDuplicateNormalizedNameExcludingCurrentCategory() {
        givenExistingUserInGroup();

        Category category = category(CATEGORY_ID, "Vecchio nome");

        given(categoryRepository.findByCategoryIdAndUserGroup_UserGroupId(CATEGORY_ID, USER_GROUP_ID))
                .willReturn(Optional.of(category));

        given(categoryRepository.existsByUserGroupIdAndNormalizedCurrentNameExcludingCategoryId(
                USER_GROUP_ID,
                CATEGORY_ID,
                "Casa"
        )).willReturn(true);

        assertThatThrownBy(() -> categoryService.updateCategory(
                USER_ID,
                CATEGORY_ID,
                new CategoryUpdateRequest(
                        "  Casa  ",
                        "Spese casa"
                )
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.category.nameAlreadyExists");

        assertThat(category.getCategoryCurrentName()).isEqualTo("Vecchio nome");

        verify(categoryDetailsHistoryRepository, never()).save(any(CategoryDetailsHistory.class));
        verifyNoInteractions(categoryStatusHistoryRepository);
    }

    @Test
    void updateCategoryShouldRejectBlankName() {
        givenExistingUserInGroup();

        assertThatThrownBy(() -> categoryService.updateCategory(
                USER_ID,
                CATEGORY_ID,
                new CategoryUpdateRequest(
                        " ",
                        "Descrizione"
                )
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.category.nameRequired");

        verifyNoInteractions(categoryRepository, categoryDetailsHistoryRepository, categoryStatusHistoryRepository);
    }

    @Test
    void updateCategoryShouldRejectTooLongName() {
        givenExistingUserInGroup();

        assertThatThrownBy(() -> categoryService.updateCategory(
                USER_ID,
                CATEGORY_ID,
                new CategoryUpdateRequest(
                        "a".repeat(256),
                        "Descrizione"
                )
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.category.nameTooLong");

        verifyNoInteractions(categoryRepository, categoryDetailsHistoryRepository, categoryStatusHistoryRepository);
    }

    @Test
    void updateCategoryShouldRejectTooLongDescription() {
        givenExistingUserInGroup();

        assertThatThrownBy(() -> categoryService.updateCategory(
                USER_ID,
                CATEGORY_ID,
                new CategoryUpdateRequest(
                        "Casa",
                        "a".repeat(501)
                )
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.category.descriptionTooLong");

        verifyNoInteractions(categoryRepository, categoryDetailsHistoryRepository, categoryStatusHistoryRepository);
    }

    @Test
    void updateCategoryShouldFailWhenStatusHistoryIsMissing() {
        givenExistingUserInGroup();

        Category category = category(CATEGORY_ID, "Casa");

        given(categoryRepository.findByCategoryIdAndUserGroup_UserGroupId(CATEGORY_ID, USER_GROUP_ID))
                .willReturn(Optional.of(category));

        given(categoryRepository.existsByUserGroupIdAndNormalizedCurrentNameExcludingCategoryId(
                USER_GROUP_ID,
                CATEGORY_ID,
                "Casa aggiornata"
        )).willReturn(false);

        given(categoryDetailsHistoryRepository.save(any(CategoryDetailsHistory.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        given(categoryStatusHistoryRepository.findTopByCategory_CategoryIdOrderByCategoryStatusUpdatedAtDescCategoryStatusHistoryIdDesc(CATEGORY_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.updateCategory(
                USER_ID,
                CATEGORY_ID,
                new CategoryUpdateRequest(
                        "Casa aggiornata",
                        "Descrizione aggiornata"
                )
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("finance.category.statusHistoryMissing");
    }

    @Test
    void deactivateCategoryShouldAppendInactiveStatus() {
        givenExistingUserInGroup();

        Category category = category(CATEGORY_ID, "Vacanze");
        CategoryDetailsHistory details = details(category, DETAILS_HISTORY_ID_1, NEWER, "Viaggi e ferie");
        CategoryStatusHistory currentStatus = status(category, STATUS_HISTORY_ID_1, NEWER, true);

        given(categoryRepository.findByCategoryIdAndUserGroup_UserGroupId(CATEGORY_ID, USER_GROUP_ID))
                .willReturn(Optional.of(category));

        given(categoryDetailsHistoryRepository.findTopByCategory_CategoryIdOrderByCategoryDetailsUpdatedAtDescCategoryDetailsHistoryIdDesc(CATEGORY_ID))
                .willReturn(Optional.of(details));

        given(categoryStatusHistoryRepository.findTopByCategory_CategoryIdOrderByCategoryStatusUpdatedAtDescCategoryStatusHistoryIdDesc(CATEGORY_ID))
                .willReturn(Optional.of(currentStatus));

        given(categoryStatusHistoryRepository.save(any(CategoryStatusHistory.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response = categoryService.deactivateCategory(USER_ID, CATEGORY_ID);

        assertThat(response).isEqualTo(new CategoryResponse(
                CATEGORY_ID,
                "Vacanze",
                "Viaggi e ferie",
                false
        ));

        ArgumentCaptor<CategoryStatusHistory> statusCaptor =
                ArgumentCaptor.forClass(CategoryStatusHistory.class);

        verify(categoryStatusHistoryRepository).save(statusCaptor.capture());

        CategoryStatusHistory statusToSave = statusCaptor.getValue();

        assertThat(statusToSave.getCategory()).isSameAs(category);
        assertThat(statusToSave.isActive()).isFalse();
    }

    @Test
    void deactivateCategoryShouldBeIdempotentWhenAlreadyInactive() {
        givenExistingUserInGroup();

        Category category = category(CATEGORY_ID, "Vacanze");
        CategoryDetailsHistory details = details(category, DETAILS_HISTORY_ID_1, NEWER, "Viaggi e ferie");
        CategoryStatusHistory currentStatus = status(category, STATUS_HISTORY_ID_1, NEWER, false);

        given(categoryRepository.findByCategoryIdAndUserGroup_UserGroupId(CATEGORY_ID, USER_GROUP_ID))
                .willReturn(Optional.of(category));

        given(categoryDetailsHistoryRepository.findTopByCategory_CategoryIdOrderByCategoryDetailsUpdatedAtDescCategoryDetailsHistoryIdDesc(CATEGORY_ID))
                .willReturn(Optional.of(details));

        given(categoryStatusHistoryRepository.findTopByCategory_CategoryIdOrderByCategoryStatusUpdatedAtDescCategoryStatusHistoryIdDesc(CATEGORY_ID))
                .willReturn(Optional.of(currentStatus));

        CategoryResponse response = categoryService.deactivateCategory(USER_ID, CATEGORY_ID);

        assertThat(response).isEqualTo(new CategoryResponse(
                CATEGORY_ID,
                "Vacanze",
                "Viaggi e ferie",
                false
        ));

        verify(categoryStatusHistoryRepository, never()).save(any(CategoryStatusHistory.class));
    }

    @Test
    void reactivateCategoryShouldAppendActiveStatus() {
        givenExistingUserInGroup();

        Category category = category(CATEGORY_ID, "Vacanze");
        CategoryDetailsHistory details = details(category, DETAILS_HISTORY_ID_1, NEWER, "Viaggi e ferie");
        CategoryStatusHistory currentStatus = status(category, STATUS_HISTORY_ID_1, NEWER, false);

        given(categoryRepository.findByCategoryIdAndUserGroup_UserGroupId(CATEGORY_ID, USER_GROUP_ID))
                .willReturn(Optional.of(category));

        given(categoryDetailsHistoryRepository.findTopByCategory_CategoryIdOrderByCategoryDetailsUpdatedAtDescCategoryDetailsHistoryIdDesc(CATEGORY_ID))
                .willReturn(Optional.of(details));

        given(categoryStatusHistoryRepository.findTopByCategory_CategoryIdOrderByCategoryStatusUpdatedAtDescCategoryStatusHistoryIdDesc(CATEGORY_ID))
                .willReturn(Optional.of(currentStatus));

        given(categoryStatusHistoryRepository.save(any(CategoryStatusHistory.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response = categoryService.reactivateCategory(USER_ID, CATEGORY_ID);

        assertThat(response).isEqualTo(new CategoryResponse(
                CATEGORY_ID,
                "Vacanze",
                "Viaggi e ferie",
                true
        ));

        ArgumentCaptor<CategoryStatusHistory> statusCaptor =
                ArgumentCaptor.forClass(CategoryStatusHistory.class);

        verify(categoryStatusHistoryRepository).save(statusCaptor.capture());

        CategoryStatusHistory statusToSave = statusCaptor.getValue();

        assertThat(statusToSave.getCategory()).isSameAs(category);
        assertThat(statusToSave.isActive()).isTrue();
    }

    @Test
    void reactivateCategoryShouldBeIdempotentWhenAlreadyActive() {
        givenExistingUserInGroup();

        Category category = category(CATEGORY_ID, "Vacanze");
        CategoryDetailsHistory details = details(category, DETAILS_HISTORY_ID_1, NEWER, "Viaggi e ferie");
        CategoryStatusHistory currentStatus = status(category, STATUS_HISTORY_ID_1, NEWER, true);

        given(categoryRepository.findByCategoryIdAndUserGroup_UserGroupId(CATEGORY_ID, USER_GROUP_ID))
                .willReturn(Optional.of(category));

        given(categoryDetailsHistoryRepository.findTopByCategory_CategoryIdOrderByCategoryDetailsUpdatedAtDescCategoryDetailsHistoryIdDesc(CATEGORY_ID))
                .willReturn(Optional.of(details));

        given(categoryStatusHistoryRepository.findTopByCategory_CategoryIdOrderByCategoryStatusUpdatedAtDescCategoryStatusHistoryIdDesc(CATEGORY_ID))
                .willReturn(Optional.of(currentStatus));

        CategoryResponse response = categoryService.reactivateCategory(USER_ID, CATEGORY_ID);

        assertThat(response).isEqualTo(new CategoryResponse(
                CATEGORY_ID,
                "Vacanze",
                "Viaggi e ferie",
                true
        ));

        verify(categoryStatusHistoryRepository, never()).save(any(CategoryStatusHistory.class));
    }

    @Test
    void deactivateCategoryShouldRejectCategoryOutsideUserGroupAsNotFound() {
        givenExistingUserInGroup();

        given(categoryRepository.findByCategoryIdAndUserGroup_UserGroupId(CATEGORY_ID, USER_GROUP_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.deactivateCategory(USER_ID, CATEGORY_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.category.notFound");

        verifyNoInteractions(categoryDetailsHistoryRepository, categoryStatusHistoryRepository);
    }

    @Test
    void reactivateCategoryShouldRejectCategoryOutsideUserGroupAsNotFound() {
        givenExistingUserInGroup();

        given(categoryRepository.findByCategoryIdAndUserGroup_UserGroupId(CATEGORY_ID, USER_GROUP_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.reactivateCategory(USER_ID, CATEGORY_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.category.notFound");

        verifyNoInteractions(categoryDetailsHistoryRepository, categoryStatusHistoryRepository);
    }

    @Test
    void deactivateCategoryShouldFailWhenDetailsHistoryIsMissing() {
        givenExistingUserInGroup();

        Category category = category(CATEGORY_ID, "Vacanze");

        given(categoryRepository.findByCategoryIdAndUserGroup_UserGroupId(CATEGORY_ID, USER_GROUP_ID))
                .willReturn(Optional.of(category));

        given(categoryDetailsHistoryRepository.findTopByCategory_CategoryIdOrderByCategoryDetailsUpdatedAtDescCategoryDetailsHistoryIdDesc(CATEGORY_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.deactivateCategory(USER_ID, CATEGORY_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("finance.category.detailsHistoryMissing");

        verifyNoInteractions(categoryStatusHistoryRepository);
    }

    @Test
    void deactivateCategoryShouldFailWhenStatusHistoryIsMissing() {
        givenExistingUserInGroup();

        Category category = category(CATEGORY_ID, "Vacanze");
        CategoryDetailsHistory details = details(category, DETAILS_HISTORY_ID_1, NEWER, "Viaggi e ferie");

        given(categoryRepository.findByCategoryIdAndUserGroup_UserGroupId(CATEGORY_ID, USER_GROUP_ID))
                .willReturn(Optional.of(category));

        given(categoryDetailsHistoryRepository.findTopByCategory_CategoryIdOrderByCategoryDetailsUpdatedAtDescCategoryDetailsHistoryIdDesc(CATEGORY_ID))
                .willReturn(Optional.of(details));

        given(categoryStatusHistoryRepository.findTopByCategory_CategoryIdOrderByCategoryStatusUpdatedAtDescCategoryStatusHistoryIdDesc(CATEGORY_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.deactivateCategory(USER_ID, CATEGORY_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("finance.category.statusHistoryMissing");

        verify(categoryStatusHistoryRepository, never()).save(any(CategoryStatusHistory.class));
    }

    private void givenExistingUserInGroup() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(user.getUserGroup()).willReturn(userGroup);
        given(userGroup.getUserGroupId()).willReturn(USER_GROUP_ID);
    }

    private Category category(UUID categoryId, String currentName) {
        Category category = Category.create(
                userGroup,
                USER_ID,
                currentName
        );

        setField(category, "categoryId", categoryId);

        return category;
    }

    private CategoryDetailsHistory details(
            Category category,
            UUID detailsHistoryId,
            OffsetDateTime updatedAt,
            String description
    ) {
        CategoryDetailsHistory detailsHistory = CategoryDetailsHistory.create(
                category,
                category.getCategoryCurrentName(),
                description
        );

        setField(detailsHistory, "categoryDetailsHistoryId", detailsHistoryId);
        setField(detailsHistory, "categoryDetailsUpdatedAt", updatedAt);

        return detailsHistory;
    }

    private CategoryStatusHistory status(
            Category category,
            UUID statusHistoryId,
            OffsetDateTime updatedAt,
            boolean active
    ) {
        CategoryStatusHistory statusHistory = active
                ? CategoryStatusHistory.active(category)
                : CategoryStatusHistory.inactive(category);

        setField(statusHistory, "categoryStatusHistoryId", statusHistoryId);
        setField(statusHistory, "categoryStatusUpdatedAt", updatedAt);

        return statusHistory;
    }
}