package me.serenityline.api.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record ConfirmDisableEmail2faRequest(

        @NotNull(message = "{auth.email2fa.challengeId.required}")
        UUID challengeId,

        @NotBlank(message = "{auth.email2fa.code.required}")
        @Pattern(regexp = "\\d{6}", message = "{auth.email2fa.code.invalidFormat}")
        String code
) {
}