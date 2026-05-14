package me.serenityline.api.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @NotBlank(message = "{auth.password.current.required}")
        @Size(max = 128, message = "{auth.password.invalidLength}")
        String currentPassword,

        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @NotBlank(message = "{auth.password.new.required}")
        @Size(min = 10, max = 128, message = "{auth.password.invalidLength}")
        String newPassword
) {
}