package me.serenityline.api.auth.cleanup;

import me.serenityline.api.auth.entity.RefreshToken;
import me.serenityline.api.auth.entity.RefreshTokenRevokeReason;
import me.serenityline.api.auth.entity.SessionRevokeReason;
import me.serenityline.api.auth.entity.UserSession;
import me.serenityline.api.auth.repository.RefreshTokenRepository;
import me.serenityline.api.auth.repository.UserSessionRepository;
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
        "serenityline.auth.session-cleanup.enabled=false",
        "serenityline.auth.session-cleanup.retention=90d",
        "serenityline.auth.session-cleanup.batch-size=500"
})
class AuthSessionCleanupProcessorIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private AuthSessionCleanupProcessor authSessionCleanupProcessor;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Autowired
    private UserGroupRepository userGroupRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void cleanupShouldDeleteExpiredUsedRevokedAndReuseDetectedRefreshTokensOlderThanRetention() {
        User user = createUser();
        UserSession session = createSession(user, "refresh-token-cleanup-session");

        RefreshToken oldExpiredToken = createRefreshToken(user, session, "old-expired");
        RefreshToken oldUsedToken = createRefreshToken(user, session, "old-used");
        RefreshToken oldRevokedToken = createRefreshToken(user, session, "old-revoked");
        RefreshToken oldReuseDetectedToken = createRefreshToken(user, session, "old-reuse-detected");

        makeRefreshTokenExpiredDaysAgo(oldExpiredToken, 91);
        makeRefreshTokenUsedDaysAgo(oldUsedToken, 91);
        makeRefreshTokenRevokedDaysAgo(oldRevokedToken, 91);
        makeRefreshTokenReuseDetectedDaysAgo(oldReuseDetectedToken, 91);

        AuthSessionCleanupResult result = authSessionCleanupProcessor.cleanup();

        assertThat(result.refreshTokensDeleted()).isEqualTo(4);
        assertThat(result.userSessionsDeleted()).isZero();
        assertThat(result.totalDeleted()).isEqualTo(4);

        assertThat(refreshTokenRepository.findById(oldExpiredToken.getRefreshTokenId())).isEmpty();
        assertThat(refreshTokenRepository.findById(oldUsedToken.getRefreshTokenId())).isEmpty();
        assertThat(refreshTokenRepository.findById(oldRevokedToken.getRefreshTokenId())).isEmpty();
        assertThat(refreshTokenRepository.findById(oldReuseDetectedToken.getRefreshTokenId())).isEmpty();

        assertThat(userSessionRepository.findById(session.getUserSessionId())).isPresent();
    }

    @Test
    void cleanupShouldKeepExpiredUsedRevokedAndReuseDetectedRefreshTokensInsideRetentionWindow() {
        User user = createUser();
        UserSession session = createSession(user, "recent-refresh-token-session");

        RefreshToken recentExpiredToken = createRefreshToken(user, session, "recent-expired");
        RefreshToken recentUsedToken = createRefreshToken(user, session, "recent-used");
        RefreshToken recentRevokedToken = createRefreshToken(user, session, "recent-revoked");
        RefreshToken recentReuseDetectedToken = createRefreshToken(user, session, "recent-reuse-detected");

        makeRefreshTokenExpiredDaysAgo(recentExpiredToken, 1);
        makeRefreshTokenUsedDaysAgo(recentUsedToken, 1);
        makeRefreshTokenRevokedDaysAgo(recentRevokedToken, 1);
        makeRefreshTokenReuseDetectedDaysAgo(recentReuseDetectedToken, 1);

        AuthSessionCleanupResult result = authSessionCleanupProcessor.cleanup();

        assertThat(result.refreshTokensDeleted()).isZero();
        assertThat(result.userSessionsDeleted()).isZero();
        assertThat(result.totalDeleted()).isZero();

        assertThat(refreshTokenRepository.findById(recentExpiredToken.getRefreshTokenId())).isPresent();
        assertThat(refreshTokenRepository.findById(recentUsedToken.getRefreshTokenId())).isPresent();
        assertThat(refreshTokenRepository.findById(recentRevokedToken.getRefreshTokenId())).isPresent();
        assertThat(refreshTokenRepository.findById(recentReuseDetectedToken.getRefreshTokenId())).isPresent();
    }

    @Test
    void cleanupShouldDeleteExpiredAndRevokedUserSessionsOlderThanRetention() {
        User user = createUser();

        UserSession oldExpiredSession = createSession(user, "old-expired-session");
        UserSession oldRevokedSession = createSession(user, "old-revoked-session");

        makeSessionExpiredDaysAgo(oldExpiredSession, 91);
        makeSessionRevokedDaysAgo(oldRevokedSession, 91);

        AuthSessionCleanupResult result = authSessionCleanupProcessor.cleanup();

        assertThat(result.refreshTokensDeleted()).isZero();
        assertThat(result.userSessionsDeleted()).isEqualTo(2);
        assertThat(result.totalDeleted()).isEqualTo(2);

        assertThat(userSessionRepository.findById(oldExpiredSession.getUserSessionId())).isEmpty();
        assertThat(userSessionRepository.findById(oldRevokedSession.getUserSessionId())).isEmpty();
    }

    @Test
    void cleanupShouldKeepExpiredAndRevokedUserSessionsInsideRetentionWindow() {
        User user = createUser();

        UserSession recentExpiredSession = createSession(user, "recent-expired-session");
        UserSession recentRevokedSession = createSession(user, "recent-revoked-session");

        makeSessionExpiredDaysAgo(recentExpiredSession, 1);
        makeSessionRevokedDaysAgo(recentRevokedSession, 1);

        AuthSessionCleanupResult result = authSessionCleanupProcessor.cleanup();

        assertThat(result.refreshTokensDeleted()).isZero();
        assertThat(result.userSessionsDeleted()).isZero();

        assertThat(userSessionRepository.findById(recentExpiredSession.getUserSessionId())).isPresent();
        assertThat(userSessionRepository.findById(recentRevokedSession.getUserSessionId())).isPresent();
    }

    @Test
    void cleanupShouldCascadeDeleteRefreshTokensWhenDeletingOldUserSession() {
        User user = createUser();

        UserSession oldExpiredSession = createSession(user, "cascade-expired-session");
        RefreshToken activeRefreshToken = createRefreshToken(user, oldExpiredSession, "active-child-token");

        makeSessionExpiredDaysAgo(oldExpiredSession, 91);

        AuthSessionCleanupResult result = authSessionCleanupProcessor.cleanup();

        assertThat(result.refreshTokensDeleted()).isZero();
        assertThat(result.userSessionsDeleted()).isEqualTo(1);
        assertThat(result.totalDeleted()).isEqualTo(1);

        assertThat(userSessionRepository.findById(oldExpiredSession.getUserSessionId())).isEmpty();
        assertThat(refreshTokenRepository.findById(activeRefreshToken.getRefreshTokenId())).isEmpty();
    }

    @Test
    void cleanupShouldKeepActiveSessionAndActiveRefreshTokenEvenWhenCreatedLongAgo() {
        User user = createUser();

        UserSession oldButActiveSession = createSession(user, "old-active-session");
        RefreshToken oldButActiveRefreshToken = createRefreshToken(user, oldButActiveSession, "old-active-refresh-token");

        makeSessionCreatedDaysAgoButStillActive(oldButActiveSession, 120);
        makeRefreshTokenCreatedDaysAgoButStillActive(oldButActiveRefreshToken, 120);

        AuthSessionCleanupResult result = authSessionCleanupProcessor.cleanup();

        assertThat(result.refreshTokensDeleted()).isZero();
        assertThat(result.userSessionsDeleted()).isZero();
        assertThat(result.totalDeleted()).isZero();

        assertThat(userSessionRepository.findById(oldButActiveSession.getUserSessionId())).isPresent();
        assertThat(refreshTokenRepository.findById(oldButActiveRefreshToken.getRefreshTokenId())).isPresent();
    }

    @Test
    void cleanupShouldDeleteMixedEligibleRecordsAndKeepMixedNonEligibleRecords() {
        User user = createUser();

        UserSession session = createSession(user, "mixed-session");

        RefreshToken oldExpiredRefreshToken = createRefreshToken(user, session, "mixed-old-expired-refresh");
        RefreshToken oldUsedRefreshToken = createRefreshToken(user, session, "mixed-old-used-refresh");
        RefreshToken recentExpiredRefreshToken = createRefreshToken(user, session, "mixed-recent-expired-refresh");
        RefreshToken activeRefreshToken = createRefreshToken(user, session, "mixed-active-refresh");

        UserSession oldExpiredSession = createSession(user, "mixed-old-expired-session");
        UserSession recentExpiredSession = createSession(user, "mixed-recent-expired-session");
        UserSession activeSession = createSession(user, "mixed-active-session");

        makeRefreshTokenExpiredDaysAgo(oldExpiredRefreshToken, 91);
        makeRefreshTokenUsedDaysAgo(oldUsedRefreshToken, 91);
        makeRefreshTokenExpiredDaysAgo(recentExpiredRefreshToken, 1);

        makeSessionExpiredDaysAgo(oldExpiredSession, 91);
        makeSessionExpiredDaysAgo(recentExpiredSession, 1);

        AuthSessionCleanupResult result = authSessionCleanupProcessor.cleanup();

        assertThat(result.refreshTokensDeleted()).isEqualTo(2);
        assertThat(result.userSessionsDeleted()).isEqualTo(1);
        assertThat(result.totalDeleted()).isEqualTo(3);

        assertThat(refreshTokenRepository.findById(oldExpiredRefreshToken.getRefreshTokenId())).isEmpty();
        assertThat(refreshTokenRepository.findById(oldUsedRefreshToken.getRefreshTokenId())).isEmpty();
        assertThat(userSessionRepository.findById(oldExpiredSession.getUserSessionId())).isEmpty();

        assertThat(refreshTokenRepository.findById(recentExpiredRefreshToken.getRefreshTokenId())).isPresent();
        assertThat(refreshTokenRepository.findById(activeRefreshToken.getRefreshTokenId())).isPresent();
        assertThat(userSessionRepository.findById(recentExpiredSession.getUserSessionId())).isPresent();
        assertThat(userSessionRepository.findById(activeSession.getUserSessionId())).isPresent();
    }

    @Test
    void cleanupShouldReturnZeroWhenThereAreNoEligibleRecords() {
        User user = createUser();

        UserSession activeSession = createSession(user, "no-eligible-active-session");
        RefreshToken activeRefreshToken = createRefreshToken(user, activeSession, "no-eligible-active-refresh");

        UserSession recentExpiredSession = createSession(user, "no-eligible-recent-expired-session");
        RefreshToken recentExpiredRefreshToken = createRefreshToken(user, activeSession, "no-eligible-recent-expired-refresh");

        makeSessionExpiredDaysAgo(recentExpiredSession, 1);
        makeRefreshTokenExpiredDaysAgo(recentExpiredRefreshToken, 1);

        AuthSessionCleanupResult result = authSessionCleanupProcessor.cleanup();

        assertThat(result.refreshTokensDeleted()).isZero();
        assertThat(result.userSessionsDeleted()).isZero();
        assertThat(result.totalDeleted()).isZero();

        assertThat(userSessionRepository.findById(activeSession.getUserSessionId())).isPresent();
        assertThat(refreshTokenRepository.findById(activeRefreshToken.getRefreshTokenId())).isPresent();
        assertThat(userSessionRepository.findById(recentExpiredSession.getUserSessionId())).isPresent();
        assertThat(refreshTokenRepository.findById(recentExpiredRefreshToken.getRefreshTokenId())).isPresent();
    }

    @Test
    @Transactional
    void refreshTokenRepositoryDeleteCleanupCandidatesShouldRespectBatchLimit() {
        User user = createUser();
        UserSession session = createSession(user, "refresh-batch-session");

        RefreshToken token1 = createRefreshToken(user, session, "refresh-batch-1");
        RefreshToken token2 = createRefreshToken(user, session, "refresh-batch-2");
        RefreshToken token3 = createRefreshToken(user, session, "refresh-batch-3");
        RefreshToken token4 = createRefreshToken(user, session, "refresh-batch-4");
        RefreshToken token5 = createRefreshToken(user, session, "refresh-batch-5");

        makeRefreshTokenExpiredDaysAgo(token1, 91);
        makeRefreshTokenExpiredDaysAgo(token2, 91);
        makeRefreshTokenExpiredDaysAgo(token3, 91);
        makeRefreshTokenExpiredDaysAgo(token4, 91);
        makeRefreshTokenExpiredDaysAgo(token5, 91);

        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(90);

        int firstDeletedCount = refreshTokenRepository.deleteCleanupCandidates(cutoff, 2);
        int secondDeletedCount = refreshTokenRepository.deleteCleanupCandidates(cutoff, 2);
        int thirdDeletedCount = refreshTokenRepository.deleteCleanupCandidates(cutoff, 2);
        int fourthDeletedCount = refreshTokenRepository.deleteCleanupCandidates(cutoff, 2);

        assertThat(firstDeletedCount).isEqualTo(2);
        assertThat(secondDeletedCount).isEqualTo(2);
        assertThat(thirdDeletedCount).isEqualTo(1);
        assertThat(fourthDeletedCount).isZero();

        assertThat(refreshTokenRepository.findById(token1.getRefreshTokenId())).isEmpty();
        assertThat(refreshTokenRepository.findById(token2.getRefreshTokenId())).isEmpty();
        assertThat(refreshTokenRepository.findById(token3.getRefreshTokenId())).isEmpty();
        assertThat(refreshTokenRepository.findById(token4.getRefreshTokenId())).isEmpty();
        assertThat(refreshTokenRepository.findById(token5.getRefreshTokenId())).isEmpty();
    }

    @Test
    @Transactional
    void userSessionRepositoryDeleteCleanupCandidatesShouldRespectBatchLimit() {
        User user = createUser();

        UserSession session1 = createSession(user, "session-batch-1");
        UserSession session2 = createSession(user, "session-batch-2");
        UserSession session3 = createSession(user, "session-batch-3");
        UserSession session4 = createSession(user, "session-batch-4");
        UserSession session5 = createSession(user, "session-batch-5");

        makeSessionExpiredDaysAgo(session1, 91);
        makeSessionExpiredDaysAgo(session2, 91);
        makeSessionExpiredDaysAgo(session3, 91);
        makeSessionExpiredDaysAgo(session4, 91);
        makeSessionExpiredDaysAgo(session5, 91);

        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(90);

        int firstDeletedCount = userSessionRepository.deleteCleanupCandidates(cutoff, 2);
        int secondDeletedCount = userSessionRepository.deleteCleanupCandidates(cutoff, 2);
        int thirdDeletedCount = userSessionRepository.deleteCleanupCandidates(cutoff, 2);
        int fourthDeletedCount = userSessionRepository.deleteCleanupCandidates(cutoff, 2);

        assertThat(firstDeletedCount).isEqualTo(2);
        assertThat(secondDeletedCount).isEqualTo(2);
        assertThat(thirdDeletedCount).isEqualTo(1);
        assertThat(fourthDeletedCount).isZero();

        assertThat(userSessionRepository.findById(session1.getUserSessionId())).isEmpty();
        assertThat(userSessionRepository.findById(session2.getUserSessionId())).isEmpty();
        assertThat(userSessionRepository.findById(session3.getUserSessionId())).isEmpty();
        assertThat(userSessionRepository.findById(session4.getUserSessionId())).isEmpty();
        assertThat(userSessionRepository.findById(session5.getUserSessionId())).isEmpty();
    }

    @Test
    @Transactional
    void refreshTokenRepositoryDeleteCleanupCandidatesShouldUseStrictCutoffComparison() {
        User user = createUser();
        UserSession session = createSession(user, "strict-refresh-cutoff-session");

        OffsetDateTime cutoff = OffsetDateTime.now()
                .minusDays(90)
                .withNano(0);

        RefreshToken beforeCutoffToken = createRefreshToken(user, session, "refresh-before-cutoff");
        RefreshToken exactlyAtCutoffToken = createRefreshToken(user, session, "refresh-exactly-at-cutoff");
        RefreshToken afterCutoffToken = createRefreshToken(user, session, "refresh-after-cutoff");

        makeRefreshTokenExpiredAt(beforeCutoffToken, cutoff.minusSeconds(1));
        makeRefreshTokenExpiredAt(exactlyAtCutoffToken, cutoff);
        makeRefreshTokenExpiredAt(afterCutoffToken, cutoff.plusSeconds(1));

        int deletedCount = refreshTokenRepository.deleteCleanupCandidates(cutoff, 500);

        assertThat(deletedCount).isEqualTo(1);

        assertThat(refreshTokenRepository.findById(beforeCutoffToken.getRefreshTokenId())).isEmpty();
        assertThat(refreshTokenRepository.findById(exactlyAtCutoffToken.getRefreshTokenId())).isPresent();
        assertThat(refreshTokenRepository.findById(afterCutoffToken.getRefreshTokenId())).isPresent();
    }

    @Test
    @Transactional
    void userSessionRepositoryDeleteCleanupCandidatesShouldUseStrictCutoffComparison() {
        User user = createUser();

        OffsetDateTime cutoff = OffsetDateTime.now()
                .minusDays(90)
                .withNano(0);

        UserSession beforeCutoffSession = createSession(user, "session-before-cutoff");
        UserSession exactlyAtCutoffSession = createSession(user, "session-exactly-at-cutoff");
        UserSession afterCutoffSession = createSession(user, "session-after-cutoff");

        makeSessionExpiredAt(beforeCutoffSession, cutoff.minusSeconds(1));
        makeSessionExpiredAt(exactlyAtCutoffSession, cutoff);
        makeSessionExpiredAt(afterCutoffSession, cutoff.plusSeconds(1));

        int deletedCount = userSessionRepository.deleteCleanupCandidates(cutoff, 500);

        assertThat(deletedCount).isEqualTo(1);

        assertThat(userSessionRepository.findById(beforeCutoffSession.getUserSessionId())).isEmpty();
        assertThat(userSessionRepository.findById(exactlyAtCutoffSession.getUserSessionId())).isPresent();
        assertThat(userSessionRepository.findById(afterCutoffSession.getUserSessionId())).isPresent();
    }

    @Test
    void cleanupWorkerShouldNotBeRegisteredWhenCleanupIsDisabled() {
        assertThat(applicationContext.getBeansOfType(AuthSessionCleanupWorker.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(AuthSessionCleanupSchedulingConfig.class)).isEmpty();
    }

    @Test
    void processorConstructorShouldAcceptValidBoundaryConfiguration() {
        assertThatCode(() -> new AuthSessionCleanupProcessor(
                refreshTokenRepository,
                userSessionRepository,
                Duration.ofDays(3),
                1
        )).doesNotThrowAnyException();

        assertThatCode(() -> new AuthSessionCleanupProcessor(
                refreshTokenRepository,
                userSessionRepository,
                Duration.ofDays(365),
                100_000
        )).doesNotThrowAnyException();
    }

    @Test
    void processorConstructorShouldRejectTooShortRetention() {
        assertThatThrownBy(() -> new AuthSessionCleanupProcessor(
                refreshTokenRepository,
                userSessionRepository,
                Duration.ofDays(2),
                500
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("authSession.cleanup.retention.invalid");
    }

    @Test
    void processorConstructorShouldRejectTooLongRetention() {
        assertThatThrownBy(() -> new AuthSessionCleanupProcessor(
                refreshTokenRepository,
                userSessionRepository,
                Duration.ofDays(366),
                500
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("authSession.cleanup.retention.invalid");
    }

    @Test
    void processorConstructorShouldRejectZeroRetention() {
        assertThatThrownBy(() -> new AuthSessionCleanupProcessor(
                refreshTokenRepository,
                userSessionRepository,
                Duration.ZERO,
                500
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("authSession.cleanup.retention.invalid");
    }

    @Test
    void processorConstructorShouldRejectNegativeRetention() {
        assertThatThrownBy(() -> new AuthSessionCleanupProcessor(
                refreshTokenRepository,
                userSessionRepository,
                Duration.ofDays(-1),
                500
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("authSession.cleanup.retention.invalid");
    }

    @Test
    void processorConstructorShouldRejectBatchSizeBelowMinimum() {
        assertThatThrownBy(() -> new AuthSessionCleanupProcessor(
                refreshTokenRepository,
                userSessionRepository,
                Duration.ofDays(90),
                0
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("authSession.cleanup.batchSize.invalid");
    }

    @Test
    void processorConstructorShouldRejectBatchSizeAboveMaximum() {
        assertThatThrownBy(() -> new AuthSessionCleanupProcessor(
                refreshTokenRepository,
                userSessionRepository,
                Duration.ofDays(90),
                100_001
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("authSession.cleanup.batchSize.invalid");
    }

    private User createUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "");

        UserGroup userGroup = new UserGroup("Session cleanup test group " + suffix);

        userGroupRepository.saveAndFlush(userGroup);

        User user = new User(
                "Session Cleanup Test User " + suffix,
                "session.cleanup.%s@example.com".formatted(suffix),
                userGroup,
                UserRole.OWNER,
                "{bcrypt}not-a-real-test-hash"
        );

        user.setUserIsEnabled(true);

        return userRepository.saveAndFlush(user);
    }

    private UserSession createSession(
            User user,
            String labelSuffix
    ) {
        UserSession session = new UserSession(
                user,
                OffsetDateTime.now().plusDays(30),
                "session-cleanup-ip-hash-" + UUID.randomUUID(),
                "JUnit session cleanup browser",
                "Session cleanup device " + labelSuffix
        );

        return userSessionRepository.saveAndFlush(session);
    }

    private RefreshToken createRefreshToken(
            User user,
            UserSession session,
            String hashSuffix
    ) {
        RefreshToken refreshToken = new RefreshToken(
                user,
                session,
                "refresh-cleanup-token-hash-" + UUID.randomUUID() + "-" + hashSuffix,
                OffsetDateTime.now().plusDays(30),
                null
        );

        return refreshTokenRepository.saveAndFlush(refreshToken);
    }

    private void makeRefreshTokenExpiredDaysAgo(
            RefreshToken token,
            int daysAgo
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime createdAt = now.minusDays(daysAgo + 1L);
        OffsetDateTime expiresAt = now.minusDays(daysAgo);

        jdbcTemplate.update(
                """
                        update refresh_tokens
                        set refresh_token_created_at = ?,
                            refresh_token_expires_at = ?,
                            refresh_token_used_at = null,
                            refresh_token_revoked_at = null,
                            refresh_token_revoke_reason = null,
                            refresh_token_reuse_detected_at = null
                        where refresh_token_id = ?
                        """,
                createdAt,
                expiresAt,
                token.getRefreshTokenId()
        );
    }

    private void makeRefreshTokenUsedDaysAgo(
            RefreshToken token,
            int daysAgo
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime createdAt = now.minusDays(daysAgo + 1L);
        OffsetDateTime usedAt = now.minusDays(daysAgo);
        OffsetDateTime expiresAt = now.plusDays(30);

        jdbcTemplate.update(
                """
                        update refresh_tokens
                        set refresh_token_created_at = ?,
                            refresh_token_expires_at = ?,
                            refresh_token_used_at = ?,
                            refresh_token_revoked_at = null,
                            refresh_token_revoke_reason = null,
                            refresh_token_reuse_detected_at = null
                        where refresh_token_id = ?
                        """,
                createdAt,
                expiresAt,
                usedAt,
                token.getRefreshTokenId()
        );
    }

    private void makeRefreshTokenRevokedDaysAgo(
            RefreshToken token,
            int daysAgo
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime createdAt = now.minusDays(daysAgo + 1L);
        OffsetDateTime revokedAt = now.minusDays(daysAgo);
        OffsetDateTime expiresAt = now.plusDays(30);

        jdbcTemplate.update(
                """
                        update refresh_tokens
                        set refresh_token_created_at = ?,
                            refresh_token_expires_at = ?,
                            refresh_token_used_at = null,
                            refresh_token_revoked_at = ?,
                            refresh_token_revoke_reason = ?,
                            refresh_token_reuse_detected_at = null
                        where refresh_token_id = ?
                        """,
                createdAt,
                expiresAt,
                revokedAt,
                RefreshTokenRevokeReason.USER_LOGOUT.name(),
                token.getRefreshTokenId()
        );
    }

    private void makeRefreshTokenReuseDetectedDaysAgo(
            RefreshToken token,
            int daysAgo
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime createdAt = now.minusDays(daysAgo + 1L);
        OffsetDateTime detectedAt = now.minusDays(daysAgo);
        OffsetDateTime expiresAt = now.plusDays(30);

        jdbcTemplate.update(
                """
                        update refresh_tokens
                        set refresh_token_created_at = ?,
                            refresh_token_expires_at = ?,
                            refresh_token_used_at = null,
                            refresh_token_revoked_at = ?,
                            refresh_token_revoke_reason = ?,
                            refresh_token_reuse_detected_at = ?
                        where refresh_token_id = ?
                        """,
                createdAt,
                expiresAt,
                detectedAt,
                RefreshTokenRevokeReason.REUSE_DETECTED.name(),
                detectedAt,
                token.getRefreshTokenId()
        );
    }

    private void makeRefreshTokenCreatedDaysAgoButStillActive(
            RefreshToken token,
            int daysAgo
    ) {
        OffsetDateTime createdAt = OffsetDateTime.now().minusDays(daysAgo);
        OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(30);

        jdbcTemplate.update(
                """
                        update refresh_tokens
                        set refresh_token_created_at = ?,
                            refresh_token_expires_at = ?,
                            refresh_token_used_at = null,
                            refresh_token_revoked_at = null,
                            refresh_token_revoke_reason = null,
                            refresh_token_reuse_detected_at = null
                        where refresh_token_id = ?
                        """,
                createdAt,
                expiresAt,
                token.getRefreshTokenId()
        );
    }

    private void makeRefreshTokenExpiredAt(
            RefreshToken token,
            OffsetDateTime expiresAt
    ) {
        OffsetDateTime createdAt = expiresAt.minusDays(1);

        jdbcTemplate.update(
                """
                        update refresh_tokens
                        set refresh_token_created_at = ?,
                            refresh_token_expires_at = ?,
                            refresh_token_used_at = null,
                            refresh_token_revoked_at = null,
                            refresh_token_revoke_reason = null,
                            refresh_token_reuse_detected_at = null
                        where refresh_token_id = ?
                        """,
                createdAt,
                expiresAt,
                token.getRefreshTokenId()
        );
    }

    private void makeSessionExpiredDaysAgo(
            UserSession session,
            int daysAgo
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime createdAt = now.minusDays(daysAgo + 1L);
        OffsetDateTime expiresAt = now.minusDays(daysAgo);

        jdbcTemplate.update(
                """
                        update user_sessions
                        set session_created_at = ?,
                            session_last_seen_at = ?,
                            session_expires_at = ?,
                            session_revoked_at = null,
                            session_revoke_reason = null
                        where user_session_id = ?
                        """,
                createdAt,
                createdAt,
                expiresAt,
                session.getUserSessionId()
        );
    }

    private void makeSessionRevokedDaysAgo(
            UserSession session,
            int daysAgo
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime createdAt = now.minusDays(daysAgo + 1L);
        OffsetDateTime revokedAt = now.minusDays(daysAgo);
        OffsetDateTime expiresAt = now.plusDays(30);

        jdbcTemplate.update(
                """
                        update user_sessions
                        set session_created_at = ?,
                            session_last_seen_at = ?,
                            session_expires_at = ?,
                            session_revoked_at = ?,
                            session_revoke_reason = ?
                        where user_session_id = ?
                        """,
                createdAt,
                createdAt,
                expiresAt,
                revokedAt,
                SessionRevokeReason.USER_LOGOUT.name(),
                session.getUserSessionId()
        );
    }

    private void makeSessionCreatedDaysAgoButStillActive(
            UserSession session,
            int daysAgo
    ) {
        OffsetDateTime createdAt = OffsetDateTime.now().minusDays(daysAgo);
        OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(30);

        jdbcTemplate.update(
                """
                        update user_sessions
                        set session_created_at = ?,
                            session_last_seen_at = ?,
                            session_expires_at = ?,
                            session_revoked_at = null,
                            session_revoke_reason = null
                        where user_session_id = ?
                        """,
                createdAt,
                createdAt,
                expiresAt,
                session.getUserSessionId()
        );
    }

    private void makeSessionExpiredAt(
            UserSession session,
            OffsetDateTime expiresAt
    ) {
        OffsetDateTime createdAt = expiresAt.minusDays(1);

        jdbcTemplate.update(
                """
                        update user_sessions
                        set session_created_at = ?,
                            session_last_seen_at = ?,
                            session_expires_at = ?,
                            session_revoked_at = null,
                            session_revoke_reason = null
                        where user_session_id = ?
                        """,
                createdAt,
                createdAt,
                expiresAt,
                session.getUserSessionId()
        );
    }
}