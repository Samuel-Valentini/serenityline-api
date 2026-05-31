package me.serenityline.api.finance.category.dto;

import java.util.UUID;

public record CategoryResponse(
        UUID categoryId,
        String categoryName,
        String categoryDescription,
        boolean active
) {
}