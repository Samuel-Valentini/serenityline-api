package me.serenityline.api.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestEmailChangeRequest(

        @NotBlank(message = "{auth.emailChange.newEmail.required}")
        @Email(message = "{user.email.invalid}")
        @Size(max = 320, message = "{user.email.tooLong}")
        String newEmail,

        @NotBlank(message = "{auth.emailChange.currentPassword.required}")
        @Size(max = 128, message = "{auth.password.tooLong}")
        String currentPassword
) {
}