package me.serenityline.api.finance.category.controller;

import jakarta.validation.Valid;
import me.serenityline.api.finance.category.dto.CategoryCreateRequest;
import me.serenityline.api.finance.category.dto.CategoryResponse;
import me.serenityline.api.finance.category.dto.CategoryUpdateRequest;
import me.serenityline.api.finance.category.service.CategoryService;
import me.serenityline.api.security.auth.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = Objects.requireNonNull(categoryService, "categoryService");
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> listCategories(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        List<CategoryResponse> response = categoryService.listCategories(
                authenticatedUser.userId()
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody CategoryCreateRequest request
    ) {
        CategoryResponse response = categoryService.createCategory(
                authenticatedUser.userId(),
                request
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PutMapping("/{categoryId}")
    public ResponseEntity<CategoryResponse> updateCategory(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID categoryId,
            @Valid @RequestBody CategoryUpdateRequest request
    ) {
        CategoryResponse response = categoryService.updateCategory(
                authenticatedUser.userId(),
                categoryId,
                request
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{categoryId}/deactivate")
    public ResponseEntity<CategoryResponse> deactivateCategory(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID categoryId
    ) {
        CategoryResponse response = categoryService.deactivateCategory(
                authenticatedUser.userId(),
                categoryId
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{categoryId}/reactivate")
    public ResponseEntity<CategoryResponse> reactivateCategory(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID categoryId
    ) {
        CategoryResponse response = categoryService.reactivateCategory(
                authenticatedUser.userId(),
                categoryId
        );

        return ResponseEntity.ok(response);
    }
}