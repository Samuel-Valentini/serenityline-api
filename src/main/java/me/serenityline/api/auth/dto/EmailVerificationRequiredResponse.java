package me.serenityline.api.auth.dto;

import me.serenityline.api.user.entity.User;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EmailVerificationRequiredResponse(
        UUID userId,
        String email,
        String emailVerificationResendToken,
        OffsetDateTime emailVerificationResendTokenExpiresAt,
        OffsetDateTime emailVerificationResendAvailableAt
) {
    public static EmailVerificationRequiredResponse of(
            User user,
            String emailVerificationResendToken,
            OffsetDateTime emailVerificationResendTokenExpiresAt,
            OffsetDateTime emailVerificationResendAvailableAt
    ) {
        if (user == null) {
            throw new IllegalArgumentException("auth.emailVerification.user.required");
        }

        return new EmailVerificationRequiredResponse(
                user.getUserId(),
                user.getEmail(),
                emailVerificationResendToken,
                emailVerificationResendTokenExpiresAt,
                emailVerificationResendAvailableAt
        );
    }
}