package me.serenityline.api.user.controller;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.Cookie;
import me.serenityline.api.auth.entity.*;
import me.serenityline.api.auth.repository.AuthActionTokenRepository;
import me.serenityline.api.auth.repository.EmailOutboxRepository;
import me.serenityline.api.auth.service.AuthCookieService;
import me.serenityline.api.security.crypto.EmailOutboxEncryptionService;
import me.serenityline.api.security.crypto.EncryptedValue;
import me.serenityline.api.security.jwt.JwtTokenService;
import me.serenityline.api.user.entity.*;
import me.serenityline.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmailChangeIntegrationTest {

    private static final String API_ME = "/api/me";
    private static final String EMAIL_CHANGE_REQUEST_PATH = "/api/me/email-change/request";
    private static final String EMAIL_CHANGE_CONFIRM_PATH = "/api/auth/email-change/confirm";
    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String REFRESH_PATH = "/api/auth/refresh";

    private static final String JSON_FIELD_NEW_EMAIL = "newEmail";
    private static final String JSON_FIELD_CURRENT_PASSWORD = "currentPassword";
    private static final String JSON_FIELD_TOKEN = "token";
    private static final String JSON_FIELD_EMAIL = "email";
    private static final String JSON_FIELD_PASSWORD = "password";
    private static final String JSON_FIELD_DEVICE_LABEL = "deviceLabel";

    private static final String USER_GROUP_NAME_PREFIX = "Email change test group ";
    private static final String USER_NAME = "Samuel";
    private static final String USER_EMAIL_DOMAIN = "example.com";
    private static final String DEFAULT_PASSWORD = "VeryStrongPassword-2026-SerenityLine!";
    private static final String WRONG_PASSWORD = "WrongPassword-2026-SerenityLine!";
    private static final String DEVICE_LABEL = "Email change integration test device";
    private static final String INVALID_EMAIL = "not-an-email";
    private static final String INVALID_CONFIRMATION_TOKEN = "invalid-token";

    private static final UserRole USER_ROLE = UserRole.OWNER;
    private static final UserPlatformRole USER_PLATFORM_ROLE = UserPlatformRole.USER;
    private static final String PREFERRED_LOCALE = "it-IT";
    private static final PreferredTheme PREFERRED_THEME = PreferredTheme.DEFAULT;
    private static final boolean WANTS_INVOICE = false;
    private static final boolean PAYMENT_EMAIL_REMINDERS_ENABLED = true;
    private static final boolean USER_ENABLED = true;
    private static final Long DEFAULT_TOKEN_VERSION = 0L;

    private static final int UNIQUE_LABEL_MAX_LENGTH = 20;
    private static final int UNIQUE_SUFFIX_LENGTH = 12;
    private static final int TOKEN_EXPIRED_MINUTES_AGO = 1;

    private static final Pattern TOKEN_FRAGMENT_PATTERN = Pattern.compile("#token=([A-Za-z0-9_-]+)");
    private static final Pattern EMAIL_CHANGE_TOKEN_FRAGMENT_PATTERN =
            Pattern.compile("#token=([^\\s]+)");

    private static final Pattern EMAIL_CHANGE_TOKEN_TEXT_PATTERN =
            Pattern.compile("(?i)(?:confirmation token|token di conferma):\\s*([^\\s]+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthActionTokenRepository authActionTokenRepository;

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

    @Autowired
    private EmailOutboxEncryptionService emailOutboxEncryptionService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private AuthCookieService authCookieService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private TransactionTemplate transactionTemplate;

    private static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private static String uniqueEmail(String label) {
        String normalizedLabel = label
                .replace("-", "")
                .toLowerCase(Locale.ROOT);

        if (normalizedLabel.length() > UNIQUE_LABEL_MAX_LENGTH) {
            normalizedLabel = normalizedLabel.substring(0, UNIQUE_LABEL_MAX_LENGTH);
        }

        String uniqueSuffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, UNIQUE_SUFFIX_LENGTH);

        return "ec-" + normalizedLabel + "-" + uniqueSuffix + "@" + USER_EMAIL_DOMAIN;
    }

    private static String extractTokenFromBody(String body) {
        if (body == null || body.isBlank()) {
            throw new AssertionError("Email body is empty");
        }

        Matcher fragmentMatcher = EMAIL_CHANGE_TOKEN_FRAGMENT_PATTERN.matcher(body);

        if (fragmentMatcher.find()) {
            return fragmentMatcher.group(1).trim();
        }

        Matcher textMatcher = EMAIL_CHANGE_TOKEN_TEXT_PATTERN.matcher(body);

        if (textMatcher.find()) {
            return textMatcher.group(1).trim();
        }

        throw new AssertionError("Email change token not found in email body:\n" + body);
    }

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    void requestEmailChangeShouldRequireAuthentication() throws Exception {
        String newEmail = uniqueEmail("request-requires-auth-new");
        String requestBody = emailChangeRequestJson(newEmail, DEFAULT_PASSWORD);

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestEmailChangeShouldRejectMissingNewEmail() throws Exception {
        String currentEmail = uniqueEmail("missing-new-email-current");
        User user = createVerifiedUser(currentEmail);
        String accessToken = accessTokenFor(user);
        String requestBody = emailChangeRequestJsonWithoutNewEmail(DEFAULT_PASSWORD);

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        User userAfterRequest = userById(user.getUserId());

        assertThat(userAfterRequest.getEmail())
                .isEqualTo(currentEmail);
    }

    @Test
    void requestEmailChangeShouldRejectMissingCurrentPassword() throws Exception {
        String currentEmail = uniqueEmail("missing-password-current");
        String newEmail = uniqueEmail("missing-password-new");
        User user = createVerifiedUser(currentEmail);
        String accessToken = accessTokenFor(user);
        String requestBody = emailChangeRequestJsonWithoutCurrentPassword(newEmail);

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        User userAfterRequest = userById(user.getUserId());

        assertThat(userAfterRequest.getEmail())
                .isEqualTo(currentEmail);
    }

    @Test
    void requestEmailChangeShouldRejectInvalidNewEmail() throws Exception {
        String currentEmail = uniqueEmail("invalid-new-email-current");
        User user = createVerifiedUser(currentEmail);
        String accessToken = accessTokenFor(user);
        String requestBody = emailChangeRequestJson(INVALID_EMAIL, DEFAULT_PASSWORD);

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        User userAfterRequest = userById(user.getUserId());

        assertThat(userAfterRequest.getEmail())
                .isEqualTo(currentEmail);
    }

    @Test
    void requestEmailChangeShouldRejectWrongCurrentPassword() throws Exception {
        String currentEmail = uniqueEmail("wrong-password-current");
        String newEmail = uniqueEmail("wrong-password-new");
        User user = createVerifiedUser(currentEmail);
        String accessToken = accessTokenFor(user);
        String requestBody = emailChangeRequestJson(newEmail, WRONG_PASSWORD);

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        List<AuthActionToken> emailChangeTokens = emailChangeTokensForTargetValue(newEmail);

        assertThat(emailChangeTokens)
                .isEmpty();
    }

    @Test
    void requestEmailChangeShouldRejectSameEmailAfterNormalization() throws Exception {
        String currentEmail = uniqueEmail("same-email-current");
        String rawSameEmail = currentEmail.toUpperCase(Locale.ROOT);
        User user = createVerifiedUser(currentEmail);
        String accessToken = accessTokenFor(user);
        String requestBody = emailChangeRequestJson(rawSameEmail, DEFAULT_PASSWORD);

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        User userAfterRequest = userById(user.getUserId());

        assertThat(userAfterRequest.getEmail())
                .isEqualTo(currentEmail);
    }

    @Test
    void requestEmailChangeShouldRejectEmailAlreadyInUse() throws Exception {
        String currentEmail = uniqueEmail("already-used-current");
        String alreadyUsedEmail = uniqueEmail("already-used-target");
        User user = createVerifiedUser(currentEmail);
        createVerifiedUser(alreadyUsedEmail);
        String accessToken = accessTokenFor(user);
        String requestBody = emailChangeRequestJson(alreadyUsedEmail, DEFAULT_PASSWORD);

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        User userAfterRequest = userById(user.getUserId());

        assertThat(userAfterRequest.getEmail())
                .isEqualTo(currentEmail);
    }

    @Test
    void requestEmailChangeShouldCreatePendingTokenAndOutboxEmailToNewEmail() throws Exception {
        String currentEmail = uniqueEmail("request-current");
        String normalizedNewEmail = uniqueEmail("request-new");
        String rawNewEmail = normalizedNewEmail.toUpperCase(Locale.ROOT);
        User user = createVerifiedUser(currentEmail);
        String accessToken = accessTokenFor(user);
        String requestBody = emailChangeRequestJson(rawNewEmail, DEFAULT_PASSWORD);

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isAccepted());

        List<AuthActionToken> emailChangeTokens = emailChangeTokensForTargetValue(normalizedNewEmail);
        EmailOutbox emailOutbox = latestEmailOutbox(
                EmailOutboxType.EMAIL_CHANGE_CONFIRMATION,
                normalizedNewEmail
        );
        String emailBody = decryptTextBody(emailOutbox);

        assertThat(emailChangeTokens)
                .hasSize(1);

        assertThat(emailChangeTokens.getFirst().getAuthActionTokenType())
                .isEqualTo(AuthActionTokenType.EMAIL_CHANGE_CONFIRMATION);

        assertThat(emailChangeTokens.getFirst().getAuthActionTargetValue())
                .isEqualTo(normalizedNewEmail);

        assertThat(emailChangeTokens.getFirst().isPending())
                .isTrue();

        assertThat(emailOutbox.getEmailStatus())
                .isEqualTo(EmailOutboxStatus.PENDING);

        assertThat(emailOutbox.getRecipientEmail())
                .isEqualTo(normalizedNewEmail);

        assertThat(emailBody)
                .contains(currentEmail)
                .contains(normalizedNewEmail)
                .contains("#token=");
    }

    @Test
    void requestEmailChangeShouldRevokePreviousPendingRequestAndCancelPreviousEmail() throws Exception {
        String currentEmail = uniqueEmail("replace-current");
        String firstNewEmail = uniqueEmail("replace-first-new");
        String secondNewEmail = uniqueEmail("replace-second-new");
        User user = createVerifiedUser(currentEmail);
        String accessToken = accessTokenFor(user);
        String firstRequestBody = emailChangeRequestJson(firstNewEmail, DEFAULT_PASSWORD);
        String secondRequestBody = emailChangeRequestJson(secondNewEmail, DEFAULT_PASSWORD);

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstRequestBody))
                .andExpect(status().isAccepted());

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondRequestBody))
                .andExpect(status().isAccepted());

        List<AuthActionToken> firstTokens = emailChangeTokensForTargetValue(firstNewEmail);
        List<AuthActionToken> secondTokens = emailChangeTokensForTargetValue(secondNewEmail);
        EmailOutbox firstEmailOutbox = latestEmailOutbox(
                EmailOutboxType.EMAIL_CHANGE_CONFIRMATION,
                firstNewEmail
        );
        EmailOutbox secondEmailOutbox = latestEmailOutbox(
                EmailOutboxType.EMAIL_CHANGE_CONFIRMATION,
                secondNewEmail
        );

        assertThat(firstTokens)
                .hasSize(1);

        assertThat(firstTokens.getFirst().isRevoked())
                .isTrue();

        assertThat(secondTokens)
                .hasSize(1);

        assertThat(secondTokens.getFirst().isPending())
                .isTrue();

        assertThat(firstEmailOutbox.getEmailStatus())
                .isEqualTo(EmailOutboxStatus.CANCELLED);

        assertThat(secondEmailOutbox.getEmailStatus())
                .isEqualTo(EmailOutboxStatus.PENDING);
    }

    @Test
    void loginShouldKeepUsingOldEmailUntilEmailChangeIsConfirmed() throws Exception {
        String currentEmail = uniqueEmail("before-confirm-current");
        String newEmail = uniqueEmail("before-confirm-new");
        User user = createVerifiedUser(currentEmail);
        String accessToken = accessTokenFor(user);
        String requestBody = emailChangeRequestJson(newEmail, DEFAULT_PASSWORD);

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isAccepted());

        performLogin(currentEmail, DEFAULT_PASSWORD)
                .andExpect(status().isOk());

        performLogin(newEmail, DEFAULT_PASSWORD)
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmEmailChangeShouldRejectMissingToken() throws Exception {
        String requestBody = emailChangeConfirmJsonWithoutToken();

        mockMvc.perform(post(EMAIL_CHANGE_CONFIRM_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmEmailChangeShouldRejectInvalidToken() throws Exception {
        String currentEmail = uniqueEmail("invalid-token-current");
        User user = createVerifiedUser(currentEmail);
        String confirmBody = emailChangeConfirmJson(INVALID_CONFIRMATION_TOKEN);

        mockMvc.perform(post(EMAIL_CHANGE_CONFIRM_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isBadRequest());

        User userAfterConfirm = userById(user.getUserId());

        assertThat(userAfterConfirm.getEmail())
                .isEqualTo(currentEmail);
    }

    @Test
    void confirmEmailChangeShouldRejectExpiredTokenWithoutChangingEmail() throws Exception {
        String currentEmail = uniqueEmail("expired-current");
        String newEmail = uniqueEmail("expired-new");
        User user = createVerifiedUser(currentEmail);
        String accessToken = accessTokenFor(user);
        String requestBody = emailChangeRequestJson(newEmail, DEFAULT_PASSWORD);

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isAccepted());

        String confirmationToken = extractLatestEmailChangeTokenFromOutbox(newEmail);

        expireLatestEmailChangeToken(newEmail);

        mockMvc.perform(post(EMAIL_CHANGE_CONFIRM_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeConfirmJson(confirmationToken)))
                .andExpect(status().isBadRequest());

        User userAfterConfirm = userById(user.getUserId());

        assertThat(userAfterConfirm.getEmail())
                .isEqualTo(currentEmail);
    }

    @Test
    void confirmEmailChangeShouldChangeEmailInvalidateOldAccessTokenAndRevokeRefreshToken() throws Exception {
        String currentEmail = uniqueEmail("confirm-current");
        String newEmail = uniqueEmail("confirm-new");
        User user = createVerifiedUser(currentEmail);
        LoginSession loginSession = login(currentEmail, DEFAULT_PASSWORD);
        Long tokenVersionBeforeConfirm = user.getTokenVersion();
        String requestBody = emailChangeRequestJson(newEmail, DEFAULT_PASSWORD);

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(loginSession.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isAccepted());

        String confirmationToken = extractLatestEmailChangeTokenFromOutbox(newEmail);
        String confirmBody = emailChangeConfirmJson(confirmationToken);

        mockMvc.perform(post(EMAIL_CHANGE_CONFIRM_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(authCookieService.refreshCookieName())));

        User userAfterConfirm = userById(user.getUserId());
        List<AuthActionToken> emailChangeTokens = emailChangeTokensForTargetValue(newEmail);

        assertThat(userAfterConfirm.getEmail())
                .isEqualTo(newEmail);

        assertThat(userAfterConfirm.getTokenVersion())
                .isGreaterThan(tokenVersionBeforeConfirm);

        assertThat(emailChangeTokens)
                .hasSize(1);

        assertThat(emailChangeTokens.getFirst().isUsed())
                .isTrue();

        mockMvc.perform(get(API_ME)
                        .header(HttpHeaders.AUTHORIZATION, bearer(loginSession.accessToken())))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(REFRESH_PATH)
                        .cookie(loginSession.refreshCookie()))
                .andExpect(status().isUnauthorized());

        performLogin(currentEmail, DEFAULT_PASSWORD)
                .andExpect(status().isBadRequest());

        performLogin(newEmail, DEFAULT_PASSWORD)
                .andExpect(status().isOk());
    }

    @Test
    void confirmEmailChangeShouldRejectTokenReuse() throws Exception {
        String currentEmail = uniqueEmail("reuse-current");
        String newEmail = uniqueEmail("reuse-new");
        User user = createVerifiedUser(currentEmail);
        String accessToken = accessTokenFor(user);
        String requestBody = emailChangeRequestJson(newEmail, DEFAULT_PASSWORD);

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isAccepted());

        String confirmationToken = extractLatestEmailChangeTokenFromOutbox(newEmail);
        String confirmBody = emailChangeConfirmJson(confirmationToken);

        mockMvc.perform(post(EMAIL_CHANGE_CONFIRM_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(EMAIL_CHANGE_CONFIRM_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmEmailChangeShouldRejectIfTargetEmailWasTakenAfterRequest() throws Exception {
        String currentEmail = uniqueEmail("taken-after-request-current");
        String newEmail = uniqueEmail("taken-after-request-new");
        User user = createVerifiedUser(currentEmail);
        String accessToken = accessTokenFor(user);
        String requestBody = emailChangeRequestJson(newEmail, DEFAULT_PASSWORD);

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isAccepted());

        String confirmationToken = extractLatestEmailChangeTokenFromOutbox(newEmail);
        String confirmBody = emailChangeConfirmJson(confirmationToken);

        createVerifiedUser(newEmail);

        mockMvc.perform(post(EMAIL_CHANGE_CONFIRM_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isBadRequest());

        User userAfterConfirm = userById(user.getUserId());

        assertThat(userAfterConfirm.getEmail())
                .isEqualTo(currentEmail);
    }

    @Test
    void requestEmailChangeShouldRejectSoftDeletedUser() throws Exception {
        String currentEmail = uniqueEmail("request-soft-deleted-current");
        String newEmail = uniqueEmail("request-soft-deleted-new");
        User user = createVerifiedUser(currentEmail);
        String accessToken = accessTokenFor(user);

        markUserAsSoftDeleted(user.getUserId());

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeRequestJson(newEmail, DEFAULT_PASSWORD)))
                .andExpect(status().isUnauthorized());

        User userAfterRequest = userById(user.getUserId());

        assertThat(userAfterRequest.getEmail())
                .isEqualTo(currentEmail);

        assertThat(emailChangeTokensForTargetValue(newEmail))
                .isEmpty();
    }

    @Test
    void requestEmailChangeShouldRejectDisabledUser() throws Exception {
        String currentEmail = uniqueEmail("request-disabled-current");
        String newEmail = uniqueEmail("request-disabled-new");
        User user = createVerifiedUser(currentEmail);
        String accessToken = accessTokenFor(user);

        disableUser(user.getUserId());

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeRequestJson(newEmail, DEFAULT_PASSWORD)))
                .andExpect(status().isUnauthorized());

        User userAfterRequest = userById(user.getUserId());

        assertThat(userAfterRequest.getEmail())
                .isEqualTo(currentEmail);

        assertThat(emailChangeTokensForTargetValue(newEmail))
                .isEmpty();
    }

    @Test
    void confirmEmailChangeShouldRejectIfUserWasSoftDeletedAfterRequest() throws Exception {
        String currentEmail = uniqueEmail("confirm-soft-deleted-current");
        String newEmail = uniqueEmail("confirm-soft-deleted-new");
        User user = createVerifiedUser(currentEmail);
        String accessToken = accessTokenFor(user);

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeRequestJson(newEmail, DEFAULT_PASSWORD)))
                .andExpect(status().isAccepted());

        String confirmationToken = extractLatestEmailChangeTokenFromOutbox(newEmail);

        markUserAsSoftDeleted(user.getUserId());

        mockMvc.perform(post(EMAIL_CHANGE_CONFIRM_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeConfirmJson(confirmationToken)))
                .andExpect(status().isBadRequest());

        User userAfterConfirm = userById(user.getUserId());
        List<AuthActionToken> emailChangeTokens = emailChangeTokensForTargetValue(newEmail);

        assertThat(userAfterConfirm.getEmail())
                .isEqualTo(currentEmail);

        assertThat(emailChangeTokens)
                .hasSize(1);

        assertThat(emailChangeTokens.getFirst().isPending())
                .isTrue();
    }

    @Test
    void confirmEmailChangeShouldRejectIfUserWasDisabledAfterRequest() throws Exception {
        String currentEmail = uniqueEmail("confirm-disabled-current");
        String newEmail = uniqueEmail("confirm-disabled-new");
        User user = createVerifiedUser(currentEmail);
        String accessToken = accessTokenFor(user);

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeRequestJson(newEmail, DEFAULT_PASSWORD)))
                .andExpect(status().isAccepted());

        String confirmationToken = extractLatestEmailChangeTokenFromOutbox(newEmail);

        disableUser(user.getUserId());

        mockMvc.perform(post(EMAIL_CHANGE_CONFIRM_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeConfirmJson(confirmationToken)))
                .andExpect(status().isBadRequest());

        User userAfterConfirm = userById(user.getUserId());
        List<AuthActionToken> emailChangeTokens = emailChangeTokensForTargetValue(newEmail);

        assertThat(userAfterConfirm.getEmail())
                .isEqualTo(currentEmail);

        assertThat(emailChangeTokens)
                .hasSize(1);

        assertThat(emailChangeTokens.getFirst().isPending())
                .isTrue();
    }

    @Test
    void confirmEmailChangeShouldCreateNotificationEmailsToOldAndNewEmail() throws Exception {
        String currentEmail = uniqueEmail("notification-current");
        String newEmail = uniqueEmail("notification-new");
        User user = createVerifiedUser(currentEmail);
        String accessToken = accessTokenFor(user);
        String requestBody = emailChangeRequestJson(newEmail, DEFAULT_PASSWORD);

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isAccepted());

        String confirmationToken = extractLatestEmailChangeTokenFromOutbox(newEmail);

        mockMvc.perform(post(EMAIL_CHANGE_CONFIRM_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeConfirmJson(confirmationToken)))
                .andExpect(status().isNoContent());

        EmailOutbox oldEmailNotification = latestEmailOutbox(
                EmailOutboxType.EMAIL_CHANGE_NOTIFICATION,
                currentEmail
        );

        EmailOutbox newEmailNotification = latestEmailOutbox(
                EmailOutboxType.EMAIL_CHANGE_NOTIFICATION,
                newEmail
        );

        String oldEmailNotificationBody = decryptTextBody(oldEmailNotification);
        String newEmailNotificationBody = decryptTextBody(newEmailNotification);

        assertThat(oldEmailNotification.getEmailStatus())
                .isEqualTo(EmailOutboxStatus.PENDING);

        assertThat(oldEmailNotification.getRecipientEmail())
                .isEqualTo(currentEmail);

        assertThat(oldEmailNotificationBody)
                .contains(currentEmail)
                .contains(newEmail)
                .doesNotContain("#token=");

        assertThat(newEmailNotification.getEmailStatus())
                .isEqualTo(EmailOutboxStatus.PENDING);

        assertThat(newEmailNotification.getRecipientEmail())
                .isEqualTo(newEmail);

        assertThat(newEmailNotificationBody)
                .contains(currentEmail)
                .contains(newEmail)
                .doesNotContain("#token=");
    }

    @Test
    void requestEmailChangeShouldCreateConfirmationEmailButNoNotificationEmails() throws Exception {
        String currentEmail = uniqueEmail("request-no-notification-current");
        String newEmail = uniqueEmail("request-no-notification-new");
        User user = createVerifiedUser(currentEmail);
        String accessToken = accessTokenFor(user);

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeRequestJson(newEmail, DEFAULT_PASSWORD)))
                .andExpect(status().isAccepted());

        List<EmailOutbox> confirmationEmails = emailOutboxes(
                EmailOutboxType.EMAIL_CHANGE_CONFIRMATION,
                newEmail
        );

        assertThat(confirmationEmails)
                .hasSize(1);

        EmailOutbox confirmationEmail = confirmationEmails.getFirst();
        String confirmationBody = decryptTextBody(confirmationEmail);

        assertThat(confirmationEmail.getEmailStatus())
                .isEqualTo(EmailOutboxStatus.PENDING);

        assertThat(confirmationEmail.isDeleteBodyAfterSend())
                .isTrue();

        assertThat(confirmationEmail.getEmailBodyDeletedAt())
                .isNull();

        assertThat(confirmationBody)
                .contains(currentEmail)
                .contains(newEmail)
                .contains("/change-email/confirm")
                .contains("#token=");

        assertThat(emailOutboxes(EmailOutboxType.EMAIL_CHANGE_NOTIFICATION, currentEmail))
                .isEmpty();

        assertThat(emailOutboxes(EmailOutboxType.EMAIL_CHANGE_NOTIFICATION, newEmail))
                .isEmpty();
    }

    @Test
    void requestEmailChangeShouldCancelPreviousPendingConfirmationEmailAndPreserveBody() throws Exception {
        String currentEmail = uniqueEmail("request-cancel-current");
        String firstNewEmail = uniqueEmail("request-cancel-first-new");
        String secondNewEmail = uniqueEmail("request-cancel-second-new");
        User user = createVerifiedUser(currentEmail);
        String accessToken = accessTokenFor(user);

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeRequestJson(firstNewEmail, DEFAULT_PASSWORD)))
                .andExpect(status().isAccepted());

        EmailOutbox firstConfirmationBeforeCancel = latestEmailOutbox(
                EmailOutboxType.EMAIL_CHANGE_CONFIRMATION,
                firstNewEmail
        );

        UUID firstConfirmationEmailId = firstConfirmationBeforeCancel.getEmailOutboxId();
        String firstConfirmationBodyBeforeCancel = decryptTextBody(firstConfirmationBeforeCancel);

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeRequestJson(secondNewEmail, DEFAULT_PASSWORD)))
                .andExpect(status().isAccepted());

        EmailOutbox firstConfirmationAfterCancel = emailOutboxById(firstConfirmationEmailId);
        EmailOutbox secondConfirmation = latestEmailOutbox(
                EmailOutboxType.EMAIL_CHANGE_CONFIRMATION,
                secondNewEmail
        );

        assertThat(firstConfirmationAfterCancel.getEmailStatus())
                .isEqualTo(EmailOutboxStatus.CANCELLED);

        assertThat(firstConfirmationAfterCancel.getEmailCancelledAt())
                .isNotNull();

        assertThat(firstConfirmationAfterCancel.getEmailBodyDeletedAt())
                .isNull();

        assertThat(decryptTextBody(firstConfirmationAfterCancel))
                .isEqualTo(firstConfirmationBodyBeforeCancel);

        assertThat(secondConfirmation.getEmailStatus())
                .isEqualTo(EmailOutboxStatus.PENDING);

        assertThat(decryptTextBody(secondConfirmation))
                .contains(currentEmail)
                .contains(secondNewEmail)
                .contains("#token=");
    }

    @Test
    void requestEmailChangeShouldRevokePreviousPendingEmailChangeToken() throws Exception {
        String currentEmail = uniqueEmail("request-revoke-token-current");
        String firstNewEmail = uniqueEmail("request-revoke-token-first-new");
        String secondNewEmail = uniqueEmail("request-revoke-token-second-new");
        User user = createVerifiedUser(currentEmail);
        String accessToken = accessTokenFor(user);

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeRequestJson(firstNewEmail, DEFAULT_PASSWORD)))
                .andExpect(status().isAccepted());

        String firstConfirmationToken = extractLatestEmailChangeTokenFromOutbox(firstNewEmail);

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeRequestJson(secondNewEmail, DEFAULT_PASSWORD)))
                .andExpect(status().isAccepted());

        mockMvc.perform(post(EMAIL_CHANGE_CONFIRM_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeConfirmJson(firstConfirmationToken)))
                .andExpect(status().isBadRequest());

        User userAfterOldConfirmAttempt = userById(user.getUserId());

        assertThat(userAfterOldConfirmAttempt.getEmail())
                .isEqualTo(currentEmail);

        assertThat(emailOutboxes(EmailOutboxType.EMAIL_CHANGE_NOTIFICATION, currentEmail))
                .isEmpty();

        assertThat(emailOutboxes(EmailOutboxType.EMAIL_CHANGE_NOTIFICATION, firstNewEmail))
                .isEmpty();
    }

    @Test
    void confirmEmailChangeShouldCancelPendingConfirmationEmailPreserveBodyAndCreateTwoNotifications() throws Exception {
        String currentEmail = uniqueEmail("confirm-cancel-current");
        String newEmail = uniqueEmail("confirm-cancel-new");
        User user = createVerifiedUser(currentEmail);
        String accessToken = accessTokenFor(user);

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeRequestJson(newEmail, DEFAULT_PASSWORD)))
                .andExpect(status().isAccepted());

        EmailOutbox confirmationBeforeConfirm = latestEmailOutbox(
                EmailOutboxType.EMAIL_CHANGE_CONFIRMATION,
                newEmail
        );

        UUID confirmationEmailId = confirmationBeforeConfirm.getEmailOutboxId();
        String confirmationBodyBeforeConfirm = decryptTextBody(confirmationBeforeConfirm);
        String confirmationToken = extractTokenFromBody(confirmationBodyBeforeConfirm);

        mockMvc.perform(post(EMAIL_CHANGE_CONFIRM_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeConfirmJson(confirmationToken)))
                .andExpect(status().isNoContent());

        EmailOutbox confirmationAfterConfirm = emailOutboxById(confirmationEmailId);
        List<EmailOutbox> oldEmailNotifications = emailOutboxes(
                EmailOutboxType.EMAIL_CHANGE_NOTIFICATION,
                currentEmail
        );
        List<EmailOutbox> newEmailNotifications = emailOutboxes(
                EmailOutboxType.EMAIL_CHANGE_NOTIFICATION,
                newEmail
        );

        assertThat(confirmationAfterConfirm.getEmailStatus())
                .isEqualTo(EmailOutboxStatus.CANCELLED);

        assertThat(confirmationAfterConfirm.getEmailCancelledAt())
                .isNotNull();

        assertThat(confirmationAfterConfirm.getEmailBodyDeletedAt())
                .isNull();

        assertThat(decryptTextBody(confirmationAfterConfirm))
                .isEqualTo(confirmationBodyBeforeConfirm);

        assertThat(oldEmailNotifications)
                .hasSize(1);

        assertThat(newEmailNotifications)
                .hasSize(1);

        assertThat(oldEmailNotifications.getFirst().getEmailStatus())
                .isEqualTo(EmailOutboxStatus.PENDING);

        assertThat(newEmailNotifications.getFirst().getEmailStatus())
                .isEqualTo(EmailOutboxStatus.PENDING);

        assertThat(oldEmailNotifications.getFirst().isDeleteBodyAfterSend())
                .isTrue();

        assertThat(newEmailNotifications.getFirst().isDeleteBodyAfterSend())
                .isTrue();
    }

    @Test
    void confirmEmailChangeNotificationEmailsShouldNotContainConfirmationToken() throws Exception {
        String currentEmail = uniqueEmail("notification-token-current");
        String newEmail = uniqueEmail("notification-token-new");
        User user = createVerifiedUser(currentEmail);
        String accessToken = accessTokenFor(user);

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeRequestJson(newEmail, DEFAULT_PASSWORD)))
                .andExpect(status().isAccepted());

        String confirmationToken = extractLatestEmailChangeTokenFromOutbox(newEmail);

        mockMvc.perform(post(EMAIL_CHANGE_CONFIRM_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeConfirmJson(confirmationToken)))
                .andExpect(status().isNoContent());

        EmailOutbox oldEmailNotification = latestEmailOutbox(
                EmailOutboxType.EMAIL_CHANGE_NOTIFICATION,
                currentEmail
        );

        EmailOutbox newEmailNotification = latestEmailOutbox(
                EmailOutboxType.EMAIL_CHANGE_NOTIFICATION,
                newEmail
        );

        String oldEmailNotificationBody = decryptTextBody(oldEmailNotification);
        String newEmailNotificationBody = decryptTextBody(newEmailNotification);

        assertThat(oldEmailNotificationBody)
                .contains(currentEmail)
                .contains(newEmail)
                .doesNotContain("#token=")
                .doesNotContain(confirmationToken);

        assertThat(newEmailNotificationBody)
                .contains(currentEmail)
                .contains(newEmail)
                .doesNotContain("#token=")
                .doesNotContain(confirmationToken);
    }

    @Test
    void confirmExpiredEmailChangeShouldNotCancelConfirmationEmailAndShouldNotCreateNotificationEmails() throws Exception {
        String currentEmail = uniqueEmail("expired-notification-current");
        String newEmail = uniqueEmail("expired-notification-new");
        User user = createVerifiedUser(currentEmail);
        String accessToken = accessTokenFor(user);

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeRequestJson(newEmail, DEFAULT_PASSWORD)))
                .andExpect(status().isAccepted());

        EmailOutbox confirmationBeforeConfirm = latestEmailOutbox(
                EmailOutboxType.EMAIL_CHANGE_CONFIRMATION,
                newEmail
        );

        UUID confirmationEmailId = confirmationBeforeConfirm.getEmailOutboxId();
        String confirmationToken = extractLatestEmailChangeTokenFromOutbox(newEmail);

        expireLatestEmailChangeToken(newEmail);

        mockMvc.perform(post(EMAIL_CHANGE_CONFIRM_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeConfirmJson(confirmationToken)))
                .andExpect(status().isBadRequest());

        EmailOutbox confirmationAfterConfirmAttempt = emailOutboxById(confirmationEmailId);
        User userAfterConfirmAttempt = userById(user.getUserId());

        assertThat(userAfterConfirmAttempt.getEmail())
                .isEqualTo(currentEmail);

        assertThat(confirmationAfterConfirmAttempt.getEmailStatus())
                .isEqualTo(EmailOutboxStatus.PENDING);

        assertThat(confirmationAfterConfirmAttempt.getEmailCancelledAt())
                .isNull();

        assertThat(emailOutboxes(EmailOutboxType.EMAIL_CHANGE_NOTIFICATION, currentEmail))
                .isEmpty();

        assertThat(emailOutboxes(EmailOutboxType.EMAIL_CHANGE_NOTIFICATION, newEmail))
                .isEmpty();
    }

    @Test
    void confirmEmailChangeTokenReuseShouldNotCreateDuplicateNotificationEmails() throws Exception {
        String currentEmail = uniqueEmail("reuse-notification-current");
        String newEmail = uniqueEmail("reuse-notification-new");
        User user = createVerifiedUser(currentEmail);
        String accessToken = accessTokenFor(user);

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeRequestJson(newEmail, DEFAULT_PASSWORD)))
                .andExpect(status().isAccepted());

        String confirmationToken = extractLatestEmailChangeTokenFromOutbox(newEmail);
        String confirmBody = emailChangeConfirmJson(confirmationToken);

        mockMvc.perform(post(EMAIL_CHANGE_CONFIRM_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(EMAIL_CHANGE_CONFIRM_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isBadRequest());

        assertThat(emailOutboxes(EmailOutboxType.EMAIL_CHANGE_NOTIFICATION, currentEmail))
                .hasSize(1);

        assertThat(emailOutboxes(EmailOutboxType.EMAIL_CHANGE_NOTIFICATION, newEmail))
                .hasSize(1);
    }

    @Test
    void confirmEmailChangeShouldNotCancelConfirmationOrCreateNotificationsIfTargetEmailWasTakenAfterRequest() throws Exception {
        String currentEmail = uniqueEmail("taken-notification-current");
        String newEmail = uniqueEmail("taken-notification-new");
        User user = createVerifiedUser(currentEmail);
        String accessToken = accessTokenFor(user);

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeRequestJson(newEmail, DEFAULT_PASSWORD)))
                .andExpect(status().isAccepted());

        EmailOutbox confirmationBeforeConfirm = latestEmailOutbox(
                EmailOutboxType.EMAIL_CHANGE_CONFIRMATION,
                newEmail
        );

        UUID confirmationEmailId = confirmationBeforeConfirm.getEmailOutboxId();
        String confirmationToken = extractLatestEmailChangeTokenFromOutbox(newEmail);

        createVerifiedUser(newEmail);

        mockMvc.perform(post(EMAIL_CHANGE_CONFIRM_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeConfirmJson(confirmationToken)))
                .andExpect(status().isBadRequest());

        EmailOutbox confirmationAfterConfirmAttempt = emailOutboxById(confirmationEmailId);
        User userAfterConfirmAttempt = userById(user.getUserId());

        assertThat(userAfterConfirmAttempt.getEmail())
                .isEqualTo(currentEmail);

        assertThat(confirmationAfterConfirmAttempt.getEmailStatus())
                .isEqualTo(EmailOutboxStatus.PENDING);

        assertThat(confirmationAfterConfirmAttempt.getEmailCancelledAt())
                .isNull();

        assertThat(emailOutboxes(EmailOutboxType.EMAIL_CHANGE_NOTIFICATION, currentEmail))
                .isEmpty();

        assertThat(emailOutboxes(EmailOutboxType.EMAIL_CHANGE_NOTIFICATION, newEmail))
                .isEmpty();
    }

    @Test
    void confirmEmailChangeShouldNotCancelConfirmationOrCreateNotificationsIfUserWasSoftDeletedAfterRequest() throws Exception {
        String currentEmail = uniqueEmail("soft-deleted-notification-current");
        String newEmail = uniqueEmail("soft-deleted-notification-new");
        User user = createVerifiedUser(currentEmail);
        String accessToken = accessTokenFor(user);

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeRequestJson(newEmail, DEFAULT_PASSWORD)))
                .andExpect(status().isAccepted());

        EmailOutbox confirmationBeforeConfirm = latestEmailOutbox(
                EmailOutboxType.EMAIL_CHANGE_CONFIRMATION,
                newEmail
        );

        UUID confirmationEmailId = confirmationBeforeConfirm.getEmailOutboxId();
        String confirmationToken = extractLatestEmailChangeTokenFromOutbox(newEmail);

        markUserAsSoftDeleted(user.getUserId());

        mockMvc.perform(post(EMAIL_CHANGE_CONFIRM_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeConfirmJson(confirmationToken)))
                .andExpect(status().isBadRequest());

        EmailOutbox confirmationAfterConfirmAttempt = emailOutboxById(confirmationEmailId);
        User userAfterConfirmAttempt = userById(user.getUserId());

        assertThat(userAfterConfirmAttempt.getEmail())
                .isEqualTo(currentEmail);

        assertThat(confirmationAfterConfirmAttempt.getEmailStatus())
                .isEqualTo(EmailOutboxStatus.PENDING);

        assertThat(emailOutboxes(EmailOutboxType.EMAIL_CHANGE_NOTIFICATION, currentEmail))
                .isEmpty();

        assertThat(emailOutboxes(EmailOutboxType.EMAIL_CHANGE_NOTIFICATION, newEmail))
                .isEmpty();
    }

    @Test
    void confirmEmailChangeShouldNotCancelConfirmationOrCreateNotificationsIfUserWasDisabledAfterRequest() throws Exception {
        String currentEmail = uniqueEmail("disabled-notification-current");
        String newEmail = uniqueEmail("disabled-notification-new");
        User user = createVerifiedUser(currentEmail);
        String accessToken = accessTokenFor(user);

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeRequestJson(newEmail, DEFAULT_PASSWORD)))
                .andExpect(status().isAccepted());

        EmailOutbox confirmationBeforeConfirm = latestEmailOutbox(
                EmailOutboxType.EMAIL_CHANGE_CONFIRMATION,
                newEmail
        );

        UUID confirmationEmailId = confirmationBeforeConfirm.getEmailOutboxId();
        String confirmationToken = extractLatestEmailChangeTokenFromOutbox(newEmail);

        disableUser(user.getUserId());

        mockMvc.perform(post(EMAIL_CHANGE_CONFIRM_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeConfirmJson(confirmationToken)))
                .andExpect(status().isBadRequest());

        EmailOutbox confirmationAfterConfirmAttempt = emailOutboxById(confirmationEmailId);
        User userAfterConfirmAttempt = userById(user.getUserId());

        assertThat(userAfterConfirmAttempt.getEmail())
                .isEqualTo(currentEmail);

        assertThat(confirmationAfterConfirmAttempt.getEmailStatus())
                .isEqualTo(EmailOutboxStatus.PENDING);

        assertThat(emailOutboxes(EmailOutboxType.EMAIL_CHANGE_NOTIFICATION, currentEmail))
                .isEmpty();

        assertThat(emailOutboxes(EmailOutboxType.EMAIL_CHANGE_NOTIFICATION, newEmail))
                .isEmpty();
    }

    @Test
    void confirmEmailChangeShouldKeepAlreadySentConfirmationEmailSentAndCreateNotifications() throws Exception {
        String currentEmail = uniqueEmail("sent-confirm-current");
        String newEmail = uniqueEmail("sent-confirm-new");
        User user = createVerifiedUser(currentEmail);
        String accessToken = accessTokenFor(user);

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeRequestJson(newEmail, DEFAULT_PASSWORD)))
                .andExpect(status().isAccepted());

        EmailOutbox confirmationBeforeSend = latestEmailOutbox(
                EmailOutboxType.EMAIL_CHANGE_CONFIRMATION,
                newEmail
        );

        UUID confirmationEmailId = confirmationBeforeSend.getEmailOutboxId();
        String confirmationToken = extractTokenFromBody(decryptTextBody(confirmationBeforeSend));

        markEmailOutboxAsSent(confirmationEmailId);

        EmailOutbox confirmationAfterSend = emailOutboxById(confirmationEmailId);

        assertThat(confirmationAfterSend.getEmailStatus())
                .isEqualTo(EmailOutboxStatus.SENT);

        assertThat(confirmationAfterSend.getEmailSentAt())
                .isNotNull();

        assertThat(confirmationAfterSend.getEmailBodyDeletedAt())
                .isNotNull();

        assertThat(confirmationAfterSend.getBodyTextEncrypted())
                .isNull();

        mockMvc.perform(post(EMAIL_CHANGE_CONFIRM_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeConfirmJson(confirmationToken)))
                .andExpect(status().isNoContent());

        EmailOutbox confirmationAfterConfirm = emailOutboxById(confirmationEmailId);

        assertThat(confirmationAfterConfirm.getEmailStatus())
                .isEqualTo(EmailOutboxStatus.SENT);

        assertThat(confirmationAfterConfirm.getEmailCancelledAt())
                .isNull();

        assertThat(confirmationAfterConfirm.getEmailBodyDeletedAt())
                .isNotNull();

        assertThat(emailOutboxes(EmailOutboxType.EMAIL_CHANGE_NOTIFICATION, currentEmail))
                .hasSize(1);

        assertThat(emailOutboxes(EmailOutboxType.EMAIL_CHANGE_NOTIFICATION, newEmail))
                .hasSize(1);
    }

    @Test
    void requestEmailChangeShouldKeepAlreadySentConfirmationEmailSentButRevokePreviousToken() throws Exception {
        String currentEmail = uniqueEmail("sent-request-current");
        String firstNewEmail = uniqueEmail("sent-request-first-new");
        String secondNewEmail = uniqueEmail("sent-request-second-new");
        User user = createVerifiedUser(currentEmail);
        String accessToken = accessTokenFor(user);

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeRequestJson(firstNewEmail, DEFAULT_PASSWORD)))
                .andExpect(status().isAccepted());

        EmailOutbox firstConfirmationBeforeSend = latestEmailOutbox(
                EmailOutboxType.EMAIL_CHANGE_CONFIRMATION,
                firstNewEmail
        );

        UUID firstConfirmationEmailId = firstConfirmationBeforeSend.getEmailOutboxId();
        String firstConfirmationToken = extractTokenFromBody(decryptTextBody(firstConfirmationBeforeSend));

        markEmailOutboxAsSent(firstConfirmationEmailId);

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeRequestJson(secondNewEmail, DEFAULT_PASSWORD)))
                .andExpect(status().isAccepted());

        EmailOutbox firstConfirmationAfterSecondRequest = emailOutboxById(firstConfirmationEmailId);
        EmailOutbox secondConfirmation = latestEmailOutbox(
                EmailOutboxType.EMAIL_CHANGE_CONFIRMATION,
                secondNewEmail
        );

        assertThat(firstConfirmationAfterSecondRequest.getEmailStatus())
                .isEqualTo(EmailOutboxStatus.SENT);

        assertThat(firstConfirmationAfterSecondRequest.getEmailCancelledAt())
                .isNull();

        assertThat(firstConfirmationAfterSecondRequest.getEmailBodyDeletedAt())
                .isNotNull();

        assertThat(secondConfirmation.getEmailStatus())
                .isEqualTo(EmailOutboxStatus.PENDING);

        mockMvc.perform(post(EMAIL_CHANGE_CONFIRM_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeConfirmJson(firstConfirmationToken)))
                .andExpect(status().isBadRequest());

        User userAfterOldTokenConfirmAttempt = userById(user.getUserId());

        assertThat(userAfterOldTokenConfirmAttempt.getEmail())
                .isEqualTo(currentEmail);
    }

    @Test
    void confirmEmailChangeShouldCreateDifferentNotificationBodiesForOldAndNewEmail() throws Exception {
        String currentEmail = uniqueEmail("different-body-current");
        String newEmail = uniqueEmail("different-body-new");
        User user = createVerifiedUser(currentEmail);
        String accessToken = accessTokenFor(user);

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeRequestJson(newEmail, DEFAULT_PASSWORD)))
                .andExpect(status().isAccepted());

        String confirmationToken = extractLatestEmailChangeTokenFromOutbox(newEmail);

        mockMvc.perform(post(EMAIL_CHANGE_CONFIRM_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeConfirmJson(confirmationToken)))
                .andExpect(status().isNoContent());

        EmailOutbox oldEmailNotification = latestEmailOutbox(
                EmailOutboxType.EMAIL_CHANGE_NOTIFICATION,
                currentEmail
        );

        EmailOutbox newEmailNotification = latestEmailOutbox(
                EmailOutboxType.EMAIL_CHANGE_NOTIFICATION,
                newEmail
        );

        String oldBody = decryptTextBody(oldEmailNotification);
        String newBody = decryptTextBody(newEmailNotification);

        assertThat(oldEmailNotification.getRecipientEmail())
                .isEqualTo(currentEmail);

        assertThat(newEmailNotification.getRecipientEmail())
                .isEqualTo(newEmail);

        assertThat(oldBody)
                .contains(currentEmail)
                .contains(newEmail);

        assertThat(newBody)
                .contains(currentEmail)
                .contains(newEmail);

        assertThat(oldBody)
                .isNotEqualTo(newBody);
    }

    @Test
    void confirmEmailChangeShouldMarkActionTokenAsUsed() throws Exception {
        String currentEmail = uniqueEmail("token-used-current");
        String newEmail = uniqueEmail("token-used-new");
        User user = createVerifiedUser(currentEmail);
        String accessToken = accessTokenFor(user);

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeRequestJson(newEmail, DEFAULT_PASSWORD)))
                .andExpect(status().isAccepted());

        String confirmationToken = extractLatestEmailChangeTokenFromOutbox(newEmail);

        mockMvc.perform(post(EMAIL_CHANGE_CONFIRM_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeConfirmJson(confirmationToken)))
                .andExpect(status().isNoContent());

        List<AuthActionToken> tokens = emailChangeTokensForTargetValue(newEmail);

        assertThat(tokens)
                .hasSize(1);

        AuthActionToken token = tokens.getFirst();

        assertThat(token.getAuthActionUsedAt())
                .isNotNull();

        assertThat(token.getAuthActionRevokedAt())
                .isNull();
    }

    @Test
    void failedConfirmEmailChangeShouldNotMarkActionTokenAsUsed() throws Exception {
        String currentEmail = uniqueEmail("token-not-used-current");
        String newEmail = uniqueEmail("token-not-used-new");
        User user = createVerifiedUser(currentEmail);
        String accessToken = accessTokenFor(user);

        mockMvc.perform(post(EMAIL_CHANGE_REQUEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeRequestJson(newEmail, DEFAULT_PASSWORD)))
                .andExpect(status().isAccepted());

        String confirmationToken = extractLatestEmailChangeTokenFromOutbox(newEmail);

        createVerifiedUser(newEmail);

        mockMvc.perform(post(EMAIL_CHANGE_CONFIRM_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailChangeConfirmJson(confirmationToken)))
                .andExpect(status().isBadRequest());

        List<AuthActionToken> tokens = emailChangeTokensForTargetValue(newEmail);

        assertThat(tokens)
                .hasSize(1);

        AuthActionToken token = tokens.getFirst();

        assertThat(token.getAuthActionUsedAt())
                .isNull();

        assertThat(token.getAuthActionRevokedAt())
                .isNull();
    }

    private User createVerifiedUser(String email) {
        return transactionTemplate.execute(status -> {
            UserGroup userGroup = new UserGroup(USER_GROUP_NAME_PREFIX + UUID.randomUUID());
            String passwordHash = passwordEncoder.encode(DEFAULT_PASSWORD);

            User user = new User(
                    USER_NAME,
                    email,
                    userGroup,
                    USER_ROLE,
                    USER_PLATFORM_ROLE,
                    PREFERRED_LOCALE,
                    PREFERRED_THEME,
                    WANTS_INVOICE,
                    PAYMENT_EMAIL_REMINDERS_ENABLED,
                    passwordHash,
                    USER_ENABLED,
                    DEFAULT_TOKEN_VERSION
            );

            entityManager.persist(userGroup);
            entityManager.persist(user);
            entityManager.flush();

            return user;
        });
    }

    private User userById(UUID userId) {
        return transactionTemplate.execute(status -> userRepository.findById(userId)
                .orElseThrow());
    }

    private String accessTokenFor(User user) {
        return jwtTokenService.createAccessToken(user)
                .token();
    }

    private LoginSession login(String email, String password) throws Exception {
        MvcResult result = performLogin(email, password)
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = accessTokenFrom(result);
        Cookie refreshCookie = refreshCookieFrom(result);

        return new LoginSession(accessToken, refreshCookie);
    }

    private org.springframework.test.web.servlet.ResultActions performLogin(
            String email,
            String password
    ) throws Exception {
        String requestBody = loginRequestJson(email, password);

        return mockMvc.perform(post(LOGIN_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, PREFERRED_LOCALE)
                .header(HttpHeaders.USER_AGENT, "JUnit Browser")
                .content(requestBody));
    }

    private String extractLatestEmailChangeTokenFromOutbox(String recipientEmail) {
        EmailOutbox emailOutbox = latestEmailOutbox(
                EmailOutboxType.EMAIL_CHANGE_CONFIRMATION,
                recipientEmail
        );
        String textBody = decryptTextBody(emailOutbox);
        Matcher matcher = TOKEN_FRAGMENT_PATTERN.matcher(textBody);

        assertThat(matcher.find())
                .isTrue();

        return matcher.group(1);
    }

    private List<AuthActionToken> emailChangeTokensForTargetValue(String targetValue) {
        return transactionTemplate.execute(status -> authActionTokenRepository.findAll()
                .stream()
                .filter(token -> token.getAuthActionTokenType() == AuthActionTokenType.EMAIL_CHANGE_CONFIRMATION)
                .filter(token -> targetValue.equals(token.getAuthActionTargetValue()))
                .sorted(Comparator.comparing(AuthActionToken::getAuthActionCreatedAt))
                .toList());
    }

    private EmailOutbox latestEmailOutbox(
            EmailOutboxType emailType,
            String recipientEmail
    ) {
        return transactionTemplate.execute(status -> emailOutboxRepository.findAll()
                .stream()
                .filter(emailOutbox -> emailOutbox.getEmailType() == emailType)
                .filter(emailOutbox -> recipientEmail.equals(emailOutbox.getRecipientEmail()))
                .max(Comparator.comparing(EmailOutbox::getEmailCreatedAt))
                .orElseThrow());
    }

    private void expireLatestEmailChangeToken(String targetValue) {
        transactionTemplate.executeWithoutResult(status -> {
            AuthActionToken token = authActionTokenRepository.findAll()
                    .stream()
                    .filter(actionToken -> actionToken.getAuthActionTokenType() == AuthActionTokenType.EMAIL_CHANGE_CONFIRMATION)
                    .filter(actionToken -> targetValue.equals(actionToken.getAuthActionTargetValue()))
                    .max(Comparator.comparing(AuthActionToken::getAuthActionCreatedAt))
                    .orElseThrow();

            OffsetDateTime createdAt = OffsetDateTime.now().minusMinutes(2);
            OffsetDateTime expiredAt = OffsetDateTime.now().minusMinutes(1);

            entityManager.createNativeQuery("""
                            update auth_action_tokens
                            set auth_action_created_at = :createdAt,
                                auth_action_expires_at = :expiredAt
                            where auth_action_token_id = :tokenId
                            """)
                    .setParameter("createdAt", createdAt)
                    .setParameter("expiredAt", expiredAt)
                    .setParameter("tokenId", token.getAuthActionTokenId())
                    .executeUpdate();

            entityManager.flush();
            entityManager.clear();
        });
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

    private String accessTokenFrom(MvcResult result) throws Exception {
        JsonNode json = jsonMapper.readTree(result.getResponse().getContentAsString());

        return json.get("accessToken")
                .asText();
    }

    private Cookie refreshCookieFrom(MvcResult result) {
        String setCookieHeader = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        String refreshCookieName = authCookieService.refreshCookieName();
        String refreshCookiePrefix = refreshCookieName + "=";

        assertThat(setCookieHeader)
                .isNotBlank();

        String refreshCookieValue = List.of(setCookieHeader.split(";"))
                .stream()
                .map(String::trim)
                .filter(cookiePart -> cookiePart.startsWith(refreshCookiePrefix))
                .map(cookiePart -> cookiePart.substring(refreshCookiePrefix.length()))
                .findFirst()
                .orElseThrow();

        return new Cookie(refreshCookieName, refreshCookieValue);
    }

    private String emailChangeRequestJson(
            String newEmail,
            String currentPassword
    ) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put(JSON_FIELD_NEW_EMAIL, newEmail);
        request.put(JSON_FIELD_CURRENT_PASSWORD, currentPassword);

        return jsonMapper.writeValueAsString(request);
    }

    private String emailChangeRequestJsonWithoutNewEmail(
            String currentPassword
    ) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put(JSON_FIELD_CURRENT_PASSWORD, currentPassword);

        return jsonMapper.writeValueAsString(request);
    }

    private String emailChangeRequestJsonWithoutCurrentPassword(
            String newEmail
    ) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put(JSON_FIELD_NEW_EMAIL, newEmail);

        return jsonMapper.writeValueAsString(request);
    }

    private String emailChangeConfirmJson(String token) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put(JSON_FIELD_TOKEN, token);

        return jsonMapper.writeValueAsString(request);
    }

    private String emailChangeConfirmJsonWithoutToken() {
        Map<String, Object> request = new LinkedHashMap<>();

        return jsonMapper.writeValueAsString(request);
    }

    private String loginRequestJson(
            String email,
            String password
    ) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put(JSON_FIELD_EMAIL, email);
        request.put(JSON_FIELD_PASSWORD, password);
        request.put(JSON_FIELD_DEVICE_LABEL, DEVICE_LABEL);

        return jsonMapper.writeValueAsString(request);
    }

    private void markUserAsSoftDeleted(UUID userId) {
        transactionTemplate.executeWithoutResult(status -> {
            OffsetDateTime deletedAt = OffsetDateTime.now();

            entityManager.createNativeQuery("""
                            update users
                            set user_deleted_at = :deletedAt,
                                user_updated_at = :deletedAt
                            where user_id = :userId
                            """)
                    .setParameter("deletedAt", deletedAt)
                    .setParameter("userId", userId)
                    .executeUpdate();

            entityManager.flush();
            entityManager.clear();
        });
    }

    private void disableUser(UUID userId) {
        transactionTemplate.executeWithoutResult(status -> {
            OffsetDateTime updatedAt = OffsetDateTime.now();

            entityManager.createNativeQuery("""
                            update users
                            set user_is_enabled = false,
                                user_updated_at = :updatedAt
                            where user_id = :userId
                            """)
                    .setParameter("updatedAt", updatedAt)
                    .setParameter("userId", userId)
                    .executeUpdate();

            entityManager.flush();
            entityManager.clear();
        });
    }

    private List<EmailOutbox> emailOutboxes(
            EmailOutboxType emailType,
            String recipientEmail
    ) {
        return transactionTemplate.execute(status -> emailOutboxRepository.findAll()
                .stream()
                .filter(emailOutbox -> emailOutbox.getEmailType() == emailType)
                .filter(emailOutbox -> recipientEmail.equals(emailOutbox.getRecipientEmail()))
                .sorted(Comparator.comparing(EmailOutbox::getEmailCreatedAt))
                .toList());
    }

    private EmailOutbox emailOutboxById(UUID emailOutboxId) {
        return transactionTemplate.execute(status -> emailOutboxRepository.findById(emailOutboxId)
                .orElseThrow());
    }

    private void markEmailOutboxAsSent(UUID emailOutboxId) {
        transactionTemplate.executeWithoutResult(status -> {
            EmailOutbox emailOutbox = emailOutboxRepository.findById(emailOutboxId)
                    .orElseThrow();

            emailOutbox.markSent(
                    "test-provider",
                    "test-provider-message-id"
            );

            emailOutboxRepository.flush();
        });
    }

    private record LoginSession(
            String accessToken,
            Cookie refreshCookie
    ) {
    }
}