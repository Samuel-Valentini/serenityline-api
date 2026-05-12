package me.serenityline.api.auth.dto;

import me.serenityline.api.user.entity.User;

import java.util.UUID;

public record RestoreAccountVerificationRequiredResponse(
        UUID userId,
        String email,
        String preferredLocale,
        boolean emailVerificationRequired
) {
    public static RestoreAccountVerificationRequiredResponse from(User user) {
        if (user == null) {
            throw new IllegalArgumentException("auth.restoreAccount.user.required");
        }

        return new RestoreAccountVerificationRequiredResponse(
                user.getUserId(),
                user.getEmail(),
                user.getPreferredLocale(),
                true
        );
    }
}