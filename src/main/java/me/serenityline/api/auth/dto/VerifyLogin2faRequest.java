package me.serenityline.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record VerifyLogin2faRequest(
        @NotNull(message = "{auth.login2fa.challengeId.required}")
        UUID challengeId,

        @NotBlank(message = "{auth.login2fa.code.required}")
        @Pattern(regexp = "\\d{6}", message = "{auth.login2fa.code.invalidFormat}")
        String code,

        @Size(max = 255, message = "{userSession.deviceLabel.tooLong}")
        String deviceLabel
) {
}