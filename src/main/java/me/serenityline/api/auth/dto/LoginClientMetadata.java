package me.serenityline.api.auth.dto;

public record LoginClientMetadata(
        String ipAddress,
        String userAgent,
        String deviceLabel
) {
}