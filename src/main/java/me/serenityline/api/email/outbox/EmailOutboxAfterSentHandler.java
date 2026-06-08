package me.serenityline.api.email.outbox;

import me.serenityline.api.email.outbox.entity.EmailOutbox;

public interface EmailOutboxAfterSentHandler {
    void afterEmailSent(EmailOutbox emailOutbox);
}