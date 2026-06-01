package me.serenityline.api.user.service.deletion;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "serenityline.account-deletion.hard-deletion",
        name = "enabled",
        havingValue = "true"
)
public class AccountHardDeletionWorker {

    private final AccountHardDeletionProcessor accountHardDeletionProcessor;

    public AccountHardDeletionWorker(
            AccountHardDeletionProcessor accountHardDeletionProcessor
    ) {
        this.accountHardDeletionProcessor = accountHardDeletionProcessor;
    }

    @Scheduled(fixedDelayString = "${serenityline.account-deletion.hard-deletion.fixed-delay-ms:3600000}")
    public void processDueHardDeletions() {
        accountHardDeletionProcessor.processDueHardDeletions();
    }
}