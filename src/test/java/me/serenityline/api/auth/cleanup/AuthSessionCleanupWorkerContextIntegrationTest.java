package me.serenityline.api.auth.cleanup;

import me.serenityline.api.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "serenityline.auth.session-cleanup.enabled=true",
        "serenityline.auth.session-cleanup.fixed-delay-ms=3600000",
        "serenityline.auth.session-cleanup.retention=90d",
        "serenityline.auth.session-cleanup.batch-size=500"
})
class AuthSessionCleanupWorkerContextIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void cleanupWorkerShouldBeRegisteredWhenCleanupIsEnabled() {
        assertThat(applicationContext.getBeansOfType(AuthSessionCleanupWorker.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(AuthSessionCleanupSchedulingConfig.class)).hasSize(1);
    }
}