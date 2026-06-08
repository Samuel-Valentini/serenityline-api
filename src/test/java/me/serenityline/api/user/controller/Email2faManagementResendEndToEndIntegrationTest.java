package me.serenityline.api.user.controller;

import com.jayway.jsonpath.JsonPath;
import me.serenityline.api.auth.entity.AuthActionToken;
import me.serenityline.api.auth.entity.AuthActionTokenType;
import me.serenityline.api.auth.repository.AuthActionTokenRepository;
import me.serenityline.api.email.outbox.EmailOutboxProcessor;
import me.serenityline.api.email.outbox.entity.EmailOutbox;
import me.serenityline.api.email.outbox.entity.EmailOutboxStatus;
import me.serenityline.api.email.outbox.entity.EmailOutboxType;
import me.serenityline.api.email.outbox.repository.EmailOutboxRepository;
import me.serenityline.api.email.service.EmailSender;
import me.serenityline.api.security.crypto.EmailOutboxEncryptionService;
import me.serenityline.api.security.crypto.EncryptedValue;
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
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("integration")
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
class Email2faManagementResendEndToEndIntegrationTest extends IntegrationTestSupport {

    private static final String TEST_EMAIL = "test@serenityline.me";
    private static final String TEST_PASSWORD = "VeryStrongPassword-2026-SerenityLine!";
    private static final String TEST_USER_NAME = "SerenityLine Email 2FA Management Test";
    private static final String DEFAULT_LOCALE = "en-US";
    private static final String DEFAULT_USER_AGENT = "JUnit Real Email Browser";
    private static final String DEFAULT_DEVICE_LABEL = "Real email management test device";

    private static final String REFRESH_COOKIE_NAME = "serenityline_refresh";

    private static final int OUTBOX_BATCH_SIZE = 10;
    private static final Duration OUTBOX_RETRY_DELAY = Duration.ofMinutes(5);

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
    void fullEmail2faManagementCycleShouldSendRealEnableAndDisableEmailsWithResend() throws Exception {
        performRegister()
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(TEST_EMAIL))
                .andExpect(jsonPath("$.emailVerificationRequired").value(true));

        EmailOutbox verificationEmailBeforeSend = onlyEmailOutbox(
                EmailOutboxType.EMAIL_VERIFICATION,
                EmailOutboxStatus.PENDING
        );

        String verificationToken = extractTokenFromBody(
                decryptTextBody(verificationEmailBeforeSend)
        );

        int processedVerificationEmails = processDueEmailsWithResend();

        assertThat(processedVerificationEmails).isEqualTo(1);

        EmailOutbox verificationEmailAfterSend = emailOutboxRepository
                .findById(verificationEmailBeforeSend.getEmailOutboxId())
                .orElseThrow();

        assertSentAndBodyDeleted(
                verificationEmailAfterSend,
                EmailOutboxType.EMAIL_VERIFICATION
        );

