package me.serenityline.api.auth.cleanup;

import me.serenityline.api.auth.entity.AuthActionToken;
import me.serenityline.api.auth.entity.AuthActionTokenType;
import me.serenityline.api.auth.repository.AuthActionTokenRepository;
import me.serenityline.api.support.IntegrationTestSupport;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.entity.UserGroup;
import me.serenityline.api.user.entity.UserRole;
import me.serenityline.api.user.repository.UserGroupRepository;
import me.serenityline.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "serenityline.auth.action-token-cleanup.enabled=false",
        "serenityline.auth.action-token-cleanup.retention=30d",
        "serenityline.auth.action-token-cleanup.batch-size=500"
})
class AuthActionTokenCleanupProcessorIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private AuthActionTokenCleanupProcessor authActionTokenCleanupProcessor;

    @Autowired
    private AuthActionTokenRepository authActionTokenRepository;

    @Autowired
    private UserGroupRepository userGroupRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void cleanupShouldDeleteOnlyExpiredUsedOrRevokedTokensOlderThanRetention() {
        User user = createUser();

        AuthActionToken oldExpiredToken = createToken(user, "old-expired");
        AuthActionToken oldUsedToken = createToken(user, "old-used");
        AuthActionToken oldRevokedToken = createToken(user, "old-revoked");

        AuthActionToken recentExpiredToken = createToken(user, "recent-expired");
        AuthActionToken recentUsedToken = createToken(user, "recent-used");
        AuthActionToken recentRevokedToken = createToken(user, "recent-revoked");

        AuthActionToken activeToken = createToken(user, "active");

        makeExpiredDaysAgo(oldExpiredToken, 31);
        makeUsedDaysAgo(oldUsedToken, 31);
        makeRevokedDaysAgo(oldRevokedToken, 31);

        makeExpiredDaysAgo(recentExpiredToken, 1);
        makeUsedDaysAgo(recentUsedToken, 1);
        makeRevokedDaysAgo(recentRevokedToken, 1);

        int deletedCount = authActionTokenCleanupProcessor.cleanup();

        assertThat(deletedCount).isEqualTo(3);

        assertThat(authActionTokenRepository.findById(oldExpiredToken.getAuthActionTokenId())).isEmpty();
        assertThat(authActionTokenRepository.findById(oldUsedToken.getAuthActionTokenId())).isEmpty();
        assertThat(authActionTokenRepository.findById(oldRevokedToken.getAuthActionTokenId())).isEmpty();

        assertThat(authActionTokenRepository.findById(recentExpiredToken.getAuthActionTokenId())).isPresent();
        assertThat(authActionTokenRepository.findById(recentUsedToken.getAuthActionTokenId())).isPresent();
        assertThat(authActionTokenRepository.findById(recentRevokedToken.getAuthActionTokenId())).isPresent();
        assertThat(authActionTokenRepository.findById(activeToken.getAuthActionTokenId())).isPresent();
    }

    @Test
    void cleanupShouldDeleteExpiredUsedAndRevokedTokensOlderThanRetention() {
        User user = createUser();

        AuthActionToken oldExpiredToken = createToken(user, "old-expired");
        AuthActionToken oldUsedToken = createToken(user, "old-used");
        AuthActionToken oldRevokedToken = createToken(user, "old-revoked");

        makeExpiredDaysAgo(oldExpiredToken, 31);
        makeUsedDaysAgo(oldUsedToken, 31);
        makeRevokedDaysAgo(oldRevokedToken, 31);

        int deletedCount = authActionTokenCleanupProcessor.cleanup();

        assertThat(deletedCount).isEqualTo(3);

        assertThat(authActionTokenRepository.findById(oldExpiredToken.getAuthActionTokenId())).isEmpty();
        assertThat(authActionTokenRepository.findById(oldUsedToken.getAuthActionTokenId())).isEmpty();
        assertThat(authActionTokenRepository.findById(oldRevokedToken.getAuthActionTokenId())).isEmpty();
    }

    @Test
    void cleanupShouldKeepExpiredUsedAndRevokedTokensInsideRetentionWindow() {
        User user = createUser();

        AuthActionToken recentExpiredToken = createToken(user, "recent-expired");
        AuthActionToken recentUsedToken = createToken(user, "recent-used");
        AuthActionToken recentRevokedToken = createToken(user, "recent-revoked");

        makeExpiredDaysAgo(recentExpiredToken, 1);
        makeUsedDaysAgo(recentUsedToken, 1);
        makeRevokedDaysAgo(recentRevokedToken, 1);

        int deletedCount = authActionTokenCleanupProcessor.cleanup();

        assertThat(deletedCount).isZero();

        assertThat(authActionTokenRepository.findById(recentExpiredToken.getAuthActionTokenId())).isPresent();
        assertThat(authActionTokenRepository.findById(recentUsedToken.getAuthActionTokenId())).isPresent();
        assertThat(authActionTokenRepository.findById(recentRevokedToken.getAuthActionTokenId())).isPresent();
    }

    @Test
    void cleanupShouldKeepPendingTokenEvenWhenCreatedLongAgo() {
        User user = createUser();

        AuthActionToken oldButStillPendingToken = createToken(user, "old-pending");

        makeCreatedDaysAgoButStillPending(oldButStillPendingToken, 60);

        int deletedCount = authActionTokenCleanupProcessor.cleanup();

        assertThat(deletedCount).isZero();

        AuthActionToken token = authActionTokenRepository
                .findById(oldButStillPendingToken.getAuthActionTokenId())
                .orElseThrow();

        assertThat(token.getAuthActionUsedAt()).isNull();
        assertThat(token.getAuthActionRevokedAt()).isNull();
        assertThat(token.getAuthActionExpiresAt()).isAfter(OffsetDateTime.now());
    }

    @Test
    void cleanupShouldDeleteMixedEligibleTokensAndKeepMixedNonEligibleTokens() {
        User user = createUser();

        AuthActionToken oldExpiredToken = createToken(user, "mixed-old-expired");
        AuthActionToken oldUsedToken = createToken(user, "mixed-old-used");
        AuthActionToken oldRevokedToken = createToken(user, "mixed-old-revoked");

        AuthActionToken recentExpiredToken = createToken(user, "mixed-recent-expired");
        AuthActionToken recentUsedToken = createToken(user, "mixed-recent-used");
        AuthActionToken recentRevokedToken = createToken(user, "mixed-recent-revoked");
        AuthActionToken activeToken = createToken(user, "mixed-active");

        makeExpiredDaysAgo(oldExpiredToken, 31);
        makeUsedDaysAgo(oldUsedToken, 31);
        makeRevokedDaysAgo(oldRevokedToken, 31);

        makeExpiredDaysAgo(recentExpiredToken, 1);
        makeUsedDaysAgo(recentUsedToken, 1);
        makeRevokedDaysAgo(recentRevokedToken, 1);

        int deletedCount = authActionTokenCleanupProcessor.cleanup();

        assertThat(deletedCount).isEqualTo(3);

        assertThat(authActionTokenRepository.findById(oldExpiredToken.getAuthActionTokenId())).isEmpty();
        assertThat(authActionTokenRepository.findById(oldUsedToken.getAuthActionTokenId())).isEmpty();
        assertThat(authActionTokenRepository.findById(oldRevokedToken.getAuthActionTokenId())).isEmpty();

        assertThat(authActionTokenRepository.findById(recentExpiredToken.getAuthActionTokenId())).isPresent();
        assertThat(authActionTokenRepository.findById(recentUsedToken.getAuthActionTokenId())).isPresent();
        assertThat(authActionTokenRepository.findById(recentRevokedToken.getAuthActionTokenId())).isPresent();
        assertThat(authActionTokenRepository.findById(activeToken.getAuthActionTokenId())).isPresent();
    }

    @Test
    void cleanupShouldReturnZeroWhenThereAreNoEligibleTokens() {
        User user = createUser();

        AuthActionToken activeToken = createToken(user, "no-eligible-active");
        AuthActionToken recentExpiredToken = createToken(user, "no-eligible-recent-expired");

        makeExpiredDaysAgo(recentExpiredToken, 1);

        int deletedCount = authActionTokenCleanupProcessor.cleanup();

        assertThat(deletedCount).isZero();

        assertThat(authActionTokenRepository.findById(activeToken.getAuthActionTokenId())).isPresent();
        assertThat(authActionTokenRepository.findById(recentExpiredToken.getAuthActionTokenId())).isPresent();
    }

    @Test
    @Transactional
    void repositoryDeleteCleanupCandidatesShouldRespectBatchLimit() {
        User user = createUser();

        AuthActionToken token1 = createToken(user, "batch-1");
        AuthActionToken token2 = createToken(user, "batch-2");
        AuthActionToken token3 = createToken(user, "batch-3");
        AuthActionToken token4 = createToken(user, "batch-4");
        AuthActionToken token5 = createToken(user, "batch-5");

        makeExpiredDaysAgo(token1, 31);
        makeExpiredDaysAgo(token2, 31);
        makeExpiredDaysAgo(token3, 31);
        makeExpiredDaysAgo(token4, 31);
        makeExpiredDaysAgo(token5, 31);

        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(30);

        int firstDeletedCount = authActionTokenRepository.deleteCleanupCandidates(cutoff, 2);
        int secondDeletedCount = authActionTokenRepository.deleteCleanupCandidates(cutoff, 2);
        int thirdDeletedCount = authActionTokenRepository.deleteCleanupCandidates(cutoff, 2);
        int fourthDeletedCount = authActionTokenRepository.deleteCleanupCandidates(cutoff, 2);

        assertThat(firstDeletedCount).isEqualTo(2);
        assertThat(secondDeletedCount).isEqualTo(2);
        assertThat(thirdDeletedCount).isEqualTo(1);
        assertThat(fourthDeletedCount).isZero();

        assertThat(authActionTokenRepository.findById(token1.getAuthActionTokenId())).isEmpty();
        assertThat(authActionTokenRepository.findById(token2.getAuthActionTokenId())).isEmpty();
        assertThat(authActionTokenRepository.findById(token3.getAuthActionTokenId())).isEmpty();
        assertThat(authActionTokenRepository.findById(token4.getAuthActionTokenId())).isEmpty();
        assertThat(authActionTokenRepository.findById(token5.getAuthActionTokenId())).isEmpty();
    }

    @Test
    @Transactional
    void repositoryDeleteCleanupCandidatesShouldUseStrictCutoffComparison() {
        User user = createUser();

        OffsetDateTime cutoff = OffsetDateTime.now()
                .minusDays(30)
                .withNano(0);

        AuthActionToken beforeCutoffToken = createToken(user, "before-cutoff");
        AuthActionToken exactlyAtCutoffToken = createToken(user, "exactly-at-cutoff");
        AuthActionToken afterCutoffToken = createToken(user, "after-cutoff");

        makeExpiredAt(beforeCutoffToken, cutoff.minusSeconds(1));
        makeExpiredAt(exactlyAtCutoffToken, cutoff);
        makeExpiredAt(afterCutoffToken, cutoff.plusSeconds(1));

        int deletedCount = authActionTokenRepository.deleteCleanupCandidates(cutoff, 500);

        assertThat(deletedCount).isEqualTo(1);

        assertThat(authActionTokenRepository.findById(beforeCutoffToken.getAuthActionTokenId())).isEmpty();
        assertThat(authActionTokenRepository.findById(exactlyAtCutoffToken.getAuthActionTokenId())).isPresent();
        assertThat(authActionTokenRepository.findById(afterCutoffToken.getAuthActionTokenId())).isPresent();
    }

    @Test
    void cleanupWorkerShouldNotBeRegisteredWhenCleanupIsDisabled() {
        assertThat(applicationContext.getBeansOfType(AuthActionTokenCleanupWorker.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(AuthActionTokenCleanupSchedulingConfig.class)).isEmpty();
    }

    @Test
    void processorConstructorShouldAcceptValidBoundaryConfiguration() {
        assertThatCode(() -> new AuthActionTokenCleanupProcessor(
                authActionTokenRepository,
                Duration.ofDays(3),
                1
        )).doesNotThrowAnyException();

        assertThatCode(() -> new AuthActionTokenCleanupProcessor(
                authActionTokenRepository,
                Duration.ofDays(365),
                100_000
        )).doesNotThrowAnyException();
    }

    @Test
    void processorConstructorShouldRejectTooShortRetention() {
        assertThatThrownBy(() -> new AuthActionTokenCleanupProcessor(
                authActionTokenRepository,
                Duration.ofDays(2),
                500
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("authActionToken.cleanup.retention.invalid");
    }

    @Test
    void processorConstructorShouldRejectTooLongRetention() {
        assertThatThrownBy(() -> new AuthActionTokenCleanupProcessor(
                authActionTokenRepository,
                Duration.ofDays(366),
                500
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("authActionToken.cleanup.retention.invalid");
    }

    @Test
    void processorConstructorShouldRejectZeroRetention() {
        assertThatThrownBy(() -> new AuthActionTokenCleanupProcessor(
                authActionTokenRepository,
                Duration.ZERO,
                500
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("authActionToken.cleanup.retention.invalid");
    }

    @Test
    void processorConstructorShouldRejectNegativeRetention() {
        assertThatThrownBy(() -> new AuthActionTokenCleanupProcessor(
                authActionTokenRepository,
                Duration.ofDays(-1),
                500
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("authActionToken.cleanup.retention.invalid");
    }

    @Test
    void processorConstructorShouldRejectBatchSizeBelowMinimum() {
        assertThatThrownBy(() -> new AuthActionTokenCleanupProcessor(
                authActionTokenRepository,
                Duration.ofDays(30),
                0
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("authActionToken.cleanup.batchSize.invalid");
    }

    @Test
    void processorConstructorShouldRejectBatchSizeAboveMaximum() {
        assertThatThrownBy(() -> new AuthActionTokenCleanupProcessor(
                authActionTokenRepository,
                Duration.ofDays(30),
                100_001
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("authActionToken.cleanup.batchSize.invalid");
    }

    private User createUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "");

        UserGroup userGroup = new UserGroup("Cleanup test group " + suffix);

        userGroupRepository.saveAndFlush(userGroup);

        User user = new User(
                "Cleanup Test User " + suffix,
                "cleanup.%s@example.com".formatted(suffix),
                userGroup,
                UserRole.OWNER,
                "{bcrypt}not-a-real-test-hash"
        );

        user.setUserIsEnabled(true);

        return userRepository.saveAndFlush(user);
    }

    private AuthActionToken createToken(
            User user,
            String hashSuffix
    ) {
        AuthActionToken token = new AuthActionToken(
                user,
                "cleanup-token-hash-" + UUID.randomUUID() + "-" + hashSuffix,
                AuthActionTokenType.PASSWORD_RESET,
                OffsetDateTime.now().plusHours(1)
        );

        return authActionTokenRepository.saveAndFlush(token);
    }

    private void makeExpiredDaysAgo(
            AuthActionToken token,
            int daysAgo
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime createdAt = now.minusDays(daysAgo + 1L);
        OffsetDateTime expiresAt = now.minusDays(daysAgo);

        jdbcTemplate.update(
                """
                        update auth_action_tokens
                        set auth_action_created_at = ?,
                            auth_action_expires_at = ?,
                            auth_action_used_at = null,
                            auth_action_revoked_at = null,
                            auth_action_failed_attempt_count = 0,
                            auth_action_last_failed_at = null
                        where auth_action_token_id = ?
                        """,
                createdAt,
                expiresAt,
                token.getAuthActionTokenId()
        );
    }

    private void makeUsedDaysAgo(
            AuthActionToken token,
            int daysAgo
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime createdAt = now.minusDays(daysAgo + 1L);
        OffsetDateTime usedAt = now.minusDays(daysAgo);
        OffsetDateTime expiresAt = now.plusHours(1);

        jdbcTemplate.update(
                """
                        update auth_action_tokens
                        set auth_action_created_at = ?,
                            auth_action_expires_at = ?,
                            auth_action_used_at = ?,
                            auth_action_revoked_at = null,
                            auth_action_failed_attempt_count = 0,
                            auth_action_last_failed_at = null
                        where auth_action_token_id = ?
                        """,
                createdAt,
                expiresAt,
                usedAt,
                token.getAuthActionTokenId()
        );
    }

    private void makeRevokedDaysAgo(
            AuthActionToken token,
            int daysAgo
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime createdAt = now.minusDays(daysAgo + 1L);
        OffsetDateTime revokedAt = now.minusDays(daysAgo);
        OffsetDateTime expiresAt = now.plusHours(1);

        jdbcTemplate.update(
                """
                        update auth_action_tokens
                        set auth_action_created_at = ?,
                            auth_action_expires_at = ?,
                            auth_action_used_at = null,
                            auth_action_revoked_at = ?,
                            auth_action_failed_attempt_count = 0,
                            auth_action_last_failed_at = null
                        where auth_action_token_id = ?
                        """,
                createdAt,
                expiresAt,
                revokedAt,
                token.getAuthActionTokenId()
        );
    }

    private void makeCreatedDaysAgoButStillPending(
            AuthActionToken token,
            int daysAgo
    ) {
        OffsetDateTime createdAt = OffsetDateTime.now().minusDays(daysAgo);
        OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(1);

        jdbcTemplate.update(
                """
                        update auth_action_tokens
                        set auth_action_created_at = ?,
                            auth_action_expires_at = ?,
                            auth_action_used_at = null,
                            auth_action_revoked_at = null,
                            auth_action_failed_attempt_count = 0,
                            auth_action_last_failed_at = null
                        where auth_action_token_id = ?
                        """,
                createdAt,
                expiresAt,
                token.getAuthActionTokenId()
        );
    }

    private void makeExpiredAt(
            AuthActionToken token,
            OffsetDateTime expiresAt
    ) {
        OffsetDateTime createdAt = expiresAt.minusDays(1);

        jdbcTemplate.update(
                """
                        update auth_action_tokens
                        set auth_action_created_at = ?,
                            auth_action_expires_at = ?,
                            auth_action_used_at = null,
                            auth_action_revoked_at = null,
                            auth_action_failed_attempt_count = 0,
                            auth_action_last_failed_at = null
                        where auth_action_token_id = ?
                        """,
                createdAt,
                expiresAt,
                token.getAuthActionTokenId()
        );
    }
}

