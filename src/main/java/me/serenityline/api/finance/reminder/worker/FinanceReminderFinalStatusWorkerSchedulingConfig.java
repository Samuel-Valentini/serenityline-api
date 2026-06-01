package me.serenityline.api.finance.reminder.worker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(
        prefix = "serenityline.finance.reminder-final-status-worker",
        name = "enabled",
        havingValue = "true"
)
public class FinanceReminderFinalStatusWorkerSchedulingConfig {
}