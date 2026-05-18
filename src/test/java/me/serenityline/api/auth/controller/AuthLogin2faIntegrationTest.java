package me.serenityline.api.auth.controller;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import me.serenityline.api.auth.entity.AuthActionToken;
import me.serenityline.api.auth.entity.AuthActionTokenType;
import me.serenityline.api.auth.entity.UserSession;
import me.serenityline.api.auth.repository.AuthActionTokenRepository;
import me.serenityline.api.auth.repository.RefreshTokenRepository;
import me.serenityline.api.auth.repository.UserSessionRepository;
import me.serenityline.api.email.outbox.entity.EmailOutbox;
import me.serenityline.api.email.outbox.entity.EmailOutboxStatus;
import me.serenityline.api.email.outbox.entity.EmailOutboxType;
import me.serenityline.api.email.outbox.repository.EmailOutboxRepository;
import me.serenityline.api.security.crypto.EmailOutboxEncryptionService;
import me.serenityline.api.security.crypto.EncryptedValue;
import me.serenityline.api.security.token.TokenHashingService;
import me.serenityline.api.support.IntegrationTestSupport;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthLogin2faIntegrationTest extends IntegrationTestSupport {

    private static final String DEFAULT_EMAIL = "samuel@example.com";
    private static final String DEFAULT_PASSWORD = "VeryStrongPassword-2026-SerenityLine!";
    private static final String WRONG_PASSWORD = "WrongPassword-2026!";
    private static final String DEFAULT_USER_NAME = "Samuel";
    private static final String DEFAULT_LOCALE = "en-US";
    private static final String DEFAULT_USER_AGENT = "JUnit Browser";
    private static final String LOGIN_DEVICE_LABEL = "Initial login device";
    private static final String VERIFY_DEVICE_LABEL = "2FA verified device";
    private static final String REFRESH_COOKIE_NAME = "serenityline_refresh";

    private static final Pattern SIX_DIGIT_CODE_PATTERN = Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthActionTokenRepository authActionTokenRepository;

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

    @Autowired
    private EmailOutboxEncryptionService emailOutboxEncryptionService;

    @Autowired
    private TokenHashingService tokenHashingService;

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static String jsonPathString(
            MvcResult result,
            String path
    ) throws Exception {
        return JsonPath.read(
                result.getResponse().getContentAsString(),
                path
        );
    }

    @Test
    void loginWithoutEmail2faShouldRemainAuthenticatedAsBefore() throws Exception {
        registerAndVerifyDefaultUser();

        MvcResult result = performLoginWithDevice(
                DEFAULT_EMAIL,
                DEFAULT_PASSWORD,
                LOGIN_DEVICE_LABEL
        )
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(REFRESH_COOKIE_NAME + "=")))
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.accessTokenExpiresAt").exists())
                .andExpect(jsonPath("$.user.email").value(DEFAULT_EMAIL))
                .andExpect(jsonPath("$.login2faChallengeId").doesNotExist())
                .andReturn();

        assertRefreshCookieIssued(result);

        assertThat(login2faTokens()).isEmpty();
        assertThat(login2faEmails()).isEmpty();

        assertThat(userSessionRepository.findAll()).hasSize(1);
        assertThat(refreshTokenRepository.findAll()).hasSize(1);
    }

    @Test
    void loginWithEmail2faEnabledShouldReturnChallengeWithoutCreatingSessionOrRefreshToken() throws Exception {
        registerAndVerifyDefaultUser();
        enableEmail2fa();

        MvcResult result = performLoginWithDevice(
                DEFAULT_EMAIL,
                DEFAULT_PASSWORD,
                LOGIN_DEVICE_LABEL
        )
                .andExpect(status().isAccepted())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.login2faChallengeId").exists())
                .andExpect(jsonPath("$.login2faCodeExpiresAt").exists())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.accessTokenExpiresAt").doesNotExist())
                .andExpect(jsonPath("$.user").doesNotExist())
                .andReturn();

        UUID challengeId = login2faChallengeId(result);

        assertThat(userSessionRepository.findAll()).isEmpty();
        assertThat(refreshTokenRepository.findAll()).isEmpty();

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        assertThat(token.getAuthActionTokenType()).isEqualTo(AuthActionTokenType.LOGIN_2FA_CODE);
        assertThat(token.getAuthActionUsedAt()).isNull();
        assertThat(token.getAuthActionRevokedAt()).isNull();
        assertThat(token.getAuthActionExpiresAt()).isAfter(OffsetDateTime.now());
        assertThat(token.getAuthActionFailedAttemptCount()).isZero();
        assertThat(token.getAuthActionLastFailedAt()).isNull();
        assertThat(token.getAuthActionMaxAttempts()).isBetween(1, 20);

        EmailOutbox emailOutbox = latestLogin2faEmail();

        assertThat(emailOutbox.getEmailType()).isEqualTo(EmailOutboxType.LOGIN_2FA_CODE);
        assertThat(emailOutbox.getEmailStatus()).isEqualTo(EmailOutboxStatus.PENDING);
        assertThat(emailOutbox.getRecipientEmail()).isEqualTo(DEFAULT_EMAIL);
        assertThat(emailOutbox.getAttempts()).isZero();
        assertThat(emailOutbox.isDeleteBodyAfterSend()).isTrue();

        assertThat(emailOutbox.getSubjectEncrypted()).isNotEmpty();
        assertThat(emailOutbox.getSubjectIv()).hasSize(12);
        assertThat(emailOutbox.getSubjectTag()).hasSize(16);

        assertThat(emailOutbox.getBodyTextEncrypted()).isNotEmpty();
        assertThat(emailOutbox.getBodyTextIv()).hasSize(12);
        assertThat(emailOutbox.getBodyTextTag()).hasSize(16);

        String code = extractCodeFromEmail(emailOutbox);

        assertThat(code).matches("\\d{6}");
        assertThat(token.getAuthActionTokenHash())
                .isEqualTo(tokenHashingService.hash(challengeId + ":" + code));
        assertThat(token.getAuthActionTokenHash()).doesNotContain(code);
    }

    @Test
    void loginWithWrongPasswordShouldNotCreateLogin2faChallengeEvenWhenEmail2faIsEnabled() throws Exception {
        registerAndVerifyDefaultUser();
        enableEmail2fa();

        performLogin(DEFAULT_EMAIL, WRONG_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.code").value("auth.login.invalidCredentials"));

        assertThat(login2faTokens()).isEmpty();
        assertThat(login2faEmails()).isEmpty();
        assertThat(userSessionRepository.findAll()).isEmpty();
        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    @Test
    void secondLoginWithEmail2faEnabledShouldRevokePreviousPendingChallengeAndCancelPreviousEmail() throws Exception {
        registerAndVerifyDefaultUser();
        enableEmail2fa();

        UUID firstChallengeId = startLogin2faChallenge();
        UUID secondChallengeId = startLogin2faChallenge();

        assertThat(secondChallengeId).isNotEqualTo(firstChallengeId);

        AuthActionToken firstToken = authActionTokenRepository
                .findById(firstChallengeId)
                .orElseThrow();

        AuthActionToken secondToken = authActionTokenRepository
                .findById(secondChallengeId)
                .orElseThrow();

        assertThat(firstToken.getAuthActionRevokedAt()).isNotNull();
        assertThat(firstToken.getAuthActionUsedAt()).isNull();

        assertThat(secondToken.getAuthActionRevokedAt()).isNull();
        assertThat(secondToken.getAuthActionUsedAt()).isNull();

        List<EmailOutbox> emails = login2faEmails();

        assertThat(emails).hasSize(2);

        assertThat(emails)
                .filteredOn(email -> email.getEmailStatus() == EmailOutboxStatus.CANCELLED)
                .hasSize(1);

        assertThat(emails)
                .filteredOn(email -> email.getEmailStatus() == EmailOutboxStatus.PENDING)
                .hasSize(1);

        assertThat(userSessionRepository.findAll()).isEmpty();
        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    @Test
    void verifyLogin2faWithCorrectCodeShouldAuthenticateCreateSessionAndRefreshCookie() throws Exception {
        registerAndVerifyDefaultUser();
        enableEmail2fa();

        UUID challengeId = startLogin2faChallenge();
        String code = extractCodeFromLatestLogin2faEmail();

        MvcResult result = performVerifyLogin2fa(
                challengeId,
                code,
                VERIFY_DEVICE_LABEL
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.accessTokenExpiresAt").exists())
                .andExpect(jsonPath("$.user.email").value(DEFAULT_EMAIL))
                .andReturn();

        assertRefreshCookieIssued(result);

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        assertThat(token.getAuthActionUsedAt()).isNotNull();
        assertThat(token.getAuthActionRevokedAt()).isNull();

        assertThat(userSessionRepository.findAll()).hasSize(1);
        assertThat(refreshTokenRepository.findAll()).hasSize(1);
    }

    @Test
    void verifyLogin2faShouldUseDeviceLabelFromVerifyRequestNotFromInitialLogin() throws Exception {
        registerAndVerifyDefaultUser();
        enableEmail2fa();

        UUID challengeId = startLogin2faChallenge();
        String code = extractCodeFromLatestLogin2faEmail();

        performVerifyLogin2fa(
                challengeId,
                code,
                VERIFY_DEVICE_LABEL
        )
                .andExpect(status().isOk());

        assertThat(userSessionRepository.findAll()).hasSize(1);

        UserSession session = userSessionRepository.findAll().getFirst();

        assertThat(session.getUserAgent()).isEqualTo(DEFAULT_USER_AGENT);
        assertThat(session.getDeviceLabel()).isEqualTo(VERIFY_DEVICE_LABEL);
    }

    @Test
    void verifyLogin2faWithBlankDeviceLabelShouldCreateSessionWithNullDeviceLabel() throws Exception {
        registerAndVerifyDefaultUser();
        enableEmail2fa();

        UUID challengeId = startLogin2faChallenge();
        String code = extractCodeFromLatestLogin2faEmail();

        performVerifyLogin2fa(
                challengeId,
                code,
                "   "
        )
                .andExpect(status().isOk());

        assertThat(userSessionRepository.findAll()).hasSize(1);
        assertThat(userSessionRepository.findAll().getFirst().getDeviceLabel()).isNull();
    }

    @Test
    void verifyLogin2faWithWrongCodeShouldIncrementFailedAttemptCountWithoutCreatingSession() throws Exception {
        registerAndVerifyDefaultUser();
        enableEmail2fa();

        UUID challengeId = startLogin2faChallenge();
        String validCode = extractCodeFromLatestLogin2faEmail();
        String wrongCode = wrongCodeDifferentFrom(validCode);

        performVerifyLogin2fa(
                challengeId,
                wrongCode,
                VERIFY_DEVICE_LABEL
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.login2fa.invalidOrExpired"));

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        assertThat(token.getAuthActionFailedAttemptCount()).isEqualTo(1);
        assertThat(token.getAuthActionLastFailedAt()).isNotNull();
        assertThat(token.getAuthActionUsedAt()).isNull();

        if (token.getAuthActionMaxAttempts() > 1) {
            assertThat(token.getAuthActionRevokedAt()).isNull();
        }

        assertThat(userSessionRepository.findAll()).isEmpty();
        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    @Test
    void verifyLogin2faShouldRevokeChallengeWhenMaxFailedAttemptsIsReached() throws Exception {
        registerAndVerifyDefaultUser();
        enableEmail2fa();

        UUID challengeId = startLogin2faChallenge();
        String validCode = extractCodeFromLatestLogin2faEmail();
        String wrongCode = wrongCodeDifferentFrom(validCode);

        AuthActionToken initialToken = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        int maxAttempts = initialToken.getAuthActionMaxAttempts();

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            performVerifyLogin2fa(
                    challengeId,
                    wrongCode,
                    VERIFY_DEVICE_LABEL
            )
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("auth.login2fa.invalidOrExpired"));
        }

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        assertThat(token.getAuthActionFailedAttemptCount()).isEqualTo(maxAttempts);
        assertThat(token.hasReachedFailedAttemptLimit()).isTrue();
        assertThat(token.getAuthActionRevokedAt()).isNotNull();
        assertThat(token.getAuthActionUsedAt()).isNull();

        assertThat(userSessionRepository.findAll()).isEmpty();
        assertThat(refreshTokenRepository.findAll()).isEmpty();

        performVerifyLogin2fa(
                challengeId,
                validCode,
                VERIFY_DEVICE_LABEL
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.login2fa.invalidOrExpired"));

        assertThat(userSessionRepository.findAll()).isEmpty();
        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    @Test
    void verifyLogin2faShouldRejectAlreadyUsedChallenge() throws Exception {
        registerAndVerifyDefaultUser();
        enableEmail2fa();

        UUID challengeId = startLogin2faChallenge();
        String code = extractCodeFromLatestLogin2faEmail();

        performVerifyLogin2fa(
                challengeId,
                code,
                VERIFY_DEVICE_LABEL
        )
                .andExpect(status().isOk());

        performVerifyLogin2fa(
                challengeId,
                code,
                VERIFY_DEVICE_LABEL
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.login2fa.invalidOrExpired"));

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        assertThat(token.getAuthActionUsedAt()).isNotNull();
        assertThat(token.getAuthActionRevokedAt()).isNull();

        assertThat(userSessionRepository.findAll()).hasSize(1);
        assertThat(refreshTokenRepository.findAll()).hasSize(1);
    }

    @Test
    void verifyLogin2faShouldRejectExpiredChallenge() throws Exception {
        registerAndVerifyDefaultUser();
        enableEmail2fa();

        UUID challengeId = startLogin2faChallenge();
        String code = extractCodeFromLatestLogin2faEmail();

        OffsetDateTime now = OffsetDateTime.now();

        jdbcTemplate.update(
                """
                        update auth_action_tokens
                        set auth_action_created_at = ?,
                            auth_action_expires_at = ?
                        where auth_action_token_id = ?
                        """,
                now.minusMinutes(10),
                now.minusMinutes(1),
                challengeId
        );

        performVerifyLogin2fa(
                challengeId,
                code,
                VERIFY_DEVICE_LABEL
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.login2fa.invalidOrExpired"));

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        assertThat(token.getAuthActionUsedAt()).isNull();

        assertThat(userSessionRepository.findAll()).isEmpty();
        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    @Test
    void verifyLogin2faShouldRejectRevokedChallenge() throws Exception {
        registerAndVerifyDefaultUser();
        enableEmail2fa();

        UUID challengeId = startLogin2faChallenge();
        String code = extractCodeFromLatestLogin2faEmail();

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        token.revoke();
        authActionTokenRepository.saveAndFlush(token);

        performVerifyLogin2fa(
                challengeId,
                code,
                VERIFY_DEVICE_LABEL
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.login2fa.invalidOrExpired"));

        assertThat(userSessionRepository.findAll()).isEmpty();
        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    @Test
    void verifyLogin2faShouldRejectUnknownChallengeId() throws Exception {
        registerAndVerifyDefaultUser();
        enableEmail2fa();

        performVerifyLogin2fa(
                UUID.randomUUID(),
                "123456",
                VERIFY_DEVICE_LABEL
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.login2fa.invalidOrExpired"));

        assertThat(userSessionRepository.findAll()).isEmpty();
        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    @Test
    void verifyLogin2faShouldRejectChallengeWithWrongTokenType() throws Exception {
        registerAndVerifyDefaultUser();

        User user = defaultUser();

        AuthActionToken emailVerificationToken = new AuthActionToken(
                user,
                tokenHashingService.hash("not-a-login-2fa-token"),
                AuthActionTokenType.EMAIL_VERIFICATION,
                OffsetDateTime.now().plusMinutes(10)
        );

        authActionTokenRepository.saveAndFlush(emailVerificationToken);

        performVerifyLogin2fa(
                emailVerificationToken.getAuthActionTokenId(),
                "123456",
                VERIFY_DEVICE_LABEL
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.login2fa.invalidOrExpired"));

        assertThat(userSessionRepository.findAll()).isEmpty();
        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    @Test
    void verifyLogin2faShouldRejectWhenUserDisablesEmail2faAfterChallengeCreation() throws Exception {
        registerAndVerifyDefaultUser();
        enableEmail2fa();

        UUID challengeId = startLogin2faChallenge();
        String code = extractCodeFromLatestLogin2faEmail();

        User user = defaultUser();
        user.disableEmailTwoFactorEnabled();
        userRepository.saveAndFlush(user);

        performVerifyLogin2fa(
                challengeId,
                code,
                VERIFY_DEVICE_LABEL
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.login2fa.invalidOrExpired"));

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        assertThat(token.getAuthActionUsedAt()).isNull();
        assertThat(token.getAuthActionFailedAttemptCount()).isZero();

        assertThat(userSessionRepository.findAll()).isEmpty();
        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    @Test
    void verifyLogin2faShouldRejectWhenUserIsPendingDeletionAfterChallengeCreation() throws Exception {
        registerAndVerifyDefaultUser();
        enableEmail2fa();

        UUID challengeId = startLogin2faChallenge();
        String code = extractCodeFromLatestLogin2faEmail();

        User user = defaultUser();
        user.markAsSoftDeleted();
        userRepository.saveAndFlush(user);

        performVerifyLogin2fa(
                challengeId,
                code,
                VERIFY_DEVICE_LABEL
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.login2fa.invalidOrExpired"));

        assertThat(userSessionRepository.findAll()).isEmpty();
        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    @Test
    void verifyLogin2faShouldRejectWhenUserIsDisabledAfterChallengeCreation() throws Exception {
        registerAndVerifyDefaultUser();
        enableEmail2fa();

        UUID challengeId = startLogin2faChallenge();
        String code = extractCodeFromLatestLogin2faEmail();

        User user = defaultUser();
        user.setUserIsEnabled(false);
        userRepository.saveAndFlush(user);

        performVerifyLogin2fa(
                challengeId,
                code,
                VERIFY_DEVICE_LABEL
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.login2fa.invalidOrExpired"));

        assertThat(userSessionRepository.findAll()).isEmpty();
        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    @Test
    void verifyLogin2faShouldRejectInvalidCodeFormatBeforeTouchingChallengeCounter() throws Exception {
        registerAndVerifyDefaultUser();
        enableEmail2fa();

        UUID challengeId = startLogin2faChallenge();

        performVerifyLogin2fa(
                challengeId,
                "abc123",
                VERIFY_DEVICE_LABEL
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        assertThat(token.getAuthActionFailedAttemptCount()).isZero();
        assertThat(token.getAuthActionLastFailedAt()).isNull();
        assertThat(token.getAuthActionUsedAt()).isNull();
        assertThat(token.getAuthActionRevokedAt()).isNull();

        assertThat(userSessionRepository.findAll()).isEmpty();
        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    @Test
    void verifyLogin2faEndpointShouldBePublicAndReturnValidationErrorInsteadOfUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/login/2fa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));
    }

    @Test
    void loginForSoftDeletedUserShouldPreferRestoreChallengeOverLogin2faChallenge() throws Exception {
        registerAndVerifyDefaultUser();

        User user = defaultUser();
        user.enableEmailTwoFactorEnabled();
        user.markAsSoftDeleted();
        userRepository.saveAndFlush(user);

        performLogin(DEFAULT_EMAIL, DEFAULT_PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.restoreToken").exists())
                .andExpect(jsonPath("$.restoreTokenExpiresAt").exists())
                .andExpect(jsonPath("$.login2faChallengeId").doesNotExist());

        assertThat(login2faTokens()).isEmpty();
        assertThat(login2faEmails()).isEmpty();
        assertThat(userSessionRepository.findAll()).isEmpty();
        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    @Test
    void loginForUnverifiedUserShouldPreferEmailVerificationChallengeOverLogin2faChallenge() throws Exception {
        registerAndExtractVerificationToken();

        User user = defaultUser();
        user.enableEmailTwoFactorEnabled();
        userRepository.saveAndFlush(user);

        performLogin(DEFAULT_EMAIL, DEFAULT_PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.emailVerificationResendToken").exists())
                .andExpect(jsonPath("$.email").value(DEFAULT_EMAIL))
                .andExpect(jsonPath("$.login2faChallengeId").doesNotExist());

        assertThat(login2faTokens()).isEmpty();
        assertThat(login2faEmails()).isEmpty();
        assertThat(userSessionRepository.findAll()).isEmpty();
        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    @Test
    void login2faChallengeResponseShouldNotLeakCodeOrPassword() throws Exception {
        registerAndVerifyDefaultUser();
        enableEmail2fa();

        performLoginWithDevice(
                DEFAULT_EMAIL,
                DEFAULT_PASSWORD,
                LOGIN_DEVICE_LABEL
        )
                .andExpect(status().isAccepted())
                .andExpect(content().string(not(containsString(DEFAULT_PASSWORD))))
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString("userPasswordHash"))))
                .andExpect(content().string(not(containsString("code"))));
    }

    private UUID startLogin2faChallenge() throws Exception {
        MvcResult result = performLoginWithDevice(
                DEFAULT_EMAIL,
                DEFAULT_PASSWORD,
                LOGIN_DEVICE_LABEL
        )
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.login2faChallengeId").exists())
                .andExpect(jsonPath("$.login2faCodeExpiresAt").exists())
                .andReturn();

        return login2faChallengeId(result);
    }

    private UUID login2faChallengeId(MvcResult result) throws Exception {
        return UUID.fromString(jsonPathString(result, "$.login2faChallengeId"));
    }

    private void enableEmail2fa() {
        User user = defaultUser();

        user.enableEmailTwoFactorEnabled();

        userRepository.saveAndFlush(user);
    }

    private String decryptSubject(EmailOutbox emailOutbox) {
        return emailOutboxEncryptionService.decrypt(
                new EncryptedValue(
                        emailOutbox.getSubjectEncrypted(),
                        emailOutbox.getSubjectIv(),
                        emailOutbox.getSubjectTag()
                )
        );
    }

    private String decryptTextBody(EmailOutbox emailOutbox) {
        return emailOutboxEncryptionService.decrypt(
                new EncryptedValue(
                        emailOutbox.getBodyTextEncrypted(),
                        emailOutbox.getBodyTextIv(),
                        emailOutbox.getBodyTextTag()
                )
        );
    }

    private String extractTokenFromBody(String body) {
        String marker = "#token=";

        int tokenStart = body.indexOf(marker);

        assertThat(tokenStart).isGreaterThanOrEqualTo(0);

        tokenStart += marker.length();

        int tokenEnd = body.indexOf('\n', tokenStart);

        if (tokenEnd == -1) {
            tokenEnd = body.length();
        }

        return body.substring(tokenStart, tokenEnd).trim();
    }

    private String extractCodeFromLatestLogin2faEmail() {
        return extractCodeFromEmail(latestLogin2faEmail());
    }

    private String extractCodeFromEmail(EmailOutbox emailOutbox) {
        String subject = decryptSubject(emailOutbox);
        String body = decryptTextBody(emailOutbox);

        Matcher bodyMatcher = SIX_DIGIT_CODE_PATTERN.matcher(body);

        if (bodyMatcher.find()) {
            return bodyMatcher.group(1);
        }

        Matcher subjectMatcher = SIX_DIGIT_CODE_PATTERN.matcher(subject);

        if (subjectMatcher.find()) {
            return subjectMatcher.group(1);
        }

        throw new AssertionError("No 6-digit 2FA code found in email subject/body.");
    }

    private String wrongCodeDifferentFrom(String validCode) {
        return "000000".equals(validCode) ? "000001" : "000000";
    }

    private List<AuthActionToken> login2faTokens() {
        return authActionTokenRepository.findAll()
                .stream()
                .filter(token -> token.getAuthActionTokenType() == AuthActionTokenType.LOGIN_2FA_CODE)
                .toList();
    }

    private List<EmailOutbox> login2faEmails() {
        return emailOutboxRepository.findAll()
                .stream()
                .filter(emailOutbox -> emailOutbox.getEmailType() == EmailOutboxType.LOGIN_2FA_CODE)
                .toList();
    }

    private EmailOutbox latestLogin2faEmail() {
        return login2faEmails()
                .stream()
                .max(Comparator.comparing(EmailOutbox::getEmailCreatedAt))
                .orElseThrow();
    }

    private String registerAndExtractVerificationToken() throws Exception {
        performDefaultRegister()
                .andExpect(status().isCreated());

        EmailOutbox emailOutbox = emailOutboxRepository.findAll().getFirst();

        return extractTokenFromBody(decryptTextBody(emailOutbox));
    }

    private void verifyEmail(String token) throws Exception {
        performVerifyEmail(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true));
    }

    private void registerAndVerifyDefaultUser() throws Exception {
        String verificationToken = registerAndExtractVerificationToken();

        verifyEmail(verificationToken);
    }

    private User defaultUser() {
        return userRepository.findByEmailAndUserDeletedAtIsNull(DEFAULT_EMAIL)
                .orElseThrow();
    }

    private ResultActions performDefaultRegister() throws Exception {
        return performRegister(
                DEFAULT_LOCALE,
                DEFAULT_USER_NAME,
                DEFAULT_EMAIL,
                DEFAULT_PASSWORD,
                DEFAULT_LOCALE
        );
    }

    private ResultActions performRegister(
            String acceptLanguage,
            String userName,
            String email,
            String password,
            String preferredLocale
    ) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage)
                .content("""
                        {
                          "userName": "%s",
                          "email": "%s",
                          "password": "%s",
                          "preferredLocale": "%s"
                        }
                        """.formatted(
                        userName,
                        email,
                        password,
                        preferredLocale
                )));
    }

    private ResultActions performVerifyEmail(String token) throws Exception {
        return mockMvc.perform(post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .content("""
                        {
                          "token": "%s"
                        }
                        """.formatted(token)));
    }

    private ResultActions performLogin(
            String email,
            String password
    ) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .content("""
                        {
                          "email": "%s",
                          "password": "%s"
                        }
                        """.formatted(
                        email,
                        password
                )));
    }

    private ResultActions performLoginWithDevice(
            String email,
            String password,
            String deviceLabel
    ) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .header(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT)
                .content("""
                        {
                          "email": "%s",
                          "password": "%s",
                          "deviceLabel": "%s"
                        }
                        """.formatted(
                        email,
                        password,
                        deviceLabel
                )));
    }

    private ResultActions performVerifyLogin2fa(
            UUID challengeId,
            String code,
            String deviceLabel
    ) throws Exception {
        return mockMvc.perform(post("/api/auth/login/2fa/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .header(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT)
                .content("""
                        {
                          "challengeId": "%s",
                          "code": "%s",
                          "deviceLabel": "%s"
                        }
                        """.formatted(
                        challengeId,
                        code,
                        deviceLabel
                )));
    }

    private void assertRefreshCookieIssued(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertThat(setCookie).isNotBlank();
        assertThat(setCookie).contains(REFRESH_COOKIE_NAME + "=");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("Path=/api/auth");
        assertThat(setCookie).contains("SameSite=Lax");
    }

    private ResultActions performRefresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .cookie(new Cookie(REFRESH_COOKIE_NAME, refreshToken)));
    }
}