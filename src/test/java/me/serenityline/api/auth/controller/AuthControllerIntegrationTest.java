package me.serenityline.api.auth.controller;

import me.serenityline.api.auth.entity.*;
import me.serenityline.api.auth.repository.AuthActionTokenRepository;
import me.serenityline.api.auth.repository.EmailOutboxRepository;
import me.serenityline.api.auth.service.EmailVerificationService;
import me.serenityline.api.security.crypto.EmailOutboxEncryptionService;
import me.serenityline.api.security.crypto.EncryptedValue;
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
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
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
    void loginShouldRejectUnverifiedEmail() throws Exception {
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
                .andExpect(jsonPath("$.code").value("auth.login.emailNotVerified"));
    }

    @Test
    void loginShouldAuthenticateVerifiedUser() throws Exception {
        String token = registerAndExtractVerificationToken();

        verifyEmail(token);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .content("""
                                {
                                  "email": "samuel@example.com",
                                  "password": "VeryStrongPassword-2026-SerenityLine!"
                                }
                                """))
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

        User user = userRepository.findByEmailAndUserDeletedAtIsNull("samuel@example.com")
                .orElseThrow();

        assertThat(user.getUserLastLoginAt()).isNotNull();
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
                .andExpect(jsonPath("$.code").value("auth.login.invalidCredentials"));
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
    void loginShouldReturnAccountPendingDeletionForSoftDeletedUserWithCorrectPassword() throws Exception {
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
                                  "password": "VeryStrongPassword-2026-SerenityLine!"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("auth.login.accountPendingDeletion"));
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
}