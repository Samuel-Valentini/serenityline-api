package me.serenityline.api.finance.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryUpdateRequest(

        @NotBlank(message = "finance.category.nameRequired")
        @Size(max = 255, message = "finance.category.nameTooLong")
        String categoryName,

        @Size(max = 500, message = "finance.category.descriptionTooLong")
        String categoryDescription
) {
}