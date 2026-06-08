package me.serenityline.api.auth.controller;

import com.jayway.jsonpath.JsonPath;
import me.serenityline.api.auth.entity.AuthActionToken;
import me.serenityline.api.auth.entity.AuthActionTokenType;
import me.serenityline.api.auth.entity.RefreshToken;
import me.serenityline.api.auth.entity.UserSession;
import me.serenityline.api.auth.repository.AuthActionTokenRepository;
import me.serenityline.api.auth.repository.RefreshTokenRepository;
import me.serenityline.api.auth.repository.UserSessionRepository;
import me.serenityline.api.email.outbox.EmailOutboxProcessor;
import me.serenityline.api.email.outbox.entity.EmailOutbox;
import me.serenityline.api.email.outbox.entity.EmailOutboxStatus;
import me.serenityline.api.email.outbox.entity.EmailOutboxType;
import me.serenityline.api.email.outbox.repository.EmailOutboxRepository;
import me.serenityline.api.email.service.EmailSender;
import me.serenityline.api.security.crypto.EmailOutboxEncryptionService;
import me.serenityline.api.security.crypto.EncryptedValue;
import me.serenityline.api.security.jwt.JwtTokenClaims;
import me.serenityline.api.security.jwt.JwtTokenService;
import me.serenityline.api.security.token.TokenHashingService;
import me.serenityline.api.support.IntegrationTestSupport;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.repository.UserRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("integration")
@Tag("real-email")
@SpringBootTest(
        properties = {
                "serenityline.email.provider=resend",
                "serenityline.email.outbox-worker.enabled=false"
        }
)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "RESEND_API_KEY", matches = ".+")
@EnabledIfSystemProperty(
        named = "serenityline.tests.real-email.enabled",
        matches = "true"
)
class AuthLogin2faResendEndToEndIntegrationTest extends IntegrationTestSupport {

    private static final String TEST_EMAIL = "test@serenityline.me";
    private static final String TEST_PASSWORD = "VeryStrongPassword-2026-SerenityLine!";
    private static final String TEST_USER_NAME = "SerenityLine Email Test";
    private static final String DEFAULT_LOCALE = "en-US";
    private static final String DEFAULT_USER_AGENT = "JUnit Real Email Browser";
    private static final String INITIAL_DEVICE_LABEL = "Initial device before 2FA";
    private static final String VERIFIED_DEVICE_LABEL = "Verified 2FA device";

    private static final String REFRESH_COOKIE_NAME = "serenityline_refresh";

    private static final int OUTBOX_BATCH_SIZE = 10;
    private static final Duration OUTBOX_RETRY_DELAY = Duration.ofMinutes(5);

    private static final Pattern SIX_DIGIT_CODE_PATTERN = Pattern.compile("\\b\\d{6}\\b");

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
    private JwtTokenService jwtTokenService;

    @Autowired
    private EmailSender emailSender;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

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
    void fullLogin2faCycleShouldSendRealVerificationAndLogin2faEmailsWithResend() throws Exception {
        performRegister()
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(TEST_EMAIL))
                .andExpect(jsonPath("$.emailVerificationRequired").value(true));

        EmailOutbox verificationEmailBeforeSend = onlyEmailOutbox(
                EmailOutboxType.EMAIL_VERIFICATION,
                EmailOutboxStatus.PENDING
        );

        String verificationBody = decryptTextBody(verificationEmailBeforeSend);
        String verificationToken = extractTokenFromBody(verificationBody);

        AuthActionToken verificationActionToken = onlyActionToken(AuthActionTokenType.EMAIL_VERIFICATION);

        assertThat(verificationActionToken.getAuthActionTokenHash())
                .isEqualTo(tokenHashingService.hash(verificationToken));
        assertThat(verificationActionToken.getAuthActionTokenHash())
                .isNotEqualTo(verificationToken);

        int processedVerificationEmails = processDueEmailsWithResend();

        assertThat(processedVerificationEmails).isEqualTo(1);

        EmailOutbox verificationEmailAfterSend = emailOutboxRepository
                .findById(verificationEmailBeforeSend.getEmailOutboxId())
                .orElseThrow();

