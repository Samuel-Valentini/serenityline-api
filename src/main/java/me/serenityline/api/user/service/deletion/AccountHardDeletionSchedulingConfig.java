package me.serenityline.api.user.service.deletion;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(
        prefix = "serenityline.account-deletion.hard-deletion",
        name = "enabled",
        havingValue = "true"
)
public class AccountHardDeletionSchedulingConfig {
}