        performVerifyEmail(verificationToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true));

        MvcResult loginResult = performLogin()
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(REFRESH_COOKIE_NAME + "=")))
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.user.email").value(TEST_EMAIL))
                .andReturn();

        String accessToken = jsonPathString(loginResult, "$.accessToken");

        performGetMe(accessToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(TEST_EMAIL))
                .andExpect(jsonPath("$.emailTwoFactorEnabled").value(false))
                .andExpect(jsonPath("$.paymentEmailRemindersEnabled").value(true));

        MvcResult enableRequestResult = performRequestEnableEmail2fa(accessToken)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.challengeId").isString())
                .andExpect(jsonPath("$.codeExpiresAt").exists())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andReturn();

        UUID enableChallengeId = challengeId(enableRequestResult);

        EmailOutbox enableEmailBeforeSend = onlyEmailOutbox(
                EmailOutboxType.EMAIL_2FA_ENABLE_CONFIRMATION,
                EmailOutboxStatus.PENDING
        );

        String enableCode = extractCodeFromEmail(enableEmailBeforeSend);

        AuthActionToken enableToken = authActionTokenRepository
                .findById(enableChallengeId)
                .orElseThrow();

        assertThat(enableToken.getAuthActionTokenType())
                .isEqualTo(AuthActionTokenType.EMAIL_2FA_ENABLE_CONFIRMATION);
        assertThat(enableToken.getAuthActionTokenHash())
                .isEqualTo(tokenHashingService.hash(enableChallengeId + ":" + enableCode));
        assertThat(enableToken.getAuthActionUsedAt()).isNull();
        assertThat(enableToken.getAuthActionRevokedAt()).isNull();
        assertThat(enableToken.getAuthActionFailedAttemptCount()).isZero();

        int processedEnableEmails = processDueEmailsWithResend();

        assertThat(processedEnableEmails).isEqualTo(1);

        EmailOutbox enableEmailAfterSend = emailOutboxRepository
                .findById(enableEmailBeforeSend.getEmailOutboxId())
                .orElseThrow();

        assertSentAndBodyDeleted(
                enableEmailAfterSend,
                EmailOutboxType.EMAIL_2FA_ENABLE_CONFIRMATION
        );

        performConfirmEnableEmail2fa(
                accessToken,
                enableChallengeId,
                enableCode
        )
                .andExpect(status().isNoContent());

        User enabledUser = defaultUser();

        assertThat(enabledUser.isEmailTwoFactorEnabled()).isTrue();

        AuthActionToken usedEnableToken = authActionTokenRepository
                .findById(enableChallengeId)
                .orElseThrow();

        assertThat(usedEnableToken.getAuthActionUsedAt()).isNotNull();
        assertThat(usedEnableToken.getAuthActionRevokedAt()).isNull();

        performGetMe(accessToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(TEST_EMAIL))
                .andExpect(jsonPath("$.emailTwoFactorEnabled").value(true));

        MvcResult disableRequestResult = performRequestDisableEmail2fa(accessToken)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.challengeId").isString())
                .andExpect(jsonPath("$.codeExpiresAt").exists())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andReturn();

        UUID disableChallengeId = challengeId(disableRequestResult);

        EmailOutbox disableEmailBeforeSend = onlyEmailOutbox(
                EmailOutboxType.EMAIL_2FA_DISABLE_CONFIRMATION,
                EmailOutboxStatus.PENDING
        );

        String disableCode = extractCodeFromEmail(disableEmailBeforeSend);

        AuthActionToken disableToken = authActionTokenRepository
                .findById(disableChallengeId)
                .orElseThrow();

        assertThat(disableToken.getAuthActionTokenType())
                .isEqualTo(AuthActionTokenType.EMAIL_2FA_DISABLE_CONFIRMATION);
        assertThat(disableToken.getAuthActionTokenHash())
                .isEqualTo(tokenHashingService.hash(disableChallengeId + ":" + disableCode));
        assertThat(disableToken.getAuthActionUsedAt()).isNull();
        assertThat(disableToken.getAuthActionRevokedAt()).isNull();
        assertThat(disableToken.getAuthActionFailedAttemptCount()).isZero();

        int processedDisableEmails = processDueEmailsWithResend();

        assertThat(processedDisableEmails).isEqualTo(1);

        EmailOutbox disableEmailAfterSend = emailOutboxRepository
                .findById(disableEmailBeforeSend.getEmailOutboxId())
                .orElseThrow();

        assertSentAndBodyDeleted(
                disableEmailAfterSend,
                EmailOutboxType.EMAIL_2FA_DISABLE_CONFIRMATION
        );

        performConfirmDisableEmail2fa(
                accessToken,
                disableChallengeId,
                disableCode
        )
                .andExpect(status().isNoContent());

        User disabledUser = defaultUser();

        assertThat(disabledUser.isEmailTwoFactorEnabled()).isFalse();

        AuthActionToken usedDisableToken = authActionTokenRepository
                .findById(disableChallengeId)
                .orElseThrow();

        assertThat(usedDisableToken.getAuthActionUsedAt()).isNotNull();
        assertThat(usedDisableToken.getAuthActionRevokedAt()).isNull();

        performGetMe(accessToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(TEST_EMAIL))
                .andExpect(jsonPath("$.emailTwoFactorEnabled").value(false))
                .andExpect(jsonPath("$.paymentEmailRemindersEnabled").value(true));
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
        List<EmailOutbox> emails = emailOutboxRepository.findAll()
                .stream()
                .filter(email -> email.getEmailType() == emailType)
                .filter(email -> email.getEmailStatus() == emailStatus)
                .toList();

        assertThat(emails).hasSize(1);

        return emails.getFirst();
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

    private User defaultUser() {
        return userRepository.findLoginCandidateByEmail(TEST_EMAIL)
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

    private String extractCodeFromEmail(EmailOutbox emailOutbox) {
        String body = decryptTextBody(emailOutbox);

        Matcher matcher = SIX_DIGIT_CODE_PATTERN.matcher(body);

        assertThat(matcher.find()).isTrue();

        return matcher.group(1);
    }

    private UUID challengeId(MvcResult result) throws Exception {
        return UUID.fromString(jsonPathString(result, "$.challengeId"));
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

    private ResultActions performLogin() throws Exception {
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
                        DEFAULT_DEVICE_LABEL
                )));
    }

    private ResultActions performGetMe(String accessToken) throws Exception {
        return mockMvc.perform(get("/api/me")
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    private ResultActions performRequestEnableEmail2fa(String accessToken) throws Exception {
        return mockMvc.perform(post("/api/me/email-2fa/enable/request")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .content("""
                        {
                          "currentPassword": "%s"
                        }
                        """.formatted(TEST_PASSWORD)));
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

    private ResultActions performRequestDisableEmail2fa(String accessToken) throws Exception {
        return mockMvc.perform(post("/api/me/email-2fa/disable/request")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .content("""
                        {
                          "currentPassword": "%s"
                        }
                        """.formatted(TEST_PASSWORD)));
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
}