package me.serenityline.api.user.service.deletion;

import me.serenityline.api.user.entity.UserRole;

import java.util.Objects;
import java.util.UUID;

public record AccountHardDeletionCandidate(
        UUID userId,
        UUID userGroupId,
        UserRole userRole
) {

    public AccountHardDeletionCandidate {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(userGroupId, "userGroupId");
        Objects.requireNonNull(userRole, "userRole");
    }

    public boolean isOwner() {
        return userRole == UserRole.OWNER;
    }
}