package me.serenityline.api.auth.cleanup;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "serenityline.auth.session-cleanup",
        name = "enabled",
        havingValue = "true"
)
public class AuthSessionCleanupWorker {

    private final AuthSessionCleanupProcessor authSessionCleanupProcessor;

    public AuthSessionCleanupWorker(
            AuthSessionCleanupProcessor authSessionCleanupProcessor
    ) {
        this.authSessionCleanupProcessor = authSessionCleanupProcessor;
    }

    @Scheduled(fixedDelayString = "${serenityline.auth.session-cleanup.fixed-delay-ms:3600000}")
    public void cleanupAuthSessions() {
        authSessionCleanupProcessor.cleanup();
    }
}