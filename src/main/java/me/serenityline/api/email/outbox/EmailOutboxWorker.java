package me.serenityline.api.email.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "serenityline.email.outbox-worker",
        name = "enabled",
        havingValue = "true"
)
public class EmailOutboxWorker {

    private final EmailOutboxProcessor emailOutboxProcessor;

    public EmailOutboxWorker(EmailOutboxProcessor emailOutboxProcessor) {
        this.emailOutboxProcessor = emailOutboxProcessor;
    }

    @Scheduled(fixedDelayString = "${serenityline.email.outbox-worker.fixed-delay-ms:10000}")
    public void processPendingEmails() {
        emailOutboxProcessor.processDueEmails();
    }
}