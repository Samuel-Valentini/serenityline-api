package me.serenityline.api.auth.dto;

public record VerifyEmailResponse(boolean emailVerified) {
    public static VerifyEmailResponse verified() {
        return new VerifyEmailResponse(true);
    }
}
