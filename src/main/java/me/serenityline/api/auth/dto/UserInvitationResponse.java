package me.serenityline.api.auth.dto;

import me.serenityline.api.user.entity.User;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public record UserInvitationResponse(
        UUID userId,
        String userName,
        String email,
        UUID userGroupId,
        String userGroupName,
        String userRole,
        String preferredLocale,
        List<UUID> accountIds
) {
    public static UserInvitationResponse from(
            User user,
            Collection<UUID> accountIds
    ) {
        if (user == null) {
            throw new IllegalArgumentException("auth.userInvitation.user.required");
        }

        return new UserInvitationResponse(
                user.getUserId(),
                user.getUserName(),
                user.getEmail(),
                user.getUserGroup().getUserGroupId(),
                user.getUserGroup().getUserGroupName(),
                user.getUserRole().name(),
                user.getPreferredLocale(),
                accountIds == null ? List.of() : List.copyOf(accountIds)
        );
    }
}