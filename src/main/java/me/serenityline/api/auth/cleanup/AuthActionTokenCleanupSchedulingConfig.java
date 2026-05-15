package me.serenityline.api.auth.cleanup;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(
        prefix = "serenityline.auth.action-token-cleanup",
        name = "enabled",
        havingValue = "true"
)
public class AuthActionTokenCleanupSchedulingConfig {
}
