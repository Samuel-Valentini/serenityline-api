package me.serenityline.api.support.contact.dto;

public record SupportContactResponse(
        boolean accepted,
        String message
) {
    public static SupportContactResponse accepted(String message) {
        return new SupportContactResponse(true, message);
    }
}