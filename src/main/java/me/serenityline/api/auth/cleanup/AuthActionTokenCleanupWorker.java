package me.serenityline.api.auth.cleanup;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "serenityline.auth.action-token-cleanup",
        name = "enabled",
        havingValue = "true"
)
public class AuthActionTokenCleanupWorker {

    private final AuthActionTokenCleanupProcessor authActionTokenCleanupProcessor;

    public AuthActionTokenCleanupWorker(
            AuthActionTokenCleanupProcessor authActionTokenCleanupProcessor
    ) {
        this.authActionTokenCleanupProcessor = authActionTokenCleanupProcessor;
    }

    @Scheduled(fixedDelayString = "${serenityline.auth.action-token-cleanup.fixed-delay-ms:3600000}")
    public void cleanupAuthActionTokens() {
        authActionTokenCleanupProcessor.cleanup();
    }
}