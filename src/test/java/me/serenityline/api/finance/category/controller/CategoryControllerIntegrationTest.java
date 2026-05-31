package me.serenityline.api.finance.category.controller;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import me.serenityline.api.finance.category.dto.CategoryCreateRequest;
import me.serenityline.api.finance.category.dto.CategoryResponse;
import me.serenityline.api.finance.category.dto.CategoryUpdateRequest;
import me.serenityline.api.finance.category.service.CategoryService;
import me.serenityline.api.security.auth.AuthenticatedUser;
import me.serenityline.api.security.auth.JwtAuthenticationFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CategoryController.class)
class CategoryControllerIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID SECOND_CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() throws Exception {
        bypassJwtAuthenticationFilter();
    }

    @Test
    void listCategoriesShouldReturnCategories() throws Exception {
        given(categoryService.listCategories(USER_ID)).willReturn(List.of(
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
        ));

        mockMvc.perform(get("/api/finance/categories")
                        .with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].categoryId").value(CATEGORY_ID.toString()))
                .andExpect(jsonPath("$[0].categoryName").value("Affitto"))
                .andExpect(jsonPath("$[0].categoryDescription").value("Canone mensile di locazione"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[1].categoryId").value(SECOND_CATEGORY_ID.toString()))
                .andExpect(jsonPath("$[1].categoryName").value("Vacanze"))
                .andExpect(jsonPath("$[1].categoryDescription").value("Viaggi e ferie"))
                .andExpect(jsonPath("$[1].active").value(false));

        verify(categoryService).listCategories(USER_ID);
    }

    @Test
    void listCategoriesShouldReturnEmptyArray() throws Exception {
        given(categoryService.listCategories(USER_ID)).willReturn(List.of());

        mockMvc.perform(get("/api/finance/categories")
                        .with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(categoryService).listCategories(USER_ID);
    }

    @Test
    void createCategoryShouldReturnCreatedCategoryAndDelegateToService() throws Exception {
        given(categoryService.createCategory(
                eq(USER_ID),
                any(CategoryCreateRequest.class)
        )).willReturn(new CategoryResponse(
                CATEGORY_ID,
                "Viaggi",
                "Spese per viaggi",
                true
        ));

        mockMvc.perform(post("/api/finance/categories")
                        .with(authenticatedUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryName": "Viaggi",
                                  "categoryDescription": "Spese per viaggi"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.categoryId").value(CATEGORY_ID.toString()))
                .andExpect(jsonPath("$.categoryName").value("Viaggi"))
                .andExpect(jsonPath("$.categoryDescription").value("Spese per viaggi"))
                .andExpect(jsonPath("$.active").value(true));

        ArgumentCaptor<CategoryCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(CategoryCreateRequest.class);

        verify(categoryService).createCategory(
                eq(USER_ID),
                requestCaptor.capture()
        );

        assertThat(requestCaptor.getValue().categoryName()).isEqualTo("Viaggi");
        assertThat(requestCaptor.getValue().categoryDescription()).isEqualTo("Spese per viaggi");
    }

    @Test
    void createCategoryShouldAcceptNullDescription() throws Exception {
        given(categoryService.createCategory(
                eq(USER_ID),
                any(CategoryCreateRequest.class)
        )).willReturn(new CategoryResponse(
                CATEGORY_ID,
                "Altro",
                null,
                true
        ));

        mockMvc.perform(post("/api/finance/categories")
                        .with(authenticatedUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryName": "Altro",
                                  "categoryDescription": null
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryId").value(CATEGORY_ID.toString()))
                .andExpect(jsonPath("$.categoryName").value("Altro"))
                .andExpect(jsonPath("$.categoryDescription").doesNotExist())
                .andExpect(jsonPath("$.active").value(true));

        ArgumentCaptor<CategoryCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(CategoryCreateRequest.class);

        verify(categoryService).createCategory(
                eq(USER_ID),
                requestCaptor.capture()
        );

        assertThat(requestCaptor.getValue().categoryName()).isEqualTo("Altro");
        assertThat(requestCaptor.getValue().categoryDescription()).isNull();
    }

    @Test
    void createCategoryShouldRejectBlankName() throws Exception {
        mockMvc.perform(post("/api/finance/categories")
                        .with(authenticatedUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryName": "   ",
                                  "categoryDescription": "Descrizione"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("categoryName"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("finance.category.nameRequired"));

        verifyNoInteractions(categoryService);
    }

    @Test
    void createCategoryShouldRejectTooLongName() throws Exception {
        String tooLongName = "a".repeat(256);

        mockMvc.perform(post("/api/finance/categories")
                        .with(authenticatedUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryName": "%s",
                                  "categoryDescription": "Descrizione"
                                }
                                """.formatted(tooLongName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("categoryName"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("finance.category.nameTooLong"));

        verifyNoInteractions(categoryService);
    }

    @Test
    void createCategoryShouldRejectTooLongDescription() throws Exception {
        String tooLongDescription = "a".repeat(501);

        mockMvc.perform(post("/api/finance/categories")
                        .with(authenticatedUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryName": "Casa",
                                  "categoryDescription": "%s"
                                }
                                """.formatted(tooLongDescription)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("categoryDescription"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("finance.category.descriptionTooLong"));

        verifyNoInteractions(categoryService);
    }

    @Test
    void updateCategoryShouldReturnUpdatedCategoryAndDelegateToService() throws Exception {
        given(categoryService.updateCategory(
                eq(USER_ID),
                eq(CATEGORY_ID),
                any(CategoryUpdateRequest.class)
        )).willReturn(new CategoryResponse(
                CATEGORY_ID,
                "Casa",
                "Spese casa aggiornate",
                true
        ));

        mockMvc.perform(put("/api/finance/categories/{categoryId}", CATEGORY_ID)
                        .with(authenticatedUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryName": "Casa",
                                  "categoryDescription": "Spese casa aggiornate"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.categoryId").value(CATEGORY_ID.toString()))
                .andExpect(jsonPath("$.categoryName").value("Casa"))
                .andExpect(jsonPath("$.categoryDescription").value("Spese casa aggiornate"))
                .andExpect(jsonPath("$.active").value(true));

        ArgumentCaptor<CategoryUpdateRequest> requestCaptor =
                ArgumentCaptor.forClass(CategoryUpdateRequest.class);

        verify(categoryService).updateCategory(
                eq(USER_ID),
                eq(CATEGORY_ID),
                requestCaptor.capture()
        );

        assertThat(requestCaptor.getValue().categoryName()).isEqualTo("Casa");
        assertThat(requestCaptor.getValue().categoryDescription()).isEqualTo("Spese casa aggiornate");
    }

    @Test
    void updateCategoryShouldRejectBlankName() throws Exception {
        mockMvc.perform(put("/api/finance/categories/{categoryId}", CATEGORY_ID)
                        .with(authenticatedUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryName": "",
                                  "categoryDescription": "Descrizione"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("categoryName"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("finance.category.nameRequired"));

        verifyNoInteractions(categoryService);
    }

    @Test
    void updateCategoryShouldRejectTooLongName() throws Exception {
        String tooLongName = "a".repeat(256);

        mockMvc.perform(put("/api/finance/categories/{categoryId}", CATEGORY_ID)
                        .with(authenticatedUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryName": "%s",
                                  "categoryDescription": "Descrizione"
                                }
                                """.formatted(tooLongName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("categoryName"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("finance.category.nameTooLong"));

        verifyNoInteractions(categoryService);
    }

    @Test
    void updateCategoryShouldRejectTooLongDescription() throws Exception {
        String tooLongDescription = "a".repeat(501);

        mockMvc.perform(put("/api/finance/categories/{categoryId}", CATEGORY_ID)
                        .with(authenticatedUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryName": "Casa",
                                  "categoryDescription": "%s"
                                }
                                """.formatted(tooLongDescription)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("categoryDescription"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("finance.category.descriptionTooLong"));

        verifyNoInteractions(categoryService);
    }

    @Test
    void deactivateCategoryShouldReturnDeactivatedCategoryAndDelegateToService() throws Exception {
        given(categoryService.deactivateCategory(USER_ID, CATEGORY_ID))
                .willReturn(new CategoryResponse(
                        CATEGORY_ID,
                        "Vacanze",
                        "Viaggi e ferie",
                        false
                ));

        mockMvc.perform(post("/api/finance/categories/{categoryId}/deactivate", CATEGORY_ID)
                        .with(authenticatedUser())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(CATEGORY_ID.toString()))
                .andExpect(jsonPath("$.categoryName").value("Vacanze"))
                .andExpect(jsonPath("$.categoryDescription").value("Viaggi e ferie"))
                .andExpect(jsonPath("$.active").value(false));

        verify(categoryService).deactivateCategory(USER_ID, CATEGORY_ID);
    }

    @Test
    void reactivateCategoryShouldReturnReactivatedCategoryAndDelegateToService() throws Exception {
        given(categoryService.reactivateCategory(USER_ID, CATEGORY_ID))
                .willReturn(new CategoryResponse(
                        CATEGORY_ID,
                        "Vacanze",
                        "Viaggi e ferie",
                        true
                ));

        mockMvc.perform(post("/api/finance/categories/{categoryId}/reactivate", CATEGORY_ID)
                        .with(authenticatedUser())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(CATEGORY_ID.toString()))
                .andExpect(jsonPath("$.categoryName").value("Vacanze"))
                .andExpect(jsonPath("$.categoryDescription").value("Viaggi e ferie"))
                .andExpect(jsonPath("$.active").value(true));

        verify(categoryService).reactivateCategory(USER_ID, CATEGORY_ID);
    }

    @Test
    void listCategoriesShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/finance/categories"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(categoryService);
    }

    @Test
    void createCategoryShouldRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/finance/categories")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryName": "Casa",
                                  "categoryDescription": "Spese casa"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(categoryService);
    }

    @Test
    void updateCategoryShouldRequireAuthentication() throws Exception {
        mockMvc.perform(put("/api/finance/categories/{categoryId}", CATEGORY_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryName": "Casa",
                                  "categoryDescription": "Spese casa"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(categoryService);
    }

    @Test
    void deactivateCategoryShouldRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/finance/categories/{categoryId}/deactivate", CATEGORY_ID)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(categoryService);
    }

    @Test
    void reactivateCategoryShouldRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/finance/categories/{categoryId}/reactivate", CATEGORY_ID)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(categoryService);
    }

    private RequestPostProcessor authenticatedUser() {
        AuthenticatedUser principal = mock(AuthenticatedUser.class);
        given(principal.userId()).willReturn(USER_ID);

        TestingAuthenticationToken authenticationToken =
                new TestingAuthenticationToken(principal, null, "ROLE_USER");

        authenticationToken.setAuthenticated(true);

        return authentication(authenticationToken);
    }

    private void bypassJwtAuthenticationFilter() throws Exception {
        doAnswer(invocation -> {
            ServletRequest request = invocation.getArgument(0);
            ServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);

            chain.doFilter(request, response);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(
                any(ServletRequest.class),
                any(ServletResponse.class),
                any(FilterChain.class)
        );
    }
}