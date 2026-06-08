package me.serenityline.api.email.outbox;

import me.serenityline.api.email.outbox.entity.EmailOutboxType;

import java.util.UUID;

public record EmailOutboxSentEvent(
        UUID emailOutboxId,
        UUID userId,
        String recipientEmail,
        EmailOutboxType emailType
) {
}