package me.serenityline.api.email.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(
        prefix = "serenityline.email.outbox-worker",
        name = "enabled",
        havingValue = "true"
)
public class EmailOutboxSchedulingConfig {
}