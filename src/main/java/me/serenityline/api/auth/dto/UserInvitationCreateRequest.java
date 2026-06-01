package me.serenityline.api.auth.dto;

import jakarta.validation.constraints.*;
import me.serenityline.api.user.entity.UserRole;

import java.util.Set;
import java.util.UUID;

public record UserInvitationCreateRequest(
        @NotBlank(message = "{user.userName.required}")
        @Size(max = 100, message = "{user.userName.tooLong}")
        String userName,

        @NotBlank(message = "{user.email.required}")
        @Email(message = "{user.email.invalid}")
        @Size(max = 320, message = "{user.email.tooLong}")
        String email,

        @NotNull(message = "{auth.userInvitation.role.required}")
        UserRole userRole,

        @Pattern(
                regexp = "it-IT|en-US",
                message = "{user.preferredLocale.invalid}"
        )
        String preferredLocale,

        Boolean paymentEmailRemindersEnabled,

        Set<UUID> accountIds
) {
}