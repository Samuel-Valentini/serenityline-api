package me.serenityline.api.finance.category.controller;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import me.serenityline.api.finance.category.entity.Category;
import me.serenityline.api.finance.category.entity.CategoryDetailsHistory;
import me.serenityline.api.finance.category.entity.CategoryStatusHistory;
import me.serenityline.api.finance.category.repository.CategoryDetailsHistoryRepository;
import me.serenityline.api.finance.category.repository.CategoryRepository;
import me.serenityline.api.finance.category.repository.CategoryStatusHistoryRepository;
import me.serenityline.api.security.jwt.JwtTokenService;
import me.serenityline.api.user.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CategoryLifecycleIntegrationTest {

    private static final String CATEGORIES_PATH = "/api/finance/categories";

    private static final String USER_GROUP_NAME_PREFIX = "Category lifecycle test group ";
    private static final String USER_NAME = "Samuel";
    private static final String USER_EMAIL_PREFIX = "category-lifecycle";
    private static final String USER_EMAIL_DOMAIN = "example.com";
    private static final String DEFAULT_PASSWORD = "VeryStrongPassword-2026-SerenityLine!";

    private static final UserRole USER_ROLE = UserRole.OWNER;
    private static final UserPlatformRole USER_PLATFORM_ROLE = UserPlatformRole.USER;
    private static final String PREFERRED_LOCALE = "it-IT";
    private static final PreferredTheme PREFERRED_THEME = PreferredTheme.DEFAULT;
    private static final boolean WANTS_INVOICE = false;
    private static final boolean PAYMENT_EMAIL_REMINDERS_ENABLED = true;
    private static final boolean USER_ENABLED = true;
    private static final Long DEFAULT_TOKEN_VERSION = 0L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CategoryDetailsHistoryRepository categoryDetailsHistoryRepository;

    @Autowired
    private CategoryStatusHistoryRepository categoryStatusHistoryRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private TransactionTemplate transactionTemplate;

    private static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private static String uniqueEmail(String label) {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16);

        return "cat-" + suffix + "@" + USER_EMAIL_DOMAIN;
    }

    private static UUID userGroupIdOf(User user) {
        return user.getUserGroup()
                .getUserGroupId();
    }

    private static String categoryJson(String categoryName, String categoryDescription) {
        return """
                {
                  "categoryName": "%s",
                  "categoryDescription": "%s"
                }
                """.formatted(categoryName, categoryDescription);
    }

    private static String jsonString(MvcResult result, String jsonPath) throws Exception {
        return JsonPath.read(
                result.getResponse().getContentAsString(),
                jsonPath
        );
    }

    private static Optional<Map<String, Object>> categoryInResponse(
            MvcResult result,
            UUID categoryId
    ) throws Exception {
        List<Map<String, Object>> categories = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$"
        );

        return categories.stream()
                .filter(category -> categoryId.toString().equals(category.get("categoryId")))
                .findFirst();
    }

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    void categoryLifecycleShouldPersistCategoryDetailsHistoryAndStatusHistory() throws Exception {
        User user = createVerifiedUser("owner");
        String accessToken = accessTokenFor(user);
        UUID userGroupId = userGroupIdOf(user);

        MvcResult createResult = mockMvc.perform(post(CATEGORIES_PATH)
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("  Viaggi  ", "  Spese per viaggi  ")))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.categoryName").value("Viaggi"))
                .andExpect(jsonPath("$.categoryDescription").value("Spese per viaggi"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();

        UUID categoryId = UUID.fromString(jsonString(createResult, "$.categoryId"));

        assertPersistedCategory(
                categoryId,
                userGroupId,
                user.getUserId(),
                "Viaggi",
                "Spese per viaggi",
                true,
                1,
                1
        );

        MvcResult listAfterCreateResult = mockMvc.perform(get(CATEGORIES_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        Map<String, Object> listedAfterCreate = categoryInResponse(listAfterCreateResult, categoryId)
                .orElseThrow();

        assertThat(listedAfterCreate.get("categoryName")).isEqualTo("Viaggi");
        assertThat(listedAfterCreate.get("categoryDescription")).isEqualTo("Spese per viaggi");
        assertThat(listedAfterCreate.get("active")).isEqualTo(true);

        mockMvc.perform(put(CATEGORIES_PATH + "/{categoryId}", categoryId)
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("  Casa  ", "  Spese casa aggiornate  ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(categoryId.toString()))
                .andExpect(jsonPath("$.categoryName").value("Casa"))
                .andExpect(jsonPath("$.categoryDescription").value("Spese casa aggiornate"))
                .andExpect(jsonPath("$.active").value(true));

        assertPersistedCategory(
                categoryId,
                userGroupId,
                user.getUserId(),
                "Casa",
                "Spese casa aggiornate",
                true,
                2,
                1
        );

        mockMvc.perform(post(CATEGORIES_PATH + "/{categoryId}/deactivate", categoryId)
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(categoryId.toString()))
                .andExpect(jsonPath("$.categoryName").value("Casa"))
                .andExpect(jsonPath("$.categoryDescription").value("Spese casa aggiornate"))
                .andExpect(jsonPath("$.active").value(false));

        assertPersistedCategory(
                categoryId,
                userGroupId,
                user.getUserId(),
                "Casa",
                "Spese casa aggiornate",
                false,
                2,
                2
        );

        MvcResult listAfterDeactivateResult = mockMvc.perform(get(CATEGORIES_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> listedAfterDeactivate = categoryInResponse(listAfterDeactivateResult, categoryId)
                .orElseThrow();

        assertThat(listedAfterDeactivate.get("active")).isEqualTo(false);

        mockMvc.perform(post(CATEGORIES_PATH + "/{categoryId}/reactivate", categoryId)
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(categoryId.toString()))
                .andExpect(jsonPath("$.categoryName").value("Casa"))
                .andExpect(jsonPath("$.categoryDescription").value("Spese casa aggiornate"))
                .andExpect(jsonPath("$.active").value(true));

        assertPersistedCategory(
                categoryId,
                userGroupId,
                user.getUserId(),
                "Casa",
                "Spese casa aggiornate",
                true,
                2,
                3
        );

        mockMvc.perform(post(CATEGORIES_PATH)
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("  casa  ", "Duplicata")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.category.nameAlreadyExists"));

        assertOnlyOneCategoryWithCurrentName(userGroupId, "Casa");
    }

    @Test
    void categoryEndpointsShouldEnforceUserGroupIsolation() throws Exception {
        User owner = createVerifiedUser("owner-isolation");
        User otherUser = createVerifiedUser("other-isolation");

        String ownerAccessToken = accessTokenFor(owner);
        String otherAccessToken = accessTokenFor(otherUser);
        UUID ownerUserGroupId = userGroupIdOf(owner);

        MvcResult createResult = mockMvc.perform(post(CATEGORIES_PATH)
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("Privata", "Categoria del primo gruppo")))
                .andExpect(status().isCreated())
                .andReturn();

        UUID categoryId = UUID.fromString(jsonString(createResult, "$.categoryId"));

        MvcResult otherUserListResult = mockMvc.perform(get(CATEGORIES_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherAccessToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(categoryInResponse(otherUserListResult, categoryId))
                .isEmpty();

        mockMvc.perform(put(CATEGORIES_PATH + "/{categoryId}", categoryId)
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("Tentativo modifica", "Non deve riuscire")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.category.notFound"));

        mockMvc.perform(post(CATEGORIES_PATH + "/{categoryId}/deactivate", categoryId)
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherAccessToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.category.notFound"));

        assertPersistedCategory(
                categoryId,
                ownerUserGroupId,
                owner.getUserId(),
                "Privata",
                "Categoria del primo gruppo",
                true,
                1,
                1
        );
    }

    private User createVerifiedUser(String label) {
        return transactionTemplate.execute(status -> {
            UserGroup userGroup = new UserGroup(USER_GROUP_NAME_PREFIX + UUID.randomUUID());
            String passwordHash = passwordEncoder.encode(DEFAULT_PASSWORD);

            User user = new User(
                    USER_NAME,
                    uniqueEmail(label),
                    userGroup,
                    USER_ROLE,
                    USER_PLATFORM_ROLE,
                    PREFERRED_LOCALE,
                    PREFERRED_THEME,
                    WANTS_INVOICE,
                    PAYMENT_EMAIL_REMINDERS_ENABLED,
                    passwordHash,
                    USER_ENABLED,
                    DEFAULT_TOKEN_VERSION
            );

            entityManager.persist(userGroup);
            entityManager.persist(user);
            entityManager.flush();

            return user;
        });
    }

    private void assertPersistedCategory(
            UUID categoryId,
            UUID userGroupId,
            UUID createdByUserId,
            String expectedCurrentName,
            String expectedLatestDescription,
            boolean expectedActive,
            int expectedDetailsHistoryCount,
            int expectedStatusHistoryCount
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            Category category = categoryRepository.findByCategoryIdAndUserGroup_UserGroupId(
                            categoryId,
                            userGroupId
                    )
                    .orElseThrow();

            CategoryDetailsHistory latestDetails = categoryDetailsHistoryRepository
                    .findTopByCategory_CategoryIdOrderByCategoryDetailsUpdatedAtDescCategoryDetailsHistoryIdDesc(categoryId)
                    .orElseThrow();

            CategoryStatusHistory latestStatus = categoryStatusHistoryRepository
                    .findTopByCategory_CategoryIdOrderByCategoryStatusUpdatedAtDescCategoryStatusHistoryIdDesc(categoryId)
                    .orElseThrow();

            List<CategoryDetailsHistory> detailsHistory = categoryDetailsHistoryRepository
                    .findAllByCategory_CategoryIdOrderByCategoryDetailsUpdatedAtDesc(categoryId);

            List<CategoryStatusHistory> statusHistory = categoryStatusHistoryRepository
                    .findAllByCategory_CategoryIdOrderByCategoryStatusUpdatedAtDesc(categoryId);

            assertThat(category.getCategoryCurrentName())
                    .isEqualTo(expectedCurrentName);

            assertThat(category.getCategoryCreatedByUserId())
                    .isEqualTo(createdByUserId);

            assertThat(latestDetails.getCategoryName())
                    .isEqualTo(expectedCurrentName);

            assertThat(latestDetails.getCategoryDescription())
                    .isEqualTo(expectedLatestDescription);

            assertThat(latestStatus.isActive())
                    .isEqualTo(expectedActive);

            assertThat(detailsHistory)
                    .hasSize(expectedDetailsHistoryCount);

            assertThat(statusHistory)
                    .hasSize(expectedStatusHistoryCount);
        });
    }

    private void assertOnlyOneCategoryWithCurrentName(UUID userGroupId, String currentName) {
        transactionTemplate.executeWithoutResult(status -> {
            long matchingCategories = categoryRepository
                    .findAllByUserGroup_UserGroupIdOrderByCategoryCurrentNameAsc(userGroupId)
                    .stream()
                    .filter(category -> currentName.equals(category.getCategoryCurrentName()))
                    .count();

            assertThat(matchingCategories)
                    .isEqualTo(1);
        });
    }

    private String accessTokenFor(User user) {
        return jwtTokenService.createAccessToken(user)
                .token();
    }
}