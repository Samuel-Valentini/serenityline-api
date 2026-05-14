package me.serenityline.api.auth.controller;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import me.serenityline.api.auth.entity.*;
import me.serenityline.api.auth.repository.AuthActionTokenRepository;
import me.serenityline.api.auth.repository.EmailOutboxRepository;
import me.serenityline.api.auth.repository.RefreshTokenRepository;
import me.serenityline.api.auth.repository.UserSessionRepository;
import me.serenityline.api.auth.service.EmailVerificationService;
import me.serenityline.api.security.crypto.EmailOutboxEncryptionService;
import me.serenityline.api.security.crypto.EncryptedValue;
import me.serenityline.api.security.jwt.JwtTokenClaims;
import me.serenityline.api.security.jwt.JwtTokenService;
import me.serenityline.api.security.token.TokenHashingService;
import me.serenityline.api.support.IntegrationTestSupport;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.entity.UserGroup;
import me.serenityline.api.user.entity.UserRole;
import me.serenityline.api.user.repository.UserGroupRepository;
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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerIntegrationTest extends IntegrationTestSupport {

    private static final String DEFAULT_EMAIL = "samuel@example.com";
    private static final String DEFAULT_PASSWORD = "VeryStrongPassword-2026-SerenityLine!";
    private static final String DEFAULT_USER_NAME = "Samuel";
    private static final String DEFAULT_LOCALE = "en-US";
    private static final String DEFAULT_USER_AGENT = "JUnit Browser";
    private static final String DEFAULT_DEVICE_LABEL = "Samuel test device";

    private static final String IT_LOCALE = "it-IT";
    private static final String DEFAULT_GROUP_NAME = "Samuel's group";
    private static final String IT_GROUP_NAME = "Gruppo di Samuel";
    private static final String REFRESH_COOKIE_NAME = "serenityline_refresh";
    private static final String WRONG_PASSWORD = "WrongPassword-2026!";
    private static final String MISSING_EMAIL = "missing@example.com";
    private static final String DEV_ORIGIN = "http://localhost:5173";

    private static final String IT_TEST_PASSWORD = "TrenoMareLuna2026!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserGroupRepository userGroupRepository;

    @Autowired
    private AuthActionTokenRepository authActionTokenRepository;

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

    @Autowired
    private EmailOutboxEncryptionService emailOutboxEncryptionService;

    @Autowired
    private TokenHashingService tokenHashingService;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    private static String extractCookieValue(String setCookie, String cookieName) {
        assertThat(setCookie).isNotBlank();

        String prefix = cookieName + "=";

        return Arrays.stream(setCookie.split(";"))
                .map(String::trim)
                .filter(part -> part.startsWith(prefix))
                .map(part -> part.substring(prefix.length()))
                .findFirst()
                .orElseThrow();
    }

    private static String tamperToken(String token) {
        assertThat(token).isNotBlank();

        String[] parts = token.split("\\.", -1);

        assertThat(parts).hasSize(3);
        assertThat(parts[0]).isNotBlank();
        assertThat(parts[1]).isNotBlank();
        assertThat(parts[2]).isNotBlank();

        return parts[0]
                + "."
                + parts[1]
                + "."
                + tamperFirstCharacter(parts[2]);
    }

    private static String tamperFirstCharacter(String value) {
        assertThat(value).isNotBlank();

        char first = value.charAt(0);
        char replacement = first == 'A' ? 'B' : 'A';

        return replacement + value.substring(1);
    }

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
    void registerShouldCreateDisabledOwnerUserAndGroup() throws Exception {
        String email = uniqueEmail("valid");

        performRegister(
                IT_LOCALE,
                DEFAULT_USER_NAME,
                email,
                IT_TEST_PASSWORD,
                IT_LOCALE
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").isString())
                .andExpect(jsonPath("$.userName").value(DEFAULT_USER_NAME))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.userGroupId").isString())
                .andExpect(jsonPath("$.userGroupName").value(IT_GROUP_NAME))
                .andExpect(jsonPath("$.userRole").value("OWNER"))
                .andExpect(jsonPath("$.preferredLocale").value(IT_LOCALE))
                .andExpect(jsonPath("$.wantsInvoice").value(false))
                .andExpect(jsonPath("$.emailVerificationRequired").value(true))
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString("userPasswordHash"))));

        User savedUser = userRepository.findByEmailAndUserDeletedAtIsNull(email)
                .orElseThrow();

        assertThat(savedUser.getEmail()).isEqualTo(email);
        assertThat(savedUser.getUserName()).isEqualTo(DEFAULT_USER_NAME);
        assertThat(savedUser.getUserRole()).isEqualTo(UserRole.OWNER);
        assertThat(savedUser.isUserIsEnabled()).isFalse();
        assertThat(savedUser.isWantsInvoice()).isFalse();
        assertThat(savedUser.getUserDeletedAt()).isNull();

        List<UserGroup> groups = userGroupRepository.findAll();

        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().getUserGroupName()).isEqualTo(IT_GROUP_NAME);
    }

    @Test
    void registerShouldReturnConflictWhenEmailAlreadyExists() throws Exception {
        String email = uniqueEmail("duplicate");

        registerValidUser(email);

        performRegister(
                IT_LOCALE,
                "Samuel Duplicate",
                email,
                IT_TEST_PASSWORD,
                IT_LOCALE
        )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("auth.register.emailAlreadyExists"))
                .andExpect(jsonPath("$.message").value("Esiste già un account con questa email."))
                .andExpect(jsonPath("$.path").value("/api/auth/register"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void registerShouldReturnEnglishFallbackMessageWhenAcceptLanguageIsEnglish() throws Exception {
        String email = uniqueEmail("duplicate-en");

        registerValidUser(email);

        performRegister(
                DEFAULT_LOCALE,
                "Samuel Duplicate",
                email,
                IT_TEST_PASSWORD,
                IT_LOCALE
        )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("auth.register.emailAlreadyExists"))
                .andExpect(jsonPath("$.message").value("An account with this email already exists."));
    }

    @Test
    void registerShouldRejectWeakPassword() throws Exception {
        String email = uniqueEmail("weak");

        performRegister(
                IT_LOCALE,
                "Weak Password User",
                email,
                "password12345",
                IT_LOCALE
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.password.tooWeak"))
                .andExpect(jsonPath("$.message").value("La password scelta è troppo debole."))
                .andExpect(jsonPath("$.path").value("/api/auth/register"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        assertThat(userRepository.findByEmailAndUserDeletedAtIsNull(email)).isEmpty();
    }

    @Test
    void registerShouldReturnDtoValidationErrors() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userName": "",
                                  "email": "not-an-email",
                                  "password": "short",
                                  "preferredLocale": "fr-FR"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.message").value("Validazione fallita."))
                .andExpect(jsonPath("$.path").value("/api/auth/register"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors[*].code", hasItems(
                        "user.userName.required",
                        "user.email.invalid",
                        "auth.password.invalidLength",
                        "user.preferredLocale.invalid"
                )))
                .andExpect(content().string(not(containsString("exception"))))
                .andExpect(content().string(not(containsString("stacktrace"))))
                .andExpect(content().string(not(containsString("hibernate"))))
                .andExpect(content().string(not(containsString("sql"))));
    }

    @Test
    void registerShouldApplyDefaultsWhenOptionalFieldsAreMissing() throws Exception {
        String email = uniqueEmail("defaults");

        mockMvc.perform(post("/api/auth/register")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userName": "Default User",
                                  "email": "%s",
                                  "password": "MontagnaFiumeStella2026!"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.preferredLocale").value(IT_LOCALE))
                .andExpect(jsonPath("$.wantsInvoice").value(false))
                .andExpect(jsonPath("$.userRole").value("OWNER"))
                .andExpect(jsonPath("$.emailVerificationRequired").value(true));
    }

    @Test
    void registerShouldUseEnglishGroupNameWhenPreferredLocaleIsEnglish() throws Exception {
        String email = uniqueEmail("english");

        performRegister(
                IT_LOCALE,
                "John",
                email,
                "RiverMountainCloud2026!",
                DEFAULT_LOCALE
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.preferredLocale").value(DEFAULT_LOCALE))
                .andExpect(jsonPath("$.userGroupName").value("John's group"));
    }

    @Test
    void registerShouldNormalizeEmailToLowercase() throws Exception {
        String uniquePart = UUID.randomUUID().toString().replace("-", "");
        String rawEmail = "UPPER.%s@EXAMPLE.COM".formatted(uniquePart);
        String expectedEmail = "upper.%s@example.com".formatted(uniquePart);

        performRegister(
                IT_LOCALE,
                "Upper Email",
                rawEmail,
                "SoleVentoMare2026!",
                IT_LOCALE
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(expectedEmail));

        assertThat(userRepository.findByEmailAndUserDeletedAtIsNull(expectedEmail)).isPresent();
    }

    @Test
    void corsPreflightShouldAllowConfiguredDevOrigin() throws Exception {
        mockMvc.perform(options("/api/auth/register")
                        .header(HttpHeaders.ORIGIN, DEV_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, DEV_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")));
    }

    @Test
    void registerShouldCreateEmailVerificationTokenAndOutboxEmail() throws Exception {
        performRegisterWithWantsInvoice(
                DEFAULT_USER_NAME,
                DEFAULT_EMAIL,
                DEFAULT_PASSWORD,
                DEFAULT_LOCALE,
                false
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emailVerificationRequired").value(true));

        List<AuthActionToken> tokens = authActionTokenRepository.findAll();

        assertThat(tokens).hasSize(1);

        AuthActionToken token = tokens.getFirst();

        assertThat(token.getAuthActionTokenType()).isEqualTo(AuthActionTokenType.EMAIL_VERIFICATION);
        assertThat(token.getAuthActionTokenHash()).isNotBlank();
        assertThat(token.getAuthActionUsedAt()).isNull();
        assertThat(token.getAuthActionRevokedAt()).isNull();
        assertThat(token.getAuthActionExpiresAt()).isAfter(OffsetDateTime.now());

        List<EmailOutbox> emails = emailOutboxRepository.findAll();

        assertThat(emails).hasSize(1);

        EmailOutbox emailOutbox = emails.getFirst();

        assertThat(emailOutbox.getEmailType()).isEqualTo(EmailOutboxType.EMAIL_VERIFICATION);
        assertThat(emailOutbox.getEmailStatus()).isEqualTo(EmailOutboxStatus.PENDING);
        assertThat(emailOutbox.getRecipientEmail()).isEqualTo(DEFAULT_EMAIL);
        assertThat(emailOutbox.getAttempts()).isZero();
        assertThat(emailOutbox.isDeleteBodyAfterSend()).isTrue();

        assertThat(emailOutbox.getSubjectEncrypted()).isNotEmpty();
        assertThat(emailOutbox.getSubjectIv()).hasSize(12);
        assertThat(emailOutbox.getSubjectTag()).hasSize(16);

        assertThat(emailOutbox.getBodyHtmlEncrypted()).isNull();
        assertThat(emailOutbox.getBodyTextEncrypted()).isNotEmpty();
        assertThat(emailOutbox.getBodyTextIv()).hasSize(12);
        assertThat(emailOutbox.getBodyTextTag()).hasSize(16);

        String subject = decryptSubject(emailOutbox);
        String body = decryptTextBody(emailOutbox);

        assertThat(subject).isEqualTo("Verify your SerenityLine email");
        assertThat(body).contains(DEV_ORIGIN + "/verify-email#token=");
        assertThat(body).contains(DEV_ORIGIN + "/verify-email");
        assertThat(body).contains("This link expires in 1 day.");
    }

    @Test
    void registerShouldStoreOnlyTokenHashMatchingEmailToken() throws Exception {
        performDefaultRegister()
                .andExpect(status().isCreated());

        AuthActionToken actionToken = authActionTokenRepository.findAll().getFirst();
        EmailOutbox emailOutbox = emailOutboxRepository.findAll().getFirst();

        String body = decryptTextBody(emailOutbox);
        String plainToken = extractTokenFromBody(body);

        assertThat(plainToken)
                .isNotBlank()
                .matches("[A-Za-z0-9_-]+");

        assertThat(actionToken.getAuthActionTokenHash()).isEqualTo(
                tokenHashingService.hash(plainToken)
        );

        assertThat(actionToken.getAuthActionTokenHash()).isNotEqualTo(plainToken);
    }

    @Test
    void registerShouldRejectDuplicateEmail() throws Exception {
        performDefaultRegister()
                .andExpect(status().isCreated());

        performRegister(
                DEFAULT_LOCALE,
                "Another Samuel",
                DEFAULT_EMAIL,
                "AnotherVeryStrongPassword-2026!",
                DEFAULT_LOCALE
        )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("auth.register.emailAlreadyExists"));

        assertThat(authActionTokenRepository.findAll()).hasSize(1);
        assertThat(emailOutboxRepository.findAll()).hasSize(1);
    }

    @Test
    void createEmailVerificationShouldCancelPreviousPendingEmailsAndRevokePreviousTokens() throws Exception {
        performDefaultRegister()
                .andExpect(status().isCreated());

        User user = defaultUser();

        assertThat(authActionTokenRepository.findAll()).hasSize(1);
        assertThat(emailOutboxRepository.findAll()).hasSize(1);

        emailVerificationService.createEmailVerification(user);

        List<AuthActionToken> tokens = authActionTokenRepository.findAll();

        assertThat(tokens).hasSize(2);

        assertThat(tokens)
                .filteredOn(token -> token.getAuthActionTokenType() == AuthActionTokenType.EMAIL_VERIFICATION)
                .hasSize(2);

        assertThat(tokens)
                .filteredOn(token -> token.getAuthActionRevokedAt() == null)
                .hasSize(1);

        assertThat(tokens)
                .filteredOn(token -> token.getAuthActionRevokedAt() != null)
                .hasSize(1);

        List<EmailOutbox> emails = emailOutboxRepository.findAll();

        assertThat(emails).hasSize(2);

        assertThat(emails)
                .filteredOn(email -> email.getEmailType() == EmailOutboxType.EMAIL_VERIFICATION)
                .hasSize(2);

        assertThat(emails)
                .filteredOn(email -> email.getEmailStatus() == EmailOutboxStatus.PENDING)
                .hasSize(1);

        assertThat(emails)
                .filteredOn(email -> email.getEmailStatus() == EmailOutboxStatus.CANCELLED)
                .hasSize(1);

        EmailOutbox cancelledEmail = emails.stream()
                .filter(email -> email.getEmailStatus() == EmailOutboxStatus.CANCELLED)
                .findFirst()
                .orElseThrow();

        assertThat(cancelledEmail.getEmailCancelledAt()).isNotNull();

        EmailOutbox pendingEmail = emails.stream()
                .filter(email -> email.getEmailStatus() == EmailOutboxStatus.PENDING)
                .findFirst()
                .orElseThrow();

        assertThat(pendingEmail.getEmailCancelledAt()).isNull();
        assertThat(pendingEmail.getRecipientEmail()).isEqualTo(DEFAULT_EMAIL);
    }

    @Test
    void verifyEmailShouldEnableUserAndMarkTokenAsUsed() throws Exception {
        String token = registerAndExtractVerificationToken();

        verifyEmail(token);

        User user = defaultUser();

        assertThat(user.isUserIsEnabled()).isTrue();

        AuthActionToken actionToken = authActionTokenRepository.findAll().getFirst();

        assertThat(actionToken.getAuthActionUsedAt()).isNotNull();
        assertThat(actionToken.getAuthActionRevokedAt()).isNull();
    }

    @Test
    void verifyEmailShouldRejectInvalidToken() throws Exception {
        performVerifyEmail("invalid-token")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.emailVerification.invalidOrExpired"));
    }

    @Test
    void verifyEmailShouldRejectBlankToken() throws Exception {
        performVerifyEmail(" ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("auth.token.required"));
    }

    @Test
    void verifyEmailShouldBeIdempotentAfterSuccessfulVerification() throws Exception {
        String token = registerAndExtractVerificationToken();

        verifyEmail(token);
        verifyEmail(token);

        User user = defaultUser();

        assertThat(user.isUserIsEnabled()).isTrue();

        AuthActionToken actionToken = authActionTokenRepository.findAll().getFirst();

        assertThat(actionToken.getAuthActionUsedAt()).isNotNull();
    }

    @Test
    void verifyEmailShouldRejectRevokedTokenAfterNewVerificationEmailIsCreated() throws Exception {
        String oldToken = registerAndExtractVerificationToken();

        User user = defaultUser();

        emailVerificationService.createEmailVerification(user);

        performVerifyEmail(oldToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.emailVerification.invalidOrExpired"));

        User reloadedUser = defaultUser();

        assertThat(reloadedUser.isUserIsEnabled()).isFalse();

        assertThat(authActionTokenRepository.findAll())
                .filteredOn(token -> token.getAuthActionRevokedAt() != null)
                .hasSize(1);

        assertThat(authActionTokenRepository.findAll())
                .filteredOn(token -> token.getAuthActionRevokedAt() == null)
                .hasSize(1);
    }

    @Test
    void loginShouldReturnEmailVerificationRequiredChallengeForUnverifiedEmail() throws Exception {
        registerAndExtractVerificationToken();

        performDefaultLogin()
                .andExpect(status().isConflict())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.email").value(DEFAULT_EMAIL))
                .andExpect(jsonPath("$.emailVerificationResendToken").exists())
                .andExpect(jsonPath("$.emailVerificationResendTokenExpiresAt").exists())
                .andExpect(jsonPath("$.emailVerificationResendAvailableAt").exists())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.userName").doesNotExist())
                .andExpect(jsonPath("$.userGroupId").doesNotExist())
                .andExpect(jsonPath("$.userRole").doesNotExist())
                .andExpect(jsonPath("$.userPlatformRole").doesNotExist());

        assertThat(userSessionRepository.findAll()).isEmpty();
        assertThat(refreshTokenRepository.findAll()).isEmpty();

        List<AuthActionToken> resendTokens = authActionTokenRepository.findAll().stream()
                .filter(actionToken -> actionToken.getAuthActionTokenType() == AuthActionTokenType.EMAIL_VERIFICATION_RESEND)
                .toList();

        assertThat(resendTokens).hasSize(1);
        assertThat(resendTokens.getFirst().getAuthActionUsedAt()).isNull();
        assertThat(resendTokens.getFirst().getAuthActionRevokedAt()).isNull();

        List<EmailOutbox> verificationEmails = emailOutboxRepository.findAll().stream()
                .filter(email -> email.getEmailType() == EmailOutboxType.EMAIL_VERIFICATION)
                .toList();

        assertThat(verificationEmails).hasSize(1);
    }

    @Test
    void loginShouldAuthenticateVerifiedUser() throws Exception {
        registerAndVerifyDefaultUser();

        MvcResult result = performDefaultLoginWithDevice()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.accessTokenExpiresAt").exists())
                .andExpect(jsonPath("$.user.userId").exists())
                .andExpect(jsonPath("$.user.userName").value(DEFAULT_USER_NAME))
                .andExpect(jsonPath("$.user.email").value(DEFAULT_EMAIL))
                .andExpect(jsonPath("$.user.userGroupId").exists())
                .andExpect(jsonPath("$.user.userGroupName").value(DEFAULT_GROUP_NAME))
                .andExpect(jsonPath("$.user.userRole").value("OWNER"))
                .andExpect(jsonPath("$.user.userPlatformRole").value("USER"))
                .andExpect(jsonPath("$.user.preferredLocale").value(DEFAULT_LOCALE))
                .andExpect(jsonPath("$.user.preferredTheme").value("DEFAULT"))
                .andExpect(jsonPath("$.user.wantsInvoice").value(false))
                .andReturn();

        assertRefreshCookieIssued(result);

        User user = defaultUser();

        assertThat(user.getUserLastLoginAt()).isNotNull();

        String accessToken = extractAccessToken(result);

        Optional<JwtTokenClaims> claims = jwtTokenService.parseAndValidate(accessToken);

        assertThat(claims).isPresent();
        assertThat(claims.get().userId()).isEqualTo(user.getUserId());
        assertThat(claims.get().tokenVersion()).isEqualTo(user.getTokenVersion());

        assertThat(userSessionRepository.findAll()).hasSize(1);
        assertThat(refreshTokenRepository.findAll()).hasSize(1);

        UserSession session = userSessionRepository.findAll().getFirst();

        assertThat(session.getUser().getUserId()).isEqualTo(user.getUserId());
        assertThat(session.getIpAddressHash()).isNotBlank();
        assertThat(session.getIpAddressHash()).isNotEqualTo("127.0.0.1");
        assertThat(session.getUserAgent()).isEqualTo(DEFAULT_USER_AGENT);
        assertThat(session.getDeviceLabel()).isEqualTo(DEFAULT_DEVICE_LABEL);
        assertThat(session.getSessionExpiresAt()).isAfter(OffsetDateTime.now());
        assertThat(session.getSessionRevokedAt()).isNull();

        RefreshToken refreshToken = refreshTokenRepository.findAll().getFirst();

        assertThat(refreshToken.getUser().getUserId()).isEqualTo(user.getUserId());
        assertThat(refreshToken.getUserSession().getUserSessionId()).isEqualTo(session.getUserSessionId());
        assertThat(refreshToken.getRefreshTokenHash()).isNotBlank();
        assertThat(refreshToken.getRefreshTokenUsedAt()).isNull();
        assertThat(refreshToken.getRefreshTokenRevokedAt()).isNull();
        assertThat(refreshToken.getRefreshTokenExpiresAt()).isAfter(OffsetDateTime.now());

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertThat(setCookie).isNotNull();
        assertThat(setCookie).contains(REFRESH_COOKIE_NAME + "=");
        assertThat(setCookie).doesNotContain(refreshToken.getRefreshTokenHash());
    }

    @Test
    void loginShouldAuthenticateVerifiedUserWithoutDeviceLabel() throws Exception {
        registerAndVerifyDefaultUser();

        performDefaultLoginWithUserAgent()
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(REFRESH_COOKIE_NAME + "=")))
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.user.email").value(DEFAULT_EMAIL));

        UserSession session = userSessionRepository.findAll().getFirst();

        assertThat(session.getUserAgent()).isEqualTo(DEFAULT_USER_AGENT);
        assertThat(session.getDeviceLabel()).isNull();
    }

    @Test
    void loginShouldRejectWrongPassword() throws Exception {
        registerAndVerifyDefaultUser();

        performLogin(DEFAULT_EMAIL, WRONG_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.code").value("auth.login.invalidCredentials"));

        assertThat(userSessionRepository.findAll()).isEmpty();
        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    @Test
    void loginShouldRejectUnknownEmail() throws Exception {
        performLogin(MISSING_EMAIL, DEFAULT_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.login.invalidCredentials"));
    }

    @Test
    void loginShouldReturnRestoreChallengeForSoftDeletedUserWithCorrectPassword() throws Exception {
        registerAndVerifyDefaultUser();

        User user = defaultUser();

        user.markAsSoftDeleted();
        userRepository.saveAndFlush(user);

        assertThat(user.isUserIsEnabled()).isTrue();
        assertThat(user.isPendingDeletion()).isTrue();

        performDefaultLogin()
                .andExpect(status().isConflict())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.restoreToken").exists())
                .andExpect(jsonPath("$.restoreTokenExpiresAt").exists())
                .andExpect(jsonPath("$.code").doesNotExist());

        assertThat(userSessionRepository.findAll()).isEmpty();
        assertThat(refreshTokenRepository.findAll()).isEmpty();

        List<AuthActionToken> tokens = authActionTokenRepository.findAll();

        assertThat(tokens)
                .filteredOn(actionToken -> actionToken.getAuthActionTokenType() == AuthActionTokenType.RESTORE_ACCOUNT)
                .hasSize(1);
    }

    @Test
    void loginShouldNotRevealSoftDeletedAccountWithWrongPassword() throws Exception {
        registerAndVerifyDefaultUser();

        User user = defaultUser();

        user.markAsSoftDeleted();
        userRepository.saveAndFlush(user);

        performLogin(DEFAULT_EMAIL, WRONG_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.login.invalidCredentials"));
    }

    @Test
    void loginShouldTreatBlankDeviceLabelAsNull() throws Exception {
        registerAndVerifyDefaultUser();

        performLoginWithDevice(DEFAULT_EMAIL, DEFAULT_PASSWORD, "   ")
                .andExpect(status().isOk());

        UserSession session = userSessionRepository.findAll().getFirst();

        assertThat(session.getDeviceLabel()).isNull();
    }

    @Test
    void restoreAccountShouldRestoreSoftDeletedUserAndMarkTokenAsUsed() throws Exception {
        registerAndVerifyDefaultUser();

        User user = defaultUser();

        user.markAsSoftDeleted();
        userRepository.saveAndFlush(user);

        String restoreToken = loginAndExtractRestoreToken();

        performRestoreAccount(restoreToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.userName").value(DEFAULT_USER_NAME))
                .andExpect(jsonPath("$.email").value(DEFAULT_EMAIL))
                .andExpect(jsonPath("$.userGroupId").exists())
                .andExpect(jsonPath("$.userGroupName").value(DEFAULT_GROUP_NAME))
                .andExpect(jsonPath("$.userRole").value("OWNER"))
                .andExpect(jsonPath("$.userPlatformRole").value("USER"))
                .andExpect(jsonPath("$.preferredLocale").value(DEFAULT_LOCALE))
                .andExpect(jsonPath("$.preferredTheme").value("DEFAULT"))
                .andExpect(jsonPath("$.wantsInvoice").value(false));

        User restoredUser = defaultUser();

        assertThat(restoredUser.isPendingDeletion()).isFalse();
        assertThat(restoredUser.getUserDeletedAt()).isNull();
        assertThat(restoredUser.getUserLastLoginAt()).isNotNull();
        assertThat(restoredUser.isUserIsEnabled()).isTrue();

        AuthActionToken restoreActionToken = authActionTokenRepository.findAll().stream()
                .filter(actionToken -> actionToken.getAuthActionTokenType() == AuthActionTokenType.RESTORE_ACCOUNT)
                .findFirst()
                .orElseThrow();

        assertThat(restoreActionToken.getAuthActionUsedAt()).isNotNull();
        assertThat(restoreActionToken.getAuthActionRevokedAt()).isNull();

        List<EmailOutbox> verificationEmails = emailOutboxRepository.findAll().stream()
                .filter(email -> email.getEmailType() == EmailOutboxType.EMAIL_VERIFICATION)
                .toList();

        assertThat(verificationEmails).hasSize(1);
    }

    @Test
    void restoreAccountShouldRejectInvalidToken() throws Exception {
        performRestoreAccount("invalid-token")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.restoreAccount.invalidOrExpired"));
    }

    @Test
    void restoreAccountShouldRejectAlreadyUsedRestoreToken() throws Exception {
        registerAndVerifyDefaultUser();

        User user = defaultUser();

        user.markAsSoftDeleted();
        userRepository.saveAndFlush(user);

        String restoreToken = loginAndExtractRestoreToken();

        performRestoreAccount(restoreToken)
                .andExpect(status().isOk());

        performRestoreAccount(restoreToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.restoreAccount.invalidOrExpired"));
    }

    @Test
    void loginShouldRevokePreviousRestoreTokenWhenCreatingNewRestoreChallenge() throws Exception {
        registerAndVerifyDefaultUser();

        User user = defaultUser();

        user.markAsSoftDeleted();
        userRepository.saveAndFlush(user);

        String firstRestoreToken = loginAndExtractRestoreToken();
        String secondRestoreToken = loginAndExtractRestoreToken();

        assertThat(secondRestoreToken).isNotEqualTo(firstRestoreToken);

        List<AuthActionToken> restoreTokens = authActionTokenRepository.findAll().stream()
                .filter(actionToken -> actionToken.getAuthActionTokenType() == AuthActionTokenType.RESTORE_ACCOUNT)
                .toList();

        assertThat(restoreTokens).hasSize(2);

        assertThat(restoreTokens)
                .filteredOn(actionToken -> actionToken.getAuthActionRevokedAt() != null)
                .hasSize(1);

        assertThat(restoreTokens)
                .filteredOn(actionToken -> actionToken.getAuthActionRevokedAt() == null)
                .hasSize(1);

        performRestoreAccount(firstRestoreToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.restoreAccount.invalidOrExpired"));
    }

    @Test
    void loginShouldReturnRestoreChallengeForSoftDeletedUnverifiedUserWithCorrectPassword() throws Exception {
        registerAndExtractVerificationToken();

        User user = defaultUser();

        assertThat(user.isUserIsEnabled()).isFalse();

        user.markAsSoftDeleted();
        userRepository.saveAndFlush(user);

        performDefaultLogin()
                .andExpect(status().isConflict())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.restoreToken").exists())
                .andExpect(jsonPath("$.restoreTokenExpiresAt").exists())
                .andExpect(jsonPath("$.code").doesNotExist());

        assertThat(userSessionRepository.findAll()).isEmpty();
        assertThat(refreshTokenRepository.findAll()).isEmpty();

        List<AuthActionToken> restoreTokens = authActionTokenRepository.findAll().stream()
                .filter(actionToken -> actionToken.getAuthActionTokenType() == AuthActionTokenType.RESTORE_ACCOUNT)
                .toList();

        assertThat(restoreTokens).hasSize(1);
        assertThat(restoreTokens.getFirst().getAuthActionUsedAt()).isNull();
        assertThat(restoreTokens.getFirst().getAuthActionRevokedAt()).isNull();
    }

    @Test
    void restoreAccountShouldRestoreUnverifiedUserWithoutLoginAndCreateNewVerificationEmail() throws Exception {
        registerAndExtractVerificationToken();

        User user = defaultUser();

        assertThat(user.isUserIsEnabled()).isFalse();
        assertThat(user.getUserLastLoginAt()).isNull();

        user.markAsSoftDeleted();
        userRepository.saveAndFlush(user);

        String restoreToken = loginAndExtractRestoreToken();

        performRestoreAccount(restoreToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.email").value(DEFAULT_EMAIL))
                .andExpect(jsonPath("$.emailVerificationResendToken").exists())
                .andExpect(jsonPath("$.emailVerificationResendTokenExpiresAt").exists())
                .andExpect(jsonPath("$.emailVerificationResendAvailableAt").exists())
                .andExpect(jsonPath("$.preferredLocale").doesNotExist())
                .andExpect(jsonPath("$.emailVerificationRequired").doesNotExist())
                .andExpect(jsonPath("$.userName").doesNotExist())
                .andExpect(jsonPath("$.userGroupId").doesNotExist())
                .andExpect(jsonPath("$.userRole").doesNotExist())
                .andExpect(jsonPath("$.userPlatformRole").doesNotExist());

        User restoredUser = defaultUser();

        assertThat(restoredUser.isPendingDeletion()).isFalse();
        assertThat(restoredUser.getUserDeletedAt()).isNull();
        assertThat(restoredUser.isUserIsEnabled()).isFalse();
        assertThat(restoredUser.getUserLastLoginAt()).isNull();

        AuthActionToken restoreActionToken = authActionTokenRepository.findAll().stream()
                .filter(actionToken -> actionToken.getAuthActionTokenType() == AuthActionTokenType.RESTORE_ACCOUNT)
                .findFirst()
                .orElseThrow();

        assertThat(restoreActionToken.getAuthActionUsedAt()).isNotNull();
        assertThat(restoreActionToken.getAuthActionRevokedAt()).isNull();

        List<AuthActionToken> emailVerificationTokens = authActionTokenRepository.findAll().stream()
                .filter(actionToken -> actionToken.getAuthActionTokenType() == AuthActionTokenType.EMAIL_VERIFICATION)
                .toList();

        assertThat(emailVerificationTokens).hasSize(2);

        assertThat(emailVerificationTokens)
                .filteredOn(actionToken -> actionToken.getAuthActionRevokedAt() != null)
                .hasSize(1);

        assertThat(emailVerificationTokens)
                .filteredOn(actionToken -> actionToken.getAuthActionRevokedAt() == null)
                .hasSize(1);

        List<EmailOutbox> verificationEmails = emailOutboxRepository.findAll().stream()
                .filter(email -> email.getEmailType() == EmailOutboxType.EMAIL_VERIFICATION)
                .toList();

        assertThat(verificationEmails).hasSize(2);

        assertThat(verificationEmails)
                .filteredOn(email -> email.getEmailStatus() == EmailOutboxStatus.CANCELLED)
                .hasSize(1);

        assertThat(verificationEmails)
                .filteredOn(email -> email.getEmailStatus() == EmailOutboxStatus.PENDING)
                .hasSize(1);

        List<AuthActionToken> resendTokens = authActionTokenRepository.findAll().stream()
                .filter(actionToken -> actionToken.getAuthActionTokenType() == AuthActionTokenType.EMAIL_VERIFICATION_RESEND)
                .toList();

        assertThat(resendTokens).hasSize(1);
        assertThat(resendTokens.getFirst().getAuthActionUsedAt()).isNull();
        assertThat(resendTokens.getFirst().getAuthActionRevokedAt()).isNull();
    }

    @Test
    void resendEmailVerificationShouldRejectInvalidToken() throws Exception {
        performResendEmailVerification("invalid-token")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.emailVerificationResend.invalidOrExpired"));
    }

    @Test
    void resendEmailVerificationShouldRejectWhenCooldownHasNotExpired() throws Exception {
        registerAndExtractVerificationToken();

        String resendToken = loginAndExtractEmailVerificationResendToken();

        performResendEmailVerification(resendToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("auth.emailVerificationResend.tooSoon"));

        AuthActionToken resendActionToken = authActionTokenRepository
                .findByAuthActionTokenHash(tokenHashingService.hash(resendToken))
                .orElseThrow();

        assertThat(resendActionToken.getAuthActionUsedAt()).isNull();
        assertThat(resendActionToken.getAuthActionRevokedAt()).isNull();
    }

    @Test
    void resendEmailVerificationShouldCreateNewVerificationEmailAndRotateResendToken() throws Exception {
        registerAndExtractVerificationToken();

        String firstResendToken = loginAndExtractEmailVerificationResendToken();

        makeEmailVerificationCooldownExpired(DEFAULT_EMAIL);

        MvcResult result = performResendEmailVerification(firstResendToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.email").value(DEFAULT_EMAIL))
                .andExpect(jsonPath("$.emailVerificationResendToken").exists())
                .andExpect(jsonPath("$.emailVerificationResendTokenExpiresAt").exists())
                .andExpect(jsonPath("$.emailVerificationResendAvailableAt").exists())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andReturn();

        String secondResendToken = jsonPathString(result, "$.emailVerificationResendToken");

        assertThat(secondResendToken).isNotEqualTo(firstResendToken);

        AuthActionToken firstResendActionToken = authActionTokenRepository
                .findByAuthActionTokenHash(tokenHashingService.hash(firstResendToken))
                .orElseThrow();

        assertThat(firstResendActionToken.getAuthActionTokenType())
                .isEqualTo(AuthActionTokenType.EMAIL_VERIFICATION_RESEND);
        assertThat(firstResendActionToken.getAuthActionUsedAt()).isNotNull();

        AuthActionToken secondResendActionToken = authActionTokenRepository
                .findByAuthActionTokenHash(tokenHashingService.hash(secondResendToken))
                .orElseThrow();

        assertThat(secondResendActionToken.getAuthActionTokenType())
                .isEqualTo(AuthActionTokenType.EMAIL_VERIFICATION_RESEND);
        assertThat(secondResendActionToken.getAuthActionUsedAt()).isNull();
        assertThat(secondResendActionToken.getAuthActionRevokedAt()).isNull();

        List<AuthActionToken> emailVerificationTokens = authActionTokenRepository.findAll().stream()
                .filter(actionToken -> actionToken.getAuthActionTokenType() == AuthActionTokenType.EMAIL_VERIFICATION)
                .toList();

        assertThat(emailVerificationTokens).hasSize(2);

        assertThat(emailVerificationTokens)
                .filteredOn(actionToken -> actionToken.getAuthActionRevokedAt() != null)
                .hasSize(1);

        assertThat(emailVerificationTokens)
                .filteredOn(actionToken -> actionToken.getAuthActionRevokedAt() == null)
                .hasSize(1);

        List<EmailOutbox> verificationEmails = emailOutboxRepository.findAll().stream()
                .filter(email -> email.getEmailType() == EmailOutboxType.EMAIL_VERIFICATION)
                .toList();

        assertThat(verificationEmails).hasSize(2);

        assertThat(verificationEmails)
                .filteredOn(email -> email.getEmailStatus() == EmailOutboxStatus.CANCELLED)
                .hasSize(1);

        assertThat(verificationEmails)
                .filteredOn(email -> email.getEmailStatus() == EmailOutboxStatus.PENDING)
                .hasSize(1);
    }

    @Test
    void resendEmailVerificationShouldRejectAlreadyUsedResendToken() throws Exception {
        registerAndExtractVerificationToken();

        String resendToken = loginAndExtractEmailVerificationResendToken();

        makeEmailVerificationCooldownExpired(DEFAULT_EMAIL);

        performResendEmailVerification(resendToken)
                .andExpect(status().isOk());

        performResendEmailVerification(resendToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.emailVerificationResend.invalidOrExpired"));
    }

    @Test
    void resendEmailVerificationShouldRejectWhenUserIsAlreadyVerified() throws Exception {
        String verificationToken = registerAndExtractVerificationToken();

        String resendToken = loginAndExtractEmailVerificationResendToken();

        verifyEmail(verificationToken);

        performResendEmailVerification(resendToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("auth.emailVerification.userAlreadyVerified"));
    }

    @Test
    void refreshShouldRejectMissingCookie() throws Exception {
        ResultActions resultActions = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE))
                .andExpect(status().isUnauthorized());

        assertRefreshCookieCleared(resultActions);
    }

    @Test
    void refreshShouldRotateRefreshTokenAndIssueNewAccessToken() throws Exception {
        registerAndVerifyDefaultUser();

        MvcResult loginResult = performDefaultLoginWithDevice()
                .andExpect(status().isOk())
                .andReturn();

        assertRefreshCookieIssued(loginResult);

        String firstPlainRefreshToken = extractRefreshCookie(loginResult);

        assertThat(refreshTokenRepository.findAll()).hasSize(1);

        RefreshToken firstRefreshToken = refreshTokenRepository.findAll().getFirst();

        MvcResult refreshResult = performRefresh(firstPlainRefreshToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.accessTokenExpiresAt").exists())
                .andExpect(jsonPath("$.user.userId").exists())
                .andExpect(jsonPath("$.user.email").value(DEFAULT_EMAIL))
                .andExpect(jsonPath("$.user.userRole").value("OWNER"))
                .andExpect(jsonPath("$.user.userPlatformRole").value("USER"))
                .andReturn();

        assertRefreshCookieIssued(refreshResult);

        String refreshSetCookie = refreshResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        String secondPlainRefreshToken = extractRefreshCookie(refreshResult);

        assertThat(secondPlainRefreshToken).isNotBlank();
        assertThat(secondPlainRefreshToken).isNotEqualTo(firstPlainRefreshToken);

        User user = defaultUser();

        String accessToken = extractAccessToken(refreshResult);

        Optional<JwtTokenClaims> claims = jwtTokenService.parseAndValidate(accessToken);

        assertThat(claims).isPresent();
        assertThat(claims.get().userId()).isEqualTo(user.getUserId());
        assertThat(claims.get().tokenVersion()).isEqualTo(user.getTokenVersion());

        assertThat(userSessionRepository.findAll()).hasSize(1);
        assertThat(refreshTokenRepository.findAll()).hasSize(2);

        RefreshToken oldToken = refreshTokenRepository.findAll().stream()
                .filter(token -> token.getRefreshTokenId().equals(firstRefreshToken.getRefreshTokenId()))
                .findFirst()
                .orElseThrow();

        RefreshToken newToken = refreshTokenRepository.findAll().stream()
                .filter(token -> !token.getRefreshTokenId().equals(firstRefreshToken.getRefreshTokenId()))
                .findFirst()
                .orElseThrow();

        assertThat(oldToken.getRefreshTokenUsedAt()).isNotNull();
        assertThat(oldToken.getRefreshTokenRevokedAt()).isNull();
        assertThat(oldToken.getReplacedByRefreshToken()).isNotNull();
        assertThat(oldToken.getReplacedByRefreshToken().getRefreshTokenId())
                .isEqualTo(newToken.getRefreshTokenId());

        assertThat(newToken.getParentRefreshToken()).isNotNull();
        assertThat(newToken.getParentRefreshToken().getRefreshTokenId())
                .isEqualTo(oldToken.getRefreshTokenId());

        assertThat(newToken.getUser().getUserId()).isEqualTo(user.getUserId());
        assertThat(newToken.getUserSession().getUserSessionId())
                .isEqualTo(oldToken.getUserSession().getUserSessionId());

        assertThat(newToken.getRefreshTokenHash()).isNotBlank();
        assertThat(newToken.getRefreshTokenHash()).isNotEqualTo(oldToken.getRefreshTokenHash());
        assertThat(newToken.getRefreshTokenUsedAt()).isNull();
        assertThat(newToken.getRefreshTokenRevokedAt()).isNull();
        assertThat(newToken.getRefreshTokenExpiresAt()).isAfter(OffsetDateTime.now());

        assertThat(refreshSetCookie).doesNotContain(newToken.getRefreshTokenHash());
    }

    @Test
    void refreshShouldRejectUsedRefreshTokenAndMarkReuseDetected() throws Exception {
        registerAndVerifyDefaultUser();

        String firstPlainRefreshToken = loginDefaultUserWithDeviceAndExtractRefreshToken();

        MvcResult firstRefreshResult = performRefresh(firstPlainRefreshToken)
                .andExpect(status().isOk())
                .andReturn();

        String secondPlainRefreshToken = extractRefreshCookie(firstRefreshResult);

        assertThat(secondPlainRefreshToken).isNotBlank();
        assertThat(secondPlainRefreshToken).isNotEqualTo(firstPlainRefreshToken);

        ResultActions reuseResultActions = performRefresh(firstPlainRefreshToken)
                .andExpect(status().isUnauthorized());

        assertRefreshCookieCleared(reuseResultActions);

        assertThat(userSessionRepository.findAll()).hasSize(1);
        assertThat(refreshTokenRepository.findAll()).hasSize(2);

        UserSession session = userSessionRepository.findAll().getFirst();

        assertThat(session.getSessionRevokedAt()).isNotNull();
        assertThat(session.getSessionRevokeReason())
                .isEqualTo(SessionRevokeReason.TOKEN_REUSE_DETECTED);

        RefreshToken reusedToken = refreshTokenRepository.findAll().stream()
                .filter(token -> token.getRefreshTokenUsedAt() != null)
                .findFirst()
                .orElseThrow();

        RefreshToken childToken = refreshTokenRepository.findAll().stream()
                .filter(token -> token.getParentRefreshToken() != null)
                .findFirst()
                .orElseThrow();

        assertThat(reusedToken.getRefreshTokenReuseDetectedAt()).isNotNull();
        assertThat(reusedToken.getRefreshTokenRevokedAt()).isNotNull();
        assertThat(reusedToken.getRefreshTokenRevokeReason())
                .isEqualTo(RefreshTokenRevokeReason.REUSE_DETECTED);

        assertThat(childToken.getRefreshTokenUsedAt()).isNull();
        assertThat(childToken.getRefreshTokenRevokedAt()).isNull();
    }

    @Test
    void refreshShouldRejectCurrentRefreshTokenAfterReuseDetectionRevokedSession() throws Exception {
        registerAndVerifyDefaultUser();

        String firstPlainRefreshToken = loginDefaultUserWithDeviceAndExtractRefreshToken();

        MvcResult firstRefreshResult = performRefresh(firstPlainRefreshToken)
                .andExpect(status().isOk())
                .andReturn();

        String secondPlainRefreshToken = extractRefreshCookie(firstRefreshResult);

        assertThat(secondPlainRefreshToken).isNotBlank();
        assertThat(secondPlainRefreshToken).isNotEqualTo(firstPlainRefreshToken);

        performRefresh(firstPlainRefreshToken)
                .andExpect(status().isUnauthorized());

        UserSession sessionAfterReuse = userSessionRepository.findAll().getFirst();

        assertThat(sessionAfterReuse.getSessionRevokedAt()).isNotNull();
        assertThat(sessionAfterReuse.getSessionRevokeReason())
                .isEqualTo(SessionRevokeReason.TOKEN_REUSE_DETECTED);

        ResultActions resultActions = performRefresh(secondPlainRefreshToken)
                .andExpect(status().isUnauthorized());

        assertRefreshCookieCleared(resultActions);

        assertThat(userSessionRepository.findAll()).hasSize(1);
        assertThat(refreshTokenRepository.findAll()).hasSize(2);

        RefreshToken childToken = refreshTokenRepository.findAll().stream()
                .filter(token -> token.getParentRefreshToken() != null)
                .findFirst()
                .orElseThrow();

        assertThat(childToken.getRefreshTokenUsedAt()).isNull();
        assertThat(childToken.getRefreshTokenRevokedAt()).isNull();
    }

    @Test
    void refreshShouldRejectUnknownRefreshToken() throws Exception {
        ResultActions resultActions = performRefresh("unknown-refresh-token")
                .andExpect(status().isUnauthorized());

        assertRefreshCookieCleared(resultActions);

        assertThat(userSessionRepository.findAll()).isEmpty();
        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    @Test
    void refreshShouldRejectRevokedRefreshToken() throws Exception {
        registerAndVerifyDefaultUser();

        String plainRefreshToken = loginDefaultUserWithDeviceAndExtractRefreshToken();

        RefreshToken refreshToken = refreshTokenRepository.findAll().getFirst();
        refreshToken.revoke(RefreshTokenRevokeReason.USER_LOGOUT);
        refreshTokenRepository.save(refreshToken);

        ResultActions resultActions = performRefresh(plainRefreshToken)
                .andExpect(status().isUnauthorized());

        assertRefreshCookieCleared(resultActions);

        assertThat(refreshTokenRepository.findAll()).hasSize(1);

        RefreshToken rejectedToken = refreshTokenRepository.findAll().getFirst();

        assertThat(rejectedToken.getRefreshTokenUsedAt()).isNull();
        assertThat(rejectedToken.getRefreshTokenRevokedAt()).isNotNull();
        assertThat(rejectedToken.getRefreshTokenRevokeReason())
                .isEqualTo(RefreshTokenRevokeReason.USER_LOGOUT);
    }

    @Test
    void refreshShouldRejectRevokedSession() throws Exception {
        registerAndVerifyDefaultUser();

        String plainRefreshToken = loginDefaultUserWithDeviceAndExtractRefreshToken();

        UserSession session = userSessionRepository.findAll().getFirst();
        session.revoke(SessionRevokeReason.USER_LOGOUT);
        userSessionRepository.save(session);

        ResultActions resultActions = performRefresh(plainRefreshToken)
                .andExpect(status().isUnauthorized());

        assertRefreshCookieCleared(resultActions);

        assertThat(userSessionRepository.findAll()).hasSize(1);
        assertThat(refreshTokenRepository.findAll()).hasSize(1);

        UserSession rejectedSession = userSessionRepository.findAll().getFirst();
        RefreshToken refreshToken = refreshTokenRepository.findAll().getFirst();

        assertThat(rejectedSession.getSessionRevokedAt()).isNotNull();
        assertThat(rejectedSession.getSessionRevokeReason())
                .isEqualTo(SessionRevokeReason.USER_LOGOUT);

        assertThat(refreshToken.getRefreshTokenUsedAt()).isNull();
        assertThat(refreshToken.getRefreshTokenRevokedAt()).isNull();
    }

    @Test
    void refreshShouldRejectExpiredRefreshToken() throws Exception {
        registerAndVerifyDefaultUser();

        String plainRefreshToken = loginDefaultUserWithDeviceAndExtractRefreshToken();

        RefreshToken refreshToken = refreshTokenRepository.findAll().getFirst();

        jdbcTemplate.update("""
                update refresh_tokens
                set refresh_token_created_at = now() - interval '2 hours',
                    refresh_token_expires_at = now() - interval '1 hour'
                where refresh_token_id = ?
                """, refreshToken.getRefreshTokenId());

        ResultActions resultActions = performRefresh(plainRefreshToken)
                .andExpect(status().isUnauthorized());

        assertRefreshCookieCleared(resultActions);

        assertThat(refreshTokenRepository.findAll()).hasSize(1);

        RefreshToken rejectedToken = refreshTokenRepository.findAll().getFirst();

        assertThat(rejectedToken.getRefreshTokenUsedAt()).isNull();
        assertThat(rejectedToken.getRefreshTokenRevokedAt()).isNull();
    }

    @Test
    void refreshShouldRejectExpiredSession() throws Exception {
        registerAndVerifyDefaultUser();

        String plainRefreshToken = loginDefaultUserWithDeviceAndExtractRefreshToken();

        UserSession session = userSessionRepository.findAll().getFirst();

        jdbcTemplate.update("""
                update user_sessions
                set session_created_at = now() - interval '2 hours',
                    session_last_seen_at = now() - interval '2 hours',
                    session_expires_at = now() - interval '1 hour'
                where user_session_id = ?
                """, session.getUserSessionId());

        ResultActions resultActions = performRefresh(plainRefreshToken)
                .andExpect(status().isUnauthorized());

        assertRefreshCookieCleared(resultActions);

        assertThat(userSessionRepository.findAll()).hasSize(1);
        assertThat(refreshTokenRepository.findAll()).hasSize(1);

        RefreshToken refreshToken = refreshTokenRepository.findAll().getFirst();

        assertThat(refreshToken.getRefreshTokenUsedAt()).isNull();
        assertThat(refreshToken.getRefreshTokenRevokedAt()).isNull();
    }

    @Test
    void refreshShouldRejectDisabledUser() throws Exception {
        registerAndVerifyDefaultUser();

        String plainRefreshToken = loginDefaultUserWithDeviceAndExtractRefreshToken();

        User user = defaultUser();

        user.setUserIsEnabled(false);
        userRepository.save(user);

        ResultActions resultActions = performRefresh(plainRefreshToken)
                .andExpect(status().isUnauthorized());

        assertRefreshCookieCleared(resultActions);

        assertThat(userSessionRepository.findAll()).hasSize(1);
        assertThat(refreshTokenRepository.findAll()).hasSize(1);

        RefreshToken refreshToken = refreshTokenRepository.findAll().getFirst();

        assertThat(refreshToken.getRefreshTokenUsedAt()).isNull();
        assertThat(refreshToken.getRefreshTokenRevokedAt()).isNull();
    }

    @Test
    void refreshShouldRejectSoftDeletedUser() throws Exception {
        registerAndVerifyDefaultUser();

        String plainRefreshToken = loginDefaultUserWithDeviceAndExtractRefreshToken();

        User user = defaultUser();

        user.markAsSoftDeleted();
        userRepository.save(user);

        ResultActions resultActions = performRefresh(plainRefreshToken)
                .andExpect(status().isUnauthorized());

        assertRefreshCookieCleared(resultActions);

        assertThat(userSessionRepository.findAll()).hasSize(1);
        assertThat(refreshTokenRepository.findAll()).hasSize(1);

        RefreshToken refreshToken = refreshTokenRepository.findAll().getFirst();

        assertThat(refreshToken.getRefreshTokenUsedAt()).isNull();
        assertThat(refreshToken.getRefreshTokenRevokedAt()).isNull();
    }

    @Test
    void meShouldRejectMissingAccessToken() throws Exception {
        mockMvc.perform(get("/api/me")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meShouldReturnCurrentUserWithValidAccessToken() throws Exception {
        String accessToken = loginAndExtractAccessToken();

        performMe(accessToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.userName").value(DEFAULT_USER_NAME))
                .andExpect(jsonPath("$.email").value(DEFAULT_EMAIL))
                .andExpect(jsonPath("$.userGroupId").exists())
                .andExpect(jsonPath("$.userGroupName").value(DEFAULT_GROUP_NAME))
                .andExpect(jsonPath("$.userRole").value("OWNER"))
                .andExpect(jsonPath("$.userPlatformRole").value("USER"))
                .andExpect(jsonPath("$.preferredLocale").value(DEFAULT_LOCALE))
                .andExpect(jsonPath("$.preferredTheme").value("DEFAULT"))
                .andExpect(jsonPath("$.wantsInvoice").value(false));
    }

    @Test
    void meShouldRejectUnsupportedAuthorizationScheme() throws Exception {
        mockMvc.perform(get("/api/me")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, "Basic abc123"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meShouldRejectBlankBearerToken() throws Exception {
        mockMvc.perform(get("/api/me")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meShouldRejectTamperedAccessToken() throws Exception {
        String accessToken = loginAndExtractAccessToken();

        String tamperedAccessToken = tamperToken(accessToken);

        assertThat(tamperedAccessToken).isNotEqualTo(accessToken);

        performMe(tamperedAccessToken)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meShouldRejectAccessTokenAfterTokenVersionChanged() throws Exception {
        String accessToken = loginAndExtractAccessToken();

        User user = defaultUser();

        jdbcTemplate.update("""
                update users
                set token_version = token_version + 1,
                    user_updated_at = now()
                where user_id = ?
                """, user.getUserId());

        performMe(accessToken)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meShouldRejectDisabledUser() throws Exception {
        String accessToken = loginAndExtractAccessToken();

        User user = defaultUser();

        user.setUserIsEnabled(false);
        userRepository.saveAndFlush(user);

        performMe(accessToken)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meShouldRejectSoftDeletedUser() throws Exception {
        String accessToken = loginAndExtractAccessToken();

        User user = defaultUser();

        user.markAsSoftDeleted();
        userRepository.saveAndFlush(user);

        performMe(accessToken)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meShouldReturnUpdatedUserDataFromDatabase() throws Exception {
        String accessToken = loginAndExtractAccessToken();

        User user = defaultUser();

        jdbcTemplate.update("""
                update users
                set user_platform_role = 'ADMIN',
                    preferred_theme = 'DARK',
                    user_updated_at = now()
                where user_id = ?
                """, user.getUserId());

        performMe(accessToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(DEFAULT_EMAIL))
                .andExpect(jsonPath("$.userRole").value("OWNER"))
                .andExpect(jsonPath("$.userPlatformRole").value("ADMIN"))
                .andExpect(jsonPath("$.preferredTheme").value("DARK"));
    }

    @Test
    void refreshShouldIgnoreInvalidAuthorizationHeaderAndUseRefreshCookie() throws Exception {
        registerAndVerifyDefaultUser();

        String plainRefreshToken = loginDefaultUserWithDeviceAndExtractRefreshToken();

        performRefreshWithAuthorization(plainRefreshToken, "Bearer invalid-access-token")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(REFRESH_COOKIE_NAME + "=")));
    }

    @Test
    void logoutShouldRevokeCurrentRefreshTokenAndSessionAndClearCookie() throws Exception {
        registerAndVerifyDefaultUser();

        String plainRefreshToken = loginDefaultUserWithDeviceAndExtractRefreshToken();

        ResultActions logoutResult = performLogout(plainRefreshToken)
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertRefreshCookieCleared(logoutResult);

        assertThat(userSessionRepository.findAll()).hasSize(1);
        assertThat(refreshTokenRepository.findAll()).hasSize(1);

        UserSession session = userSessionRepository.findAll().getFirst();
        RefreshToken refreshToken = refreshTokenRepository.findAll().getFirst();

        assertThat(session.getSessionRevokedAt()).isNotNull();
        assertThat(session.getSessionRevokeReason())
                .isEqualTo(SessionRevokeReason.USER_LOGOUT);

        assertThat(refreshToken.getRefreshTokenRevokedAt()).isNotNull();
        assertThat(refreshToken.getRefreshTokenRevokeReason())
                .isEqualTo(RefreshTokenRevokeReason.USER_LOGOUT);
        assertThat(refreshToken.getRefreshTokenUsedAt()).isNull();
    }

    @Test
    void refreshAfterLogoutShouldBeRejected() throws Exception {
        registerAndVerifyDefaultUser();

        String plainRefreshToken = loginDefaultUserWithDeviceAndExtractRefreshToken();

        performLogout(plainRefreshToken)
                .andExpect(status().isNoContent());

        ResultActions refreshResult = performRefresh(plainRefreshToken)
                .andExpect(status().isUnauthorized());

        assertRefreshCookieCleared(refreshResult);
    }

    @Test
    void logoutShouldSucceedWithoutCookieAndClearCookie() throws Exception {
        ResultActions logoutResult = performLogoutWithoutCookie()
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertRefreshCookieCleared(logoutResult);

        assertThat(userSessionRepository.findAll()).isEmpty();
        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    @Test
    void logoutShouldSucceedWithUnknownRefreshTokenAndClearCookie() throws Exception {
        ResultActions logoutResult = performLogout("unknown-refresh-token")
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertRefreshCookieCleared(logoutResult);

        assertThat(userSessionRepository.findAll()).isEmpty();
        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    @Test
    void logoutShouldSucceedWithAlreadyRevokedRefreshToken() throws Exception {
        registerAndVerifyDefaultUser();

        String plainRefreshToken = loginDefaultUserWithDeviceAndExtractRefreshToken();

        RefreshToken refreshToken = refreshTokenRepository.findAll().getFirst();
        refreshToken.revoke(RefreshTokenRevokeReason.USER_LOGOUT);
        refreshTokenRepository.save(refreshToken);

        ResultActions logoutResult = performLogout(plainRefreshToken)
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertRefreshCookieCleared(logoutResult);

        assertThat(refreshTokenRepository.findAll()).hasSize(1);

        RefreshToken reloadedToken = refreshTokenRepository.findAll().getFirst();

        assertThat(reloadedToken.getRefreshTokenRevokedAt()).isNotNull();
        assertThat(reloadedToken.getRefreshTokenRevokeReason())
                .isEqualTo(RefreshTokenRevokeReason.USER_LOGOUT);
    }

    @Test
    void logoutShouldIgnoreInvalidAuthorizationHeaderAndUseRefreshCookie() throws Exception {
        registerAndVerifyDefaultUser();

        String plainRefreshToken = loginDefaultUserWithDeviceAndExtractRefreshToken();

        ResultActions logoutResult = performLogoutWithAuthorization(
                plainRefreshToken,
                "Bearer invalid-access-token"
        )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertRefreshCookieCleared(logoutResult);

        UserSession session = userSessionRepository.findAll().getFirst();
        RefreshToken refreshToken = refreshTokenRepository.findAll().getFirst();

        assertThat(session.getSessionRevokedAt()).isNotNull();
        assertThat(session.getSessionRevokeReason())
                .isEqualTo(SessionRevokeReason.USER_LOGOUT);

        assertThat(refreshToken.getRefreshTokenRevokedAt()).isNotNull();
        assertThat(refreshToken.getRefreshTokenRevokeReason())
                .isEqualTo(RefreshTokenRevokeReason.USER_LOGOUT);
    }

    @Test
    void logoutShouldRevokeRefreshTokenButNotAlreadyIssuedAccessToken() throws Exception {
        registerAndVerifyDefaultUser();

        MvcResult loginResult = performDefaultLoginWithDevice()
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = extractAccessToken(loginResult);
        String plainRefreshToken = extractRefreshCookie(loginResult);

        performLogout(plainRefreshToken)
                .andExpect(status().isNoContent());

        performRefresh(plainRefreshToken)
                .andExpect(status().isUnauthorized());

        performMe(accessToken)
                .andExpect(status().isOk());
    }

    @Test
    void logoutAllShouldRevokeAllSessionsAndRefreshTokensAndInvalidateAccessTokens() throws Exception {
        registerAndVerifyDefaultUser();

        MvcResult firstLoginResult = performDefaultLoginWithDevice()
                .andExpect(status().isOk())
                .andReturn();

        String firstAccessToken = extractAccessToken(firstLoginResult);
        String firstRefreshToken = extractRefreshCookie(firstLoginResult);

        MvcResult secondLoginResult = performLoginWithDevice(
                DEFAULT_EMAIL,
                DEFAULT_PASSWORD,
                "Second test device"
        )
                .andExpect(status().isOk())
                .andReturn();

        String secondRefreshToken = extractRefreshCookie(secondLoginResult);

        User userBeforeLogoutAll = defaultUser();
        Long tokenVersionBeforeLogoutAll = userBeforeLogoutAll.getTokenVersion();

        ResultActions logoutAllResult = performLogoutAll(firstAccessToken)
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertRefreshCookieCleared(logoutAllResult);

        User userAfterLogoutAll = defaultUser();

        assertThat(userAfterLogoutAll.getTokenVersion())
                .isEqualTo(tokenVersionBeforeLogoutAll + 1);

        assertThat(userSessionRepository.findAll()).hasSize(2);
        assertThat(refreshTokenRepository.findAll()).hasSize(2);

        assertThat(userSessionRepository.findAll())
                .allSatisfy(session -> {
                    assertThat(session.getSessionRevokedAt()).isNotNull();
                    assertThat(session.getSessionRevokeReason())
                            .isEqualTo(SessionRevokeReason.USER_LOGOUT);
                });

        assertThat(refreshTokenRepository.findAll())
                .allSatisfy(refreshToken -> {
                    assertThat(refreshToken.getRefreshTokenRevokedAt()).isNotNull();
                    assertThat(refreshToken.getRefreshTokenRevokeReason())
                            .isEqualTo(RefreshTokenRevokeReason.USER_LOGOUT);
                });

        performMe(firstAccessToken)
                .andExpect(status().isUnauthorized());

        performRefresh(firstRefreshToken)
                .andExpect(status().isUnauthorized());

        performRefresh(secondRefreshToken)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutAllShouldRequireAccessToken() throws Exception {
        registerAndVerifyDefaultUser();

        String refreshToken = loginDefaultUserWithDeviceAndExtractRefreshToken();

        performLogoutAllWithoutAccessToken()
                .andExpect(status().isUnauthorized());

        performRefresh(refreshToken)
                .andExpect(status().isOk());
    }

    private void registerValidUser(String email) throws Exception {
        performRegister(
                IT_LOCALE,
                DEFAULT_USER_NAME,
                email,
                IT_TEST_PASSWORD,
                IT_LOCALE
        )
                .andExpect(status().isCreated());
    }

    private String uniqueEmail(String prefix) {
        return "%s.%s@example.com".formatted(
                prefix,
                UUID.randomUUID().toString().replace("-", "")
        );
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

    private String loginAndExtractRestoreToken() throws Exception {
        MvcResult result = performDefaultLogin()
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.restoreToken").exists())
                .andExpect(jsonPath("$.restoreTokenExpiresAt").exists())
                .andReturn();

        return jsonPathString(result, "$.restoreToken");
    }

    private String loginAndExtractEmailVerificationResendToken() throws Exception {
        MvcResult result = performDefaultLogin()
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.email").value(DEFAULT_EMAIL))
                .andExpect(jsonPath("$.emailVerificationResendToken").exists())
                .andExpect(jsonPath("$.emailVerificationResendTokenExpiresAt").exists())
                .andExpect(jsonPath("$.emailVerificationResendAvailableAt").exists())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andReturn();

        return jsonPathString(result, "$.emailVerificationResendToken");
    }

    private void makeEmailVerificationCooldownExpired(String email) {
        jdbcTemplate.update("""
                update auth_action_tokens
                set auth_action_created_at = now() - interval '2 minutes'
                where user_id = (
                    select user_id
                    from users
                    where email = ?
                )
                and auth_action_token_type = 'EMAIL_VERIFICATION'
                and auth_action_used_at is null
                and auth_action_revoked_at is null
                """, email);
    }

    private String loginAndExtractAccessToken() throws Exception {
        registerAndVerifyDefaultUser();

        MvcResult loginResult = performDefaultLoginWithDevice()
                .andExpect(status().isOk())
                .andReturn();

        return extractAccessToken(loginResult);
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

    private ResultActions performDefaultLogin() throws Exception {
        return performLogin(
                DEFAULT_EMAIL,
                DEFAULT_PASSWORD
        );
    }

    private ResultActions performDefaultLoginWithDevice() throws Exception {
        return performLoginWithDevice(
                DEFAULT_EMAIL,
                DEFAULT_PASSWORD,
                DEFAULT_DEVICE_LABEL
        );
    }

    private ResultActions performDefaultLoginWithUserAgent() throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .header(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT)
                .content("""
                        {
                          "email": "%s",
                          "password": "%s"
                        }
                        """.formatted(
                        DEFAULT_EMAIL,
                        DEFAULT_PASSWORD
                )));
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

    private ResultActions performRegisterWithWantsInvoice(
            String userName,
            String email,
            String password,
            String preferredLocale,
            boolean wantsInvoice
    ) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, preferredLocale)
                .content("""
                        {
                          "userName": "%s",
                          "email": "%s",
                          "password": "%s",
                          "preferredLocale": "%s",
                          "wantsInvoice": %s
                        }
                        """.formatted(
                        userName,
                        email,
                        password,
                        preferredLocale,
                        wantsInvoice
                )));
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

    private ResultActions performRefresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .cookie(new Cookie(REFRESH_COOKIE_NAME, refreshToken)));
    }

    private ResultActions performRefreshWithAuthorization(
            String refreshToken,
            String authorizationHeader
    ) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .cookie(new Cookie(REFRESH_COOKIE_NAME, refreshToken)));
    }

    private ResultActions performRestoreAccount(String restoreToken) throws Exception {
        return mockMvc.perform(post("/api/auth/restore-account")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .content("""
                        {
                          "restoreToken": "%s"
                        }
                        """.formatted(restoreToken)));
    }

    private ResultActions performResendEmailVerification(String resendToken) throws Exception {
        return mockMvc.perform(post("/api/auth/resend-email-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .content("""
                        {
                          "emailVerificationResendToken": "%s"
                        }
                        """.formatted(resendToken)));
    }

    private ResultActions performMe(String accessToken) throws Exception {
        return mockMvc.perform(get("/api/me")
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    private void registerAndVerifyDefaultUser() throws Exception {
        String verificationToken = registerAndExtractVerificationToken();
        verifyEmail(verificationToken);
    }

    private User defaultUser() {
        return userRepository.findByEmailAndUserDeletedAtIsNull(DEFAULT_EMAIL)
                .orElseThrow();
    }

    private MvcResult loginDefaultUserWithDevice() throws Exception {
        return performDefaultLoginWithDevice()
                .andExpect(status().isOk())
                .andReturn();
    }

    private String loginDefaultUserWithDeviceAndExtractRefreshToken() throws Exception {
        MvcResult loginResult = loginDefaultUserWithDevice();

        return extractRefreshCookie(loginResult);
    }

    private String extractRefreshCookie(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);

        return extractCookieValue(setCookie, REFRESH_COOKIE_NAME);
    }

    private String extractAccessToken(MvcResult result) throws Exception {
        return jsonPathString(result, "$.accessToken");
    }

    private void assertRefreshCookieCleared(ResultActions resultActions) throws Exception {
        resultActions
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(REFRESH_COOKIE_NAME + "=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/auth")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")));
    }

    private void assertRefreshCookieIssued(ResultActions resultActions) throws Exception {
        resultActions
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(REFRESH_COOKIE_NAME + "=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/auth")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")));
    }

    private void assertRefreshCookieIssued(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertThat(setCookie).isNotBlank();
        assertThat(setCookie).contains(REFRESH_COOKIE_NAME + "=");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("Path=/api/auth");
        assertThat(setCookie).contains("SameSite=Lax");
    }

    private ResultActions performLogout(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .cookie(new Cookie(REFRESH_COOKIE_NAME, refreshToken)));
    }

    private ResultActions performLogoutWithoutCookie() throws Exception {
        return mockMvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE));
    }

    private ResultActions performLogoutWithAuthorization(
            String refreshToken,
            String authorizationHeader
    ) throws Exception {
        return mockMvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .cookie(new Cookie(REFRESH_COOKIE_NAME, refreshToken)));
    }

    private ResultActions performLogoutAll(String accessToken) throws Exception {
        return mockMvc.perform(post("/api/auth/logout-all")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    private ResultActions performLogoutAllWithoutAccessToken() throws Exception {
        return mockMvc.perform(post("/api/auth/logout-all")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE));
    }
}