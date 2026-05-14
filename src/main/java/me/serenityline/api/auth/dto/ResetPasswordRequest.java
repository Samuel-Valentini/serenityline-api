package me.serenityline.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @NotBlank(message = "{auth.token.required}")
        @Size(max = 512, message = "{auth.token.tooLong}")
        String resetToken,

        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @NotBlank(message = "{auth.password.new.required}")
        @Size(min = 10, max = 128, message = "{auth.password.invalidLength}")
        String newPassword
) {
}