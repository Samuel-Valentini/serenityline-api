package me.serenityline.api.user.dto;

import me.serenityline.api.security.auth.AuthenticatedUser;

import java.util.UUID;

public record CurrentUserResponse(
        UUID userId,
        String userName,
        String email,
        UUID userGroupId,
        String userGroupName,
        String userRole,
        String userPlatformRole,
        String preferredLocale,
        String preferredTheme,
        boolean wantsInvoice,
        boolean emailTwoFactorEnabled,
        boolean paymentEmailRemindersEnabled
) {

    public static CurrentUserResponse from(AuthenticatedUser user) {
        if (user == null) {
            throw new IllegalArgumentException("auth.authentication.user.required");
        }

        return new CurrentUserResponse(
                user.userId(),
                user.userName(),
                user.email(),
                user.userGroupId(),
                user.userGroupName(),
                user.userRole(),
                user.userPlatformRole(),
                user.preferredLocale(),
                user.preferredTheme(),
                user.wantsInvoice(),
                user.emailTwoFactorEnabled(),
                user.paymentEmailRemindersEnabled()
        );
    }
}