package me.serenityline.api.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestDisableEmail2faRequest(

        @NotBlank(message = "{auth.password.current.required}")
        @Size(max = 128, message = "{auth.password.invalidLength}")
        String currentPassword
) {
}