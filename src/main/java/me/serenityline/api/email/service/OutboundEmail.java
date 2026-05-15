package me.serenityline.api.email.service;

public record OutboundEmail(
        String to,
        String subject,
        String text,
        String html
) {

    public OutboundEmail {
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("email.outbound.to.required");
        }

        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("email.outbound.subject.required");
        }

        if ((text == null || text.isBlank()) && (html == null || html.isBlank())) {
            throw new IllegalArgumentException("email.outbound.body.required");
        }
    }
}