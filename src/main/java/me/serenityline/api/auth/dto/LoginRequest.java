package me.serenityline.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

        @NotBlank(message = "{user.email.required}")
        @Email(message = "{user.email.invalid}")
        @Size(max = 320, message = "{user.email.tooLong}")
        String email,

        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @NotBlank(message = "{auth.password.required}")
        @Size(max = 128, message = "{auth.password.invalidLength}")
        String password,

        @Size(max = 255, message = "{userSession.deviceLabel.tooLong}")
        String deviceLabel
) {
}