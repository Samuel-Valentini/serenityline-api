package me.serenityline.api.user.controller;

import com.jayway.jsonpath.JsonPath;
import me.serenityline.api.auth.entity.*;
import me.serenityline.api.auth.repository.AuthActionTokenRepository;
import me.serenityline.api.auth.repository.EmailOutboxRepository;
import me.serenityline.api.auth.repository.RefreshTokenRepository;
import me.serenityline.api.auth.repository.UserSessionRepository;
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
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class Email2faManagementIntegrationTest extends IntegrationTestSupport {

    private static final String DEFAULT_EMAIL = "samuel@example.com";
    private static final String DEFAULT_PASSWORD = "VeryStrongPassword-2026-SerenityLine!";
    private static final String WRONG_PASSWORD = "WrongPassword-2026!";
    private static final String DEFAULT_USER_NAME = "Samuel";
    private static final String DEFAULT_LOCALE = "en-US";
    private static final String DEFAULT_USER_AGENT = "JUnit Browser";
    private static final String DEFAULT_DEVICE_LABEL = "Samuel test device";
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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

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
    void requestEnableEmail2faShouldRequireAccessToken() throws Exception {
        performRequestEnableEmail2faWithoutAccessToken(DEFAULT_PASSWORD)
                .andExpect(status().isUnauthorized());

        assertThat(email2faEnableTokens()).isEmpty();
        assertThat(email2faEnableEmails()).isEmpty();
    }

    @Test
    void requestEnableEmail2faShouldRejectWrongCurrentPasswordWithoutCreatingChallenge() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        performRequestEnableEmail2fa(accessToken, WRONG_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.password.current.invalid"));

        User user = defaultUser();

        assertThat(user.isEmailTwoFactorEnabled()).isFalse();
        assertThat(email2faEnableTokens()).isEmpty();
        assertThat(email2faEnableEmails()).isEmpty();
    }

    @Test
    void requestEnableEmail2faShouldCreateChallengeTokenAndOutboxEmail() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        MvcResult result = performRequestEnableEmail2fa(accessToken, DEFAULT_PASSWORD)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.challengeId").isString())
                .andExpect(jsonPath("$.codeExpiresAt").exists())
                .andReturn();

        UUID challengeId = challengeId(result);

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        assertThat(token.getAuthActionTokenType())
                .isEqualTo(AuthActionTokenType.EMAIL_2FA_ENABLE_CONFIRMATION);
        assertThat(token.getAuthActionUsedAt()).isNull();
        assertThat(token.getAuthActionRevokedAt()).isNull();
        assertThat(token.getAuthActionExpiresAt()).isAfter(OffsetDateTime.now());
        assertThat(token.getAuthActionFailedAttemptCount()).isZero();
        assertThat(token.getAuthActionLastFailedAt()).isNull();
        assertThat(token.getAuthActionMaxAttempts()).isBetween(1, 20);

        EmailOutbox emailOutbox = latestEmail2faEnableEmail();

        assertThat(emailOutbox.getEmailType())
                .isEqualTo(EmailOutboxType.EMAIL_2FA_ENABLE_CONFIRMATION);
        assertThat(emailOutbox.getEmailStatus()).isEqualTo(EmailOutboxStatus.PENDING);
        assertThat(emailOutbox.getRecipientEmail()).isEqualTo(DEFAULT_EMAIL);
        assertThat(emailOutbox.getAttempts()).isZero();
        assertThat(emailOutbox.isDeleteBodyAfterSend()).isTrue();

        String code = extractCodeFromEmail(emailOutbox);

        assertThat(code).matches("\\d{6}");
        assertThat(token.getAuthActionTokenHash())
                .isEqualTo(tokenHashingService.hash(challengeId + ":" + code));
        assertThat(token.getAuthActionTokenHash()).doesNotContain(code);

        User user = defaultUser();

        assertThat(user.isEmailTwoFactorEnabled()).isFalse();
    }

    @Test
    void secondEnableRequestShouldRevokePreviousPendingChallengeAndCancelPreviousEmail() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        UUID firstChallengeId = requestEnableAndExtractChallengeId(accessToken);
        UUID secondChallengeId = requestEnableAndExtractChallengeId(accessToken);

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

        List<EmailOutbox> emails = email2faEnableEmails();

        assertThat(emails).hasSize(2);

        assertThat(emails)
                .filteredOn(email -> email.getEmailStatus() == EmailOutboxStatus.CANCELLED)
                .hasSize(1);

        assertThat(emails)
                .filteredOn(email -> email.getEmailStatus() == EmailOutboxStatus.PENDING)
                .hasSize(1);
    }

    @Test
    void confirmEnableEmail2faShouldEnableEmail2faAndMarkTokenAsUsed() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        UUID challengeId = requestEnableAndExtractChallengeId(accessToken);
        String code = extractCodeFromLatestEnableEmail();

        performConfirmEnableEmail2fa(accessToken, challengeId, code)
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        User user = defaultUser();

        assertThat(user.isEmailTwoFactorEnabled()).isTrue();

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        assertThat(token.getAuthActionUsedAt()).isNotNull();
        assertThat(token.getAuthActionRevokedAt()).isNull();
        assertThat(token.getAuthActionFailedAttemptCount()).isZero();
    }

    @Test
    void confirmEnableEmail2faWithWrongCodeShouldIncrementFailedAttemptCountWithoutEnabling() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        UUID challengeId = requestEnableAndExtractChallengeId(accessToken);
        String validCode = extractCodeFromLatestEnableEmail();
        String wrongCode = wrongCodeDifferentFrom(validCode);

        performConfirmEnableEmail2fa(accessToken, challengeId, wrongCode)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.email2fa.confirmation.invalidOrExpired"));

        User user = defaultUser();

        assertThat(user.isEmailTwoFactorEnabled()).isFalse();

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        assertThat(token.getAuthActionFailedAttemptCount()).isEqualTo(1);
        assertThat(token.getAuthActionLastFailedAt()).isNotNull();
        assertThat(token.getAuthActionUsedAt()).isNull();

        if (token.getAuthActionMaxAttempts() > 1) {
            assertThat(token.getAuthActionRevokedAt()).isNull();
        }
    }

    @Test
    void confirmEnableEmail2faShouldRevokeChallengeWhenMaxFailedAttemptsIsReached() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        UUID challengeId = requestEnableAndExtractChallengeId(accessToken);
        String validCode = extractCodeFromLatestEnableEmail();
        String wrongCode = wrongCodeDifferentFrom(validCode);

        AuthActionToken initialToken = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        int maxAttempts = initialToken.getAuthActionMaxAttempts();

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            performConfirmEnableEmail2fa(accessToken, challengeId, wrongCode)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("auth.email2fa.confirmation.invalidOrExpired"));
        }

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        assertThat(token.getAuthActionFailedAttemptCount()).isEqualTo(maxAttempts);
        assertThat(token.hasReachedFailedAttemptLimit()).isTrue();
        assertThat(token.getAuthActionRevokedAt()).isNotNull();
        assertThat(token.getAuthActionUsedAt()).isNull();

        User user = defaultUser();

        assertThat(user.isEmailTwoFactorEnabled()).isFalse();

        performConfirmEnableEmail2fa(accessToken, challengeId, validCode)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.email2fa.confirmation.invalidOrExpired"));

        assertThat(defaultUser().isEmailTwoFactorEnabled()).isFalse();
    }

    @Test
    void confirmEnableEmail2faShouldRejectInvalidCodeFormatBeforeTouchingCounter() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        UUID challengeId = requestEnableAndExtractChallengeId(accessToken);

        performConfirmEnableEmail2fa(accessToken, challengeId, "abc123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        assertThat(token.getAuthActionFailedAttemptCount()).isZero();
        assertThat(token.getAuthActionLastFailedAt()).isNull();
        assertThat(token.getAuthActionUsedAt()).isNull();
        assertThat(token.getAuthActionRevokedAt()).isNull();

        assertThat(defaultUser().isEmailTwoFactorEnabled()).isFalse();
    }

    @Test
    void requestEnableEmail2faShouldRejectAlreadyEnabledUser() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        User user = defaultUser();
        user.enableEmailTwoFactorEnabled();
        userRepository.saveAndFlush(user);

        performRequestEnableEmail2fa(accessToken, DEFAULT_PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("auth.email2fa.alreadyEnabled"));

        assertThat(email2faEnableTokens()).isEmpty();
        assertThat(email2faEnableEmails()).isEmpty();
    }

    @Test
    void confirmEnableEmail2faShouldRejectChallengeOwnedByAnotherUser() throws Exception {
        String firstUserAccessToken = registerVerifyLoginAndExtractAccessToken();

        String secondEmail = "second.%s@example.com".formatted(UUID.randomUUID().toString().replace("-", ""));
        String secondUserAccessToken = registerVerifyLoginAndExtractAccessToken(
                secondEmail,
                "Second User"
        );

        UUID secondUserChallengeId = requestEnableAndExtractChallengeId(secondUserAccessToken);
        String secondUserCode = extractCodeFromLatestEnableEmail(secondEmail);

        performConfirmEnableEmail2fa(firstUserAccessToken, secondUserChallengeId, secondUserCode)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.email2fa.confirmation.invalidOrExpired"));

        User firstUser = userRepository
                .findByEmailAndUserDeletedAtIsNull(DEFAULT_EMAIL)
                .orElseThrow();

        User secondUser = userRepository
                .findByEmailAndUserDeletedAtIsNull(secondEmail)
                .orElseThrow();

        assertThat(firstUser.isEmailTwoFactorEnabled()).isFalse();
        assertThat(secondUser.isEmailTwoFactorEnabled()).isFalse();

        AuthActionToken secondUserToken = authActionTokenRepository
                .findById(secondUserChallengeId)
                .orElseThrow();

        assertThat(secondUserToken.getAuthActionUsedAt()).isNull();
        assertThat(secondUserToken.getAuthActionFailedAttemptCount()).isZero();
    }

    @Test
    void confirmEnableEmail2faShouldRejectChallengeWithWrongTokenType() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        User user = defaultUser();

        AuthActionToken wrongTypeToken = new AuthActionToken(
                user,
                tokenHashingService.hash("wrong-type-token"),
                AuthActionTokenType.EMAIL_VERIFICATION,
                OffsetDateTime.now().plusMinutes(10)
        );

        authActionTokenRepository.saveAndFlush(wrongTypeToken);

        performConfirmEnableEmail2fa(accessToken, wrongTypeToken.getAuthActionTokenId(), "123456")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.email2fa.confirmation.invalidOrExpired"));

        assertThat(defaultUser().isEmailTwoFactorEnabled()).isFalse();
    }

    @Test
    void requestDisableEmail2faShouldRequireAccessToken() throws Exception {
        performRequestDisableEmail2faWithoutAccessToken(DEFAULT_PASSWORD)
                .andExpect(status().isUnauthorized());

        assertThat(email2faDisableTokens()).isEmpty();
        assertThat(email2faDisableEmails()).isEmpty();
    }

    @Test
    void requestDisableEmail2faShouldRejectAlreadyDisabledUser() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        performRequestDisableEmail2fa(accessToken, DEFAULT_PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("auth.email2fa.alreadyDisabled"));

        assertThat(email2faDisableTokens()).isEmpty();
        assertThat(email2faDisableEmails()).isEmpty();
    }

    @Test
    void requestDisableEmail2faShouldRejectWrongCurrentPasswordWithoutCreatingChallenge() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        enableEmail2faDirectly();

        performRequestDisableEmail2fa(accessToken, WRONG_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.password.current.invalid"));

        assertThat(defaultUser().isEmailTwoFactorEnabled()).isTrue();
        assertThat(email2faDisableTokens()).isEmpty();
        assertThat(email2faDisableEmails()).isEmpty();
    }

    @Test
    void requestDisableEmail2faShouldCreateChallengeTokenAndOutboxEmail() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        enableEmail2faDirectly();

        MvcResult result = performRequestDisableEmail2fa(accessToken, DEFAULT_PASSWORD)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.challengeId").isString())
                .andExpect(jsonPath("$.codeExpiresAt").exists())
                .andReturn();

        UUID challengeId = challengeId(result);

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        assertThat(token.getAuthActionTokenType())
                .isEqualTo(AuthActionTokenType.EMAIL_2FA_DISABLE_CONFIRMATION);
        assertThat(token.getAuthActionUsedAt()).isNull();
        assertThat(token.getAuthActionRevokedAt()).isNull();
        assertThat(token.getAuthActionExpiresAt()).isAfter(OffsetDateTime.now());
        assertThat(token.getAuthActionFailedAttemptCount()).isZero();

        EmailOutbox emailOutbox = latestEmail2faDisableEmail();

        assertThat(emailOutbox.getEmailType())
                .isEqualTo(EmailOutboxType.EMAIL_2FA_DISABLE_CONFIRMATION);
        assertThat(emailOutbox.getEmailStatus()).isEqualTo(EmailOutboxStatus.PENDING);
        assertThat(emailOutbox.getRecipientEmail()).isEqualTo(DEFAULT_EMAIL);
        assertThat(emailOutbox.isDeleteBodyAfterSend()).isTrue();

        String code = extractCodeFromEmail(emailOutbox);

        assertThat(token.getAuthActionTokenHash())
                .isEqualTo(tokenHashingService.hash(challengeId + ":" + code));

        assertThat(defaultUser().isEmailTwoFactorEnabled()).isTrue();
    }

    @Test
    void secondDisableRequestShouldRevokePreviousPendingChallengeAndCancelPreviousEmail() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        enableEmail2faDirectly();

        UUID firstChallengeId = requestDisableAndExtractChallengeId(accessToken);
        UUID secondChallengeId = requestDisableAndExtractChallengeId(accessToken);

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

        List<EmailOutbox> emails = email2faDisableEmails();

        assertThat(emails).hasSize(2);

        assertThat(emails)
                .filteredOn(email -> email.getEmailStatus() == EmailOutboxStatus.CANCELLED)
                .hasSize(1);

        assertThat(emails)
                .filteredOn(email -> email.getEmailStatus() == EmailOutboxStatus.PENDING)
                .hasSize(1);
    }

    @Test
    void confirmDisableEmail2faShouldDisableEmail2faAndMarkTokenAsUsed() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        enableEmail2faDirectly();

        UUID challengeId = requestDisableAndExtractChallengeId(accessToken);
        String code = extractCodeFromLatestDisableEmail();

        performConfirmDisableEmail2fa(accessToken, challengeId, code)
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        User user = defaultUser();

        assertThat(user.isEmailTwoFactorEnabled()).isFalse();

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        assertThat(token.getAuthActionUsedAt()).isNotNull();
        assertThat(token.getAuthActionRevokedAt()).isNull();
        assertThat(token.getAuthActionFailedAttemptCount()).isZero();
    }

    @Test
    void confirmDisableEmail2faWithWrongCodeShouldIncrementFailedAttemptCountWithoutDisabling() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        enableEmail2faDirectly();

        UUID challengeId = requestDisableAndExtractChallengeId(accessToken);
        String validCode = extractCodeFromLatestDisableEmail();
        String wrongCode = wrongCodeDifferentFrom(validCode);

        performConfirmDisableEmail2fa(accessToken, challengeId, wrongCode)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.email2fa.confirmation.invalidOrExpired"));

        User user = defaultUser();

        assertThat(user.isEmailTwoFactorEnabled()).isTrue();

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        assertThat(token.getAuthActionFailedAttemptCount()).isEqualTo(1);
        assertThat(token.getAuthActionLastFailedAt()).isNotNull();
        assertThat(token.getAuthActionUsedAt()).isNull();

        if (token.getAuthActionMaxAttempts() > 1) {
            assertThat(token.getAuthActionRevokedAt()).isNull();
        }
    }

    @Test
    void confirmDisableEmail2faShouldRejectInvalidCodeFormatBeforeTouchingCounter() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        enableEmail2faDirectly();

        UUID challengeId = requestDisableAndExtractChallengeId(accessToken);

        performConfirmDisableEmail2fa(accessToken, challengeId, "abc123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        assertThat(token.getAuthActionFailedAttemptCount()).isZero();
        assertThat(token.getAuthActionLastFailedAt()).isNull();
        assertThat(token.getAuthActionUsedAt()).isNull();
        assertThat(token.getAuthActionRevokedAt()).isNull();

        assertThat(defaultUser().isEmailTwoFactorEnabled()).isTrue();
    }

    @Test
    void confirmDisableEmail2faShouldRejectExpiredChallenge() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        enableEmail2faDirectly();

        UUID challengeId = requestDisableAndExtractChallengeId(accessToken);
        String code = extractCodeFromLatestDisableEmail();

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

        performConfirmDisableEmail2fa(accessToken, challengeId, code)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.email2fa.confirmation.invalidOrExpired"));

        assertThat(defaultUser().isEmailTwoFactorEnabled()).isTrue();

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        assertThat(token.getAuthActionUsedAt()).isNull();
    }

    @Test
    void confirmEnableEmail2faShouldRequireAccessToken() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        UUID challengeId = requestEnableAndExtractChallengeId(accessToken);
        String code = extractCodeFromLatestEnableEmail();

        performConfirmEnableEmail2faWithoutAccessToken(challengeId, code)
                .andExpect(status().isUnauthorized());

        User user = defaultUser();

        assertThat(user.isEmailTwoFactorEnabled()).isFalse();

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        assertThat(token.getAuthActionUsedAt()).isNull();
        assertThat(token.getAuthActionRevokedAt()).isNull();
        assertThat(token.getAuthActionFailedAttemptCount()).isZero();
    }

    @Test
    void confirmDisableEmail2faShouldRequireAccessToken() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        enableEmail2faDirectly();

        UUID challengeId = requestDisableAndExtractChallengeId(accessToken);
        String code = extractCodeFromLatestDisableEmail();

        performConfirmDisableEmail2faWithoutAccessToken(challengeId, code)
                .andExpect(status().isUnauthorized());

        User user = defaultUser();

        assertThat(user.isEmailTwoFactorEnabled()).isTrue();

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        assertThat(token.getAuthActionUsedAt()).isNull();
        assertThat(token.getAuthActionRevokedAt()).isNull();
        assertThat(token.getAuthActionFailedAttemptCount()).isZero();
    }

    @Test
    void confirmEnableEmail2faShouldRejectUnknownChallenge() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        performConfirmEnableEmail2fa(accessToken, UUID.randomUUID(), "123456")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.email2fa.confirmation.invalidOrExpired"));

        assertThat(defaultUser().isEmailTwoFactorEnabled()).isFalse();
    }

    @Test
    void confirmDisableEmail2faShouldRejectUnknownChallenge() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        enableEmail2faDirectly();

        performConfirmDisableEmail2fa(accessToken, UUID.randomUUID(), "123456")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.email2fa.confirmation.invalidOrExpired"));

        assertThat(defaultUser().isEmailTwoFactorEnabled()).isTrue();
    }

    @Test
    void confirmEnableEmail2faShouldRejectAlreadyUsedChallenge() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        UUID challengeId = requestEnableAndExtractChallengeId(accessToken);
        String code = extractCodeFromLatestEnableEmail();

        performConfirmEnableEmail2fa(accessToken, challengeId, code)
                .andExpect(status().isNoContent());

        assertThat(defaultUser().isEmailTwoFactorEnabled()).isTrue();

        performConfirmEnableEmail2fa(accessToken, challengeId, code)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.email2fa.confirmation.invalidOrExpired"));

        User user = defaultUser();

        assertThat(user.isEmailTwoFactorEnabled()).isTrue();

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        assertThat(token.getAuthActionUsedAt()).isNotNull();
        assertThat(token.getAuthActionRevokedAt()).isNull();
        assertThat(token.getAuthActionFailedAttemptCount()).isZero();
    }

    @Test
    void confirmDisableEmail2faShouldRejectAlreadyUsedChallenge() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        enableEmail2faDirectly();

        UUID challengeId = requestDisableAndExtractChallengeId(accessToken);
        String code = extractCodeFromLatestDisableEmail();

        performConfirmDisableEmail2fa(accessToken, challengeId, code)
                .andExpect(status().isNoContent());

        assertThat(defaultUser().isEmailTwoFactorEnabled()).isFalse();

        performConfirmDisableEmail2fa(accessToken, challengeId, code)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.email2fa.confirmation.invalidOrExpired"));

        User user = defaultUser();

        assertThat(user.isEmailTwoFactorEnabled()).isFalse();

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        assertThat(token.getAuthActionUsedAt()).isNotNull();
        assertThat(token.getAuthActionRevokedAt()).isNull();
        assertThat(token.getAuthActionFailedAttemptCount()).isZero();
    }

    @Test
    void confirmDisableEmail2faShouldRevokeChallengeWhenMaxFailedAttemptsIsReached() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        enableEmail2faDirectly();

        UUID challengeId = requestDisableAndExtractChallengeId(accessToken);
        String validCode = extractCodeFromLatestDisableEmail();
        String wrongCode = wrongCodeDifferentFrom(validCode);

        AuthActionToken initialToken = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        int maxAttempts = initialToken.getAuthActionMaxAttempts();

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            performConfirmDisableEmail2fa(accessToken, challengeId, wrongCode)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("auth.email2fa.confirmation.invalidOrExpired"));
        }

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        assertThat(token.getAuthActionFailedAttemptCount()).isEqualTo(maxAttempts);
        assertThat(token.hasReachedFailedAttemptLimit()).isTrue();
        assertThat(token.getAuthActionRevokedAt()).isNotNull();
        assertThat(token.getAuthActionUsedAt()).isNull();

        assertThat(defaultUser().isEmailTwoFactorEnabled()).isTrue();

        performConfirmDisableEmail2fa(accessToken, challengeId, validCode)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.email2fa.confirmation.invalidOrExpired"));

        assertThat(defaultUser().isEmailTwoFactorEnabled()).isTrue();
    }

    @Test
    void enablingEmail2faThroughManagementFlowShouldMakeNextLoginRequire2fa() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        enableEmail2faThroughManagementFlow(accessToken);

        assertThat(defaultUser().isEmailTwoFactorEnabled()).isTrue();

        int sessionsBeforeLoginAttempt = userSessionRepository.findAll().size();
        int refreshTokensBeforeLoginAttempt = refreshTokenRepository.findAll().size();

        performLoginWithDevice(
                DEFAULT_EMAIL,
                DEFAULT_PASSWORD,
                "Login after endpoint enable"
        )
                .andExpect(status().isAccepted())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.login2faChallengeId").isString())
                .andExpect(jsonPath("$.login2faCodeExpiresAt").exists())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.user").doesNotExist());

        assertThat(userSessionRepository.findAll()).hasSize(sessionsBeforeLoginAttempt);
        assertThat(refreshTokenRepository.findAll()).hasSize(refreshTokensBeforeLoginAttempt);

        assertThat(authActionTokenRepository.findAll())
                .filteredOn(token -> token.getAuthActionTokenType() == AuthActionTokenType.LOGIN_2FA_CODE)
                .hasSize(1);

        assertThat(emailOutboxRepository.findAll())
                .filteredOn(email -> email.getEmailType() == EmailOutboxType.LOGIN_2FA_CODE)
                .hasSize(1);
    }

    @Test
    void disablingEmail2faThroughManagementFlowShouldMakeNextLoginAuthenticateImmediately() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        enableEmail2faThroughManagementFlow(accessToken);
        disableEmail2faThroughManagementFlow(accessToken);

        assertThat(defaultUser().isEmailTwoFactorEnabled()).isFalse();

        int sessionsBeforeLogin = userSessionRepository.findAll().size();
        int refreshTokensBeforeLogin = refreshTokenRepository.findAll().size();

        performLoginWithDevice(
                DEFAULT_EMAIL,
                DEFAULT_PASSWORD,
                "Login after endpoint disable"
        )
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(REFRESH_COOKIE_NAME + "=")))
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.accessTokenExpiresAt").exists())
                .andExpect(jsonPath("$.user.email").value(DEFAULT_EMAIL))
                .andExpect(jsonPath("$.login2faChallengeId").doesNotExist());

        assertThat(userSessionRepository.findAll()).hasSize(sessionsBeforeLogin + 1);
        assertThat(refreshTokenRepository.findAll()).hasSize(refreshTokensBeforeLogin + 1);
    }

    @Test
    void requestEnableEmail2faShouldReturnValidationErrorsForEmptyBody() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        performRequestEnableEmail2faWithBody(accessToken, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fieldErrors[*].code", hasItems("auth.password.current.required")));

        assertThat(defaultUser().isEmailTwoFactorEnabled()).isFalse();
        assertThat(email2faEnableTokens()).isEmpty();
        assertThat(email2faEnableEmails()).isEmpty();
    }

    @Test
    void requestDisableEmail2faShouldReturnValidationErrorsForEmptyBody() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        enableEmail2faDirectly();

        performRequestDisableEmail2faWithBody(accessToken, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fieldErrors[*].code", hasItems("auth.password.current.required")));

        assertThat(defaultUser().isEmailTwoFactorEnabled()).isTrue();
        assertThat(email2faDisableTokens()).isEmpty();
        assertThat(email2faDisableEmails()).isEmpty();
    }

    @Test
    void requestEnableEmail2faShouldReturnValidationErrorForTooLongCurrentPassword() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        String tooLongPassword = "a".repeat(129);

        performRequestEnableEmail2fa(accessToken, tooLongPassword)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fieldErrors[*].code", hasItems("auth.password.invalidLength")));

        assertThat(defaultUser().isEmailTwoFactorEnabled()).isFalse();
        assertThat(email2faEnableTokens()).isEmpty();
        assertThat(email2faEnableEmails()).isEmpty();
    }

    @Test
    void requestDisableEmail2faShouldReturnValidationErrorForTooLongCurrentPassword() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        enableEmail2faDirectly();

        String tooLongPassword = "a".repeat(129);

        performRequestDisableEmail2fa(accessToken, tooLongPassword)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fieldErrors[*].code", hasItems("auth.password.invalidLength")));

        assertThat(defaultUser().isEmailTwoFactorEnabled()).isTrue();
        assertThat(email2faDisableTokens()).isEmpty();
        assertThat(email2faDisableEmails()).isEmpty();
    }

    @Test
    void confirmEnableEmail2faShouldReturnValidationErrorsForEmptyBody() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        performConfirmEnableEmail2faWithBody(accessToken, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fieldErrors[*].code", hasItems(
                        "auth.email2fa.challengeId.required",
                        "auth.email2fa.code.required"
                )));

        assertThat(defaultUser().isEmailTwoFactorEnabled()).isFalse();
    }

    @Test
    void confirmDisableEmail2faShouldReturnValidationErrorsForEmptyBody() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        enableEmail2faDirectly();

        performConfirmDisableEmail2faWithBody(accessToken, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fieldErrors[*].code", hasItems(
                        "auth.email2fa.challengeId.required",
                        "auth.email2fa.code.required"
                )));

        assertThat(defaultUser().isEmailTwoFactorEnabled()).isTrue();
    }

    @Test
    void confirmEnableEmail2faShouldReturnValidationErrorsForBlankCode() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        UUID challengeId = requestEnableAndExtractChallengeId(accessToken);

        performConfirmEnableEmail2faWithBody(
                accessToken,
                """
                        {
                          "challengeId": "%s",
                          "code": ""
                        }
                        """.formatted(challengeId)
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fieldErrors[*].code", hasItems("auth.email2fa.code.required")));

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        assertThat(token.getAuthActionFailedAttemptCount()).isZero();
        assertThat(token.getAuthActionUsedAt()).isNull();
        assertThat(defaultUser().isEmailTwoFactorEnabled()).isFalse();
    }

    @Test
    void confirmDisableEmail2faShouldReturnValidationErrorsForBlankCode() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        enableEmail2faDirectly();

        UUID challengeId = requestDisableAndExtractChallengeId(accessToken);

        performConfirmDisableEmail2faWithBody(
                accessToken,
                """
                        {
                          "challengeId": "%s",
                          "code": ""
                        }
                        """.formatted(challengeId)
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fieldErrors[*].code", hasItems("auth.email2fa.code.required")));

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        assertThat(token.getAuthActionFailedAttemptCount()).isZero();
        assertThat(token.getAuthActionUsedAt()).isNull();
        assertThat(defaultUser().isEmailTwoFactorEnabled()).isTrue();
    }

    @Test
    void confirmEnableEmail2faShouldRejectExpiredChallenge() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        UUID challengeId = requestEnableAndExtractChallengeId(accessToken);
        String code = extractCodeFromLatestEnableEmail();

        expireAuthActionToken(challengeId);

        performConfirmEnableEmail2fa(accessToken, challengeId, code)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.email2fa.confirmation.invalidOrExpired"));

        assertThat(defaultUser().isEmailTwoFactorEnabled()).isFalse();

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        assertThat(token.getAuthActionUsedAt()).isNull();
        assertThat(token.getAuthActionFailedAttemptCount()).isZero();
    }

    @Test
    void confirmEnableEmail2faShouldRejectRevokedChallenge() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        UUID challengeId = requestEnableAndExtractChallengeId(accessToken);
        String code = extractCodeFromLatestEnableEmail();

        revokeAuthActionToken(challengeId);

        performConfirmEnableEmail2fa(accessToken, challengeId, code)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.email2fa.confirmation.invalidOrExpired"));

        assertThat(defaultUser().isEmailTwoFactorEnabled()).isFalse();

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        assertThat(token.getAuthActionUsedAt()).isNull();
        assertThat(token.getAuthActionRevokedAt()).isNotNull();
        assertThat(token.getAuthActionFailedAttemptCount()).isZero();
    }

    @Test
    void confirmDisableEmail2faShouldRejectRevokedChallenge() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        enableEmail2faDirectly();

        UUID challengeId = requestDisableAndExtractChallengeId(accessToken);
        String code = extractCodeFromLatestDisableEmail();

        revokeAuthActionToken(challengeId);

        performConfirmDisableEmail2fa(accessToken, challengeId, code)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.email2fa.confirmation.invalidOrExpired"));

        assertThat(defaultUser().isEmailTwoFactorEnabled()).isTrue();

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        assertThat(token.getAuthActionUsedAt()).isNull();
        assertThat(token.getAuthActionRevokedAt()).isNotNull();
        assertThat(token.getAuthActionFailedAttemptCount()).isZero();
    }

    @Test
    void confirmDisableEmail2faShouldRejectChallengeWithWrongTokenType() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        enableEmail2faDirectly();

        User user = defaultUser();

        AuthActionToken wrongTypeToken = new AuthActionToken(
                user,
                tokenHashingService.hash("wrong-type-disable-token"),
                AuthActionTokenType.EMAIL_VERIFICATION,
                OffsetDateTime.now().plusMinutes(10)
        );

        authActionTokenRepository.saveAndFlush(wrongTypeToken);

        performConfirmDisableEmail2fa(accessToken, wrongTypeToken.getAuthActionTokenId(), "123456")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.email2fa.confirmation.invalidOrExpired"));

        assertThat(defaultUser().isEmailTwoFactorEnabled()).isTrue();
    }

    @Test
    void confirmDisableEmail2faShouldRejectChallengeOwnedByAnotherUser() throws Exception {
        String firstUserAccessToken = registerVerifyLoginAndExtractAccessToken();

        enableEmail2faDirectly(DEFAULT_EMAIL);

        String secondEmail = "second.%s@example.com".formatted(UUID.randomUUID().toString().replace("-", ""));
        String secondUserAccessToken = registerVerifyLoginAndExtractAccessToken(
                secondEmail,
                "Second User"
        );

        enableEmail2faDirectly(secondEmail);

        UUID secondUserChallengeId = requestDisableAndExtractChallengeId(secondUserAccessToken);
        String secondUserCode = extractCodeFromLatestDisableEmail(secondEmail);

        performConfirmDisableEmail2fa(firstUserAccessToken, secondUserChallengeId, secondUserCode)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.email2fa.confirmation.invalidOrExpired"));

        User firstUser = userRepository
                .findByEmailAndUserDeletedAtIsNull(DEFAULT_EMAIL)
                .orElseThrow();

        User secondUser = userRepository
                .findByEmailAndUserDeletedAtIsNull(secondEmail)
                .orElseThrow();

        assertThat(firstUser.isEmailTwoFactorEnabled()).isTrue();
        assertThat(secondUser.isEmailTwoFactorEnabled()).isTrue();

        AuthActionToken secondUserToken = authActionTokenRepository
                .findById(secondUserChallengeId)
                .orElseThrow();

        assertThat(secondUserToken.getAuthActionUsedAt()).isNull();
        assertThat(secondUserToken.getAuthActionFailedAttemptCount()).isZero();
    }

    @Test
    void confirmEnableEmail2faShouldRejectRevokedAccessTokenBeforeUsingChallenge() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        UUID challengeId = requestEnableAndExtractChallengeId(accessToken);
        String code = extractCodeFromLatestEnableEmail();

        User user = defaultUser();

        user.incrementTokenVersion();
        userRepository.saveAndFlush(user);

        performConfirmEnableEmail2fa(accessToken, challengeId, code)
                .andExpect(status().isUnauthorized());

        User reloadedUser = defaultUser();

        assertThat(reloadedUser.isEmailTwoFactorEnabled()).isFalse();

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        assertThat(token.getAuthActionUsedAt()).isNull();
        assertThat(token.getAuthActionFailedAttemptCount()).isZero();
    }

    @Test
    void getMeShouldExposeEmail2faDisabledAndPaymentEmailRemindersEnabledByDefault() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        performGetMe(accessToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(DEFAULT_EMAIL))
                .andExpect(jsonPath("$.userName").value(DEFAULT_USER_NAME))
                .andExpect(jsonPath("$.userRole").value("OWNER"))
                .andExpect(jsonPath("$.userPlatformRole").value("USER"))
                .andExpect(jsonPath("$.wantsInvoice").value(false))
                .andExpect(jsonPath("$.emailTwoFactorEnabled").value(false))
                .andExpect(jsonPath("$.paymentEmailRemindersEnabled").value(true));
    }

    @Test
    void getMeShouldExposeEmail2faEnabledAfterManagementEnableFlow() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        enableEmail2faThroughManagementFlow(accessToken);

        performGetMe(accessToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(DEFAULT_EMAIL))
                .andExpect(jsonPath("$.emailTwoFactorEnabled").value(true))
                .andExpect(jsonPath("$.paymentEmailRemindersEnabled").value(true));
    }

    @Test
    void getMeShouldExposeEmail2faDisabledAfterManagementDisableFlow() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        enableEmail2faThroughManagementFlow(accessToken);

        performGetMe(accessToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailTwoFactorEnabled").value(true));

        disableEmail2faThroughManagementFlow(accessToken);

        performGetMe(accessToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(DEFAULT_EMAIL))
                .andExpect(jsonPath("$.emailTwoFactorEnabled").value(false))
                .andExpect(jsonPath("$.paymentEmailRemindersEnabled").value(true));
    }

    @Test
    void getMeShouldReflectDirectPaymentEmailReminderChangesFromDatabase() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        User user = defaultUser();

        user.disablePaymentEmailReminders();

        userRepository.saveAndFlush(user);

        performGetMe(accessToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(DEFAULT_EMAIL))
                .andExpect(jsonPath("$.emailTwoFactorEnabled").value(false))
                .andExpect(jsonPath("$.paymentEmailRemindersEnabled").value(false));
    }

    @Test
    void getMeShouldExposePaymentEmailRemindersDisabledWhenUserOptedOutAtRegistration() throws Exception {
        String email = "reminders-off.%s@example.com".formatted(UUID.randomUUID().toString().replace("-", ""));

        String verificationToken = registerAndExtractVerificationTokenWithPaymentEmailReminders(
                email,
                "Reminders Off User",
                false
        );

        verifyEmail(verificationToken);

        MvcResult loginResult = performLoginWithDevice(
                email,
                DEFAULT_PASSWORD,
                DEFAULT_DEVICE_LABEL
        )
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = jsonPathString(loginResult, "$.accessToken");

        performGetMe(accessToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.emailTwoFactorEnabled").value(false))
                .andExpect(jsonPath("$.paymentEmailRemindersEnabled").value(false));
    }

    @Test
    void confirmEnableEmail2faShouldRejectWhenUserIsSoftDeletedAfterChallengeCreation() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        UUID challengeId = requestEnableAndExtractChallengeId(accessToken);
        String code = extractCodeFromLatestEnableEmail();

        softDeleteDefaultUserDirectly();

        performConfirmEnableEmail2fa(accessToken, challengeId, code)
                .andExpect(status().isUnauthorized());

        User user = loginCandidateByEmail(DEFAULT_EMAIL);

        assertThat(user.isPendingDeletion()).isTrue();
        assertThat(user.isEmailTwoFactorEnabled()).isFalse();

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        assertThat(token.getAuthActionUsedAt()).isNull();
        assertThat(token.getAuthActionFailedAttemptCount()).isZero();
    }

    @Test
    void confirmDisableEmail2faShouldRejectWhenUserIsSoftDeletedAfterChallengeCreation() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        enableEmail2faDirectly();

        UUID challengeId = requestDisableAndExtractChallengeId(accessToken);
        String code = extractCodeFromLatestDisableEmail();

        softDeleteDefaultUserDirectly();

        performConfirmDisableEmail2fa(accessToken, challengeId, code)
                .andExpect(status().isUnauthorized());

        User user = loginCandidateByEmail(DEFAULT_EMAIL);

        assertThat(user.isPendingDeletion()).isTrue();
        assertThat(user.isEmailTwoFactorEnabled()).isTrue();

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        assertThat(token.getAuthActionUsedAt()).isNull();
        assertThat(token.getAuthActionFailedAttemptCount()).isZero();
    }

    @Test
    void confirmEnableEmail2faShouldRejectWhenUserIsDisabledAfterChallengeCreation() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        UUID challengeId = requestEnableAndExtractChallengeId(accessToken);
        String code = extractCodeFromLatestEnableEmail();

        disableDefaultUserDirectly();

        performConfirmEnableEmail2fa(accessToken, challengeId, code)
                .andExpect(status().isUnauthorized());

        User user = loginCandidateByEmail(DEFAULT_EMAIL);

        assertThat(user.isUserIsEnabled()).isFalse();
        assertThat(user.isEmailTwoFactorEnabled()).isFalse();

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        assertThat(token.getAuthActionUsedAt()).isNull();
        assertThat(token.getAuthActionFailedAttemptCount()).isZero();
    }

    @Test
    void confirmDisableEmail2faShouldRejectWhenUserIsDisabledAfterChallengeCreation() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        enableEmail2faDirectly();

        UUID challengeId = requestDisableAndExtractChallengeId(accessToken);
        String code = extractCodeFromLatestDisableEmail();

        disableDefaultUserDirectly();

        performConfirmDisableEmail2fa(accessToken, challengeId, code)
                .andExpect(status().isUnauthorized());

        User user = loginCandidateByEmail(DEFAULT_EMAIL);

        assertThat(user.isUserIsEnabled()).isFalse();
        assertThat(user.isEmailTwoFactorEnabled()).isTrue();

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        assertThat(token.getAuthActionUsedAt()).isNull();
        assertThat(token.getAuthActionFailedAttemptCount()).isZero();
    }

    @Test
    void requestEnableEmail2faShouldRejectMalformedJsonWithoutCreatingChallenge() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        performRequestEnableEmail2faWithBody(accessToken, "{")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.body.invalid"));

        assertThat(defaultUser().isEmailTwoFactorEnabled()).isFalse();
        assertThat(email2faEnableTokens()).isEmpty();
        assertThat(email2faEnableEmails()).isEmpty();
    }

    @Test
    void confirmEnableEmail2faShouldRejectMalformedJsonWithoutTouchingChallenge() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        UUID challengeId = requestEnableAndExtractChallengeId(accessToken);

        performConfirmEnableEmail2faWithBody(accessToken, "{")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.body.invalid"));

        User user = defaultUser();

        assertThat(user.isEmailTwoFactorEnabled()).isFalse();

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        assertThat(token.getAuthActionUsedAt()).isNull();
        assertThat(token.getAuthActionRevokedAt()).isNull();
        assertThat(token.getAuthActionFailedAttemptCount()).isZero();
    }

    @Test
    void requestDisableEmail2faShouldRejectMalformedJsonWithoutCreatingChallenge() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        enableEmail2faDirectly();

        performRequestDisableEmail2faWithBody(accessToken, "{")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.body.invalid"));

        assertThat(defaultUser().isEmailTwoFactorEnabled()).isTrue();
        assertThat(email2faDisableTokens()).isEmpty();
        assertThat(email2faDisableEmails()).isEmpty();
    }

    @Test
    void confirmDisableEmail2faShouldRejectMalformedJsonWithoutTouchingChallenge() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        enableEmail2faDirectly();

        UUID challengeId = requestDisableAndExtractChallengeId(accessToken);

        performConfirmDisableEmail2faWithBody(accessToken, "{")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.body.invalid"));

        User user = defaultUser();

        assertThat(user.isEmailTwoFactorEnabled()).isTrue();

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        assertThat(token.getAuthActionUsedAt()).isNull();
        assertThat(token.getAuthActionRevokedAt()).isNull();
        assertThat(token.getAuthActionFailedAttemptCount()).isZero();
    }

    @Test
    void requestEnableEmail2faResponseShouldNotLeakPasswordCodeOrHashes() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        MvcResult result = performRequestEnableEmail2fa(accessToken, DEFAULT_PASSWORD)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.challengeId").isString())
                .andExpect(jsonPath("$.codeExpiresAt").exists())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.verificationCode").doesNotExist())
                .andExpect(jsonPath("$.currentPassword").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.authActionTokenHash").doesNotExist())
                .andExpect(jsonPath("$.tokenHash").doesNotExist())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();

        assertThat(responseBody)
                .doesNotContain(DEFAULT_PASSWORD)
                .doesNotContain("\"code\":")
                .doesNotContain("\"verificationCode\":")
                .doesNotContain("\"currentPassword\":")
                .doesNotContain("\"password\":")
                .doesNotContain("\"authActionTokenHash\":")
                .doesNotContain("\"tokenHash\":")
                .doesNotContain("\"hash\":");

        EmailOutbox emailOutbox = latestEmail2faEnableEmail();
        String emailCode = extractCodeFromEmail(emailOutbox);

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId(result))
                .orElseThrow();

        assertThat(emailCode).matches("\\d{6}");
        assertThat(token.getAuthActionTokenHash())
                .isEqualTo(tokenHashingService.hash(token.getAuthActionTokenId() + ":" + emailCode));
    }

    @Test
    void requestDisableEmail2faResponseShouldNotLeakPasswordCodeOrHashes() throws Exception {
        String accessToken = registerVerifyLoginAndExtractAccessToken();

        enableEmail2faDirectly();

        MvcResult result = performRequestDisableEmail2fa(accessToken, DEFAULT_PASSWORD)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.challengeId").isString())
                .andExpect(jsonPath("$.codeExpiresAt").exists())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.verificationCode").doesNotExist())
                .andExpect(jsonPath("$.currentPassword").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.authActionTokenHash").doesNotExist())
                .andExpect(jsonPath("$.tokenHash").doesNotExist())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();

        assertThat(responseBody)
                .doesNotContain(DEFAULT_PASSWORD)
                .doesNotContain("\"code\":")
                .doesNotContain("\"verificationCode\":")
                .doesNotContain("\"currentPassword\":")
                .doesNotContain("\"password\":")
                .doesNotContain("\"authActionTokenHash\":")
                .doesNotContain("\"tokenHash\":")
                .doesNotContain("\"hash\":");

        EmailOutbox emailOutbox = latestEmail2faDisableEmail();
        String emailCode = extractCodeFromEmail(emailOutbox);

        AuthActionToken token = authActionTokenRepository
                .findById(challengeId(result))
                .orElseThrow();

        assertThat(emailCode).matches("\\d{6}");
        assertThat(token.getAuthActionTokenHash())
                .isEqualTo(tokenHashingService.hash(token.getAuthActionTokenId() + ":" + emailCode));
    }

    private String registerAndExtractVerificationTokenWithPaymentEmailReminders(
            String email,
            String userName,
            boolean paymentEmailRemindersEnabled
    ) throws Exception {
        performRegisterWithPaymentEmailReminders(
                email,
                userName,
                paymentEmailRemindersEnabled
        )
                .andExpect(status().isCreated());

        EmailOutbox emailOutbox = emailOutboxRepository.findAll()
                .stream()
                .filter(outbox -> outbox.getEmailType() == EmailOutboxType.EMAIL_VERIFICATION)
                .filter(outbox -> outbox.getRecipientEmail().equals(email))
                .max(Comparator.comparing(EmailOutbox::getEmailCreatedAt))
                .orElseThrow();

        return extractTokenFromBody(decryptTextBody(emailOutbox));
    }

    private ResultActions performRegisterWithPaymentEmailReminders(
            String email,
            String userName,
            boolean paymentEmailRemindersEnabled
    ) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .content("""
                        {
                          "userName": "%s",
                          "email": "%s",
                          "password": "%s",
                          "preferredLocale": "%s",
                          "paymentEmailRemindersEnabled": %s
                        }
                        """.formatted(
                        userName,
                        email,
                        DEFAULT_PASSWORD,
                        DEFAULT_LOCALE,
                        paymentEmailRemindersEnabled
                )));
    }

    private String registerVerifyLoginAndExtractAccessToken() throws Exception {
        return registerVerifyLoginAndExtractAccessToken(DEFAULT_EMAIL, DEFAULT_USER_NAME);
    }

    private String registerVerifyLoginAndExtractAccessToken(
            String email,
            String userName
    ) throws Exception {
        String verificationToken = registerAndExtractVerificationToken(email, userName);

        verifyEmail(verificationToken);

        MvcResult loginResult = performLoginWithDevice(
                email,
                DEFAULT_PASSWORD,
                DEFAULT_DEVICE_LABEL
        )
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(REFRESH_COOKIE_NAME + "=")))
                .andReturn();

        return jsonPathString(loginResult, "$.accessToken");
    }

    private String registerAndExtractVerificationToken(
            String email,
            String userName
    ) throws Exception {
        performRegister(email, userName)
                .andExpect(status().isCreated());

        EmailOutbox emailOutbox = emailOutboxRepository.findAll()
                .stream()
                .filter(outbox -> outbox.getEmailType() == EmailOutboxType.EMAIL_VERIFICATION)
                .filter(outbox -> outbox.getRecipientEmail().equals(email))
                .max(Comparator.comparing(EmailOutbox::getEmailCreatedAt))
                .orElseThrow();

        return extractTokenFromBody(decryptTextBody(emailOutbox));
    }

    private void verifyEmail(String token) throws Exception {
        performVerifyEmail(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true));
    }

    private UUID requestEnableAndExtractChallengeId(String accessToken) throws Exception {
        MvcResult result = performRequestEnableEmail2fa(accessToken, DEFAULT_PASSWORD)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.challengeId").isString())
                .andExpect(jsonPath("$.codeExpiresAt").exists())
                .andReturn();

        return challengeId(result);
    }

    private UUID requestDisableAndExtractChallengeId(String accessToken) throws Exception {
        MvcResult result = performRequestDisableEmail2fa(accessToken, DEFAULT_PASSWORD)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.challengeId").isString())
                .andExpect(jsonPath("$.codeExpiresAt").exists())
                .andReturn();

        return challengeId(result);
    }

    private UUID challengeId(MvcResult result) throws Exception {
        return UUID.fromString(jsonPathString(result, "$.challengeId"));
    }

    private void enableEmail2faDirectly() {
        User user = defaultUser();

        user.enableEmailTwoFactorEnabled();

        userRepository.saveAndFlush(user);
    }

    private User defaultUser() {
        return userRepository.findByEmailAndUserDeletedAtIsNull(DEFAULT_EMAIL)
                .orElseThrow();
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

    private String extractCodeFromLatestEnableEmail() {
        return extractCodeFromEmail(latestEmail2faEnableEmail());
    }

    private String extractCodeFromLatestEnableEmail(String recipientEmail) {
        return extractCodeFromEmail(latestEmail2faEnableEmail(recipientEmail));
    }

    private String extractCodeFromLatestDisableEmail() {
        return extractCodeFromEmail(latestEmail2faDisableEmail());
    }

    private String extractCodeFromEmail(EmailOutbox emailOutbox) {
        String body = decryptTextBody(emailOutbox);

        Matcher matcher = SIX_DIGIT_CODE_PATTERN.matcher(body);

        assertThat(matcher.find()).isTrue();

        return matcher.group(1);
    }

    private String wrongCodeDifferentFrom(String validCode) {
        return "000000".equals(validCode) ? "000001" : "000000";
    }

    private List<AuthActionToken> email2faEnableTokens() {
        return authActionTokenRepository.findAll()
                .stream()
                .filter(token -> token.getAuthActionTokenType() == AuthActionTokenType.EMAIL_2FA_ENABLE_CONFIRMATION)
                .toList();
    }

    private List<AuthActionToken> email2faDisableTokens() {
        return authActionTokenRepository.findAll()
                .stream()
                .filter(token -> token.getAuthActionTokenType() == AuthActionTokenType.EMAIL_2FA_DISABLE_CONFIRMATION)
                .toList();
    }

    private List<EmailOutbox> email2faEnableEmails() {
        return emailOutboxRepository.findAll()
                .stream()
                .filter(email -> email.getEmailType() == EmailOutboxType.EMAIL_2FA_ENABLE_CONFIRMATION)
                .toList();
    }

    private List<EmailOutbox> email2faDisableEmails() {
        return emailOutboxRepository.findAll()
                .stream()
                .filter(email -> email.getEmailType() == EmailOutboxType.EMAIL_2FA_DISABLE_CONFIRMATION)
                .toList();
    }

    private EmailOutbox latestEmail2faEnableEmail() {
        return latestEmail2faEnableEmail(DEFAULT_EMAIL);
    }

    private EmailOutbox latestEmail2faEnableEmail(String recipientEmail) {
        return emailOutboxRepository.findAll()
                .stream()
                .filter(email -> email.getEmailType() == EmailOutboxType.EMAIL_2FA_ENABLE_CONFIRMATION)
                .filter(email -> email.getRecipientEmail().equals(recipientEmail))
                .max(Comparator.comparing(EmailOutbox::getEmailCreatedAt))
                .orElseThrow();
    }

    private EmailOutbox latestEmail2faDisableEmail() {
        return emailOutboxRepository.findAll()
                .stream()
                .filter(email -> email.getEmailType() == EmailOutboxType.EMAIL_2FA_DISABLE_CONFIRMATION)
                .filter(email -> email.getRecipientEmail().equals(DEFAULT_EMAIL))
                .max(Comparator.comparing(EmailOutbox::getEmailCreatedAt))
                .orElseThrow();
    }

    private ResultActions performRegister(
            String email,
            String userName
    ) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
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
                        DEFAULT_PASSWORD,
                        DEFAULT_LOCALE
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

    private ResultActions performRequestEnableEmail2fa(
            String accessToken,
            String currentPassword
    ) throws Exception {
        return mockMvc.perform(post("/api/me/email-2fa/enable/request")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .content("""
                        {
                          "currentPassword": "%s"
                        }
                        """.formatted(currentPassword)));
    }

    private ResultActions performRequestEnableEmail2faWithoutAccessToken(
            String currentPassword
    ) throws Exception {
        return mockMvc.perform(post("/api/me/email-2fa/enable/request")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .content("""
                        {
                          "currentPassword": "%s"
                        }
                        """.formatted(currentPassword)));
    }

    private ResultActions performConfirmEnableEmail2fa(
            String accessToken,
            UUID challengeId,
            String code
    ) throws Exception {
        return mockMvc.perform(post("/api/me/email-2fa/enable/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .content("""
                        {
                          "challengeId": "%s",
                          "code": "%s"
                        }
                        """.formatted(
                        challengeId,
                        code
                )));
    }

    private ResultActions performRequestDisableEmail2fa(
            String accessToken,
            String currentPassword
    ) throws Exception {
        return mockMvc.perform(post("/api/me/email-2fa/disable/request")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .content("""
                        {
                          "currentPassword": "%s"
                        }
                        """.formatted(currentPassword)));
    }

    private ResultActions performRequestDisableEmail2faWithoutAccessToken(
            String currentPassword
    ) throws Exception {
        return mockMvc.perform(post("/api/me/email-2fa/disable/request")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .content("""
                        {
                          "currentPassword": "%s"
                        }
                        """.formatted(currentPassword)));
    }

    private ResultActions performConfirmDisableEmail2fa(
            String accessToken,
            UUID challengeId,
            String code
    ) throws Exception {
        return mockMvc.perform(post("/api/me/email-2fa/disable/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .content("""
                        {
                          "challengeId": "%s",
                          "code": "%s"
                        }
                        """.formatted(
                        challengeId,
                        code
                )));
    }

    private void enableEmail2faThroughManagementFlow(String accessToken) throws Exception {
        UUID challengeId = requestEnableAndExtractChallengeId(accessToken);
        String code = extractCodeFromLatestEnableEmail();

        performConfirmEnableEmail2fa(accessToken, challengeId, code)
                .andExpect(status().isNoContent());
    }

    private void disableEmail2faThroughManagementFlow(String accessToken) throws Exception {
        UUID challengeId = requestDisableAndExtractChallengeId(accessToken);
        String code = extractCodeFromLatestDisableEmail();

        performConfirmDisableEmail2fa(accessToken, challengeId, code)
                .andExpect(status().isNoContent());
    }

    private void expireAuthActionToken(UUID challengeId) {
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
    }

    private void revokeAuthActionToken(UUID challengeId) {
        AuthActionToken token = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        token.revoke();

        authActionTokenRepository.saveAndFlush(token);
    }

    private String extractCodeFromLatestDisableEmail(String recipientEmail) {
        return extractCodeFromEmail(latestEmail2faDisableEmail(recipientEmail));
    }

    private EmailOutbox latestEmail2faDisableEmail(String recipientEmail) {
        return emailOutboxRepository.findAll()
                .stream()
                .filter(email -> email.getEmailType() == EmailOutboxType.EMAIL_2FA_DISABLE_CONFIRMATION)
                .filter(email -> email.getRecipientEmail().equals(recipientEmail))
                .max(Comparator.comparing(EmailOutbox::getEmailCreatedAt))
                .orElseThrow();
    }

    private ResultActions performConfirmEnableEmail2faWithoutAccessToken(
            UUID challengeId,
            String code
    ) throws Exception {
        return mockMvc.perform(post("/api/me/email-2fa/enable/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .content("""
                        {
                          "challengeId": "%s",
                          "code": "%s"
                        }
                        """.formatted(
                        challengeId,
                        code
                )));
    }

    private ResultActions performConfirmDisableEmail2faWithoutAccessToken(
            UUID challengeId,
            String code
    ) throws Exception {
        return mockMvc.perform(post("/api/me/email-2fa/disable/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .content("""
                        {
                          "challengeId": "%s",
                          "code": "%s"
                        }
                        """.formatted(
                        challengeId,
                        code
                )));
    }

    private ResultActions performRequestEnableEmail2faWithBody(
            String accessToken,
            String body
    ) throws Exception {
        return mockMvc.perform(post("/api/me/email-2fa/enable/request")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .content(body));
    }

    private ResultActions performRequestDisableEmail2faWithBody(
            String accessToken,
            String body
    ) throws Exception {
        return mockMvc.perform(post("/api/me/email-2fa/disable/request")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .content(body));
    }

    private ResultActions performConfirmEnableEmail2faWithBody(
            String accessToken,
            String body
    ) throws Exception {
        return mockMvc.perform(post("/api/me/email-2fa/enable/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .content(body));
    }

    private ResultActions performConfirmDisableEmail2faWithBody(
            String accessToken,
            String body
    ) throws Exception {
        return mockMvc.perform(post("/api/me/email-2fa/disable/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .content(body));
    }

    private void enableEmail2faDirectly(String email) {
        User user = userRepository
                .findByEmailAndUserDeletedAtIsNull(email)
                .orElseThrow();

        user.enableEmailTwoFactorEnabled();

        userRepository.saveAndFlush(user);
    }

    private ResultActions performGetMe(String accessToken) throws Exception {
        return mockMvc.perform(get("/api/me")
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    private User loginCandidateByEmail(String email) {
        return userRepository.findLoginCandidateByEmail(email)
                .orElseThrow();
    }

    private void softDeleteDefaultUserDirectly() {
        User user = defaultUser();

        user.markAsSoftDeleted();

        userRepository.saveAndFlush(user);
    }

    private void disableDefaultUserDirectly() {
        User user = defaultUser();

        user.setUserIsEnabled(false);

        userRepository.saveAndFlush(user);
    }
}