package me.serenityline.api.auth.dto;

import me.serenityline.api.user.entity.User;

import java.util.UUID;

public record RegisterResponse(
        UUID userId,
        String userName,
        String email,
        UUID userGroupId,
        String userGroupName,
        String userRole,
        String preferredLocale,
        boolean wantsInvoice,
        boolean emailVerificationRequired
) {
    public static RegisterResponse from(User user) {
        if (user == null) {
            throw new IllegalArgumentException("auth.register.user.required");
        }

        return new RegisterResponse(
                user.getUserId(),
                user.getUserName(),
                user.getEmail(),
                user.getUserGroup().getUserGroupId(),
                user.getUserGroup().getUserGroupName(),
                user.getUserRole().name(),
                user.getPreferredLocale(),
                user.isWantsInvoice(),
                !user.isUserIsEnabled()
        );
    }
}