        assertSentAndBodyDeleted(verificationEmailAfterSend, EmailOutboxType.EMAIL_VERIFICATION);
        assertThat(verificationEmailAfterSend.getProviderMessageId())
                .as("Resend provider message id for verification email")
                .isNotBlank();

        performVerifyEmail(verificationToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true));

        User verifiedUser = defaultUser();

        assertThat(verifiedUser.isUserIsEnabled()).isTrue();
        assertThat(verifiedUser.isEmailTwoFactorEnabled()).isFalse();

        verifiedUser.enableEmailTwoFactorEnabled();
        userRepository.saveAndFlush(verifiedUser);

        performLoginWithDevice(INITIAL_DEVICE_LABEL)
                .andExpect(status().isAccepted())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.login2faChallengeId").isString())
                .andExpect(jsonPath("$.login2faCodeExpiresAt").exists())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.user").doesNotExist());

        assertThat(userSessionRepository.findAll()).isEmpty();
        assertThat(refreshTokenRepository.findAll()).isEmpty();

        EmailOutbox login2faEmailBeforeSend = onlyEmailOutbox(
                EmailOutboxType.LOGIN_2FA_CODE,
                EmailOutboxStatus.PENDING
        );

        String login2faBody = decryptTextBody(login2faEmailBeforeSend);
        String login2faCode = extractSixDigitCode(login2faBody);

        AuthActionToken login2faActionToken = onlyActionToken(AuthActionTokenType.LOGIN_2FA_CODE);

        UUID challengeId = login2faActionToken.getAuthActionTokenId();

        assertThat(challengeId).isNotNull();
        assertThat(login2faActionToken.getAuthActionTokenHash())
                .isEqualTo(tokenHashingService.hash(challengeId + ":" + login2faCode));
        assertThat(login2faActionToken.getAuthActionFailedAttemptCount()).isZero();
        assertThat(login2faActionToken.getAuthActionUsedAt()).isNull();
        assertThat(login2faActionToken.getAuthActionRevokedAt()).isNull();

        int processedLogin2faEmails = processDueEmailsWithResend();

        assertThat(processedLogin2faEmails).isEqualTo(1);

        EmailOutbox login2faEmailAfterSend = emailOutboxRepository
                .findById(login2faEmailBeforeSend.getEmailOutboxId())
                .orElseThrow();

        assertSentAndBodyDeleted(login2faEmailAfterSend, EmailOutboxType.LOGIN_2FA_CODE);
        assertThat(login2faEmailAfterSend.getProviderMessageId())
                .as("Resend provider message id for login 2FA email")
                .isNotBlank();

        MvcResult verify2faResult = performVerifyLogin2fa(
                challengeId,
                login2faCode,
                VERIFIED_DEVICE_LABEL
        )
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(REFRESH_COOKIE_NAME + "=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.accessTokenExpiresAt").exists())
                .andExpect(jsonPath("$.user.email").value(TEST_EMAIL))
                .andExpect(jsonPath("$.user.userName").value(TEST_USER_NAME))
                .andExpect(jsonPath("$.user.userRole").value("OWNER"))
                .andExpect(jsonPath("$.user.userPlatformRole").value("USER"))
                .andReturn();

        User authenticatedUser = defaultUser();

        assertThat(authenticatedUser.getUserLastLoginAt()).isNotNull();

        AuthActionToken usedLogin2faToken = authActionTokenRepository
                .findById(challengeId)
                .orElseThrow();

        assertThat(usedLogin2faToken.getAuthActionUsedAt()).isNotNull();
        assertThat(usedLogin2faToken.getAuthActionRevokedAt()).isNull();
        assertThat(usedLogin2faToken.getAuthActionFailedAttemptCount()).isZero();

        assertThat(userSessionRepository.findAll()).hasSize(1);
        assertThat(refreshTokenRepository.findAll()).hasSize(1);

        UserSession session = userSessionRepository.findAll().getFirst();

        assertThat(session.getUser().getUserId()).isEqualTo(authenticatedUser.getUserId());
        assertThat(session.getUserAgent()).isEqualTo(DEFAULT_USER_AGENT);
        assertThat(session.getDeviceLabel()).isEqualTo(VERIFIED_DEVICE_LABEL);
        assertThat(session.getSessionRevokedAt()).isNull();
        assertThat(session.getSessionExpiresAt()).isAfter(OffsetDateTime.now());

        RefreshToken refreshToken = refreshTokenRepository.findAll().getFirst();

        assertThat(refreshToken.getUser().getUserId()).isEqualTo(authenticatedUser.getUserId());
        assertThat(refreshToken.getUserSession().getUserSessionId()).isEqualTo(session.getUserSessionId());
        assertThat(refreshToken.getRefreshTokenHash()).isNotBlank();
        assertThat(refreshToken.getRefreshTokenUsedAt()).isNull();
        assertThat(refreshToken.getRefreshTokenRevokedAt()).isNull();

        String accessToken = jsonPathString(verify2faResult, "$.accessToken");

        Optional<JwtTokenClaims> claims = jwtTokenService.parseAndValidate(accessToken);

        assertThat(claims).isPresent();
        assertThat(claims.get().userId()).isEqualTo(authenticatedUser.getUserId());
        assertThat(claims.get().tokenVersion()).isEqualTo(authenticatedUser.getTokenVersion());
    }

    private int processDueEmailsWithResend() {
        EmailOutboxProcessor processor = new EmailOutboxProcessor(
                emailOutboxRepository,
                emailOutboxEncryptionService,
                emailSender,
                OUTBOX_BATCH_SIZE,
                OUTBOX_RETRY_DELAY,
                eventPublisher
        );

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        return transactionTemplate.execute(status -> processor.processDueEmails());
    }

    private EmailOutbox onlyEmailOutbox(
            EmailOutboxType emailType,
            EmailOutboxStatus emailStatus
    ) {
        List<EmailOutbox> emails = emailOutboxRepository.findAll().stream()
                .filter(email -> email.getEmailType() == emailType)
                .filter(email -> email.getEmailStatus() == emailStatus)
                .toList();

        assertThat(emails).hasSize(1);

        return emails.getFirst();
    }

    private AuthActionToken onlyActionToken(AuthActionTokenType type) {
        List<AuthActionToken> tokens = authActionTokenRepository.findAll().stream()
                .filter(token -> token.getAuthActionTokenType() == type)
                .toList();

        assertThat(tokens).hasSize(1);

        return tokens.getFirst();
    }

    private void assertSentAndBodyDeleted(
            EmailOutbox emailOutbox,
            EmailOutboxType expectedType
    ) {
        assertThat(emailOutbox.getEmailType()).isEqualTo(expectedType);
        assertThat(emailOutbox.getEmailStatus()).isEqualTo(EmailOutboxStatus.SENT);
        assertThat(emailOutbox.getProvider()).isEqualTo("resend");
        assertThat(emailOutbox.getProviderMessageId()).isNotBlank();
        assertThat(emailOutbox.getAttempts()).isEqualTo(1);
        assertThat(emailOutbox.getEmailSentAt()).isNotNull();
        assertThat(emailOutbox.getLastError()).isNull();

        assertThat(emailOutbox.getBodyTextEncrypted()).isNull();
        assertThat(emailOutbox.getBodyTextIv()).isNull();
        assertThat(emailOutbox.getBodyTextTag()).isNull();
        assertThat(emailOutbox.getBodyHtmlEncrypted()).isNull();
        assertThat(emailOutbox.getBodyHtmlIv()).isNull();
        assertThat(emailOutbox.getBodyHtmlTag()).isNull();
        assertThat(emailOutbox.getEmailBodyDeletedAt()).isNotNull();
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

    private String extractSixDigitCode(String body) {
        Matcher matcher = SIX_DIGIT_CODE_PATTERN.matcher(body);

        assertThat(matcher.find()).isTrue();

        return matcher.group();
    }

    private ResultActions performRegister() throws Exception {
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
                        TEST_USER_NAME,
                        TEST_EMAIL,
                        TEST_PASSWORD,
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

    private ResultActions performLoginWithDevice(String deviceLabel) throws Exception {
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
                        TEST_EMAIL,
                        TEST_PASSWORD,
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

    private User defaultUser() {
        return userRepository.findLoginCandidateByEmail(TEST_EMAIL)
                .orElseThrow();
    }
}