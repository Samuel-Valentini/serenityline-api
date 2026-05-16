package me.serenityline.api.user.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmEmailChangeRequest(

        @NotBlank(message = "{auth.emailChange.token.required}")
        String token
) {
}