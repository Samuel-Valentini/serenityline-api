package me.serenityline.api.auth.cleanup;

import me.serenityline.api.auth.repository.RefreshTokenRepository;
import me.serenityline.api.auth.repository.UserSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

@Service
public class AuthSessionCleanupProcessor {

    private static final Duration MIN_RETENTION = Duration.ofDays(3);
    private static final Duration MAX_RETENTION = Duration.ofDays(365);

    private static final int MIN_BATCH_SIZE = 1;
    private static final int MAX_BATCH_SIZE = 100_000;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserSessionRepository userSessionRepository;
    private final Duration retention;
    private final int batchSize;

    public AuthSessionCleanupProcessor(
            RefreshTokenRepository refreshTokenRepository,
            UserSessionRepository userSessionRepository,
            @Value("${serenityline.auth.session-cleanup.retention:90d}") Duration retention,
            @Value("${serenityline.auth.session-cleanup.batch-size:500}") int batchSize
    ) {
        this.refreshTokenRepository = Objects.requireNonNull(
                refreshTokenRepository,
                "refreshTokenRepository"
        );
        this.userSessionRepository = Objects.requireNonNull(
                userSessionRepository,
                "userSessionRepository"
        );
        this.retention = Objects.requireNonNull(retention, "retention");
        this.batchSize = batchSize;

        validateConfiguration();
    }

    @Transactional
    public AuthSessionCleanupResult cleanup() {
        validateConfiguration();

        OffsetDateTime cutoff = OffsetDateTime.now().minus(retention);

        int refreshTokensDeleted = refreshTokenRepository.deleteCleanupCandidates(
                cutoff,
                batchSize
        );

        int userSessionsDeleted = userSessionRepository.deleteCleanupCandidates(
                cutoff,
                batchSize
        );

        return new AuthSessionCleanupResult(
                refreshTokensDeleted,
                userSessionsDeleted
        );
    }

    private void validateConfiguration() {
        if (
                retention.compareTo(MIN_RETENTION) < 0
                        || retention.compareTo(MAX_RETENTION) > 0
        ) {
            throw new IllegalStateException("authSession.cleanup.retention.invalid");
        }

        if (batchSize < MIN_BATCH_SIZE || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalStateException("authSession.cleanup.batchSize.invalid");
        }
    }
}