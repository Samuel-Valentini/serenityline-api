package me.serenityline.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserInvitationAcceptRequest(
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @NotBlank(message = "{auth.token.required}")
        @Size(max = 512, message = "{auth.token.tooLong}")
        String token,

        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @NotBlank(message = "{auth.password.required}")
        @Size(min = 10, max = 128, message = "{auth.password.invalidLength}")
        String password
) {
}