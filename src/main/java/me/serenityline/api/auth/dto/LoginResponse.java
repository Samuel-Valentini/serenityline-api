package me.serenityline.api.auth.dto;

import me.serenityline.api.user.entity.User;

import java.util.UUID;

public record LoginResponse(
        UUID userId,
        String userName,
        String email,
        UUID userGroupId,
        String userGroupName,
        String userRole,
        String userPlatformRole,
        String preferredLocale,
        String preferredTheme,
        boolean wantsInvoice
) {
    public static LoginResponse from(User user) {
        if (user == null) {
            throw new IllegalArgumentException("auth.login.user.required");
        }

        return new LoginResponse(
                user.getUserId(),
                user.getUserName(),
                user.getEmail(),
                user.getUserGroup().getUserGroupId(),
                user.getUserGroup().getUserGroupName(),
                user.getUserRole().name(),
                user.getUserPlatformRole().name(),
                user.getPreferredLocale(),
                user.getPreferredTheme().name(),
                user.isWantsInvoice()
        );
    }
}