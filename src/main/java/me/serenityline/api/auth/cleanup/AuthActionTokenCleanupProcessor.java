package me.serenityline.api.auth.cleanup;

import me.serenityline.api.auth.repository.AuthActionTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

@Service
public class AuthActionTokenCleanupProcessor {

    private static final Duration MIN_RETENTION = Duration.ofDays(3);
    private static final Duration MAX_RETENTION = Duration.ofDays(365);

    private static final int MIN_BATCH_SIZE = 1;
    private static final int MAX_BATCH_SIZE = 100_000;

    private final AuthActionTokenRepository authActionTokenRepository;
    private final Duration retention;
    private final int batchSize;

    public AuthActionTokenCleanupProcessor(
            AuthActionTokenRepository authActionTokenRepository,
            @Value("${serenityline.auth.action-token-cleanup.retention:30d}") Duration retention,
            @Value("${serenityline.auth.action-token-cleanup.batch-size:500}") int batchSize
    ) {
        this.authActionTokenRepository = Objects.requireNonNull(
                authActionTokenRepository,
                "authActionTokenRepository"
        );
        this.retention = Objects.requireNonNull(retention, "retention");
        this.batchSize = batchSize;

        validateConfiguration();
    }

    @Transactional
    public int cleanup() {
        validateConfiguration();

        OffsetDateTime cutoff = OffsetDateTime.now().minus(retention);

        return authActionTokenRepository.deleteCleanupCandidates(
                cutoff,
                batchSize
        );
    }

    private void validateConfiguration() {
        if (retention.compareTo(MIN_RETENTION) < 0 || retention.compareTo(MAX_RETENTION) > 0) {
            throw new IllegalStateException("authActionToken.cleanup.retention.invalid");
        }

        if (batchSize < MIN_BATCH_SIZE || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalStateException("authActionToken.cleanup.batchSize.invalid");
        }
    }
}