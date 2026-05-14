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

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerIntegrationTest extends IntegrationTestSupport {

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

    @Test
    void registerShouldCreateDisabledOwnerUserAndGroup() throws Exception {
        String email = uniqueEmail("valid");

        mockMvc.perform(post("/api/auth/register")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "it-IT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userName": "Samuel",
                                  "email": "%s",
                                  "password": "TrenoMareLuna2026!",
                                  "preferredLocale": "it-IT"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").isString())
                .andExpect(jsonPath("$.userName").value("Samuel"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.userGroupId").isString())
                .andExpect(jsonPath("$.userGroupName").value("Gruppo di Samuel"))
                .andExpect(jsonPath("$.userRole").value("OWNER"))
                .andExpect(jsonPath("$.preferredLocale").value("it-IT"))
                .andExpect(jsonPath("$.wantsInvoice").value(false))
                .andExpect(jsonPath("$.emailVerificationRequired").value(true))
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString("userPasswordHash"))));

        User savedUser = userRepository.findByEmailAndUserDeletedAtIsNull(email)
                .orElseThrow();

        assertThat(savedUser.getEmail()).isEqualTo(email);
        assertThat(savedUser.getUserName()).isEqualTo("Samuel");
        assertThat(savedUser.getUserRole()).isEqualTo(UserRole.OWNER);
        assertThat(savedUser.isUserIsEnabled()).isFalse();
        assertThat(savedUser.isWantsInvoice()).isFalse();
        assertThat(savedUser.getUserDeletedAt()).isNull();

        List<UserGroup> groups = userGroupRepository.findAll();

        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().getUserGroupName()).isEqualTo("Gruppo di Samuel");
    }

    @Test
    void registerShouldReturnConflictWhenEmailAlreadyExists() throws Exception {
        String email = uniqueEmail("duplicate");

        registerValidUser(email);

        mockMvc.perform(post("/api/auth/register")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "it-IT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userName": "Samuel Duplicate",
                                  "email": "%s",
                                  "password": "TrenoMareLuna2026!",
                                  "preferredLocale": "it-IT"
                                }
                                """.formatted(email)))
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

        mockMvc.perform(post("/api/auth/register")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userName": "Samuel Duplicate",
                                  "email": "%s",
                                  "password": "TrenoMareLuna2026!",
                                  "preferredLocale": "it-IT"
                                }
                                """.formatted(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("auth.register.emailAlreadyExists"))
                .andExpect(jsonPath("$.message").value("An account with this email already exists."));
    }

    @Test
    void registerShouldRejectWeakPassword() throws Exception {
        String email = uniqueEmail("weak");

        mockMvc.perform(post("/api/auth/register")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "it-IT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userName": "Weak Password User",
                                  "email": "%s",
                                  "password": "password12345",
                                  "preferredLocale": "it-IT"
                                }
                                """.formatted(email)))
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
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "it-IT")
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
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "it-IT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userName": "Default User",
                                  "email": "%s",
                                  "password": "MontagnaFiumeStella2026!"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.preferredLocale").value("it-IT"))
                .andExpect(jsonPath("$.wantsInvoice").value(false))
                .andExpect(jsonPath("$.userRole").value("OWNER"))
                .andExpect(jsonPath("$.emailVerificationRequired").value(true));
    }

    @Test
    void registerShouldUseEnglishGroupNameWhenPreferredLocaleIsEnglish() throws Exception {
        String email = uniqueEmail("english");

        mockMvc.perform(post("/api/auth/register")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "it-IT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userName": "John",
                                  "email": "%s",
                                  "password": "RiverMountainCloud2026!",
                                  "preferredLocale": "en-US"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.preferredLocale").value("en-US"))
                .andExpect(jsonPath("$.userGroupName").value("John's group"));
    }

    @Test
    void registerShouldNormalizeEmailToLowercase() throws Exception {
        String uniquePart = UUID.randomUUID().toString().replace("-", "");
        String rawEmail = "UPPER.%s@EXAMPLE.COM".formatted(uniquePart);
        String expectedEmail = "upper.%s@example.com".formatted(uniquePart);

        mockMvc.perform(post("/api/auth/register")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "it-IT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userName": "Upper Email",
                                  "email": "%s",
                                  "password": "SoleVentoMare2026!",
                                  "preferredLocale": "it-IT"
                                }
                                """.formatted(rawEmail)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(expectedEmail));

        assertThat(userRepository.findByEmailAndUserDeletedAtIsNull(expectedEmail)).isPresent();
    }

    @Test
    void corsPreflightShouldAllowConfiguredDevOrigin() throws Exception {
        mockMvc.perform(options("/api/auth/register")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")));
    }

    @Test
    void registerShouldCreateEmailVerificationTokenAndOutboxEmail() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .content("""
                                {
                                  "userName": "Samuel",
                                  "email": "samuel@example.com",
                                  "password": "VeryStrongPassword-2026-SerenityLine!",
                                  "preferredLocale": "en-US",
                                  "wantsInvoice": false
                                }
                                """))
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
        assertThat(emailOutbox.getRecipientEmail()).isEqualTo("samuel@example.com");
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
        assertThat(body).contains("http://localhost:5173/verify-email#token=");
        assertThat(body).contains("http://localhost:5173/verify-email");
        assertThat(body).contains("This link expires in 1 day.");
    }

    @Test
    void registerShouldStoreOnlyTokenHashMatchingEmailToken() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .content("""
                                {
                                  "userName": "Samuel",
                                  "email": "samuel@example.com",
                                  "password": "VeryStrongPassword-2026-SerenityLine!",
                                  "preferredLocale": "en-US"
                                }
                                """))
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
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userName": "Samuel",
                                  "email": "samuel@example.com",
                                  "password": "VeryStrongPassword-2026-SerenityLine!",
                                  "preferredLocale": "en-US"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userName": "Another Samuel",
                                  "email": "samuel@example.com",
                                  "password": "AnotherVeryStrongPassword-2026!",
                                  "preferredLocale": "en-US"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("auth.register.emailAlreadyExists"));

        assertThat(authActionTokenRepository.findAll()).hasSize(1);
        assertThat(emailOutboxRepository.findAll()).hasSize(1);
    }

    private void registerValidUser(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "it-IT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userName": "Samuel",
                                  "email": "%s",
                                  "password": "TrenoMareLuna2026!",
                                  "preferredLocale": "it-IT"
                                }
                                """.formatted(email)))
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

    @Test
    void createEmailVerificationShouldCancelPreviousPendingEmailsAndRevokePreviousTokens() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .content("""
                                {
                                  "userName": "Samuel",
                                  "email": "samuel@example.com",
                                  "password": "VeryStrongPassword-2026-SerenityLine!",
                                  "preferredLocale": "en-US"
                                }
                                """))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmailAndUserDeletedAtIsNull("samuel@example.com")
                .orElseThrow();

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
        assertThat(pendingEmail.getRecipientEmail()).isEqualTo("samuel@example.com");
    }

    @Test
    void verifyEmailShouldEnableUserAndMarkTokenAsUsed() throws Exception {
        String token = registerAndExtractVerificationToken();

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s"
                                }
                                """.formatted(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true));

        User user = userRepository.findByEmailAndUserDeletedAtIsNull("samuel@example.com")
                .orElseThrow();

        assertThat(user.isUserIsEnabled()).isTrue();

        AuthActionToken actionToken = authActionTokenRepository.findAll().getFirst();

        assertThat(actionToken.getAuthActionUsedAt()).isNotNull();
        assertThat(actionToken.getAuthActionRevokedAt()).isNull();
    }

    @Test
    void verifyEmailShouldRejectInvalidToken() throws Exception {
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .content("""
                                {
                                  "token": "invalid-token"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.emailVerification.invalidOrExpired"));
    }

    @Test
    void verifyEmailShouldRejectBlankToken() throws Exception {
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .content("""
                                {
                                  "token": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("auth.token.required"));
    }

    @Test
    void verifyEmailShouldBeIdempotentAfterSuccessfulVerification() throws Exception {
        String token = registerAndExtractVerificationToken();

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s"
                                }
                                """.formatted(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true));

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s"
                                }
                                """.formatted(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true));

        User user = userRepository.findByEmailAndUserDeletedAtIsNull("samuel@example.com")
                .orElseThrow();

        assertThat(user.isUserIsEnabled()).isTrue();

        AuthActionToken actionToken = authActionTokenRepository.findAll().getFirst();

        assertThat(actionToken.getAuthActionUsedAt()).isNotNull();
    }

    @Test
    void verifyEmailShouldRejectRevokedTokenAfterNewVerificationEmailIsCreated() throws Exception {
        String oldToken = registerAndExtractVerificationToken();

        User user = userRepository.findByEmailAndUserDeletedAtIsNull("samuel@example.com")
                .orElseThrow();

        emailVerificationService.createEmailVerification(user);

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .content("""
                                {
                                  "token": "%s"
                                }
                                """.formatted(oldToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.emailVerification.invalidOrExpired"));

        User reloadedUser = userRepository.findByEmailAndUserDeletedAtIsNull("samuel@example.com")
                .orElseThrow();

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

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .content("""
                                {
                                  "email": "samuel@example.com",
                                  "password": "VeryStrongPassword-2026-SerenityLine!"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.email").value("samuel@example.com"))
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
        String token = registerAndExtractVerificationToken();

        verifyEmail(token);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .header(HttpHeaders.USER_AGENT, "JUnit Browser")
                        .content("""
                                {
                                  "email": "samuel@example.com",
                                  "password": "VeryStrongPassword-2026-SerenityLine!",
                                  "deviceLabel": "Samuel test device"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("serenityline_refresh=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/auth")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")))
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.accessTokenExpiresAt").exists())
                .andExpect(jsonPath("$.user.userId").exists())
                .andExpect(jsonPath("$.user.userName").value("Samuel"))
                .andExpect(jsonPath("$.user.email").value("samuel@example.com"))
                .andExpect(jsonPath("$.user.userGroupId").exists())
                .andExpect(jsonPath("$.user.userGroupName").value("Samuel's group"))
                .andExpect(jsonPath("$.user.userRole").value("OWNER"))
                .andExpect(jsonPath("$.user.userPlatformRole").value("USER"))
                .andExpect(jsonPath("$.user.preferredLocale").value("en-US"))
                .andExpect(jsonPath("$.user.preferredTheme").value("DEFAULT"))
                .andExpect(jsonPath("$.user.wantsInvoice").value(false))
                .andReturn();

        User user = userRepository.findByEmailAndUserDeletedAtIsNull("samuel@example.com")
                .orElseThrow();

        assertThat(user.getUserLastLoginAt()).isNotNull();

        String responseBody = result.getResponse().getContentAsString();
        String accessToken = JsonPath.read(responseBody, "$.accessToken");

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
        assertThat(session.getUserAgent()).isEqualTo("JUnit Browser");
        assertThat(session.getDeviceLabel()).isEqualTo("Samuel test device");
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
        assertThat(setCookie).contains("serenityline_refresh=");
        assertThat(setCookie).doesNotContain(refreshToken.getRefreshTokenHash());
    }

    @Test
    void loginShouldAuthenticateVerifiedUserWithoutDeviceLabel() throws Exception {
        String token = registerAndExtractVerificationToken();

        verifyEmail(token);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .header(HttpHeaders.USER_AGENT, "JUnit Browser")
                        .content("""
                                {
                                  "email": "samuel@example.com",
                                  "password": "VeryStrongPassword-2026-SerenityLine!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("serenityline_refresh=")))
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.user.email").value("samuel@example.com"));

        UserSession session = userSessionRepository.findAll().getFirst();

        assertThat(session.getUserAgent()).isEqualTo("JUnit Browser");
        assertThat(session.getDeviceLabel()).isNull();
    }

    @Test
    void loginShouldRejectWrongPassword() throws Exception {
        String token = registerAndExtractVerificationToken();

        verifyEmail(token);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .content("""
                                {
                                  "email": "samuel@example.com",
                                  "password": "WrongPassword-2026!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.code").value("auth.login.invalidCredentials"));

        assertThat(userSessionRepository.findAll()).isEmpty();
        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    @Test
    void loginShouldRejectUnknownEmail() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .content("""
                                {
                                  "email": "missing@example.com",
                                  "password": "VeryStrongPassword-2026-SerenityLine!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.login.invalidCredentials"));
    }

    @Test
    void loginShouldReturnRestoreChallengeForSoftDeletedUserWithCorrectPassword() throws Exception {
        String token = registerAndExtractVerificationToken();

        verifyEmail(token);

        User user = userRepository.findByEmailAndUserDeletedAtIsNull("samuel@example.com")
                .orElseThrow();

        user.markAsSoftDeleted();
        userRepository.saveAndFlush(user);

        assertThat(user.isUserIsEnabled()).isTrue();
        assertThat(user.isPendingDeletion()).isTrue();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .content("""
                                {
                                  "email": "samuel@example.com",
                                  "password": "VeryStrongPassword-2026-SerenityLine!"
                                }
                                """))
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
        String token = registerAndExtractVerificationToken();

        verifyEmail(token);

        User user = userRepository.findByEmailAndUserDeletedAtIsNull("samuel@example.com")
                .orElseThrow();

        user.markAsSoftDeleted();
        userRepository.saveAndFlush(user);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .content("""
                                {
                                  "email": "samuel@example.com",
                                  "password": "WrongPassword-2026!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.login.invalidCredentials"));
    }

    @Test
    void loginShouldTreatBlankDeviceLabelAsNull() throws Exception {
        String token = registerAndExtractVerificationToken();

        verifyEmail(token);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .content("""
                                {
                                  "email": "samuel@example.com",
                                  "password": "VeryStrongPassword-2026-SerenityLine!",
                                  "deviceLabel": "   "
                                }
                                """))
                .andExpect(status().isOk());

        UserSession session = userSessionRepository.findAll().getFirst();

        assertThat(session.getDeviceLabel()).isNull();
    }

    @Test
    void restoreAccountShouldRestoreSoftDeletedUserAndMarkTokenAsUsed() throws Exception {
        String verificationToken = registerAndExtractVerificationToken();

        verifyEmail(verificationToken);

        User user = userRepository.findByEmailAndUserDeletedAtIsNull("samuel@example.com")
                .orElseThrow();

        user.markAsSoftDeleted();
        userRepository.saveAndFlush(user);

        String restoreToken = loginAndExtractRestoreToken();

        mockMvc.perform(post("/api/auth/restore-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .content("""
                                {
                                  "restoreToken": "%s"
                                }
                                """.formatted(restoreToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.userName").value("Samuel"))
                .andExpect(jsonPath("$.email").value("samuel@example.com"))
                .andExpect(jsonPath("$.userGroupId").exists())
                .andExpect(jsonPath("$.userGroupName").value("Samuel's group"))
                .andExpect(jsonPath("$.userRole").value("OWNER"))
                .andExpect(jsonPath("$.userPlatformRole").value("USER"))
                .andExpect(jsonPath("$.preferredLocale").value("en-US"))
                .andExpect(jsonPath("$.preferredTheme").value("DEFAULT"))
                .andExpect(jsonPath("$.wantsInvoice").value(false));

        User restoredUser = userRepository.findByEmailAndUserDeletedAtIsNull("samuel@example.com")
                .orElseThrow();

        assertThat(restoredUser.isPendingDeletion()).isFalse();
        assertThat(restoredUser.getUserDeletedAt()).isNull();
        assertThat(restoredUser.getUserLastLoginAt()).isNotNull();
        assertThat(restoredUser.isUserIsEnabled()).isTrue();
        assertThat(restoredUser.getUserLastLoginAt()).isNotNull();

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
        mockMvc.perform(post("/api/auth/restore-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .content("""
                                {
                                  "restoreToken": "invalid-token"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.restoreAccount.invalidOrExpired"));
    }

    @Test
    void restoreAccountShouldRejectAlreadyUsedRestoreToken() throws Exception {
        String verificationToken = registerAndExtractVerificationToken();

        verifyEmail(verificationToken);

        User user = userRepository.findByEmailAndUserDeletedAtIsNull("samuel@example.com")
                .orElseThrow();

        user.markAsSoftDeleted();
        userRepository.saveAndFlush(user);

        String restoreToken = loginAndExtractRestoreToken();

        mockMvc.perform(post("/api/auth/restore-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "restoreToken": "%s"
                                }
                                """.formatted(restoreToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/restore-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .content("""
                                {
                                  "restoreToken": "%s"
                                }
                                """.formatted(restoreToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.restoreAccount.invalidOrExpired"));
    }

    @Test
    void loginShouldRevokePreviousRestoreTokenWhenCreatingNewRestoreChallenge() throws Exception {
        String verificationToken = registerAndExtractVerificationToken();

        verifyEmail(verificationToken);

        User user = userRepository.findByEmailAndUserDeletedAtIsNull("samuel@example.com")
                .orElseThrow();

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

        mockMvc.perform(post("/api/auth/restore-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .content("""
                                {
                                  "restoreToken": "%s"
                                }
                                """.formatted(firstRestoreToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.restoreAccount.invalidOrExpired"));
    }

    @Test
    void loginShouldReturnRestoreChallengeForSoftDeletedUnverifiedUserWithCorrectPassword() throws Exception {
        registerAndExtractVerificationToken();

        User user = userRepository.findByEmailAndUserDeletedAtIsNull("samuel@example.com")
                .orElseThrow();

        assertThat(user.isUserIsEnabled()).isFalse();

        user.markAsSoftDeleted();
        userRepository.saveAndFlush(user);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .content("""
                                {
                                  "email": "samuel@example.com",
                                  "password": "VeryStrongPassword-2026-SerenityLine!"
                                }
                                """))
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

        User user = userRepository.findByEmailAndUserDeletedAtIsNull("samuel@example.com")
                .orElseThrow();

        assertThat(user.isUserIsEnabled()).isFalse();
        assertThat(user.getUserLastLoginAt()).isNull();

        user.markAsSoftDeleted();
        userRepository.saveAndFlush(user);

        String restoreToken = loginAndExtractRestoreToken();

        mockMvc.perform(post("/api/auth/restore-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .content("""
                                {
                                  "restoreToken": "%s"
                                }
                                """.formatted(restoreToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.email").value("samuel@example.com"))
                .andExpect(jsonPath("$.emailVerificationResendToken").exists())
                .andExpect(jsonPath("$.emailVerificationResendTokenExpiresAt").exists())
                .andExpect(jsonPath("$.emailVerificationResendAvailableAt").exists())
                .andExpect(jsonPath("$.preferredLocale").doesNotExist())
                .andExpect(jsonPath("$.emailVerificationRequired").doesNotExist())
                .andExpect(jsonPath("$.userName").doesNotExist())
                .andExpect(jsonPath("$.userGroupId").doesNotExist())
                .andExpect(jsonPath("$.userRole").doesNotExist())
                .andExpect(jsonPath("$.userPlatformRole").doesNotExist());

        User restoredUser = userRepository.findByEmailAndUserDeletedAtIsNull("samuel@example.com")
                .orElseThrow();

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
        mockMvc.perform(post("/api/auth/resend-email-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .content("""
                                {
                                  "emailVerificationResendToken": "invalid-token"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.emailVerificationResend.invalidOrExpired"));
    }

    @Test
    void resendEmailVerificationShouldRejectWhenCooldownHasNotExpired() throws Exception {
        registerAndExtractVerificationToken();

        String resendToken = loginAndExtractEmailVerificationResendToken();

        mockMvc.perform(post("/api/auth/resend-email-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .content("""
                                {
                                  "emailVerificationResendToken": "%s"
                                }
                                """.formatted(resendToken)))
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

        makeEmailVerificationCooldownExpired("samuel@example.com");

        MvcResult result = mockMvc.perform(post("/api/auth/resend-email-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .content("""
                                {
                                  "emailVerificationResendToken": "%s"
                                }
                                """.formatted(firstResendToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.email").value("samuel@example.com"))
                .andExpect(jsonPath("$.emailVerificationResendToken").exists())
                .andExpect(jsonPath("$.emailVerificationResendTokenExpiresAt").exists())
                .andExpect(jsonPath("$.emailVerificationResendAvailableAt").exists())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andReturn();

        String secondResendToken = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.emailVerificationResendToken"
        );

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

        makeEmailVerificationCooldownExpired("samuel@example.com");

        mockMvc.perform(post("/api/auth/resend-email-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "emailVerificationResendToken": "%s"
                                }
                                """.formatted(resendToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/resend-email-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .content("""
                                {
                                  "emailVerificationResendToken": "%s"
                                }
                                """.formatted(resendToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.emailVerificationResend.invalidOrExpired"));
    }

    @Test
    void resendEmailVerificationShouldRejectWhenUserIsAlreadyVerified() throws Exception {
        String verificationToken = registerAndExtractVerificationToken();

        String resendToken = loginAndExtractEmailVerificationResendToken();

        verifyEmail(verificationToken);

        mockMvc.perform(post("/api/auth/resend-email-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .content("""
                                {
                                  "emailVerificationResendToken": "%s"
                                }
                                """.formatted(resendToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("auth.emailVerification.userAlreadyVerified"));
    }

    @Test
    void refreshShouldRejectMissingCookie() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("serenityline_refresh=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/auth")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")));
    }

    @Test
    void refreshShouldRotateRefreshTokenAndIssueNewAccessToken() throws Exception {
        String emailVerificationToken = registerAndExtractVerificationToken();

        verifyEmail(emailVerificationToken);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .header(HttpHeaders.USER_AGENT, "JUnit Browser")
                        .content("""
                                {
                                  "email": "samuel@example.com",
                                  "password": "VeryStrongPassword-2026-SerenityLine!",
                                  "deviceLabel": "Samuel test device"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("serenityline_refresh=")))
                .andReturn();

        String loginSetCookie = loginResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        String firstPlainRefreshToken = extractCookieValue(loginSetCookie, "serenityline_refresh");

        assertThat(refreshTokenRepository.findAll()).hasSize(1);

        RefreshToken firstRefreshToken = refreshTokenRepository.findAll().getFirst();

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .cookie(new Cookie("serenityline_refresh", firstPlainRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("serenityline_refresh=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/auth")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")))
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.accessTokenExpiresAt").exists())
                .andExpect(jsonPath("$.user.userId").exists())
                .andExpect(jsonPath("$.user.email").value("samuel@example.com"))
                .andExpect(jsonPath("$.user.userRole").value("OWNER"))
                .andExpect(jsonPath("$.user.userPlatformRole").value("USER"))
                .andReturn();

        String refreshSetCookie = refreshResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        String secondPlainRefreshToken = extractCookieValue(refreshSetCookie, "serenityline_refresh");

        assertThat(secondPlainRefreshToken).isNotBlank();
        assertThat(secondPlainRefreshToken).isNotEqualTo(firstPlainRefreshToken);

        User user = userRepository.findByEmailAndUserDeletedAtIsNull("samuel@example.com")
                .orElseThrow();

        String responseBody = refreshResult.getResponse().getContentAsString();
        String accessToken = JsonPath.read(responseBody, "$.accessToken");

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
        String emailVerificationToken = registerAndExtractVerificationToken();

        verifyEmail(emailVerificationToken);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .header(HttpHeaders.USER_AGENT, "JUnit Browser")
                        .content("""
                                {
                                  "email": "samuel@example.com",
                                  "password": "VeryStrongPassword-2026-SerenityLine!",
                                  "deviceLabel": "Samuel test device"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String loginSetCookie = loginResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        String firstPlainRefreshToken = extractCookieValue(loginSetCookie, "serenityline_refresh");

        MvcResult firstRefreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .cookie(new Cookie("serenityline_refresh", firstPlainRefreshToken)))
                .andExpect(status().isOk())
                .andReturn();

        String firstRefreshSetCookie = firstRefreshResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        String secondPlainRefreshToken = extractCookieValue(firstRefreshSetCookie, "serenityline_refresh");

        assertThat(secondPlainRefreshToken).isNotBlank();
        assertThat(secondPlainRefreshToken).isNotEqualTo(firstPlainRefreshToken);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .cookie(new Cookie("serenityline_refresh", firstPlainRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("serenityline_refresh=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/auth")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")));

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
        String emailVerificationToken = registerAndExtractVerificationToken();

        verifyEmail(emailVerificationToken);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .header(HttpHeaders.USER_AGENT, "JUnit Browser")
                        .content("""
                                {
                                  "email": "samuel@example.com",
                                  "password": "VeryStrongPassword-2026-SerenityLine!",
                                  "deviceLabel": "Samuel test device"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String loginSetCookie = loginResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        String firstPlainRefreshToken = extractCookieValue(loginSetCookie, "serenityline_refresh");

        MvcResult firstRefreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .cookie(new Cookie("serenityline_refresh", firstPlainRefreshToken)))
                .andExpect(status().isOk())
                .andReturn();

        String firstRefreshSetCookie = firstRefreshResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        String secondPlainRefreshToken = extractCookieValue(firstRefreshSetCookie, "serenityline_refresh");

        assertThat(secondPlainRefreshToken).isNotBlank();
        assertThat(secondPlainRefreshToken).isNotEqualTo(firstPlainRefreshToken);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .cookie(new Cookie("serenityline_refresh", firstPlainRefreshToken)))
                .andExpect(status().isUnauthorized());

        UserSession sessionAfterReuse = userSessionRepository.findAll().getFirst();

        assertThat(sessionAfterReuse.getSessionRevokedAt()).isNotNull();
        assertThat(sessionAfterReuse.getSessionRevokeReason())
                .isEqualTo(SessionRevokeReason.TOKEN_REUSE_DETECTED);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .cookie(new Cookie("serenityline_refresh", secondPlainRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("serenityline_refresh=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/auth")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")));

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
        String unknownRefreshToken = "unknown-refresh-token";

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .cookie(new Cookie("serenityline_refresh", unknownRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("serenityline_refresh=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/auth")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")));

        assertThat(userSessionRepository.findAll()).isEmpty();
        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    @Test
    void refreshShouldRejectRevokedRefreshToken() throws Exception {
        String emailVerificationToken = registerAndExtractVerificationToken();

        verifyEmail(emailVerificationToken);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .header(HttpHeaders.USER_AGENT, "JUnit Browser")
                        .content("""
                                {
                                  "email": "samuel@example.com",
                                  "password": "VeryStrongPassword-2026-SerenityLine!",
                                  "deviceLabel": "Samuel test device"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String loginSetCookie = loginResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        String plainRefreshToken = extractCookieValue(loginSetCookie, "serenityline_refresh");

        RefreshToken refreshToken = refreshTokenRepository.findAll().getFirst();
        refreshToken.revoke(RefreshTokenRevokeReason.USER_LOGOUT);
        refreshTokenRepository.save(refreshToken);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .cookie(new Cookie("serenityline_refresh", plainRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("serenityline_refresh=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/auth")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")));

        assertThat(refreshTokenRepository.findAll()).hasSize(1);

        RefreshToken rejectedToken = refreshTokenRepository.findAll().getFirst();

        assertThat(rejectedToken.getRefreshTokenUsedAt()).isNull();
        assertThat(rejectedToken.getRefreshTokenRevokedAt()).isNotNull();
        assertThat(rejectedToken.getRefreshTokenRevokeReason())
                .isEqualTo(RefreshTokenRevokeReason.USER_LOGOUT);
    }

    @Test
    void refreshShouldRejectRevokedSession() throws Exception {
        String emailVerificationToken = registerAndExtractVerificationToken();

        verifyEmail(emailVerificationToken);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .header(HttpHeaders.USER_AGENT, "JUnit Browser")
                        .content("""
                                {
                                  "email": "samuel@example.com",
                                  "password": "VeryStrongPassword-2026-SerenityLine!",
                                  "deviceLabel": "Samuel test device"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String loginSetCookie = loginResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        String plainRefreshToken = extractCookieValue(loginSetCookie, "serenityline_refresh");

        UserSession session = userSessionRepository.findAll().getFirst();
        session.revoke(SessionRevokeReason.USER_LOGOUT);
        userSessionRepository.save(session);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .cookie(new Cookie("serenityline_refresh", plainRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("serenityline_refresh=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/auth")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")));

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
        String emailVerificationToken = registerAndExtractVerificationToken();

        verifyEmail(emailVerificationToken);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .header(HttpHeaders.USER_AGENT, "JUnit Browser")
                        .content("""
                                {
                                  "email": "samuel@example.com",
                                  "password": "VeryStrongPassword-2026-SerenityLine!",
                                  "deviceLabel": "Samuel test device"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String loginSetCookie = loginResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        String plainRefreshToken = extractCookieValue(loginSetCookie, "serenityline_refresh");

        RefreshToken refreshToken = refreshTokenRepository.findAll().getFirst();

        jdbcTemplate.update("""
                update refresh_tokens
                set refresh_token_created_at = now() - interval '2 hours',
                    refresh_token_expires_at = now() - interval '1 hour'
                where refresh_token_id = ?
                """, refreshToken.getRefreshTokenId());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .cookie(new Cookie("serenityline_refresh", plainRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("serenityline_refresh=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/auth")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")));

        assertThat(refreshTokenRepository.findAll()).hasSize(1);

        RefreshToken rejectedToken = refreshTokenRepository.findAll().getFirst();

        assertThat(rejectedToken.getRefreshTokenUsedAt()).isNull();
        assertThat(rejectedToken.getRefreshTokenRevokedAt()).isNull();
    }

    @Test
    void refreshShouldRejectExpiredSession() throws Exception {
        String emailVerificationToken = registerAndExtractVerificationToken();

        verifyEmail(emailVerificationToken);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .header(HttpHeaders.USER_AGENT, "JUnit Browser")
                        .content("""
                                {
                                  "email": "samuel@example.com",
                                  "password": "VeryStrongPassword-2026-SerenityLine!",
                                  "deviceLabel": "Samuel test device"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String loginSetCookie = loginResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        String plainRefreshToken = extractCookieValue(loginSetCookie, "serenityline_refresh");

        UserSession session = userSessionRepository.findAll().getFirst();

        jdbcTemplate.update("""
                update user_sessions
                set session_created_at = now() - interval '2 hours',
                    session_last_seen_at = now() - interval '2 hours',
                    session_expires_at = now() - interval '1 hour'
                where user_session_id = ?
                """, session.getUserSessionId());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .cookie(new Cookie("serenityline_refresh", plainRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("serenityline_refresh=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/auth")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")));

        assertThat(userSessionRepository.findAll()).hasSize(1);
        assertThat(refreshTokenRepository.findAll()).hasSize(1);

        RefreshToken refreshToken = refreshTokenRepository.findAll().getFirst();

        assertThat(refreshToken.getRefreshTokenUsedAt()).isNull();
        assertThat(refreshToken.getRefreshTokenRevokedAt()).isNull();
    }

    @Test
    void refreshShouldRejectDisabledUser() throws Exception {
        String emailVerificationToken = registerAndExtractVerificationToken();

        verifyEmail(emailVerificationToken);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .header(HttpHeaders.USER_AGENT, "JUnit Browser")
                        .content("""
                                {
                                  "email": "samuel@example.com",
                                  "password": "VeryStrongPassword-2026-SerenityLine!",
                                  "deviceLabel": "Samuel test device"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String loginSetCookie = loginResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        String plainRefreshToken = extractCookieValue(loginSetCookie, "serenityline_refresh");

        User user = userRepository.findByEmailAndUserDeletedAtIsNull("samuel@example.com")
                .orElseThrow();

        user.setUserIsEnabled(false);
        userRepository.save(user);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .cookie(new Cookie("serenityline_refresh", plainRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("serenityline_refresh=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/auth")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")));

        assertThat(userSessionRepository.findAll()).hasSize(1);
        assertThat(refreshTokenRepository.findAll()).hasSize(1);

        RefreshToken refreshToken = refreshTokenRepository.findAll().getFirst();

        assertThat(refreshToken.getRefreshTokenUsedAt()).isNull();
        assertThat(refreshToken.getRefreshTokenRevokedAt()).isNull();
    }

    @Test
    void refreshShouldRejectSoftDeletedUser() throws Exception {
        String emailVerificationToken = registerAndExtractVerificationToken();

        verifyEmail(emailVerificationToken);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .header(HttpHeaders.USER_AGENT, "JUnit Browser")
                        .content("""
                                {
                                  "email": "samuel@example.com",
                                  "password": "VeryStrongPassword-2026-SerenityLine!",
                                  "deviceLabel": "Samuel test device"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String loginSetCookie = loginResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        String plainRefreshToken = extractCookieValue(loginSetCookie, "serenityline_refresh");

        User user = userRepository.findByEmailAndUserDeletedAtIsNull("samuel@example.com")
                .orElseThrow();

        user.markAsSoftDeleted();
        userRepository.save(user);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .cookie(new Cookie("serenityline_refresh", plainRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("serenityline_refresh=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/auth")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")));

        assertThat(userSessionRepository.findAll()).hasSize(1);
        assertThat(refreshTokenRepository.findAll()).hasSize(1);

        RefreshToken refreshToken = refreshTokenRepository.findAll().getFirst();

        assertThat(refreshToken.getRefreshTokenUsedAt()).isNull();
        assertThat(refreshToken.getRefreshTokenRevokedAt()).isNull();
    }


    private String registerAndExtractVerificationToken() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .content("""
                                {
                                  "userName": "Samuel",
                                  "email": "samuel@example.com",
                                  "password": "VeryStrongPassword-2026-SerenityLine!",
                                  "preferredLocale": "en-US"
                                }
                                """))
                .andExpect(status().isCreated());

        EmailOutbox emailOutbox = emailOutboxRepository.findAll().getFirst();

        return extractTokenFromBody(decryptTextBody(emailOutbox));
    }

    private void verifyEmail(String token) throws Exception {
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s"
                                }
                                """.formatted(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true));
    }

    private String loginAndExtractRestoreToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .content("""
                                {
                                  "email": "samuel@example.com",
                                  "password": "VeryStrongPassword-2026-SerenityLine!"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.restoreToken").exists())
                .andExpect(jsonPath("$.restoreTokenExpiresAt").exists())
                .andReturn();

        return JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.restoreToken"
        );
    }

    private String loginAndExtractEmailVerificationResendToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .content("""
                                {
                                  "email": "samuel@example.com",
                                  "password": "VeryStrongPassword-2026-SerenityLine!"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.email").value("samuel@example.com"))
                .andExpect(jsonPath("$.emailVerificationResendToken").exists())
                .andExpect(jsonPath("$.emailVerificationResendTokenExpiresAt").exists())
                .andExpect(jsonPath("$.emailVerificationResendAvailableAt").exists())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andReturn();

        return JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.emailVerificationResendToken"
        );
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
}