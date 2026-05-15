package me.serenityline.api.auth.cleanup;

import me.serenityline.api.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "serenityline.auth.action-token-cleanup.enabled=true",
        "serenityline.auth.action-token-cleanup.fixed-delay-ms=3600000",
        "serenityline.auth.action-token-cleanup.retention=30d",
        "serenityline.auth.action-token-cleanup.batch-size=500"
})
class AuthActionTokenCleanupWorkerContextIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void cleanupWorkerShouldBeRegisteredWhenCleanupIsEnabled() {
        assertThat(applicationContext.getBeansOfType(AuthActionTokenCleanupWorker.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(AuthActionTokenCleanupSchedulingConfig.class)).hasSize(1);
    }
}