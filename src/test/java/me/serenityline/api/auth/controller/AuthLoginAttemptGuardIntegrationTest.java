package me.serenityline.api.auth.controller;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import me.serenityline.api.auth.repository.RefreshTokenRepository;
import me.serenityline.api.auth.repository.UserSessionRepository;
import me.serenityline.api.email.outbox.entity.EmailOutbox;
import me.serenityline.api.email.outbox.repository.EmailOutboxRepository;
import me.serenityline.api.security.crypto.EmailOutboxEncryptionService;
import me.serenityline.api.security.crypto.EncryptedValue;
import me.serenityline.api.security.crypto.SensitiveHashService;
import me.serenityline.api.support.IntegrationTestSupport;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties = {
        "serenityline.auth.login-attempt.window=15m",
        "serenityline.auth.login-attempt.max-failed-by-email-ip=2",
        "serenityline.auth.login-attempt.max-failed-by-email=4",
        "serenityline.auth.login-attempt.max-failed-by-ip=6",
        "serenityline.security.sensitive-hash.key-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
class AuthLoginAttemptGuardIntegrationTest extends IntegrationTestSupport {

    private static final String DEFAULT_EMAIL = "samuel@example.com";
    private static final String DEFAULT_PASSWORD = "VeryStrongPassword-2026-SerenityLine!";
    private static final String DEFAULT_USER_NAME = "Samuel";
    private static final String DEFAULT_LOCALE = "en-US";
    private static final String DEFAULT_USER_AGENT = "JUnit Browser";
    private static final String DEFAULT_DEVICE_LABEL = "Samuel test device";
    private static final String REFRESH_COOKIE_NAME = "serenityline_refresh";
    private static final String WRONG_PASSWORD = "WrongPassword-2026!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

    @Autowired
    private EmailOutboxEncryptionService emailOutboxEncryptionService;

    @Autowired
    private SensitiveHashService sensitiveHashService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private static RequestPostProcessor remoteAddr(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
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
    void loginWithWrongPasswordShouldRecordInvalidCredentialsAttempt() throws Exception {
        String ip = "203.0.113.10";

        registerAndVerifyDefaultUser();

        performLoginFromIp(DEFAULT_EMAIL, WRONG_PASSWORD, ip)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.login.invalidCredentials"));

        String emailHash = emailHash(DEFAULT_EMAIL);
        String ipHash = ipHash(ip);

        assertThat(countInvalidCredentials(emailHash, ipHash)).isEqualTo(1);
        assertThat(countRateLimited(emailHash, ipHash)).isZero();
        assertThat(countSuccessful(emailHash, ipHash)).isZero();
    }

    @Test
    void loginWithUnknownEmailShouldRecordInvalidCredentialsAttemptWithNullUser() throws Exception {
        String email = "missing@example.com";
        String ip = "203.0.113.11";

        performLoginFromIp(email, DEFAULT_PASSWORD, ip)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.login.invalidCredentials"));

        String emailHash = emailHash(email);
        String ipHash = ipHash(ip);

        assertThat(countInvalidCredentials(emailHash, ipHash)).isEqualTo(1);
        assertThat(countInvalidCredentialsWithNullUser(emailHash, ipHash)).isEqualTo(1);
    }

    @Test
    void tooManyWrongPasswordsForSameEmailAndIpShouldReturn429() throws Exception {
        String ip = "203.0.113.20";

        registerAndVerifyDefaultUser();

        performLoginFromIp(DEFAULT_EMAIL, "wrong-1", ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(DEFAULT_EMAIL, "wrong-2", ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(DEFAULT_EMAIL, "wrong-3", ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("auth.login.tooManyAttempts"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        String emailHash = emailHash(DEFAULT_EMAIL);
        String ipHash = ipHash(ip);

        assertThat(countInvalidCredentials(emailHash, ipHash)).isEqualTo(2);
        assertThat(countRateLimited(emailHash, ipHash)).isEqualTo(1);
    }

    @Test
    void rateLimitedLoginShouldNotCreateSessionOrRefreshToken() throws Exception {
        String ip = "203.0.113.21";

        registerAndVerifyDefaultUser();

        performLoginFromIp(DEFAULT_EMAIL, "wrong-1", ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(DEFAULT_EMAIL, "wrong-2", ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(DEFAULT_EMAIL, "wrong-3", ip)
                .andExpect(status().isTooManyRequests());

        assertThat(userSessionRepository.findAll()).isEmpty();
        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    @Test
    void correctCredentialsAfterFailuresShouldRecordSuccessfulAttempt() throws Exception {
        String ip = "203.0.113.30";

        registerAndVerifyDefaultUser();

        performLoginFromIp(DEFAULT_EMAIL, "wrong-1", ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(DEFAULT_EMAIL, "wrong-2", ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(DEFAULT_EMAIL, DEFAULT_PASSWORD, ip)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());

        String emailHash = emailHash(DEFAULT_EMAIL);
        String ipHash = ipHash(ip);

        assertThat(countSuccessful(emailHash, ipHash)).isEqualTo(1);
        assertThat(countInvalidCredentials(emailHash, ipHash)).isEqualTo(2);
    }

    @Test
    void successfulCredentialsShouldResetEmailIpCounterLogically() throws Exception {
        String ip = "203.0.113.31";

        registerAndVerifyDefaultUser();

        performLoginFromIp(DEFAULT_EMAIL, "wrong-1", ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(DEFAULT_EMAIL, "wrong-2", ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(DEFAULT_EMAIL, DEFAULT_PASSWORD, ip)
                .andExpect(status().isOk());

        performLoginFromIp(DEFAULT_EMAIL, "wrong-3", ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(DEFAULT_EMAIL, "wrong-4", ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(DEFAULT_EMAIL, "wrong-5", ip)
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void logoutShouldNotDeleteAttemptsAndPreviousSuccessShouldStillResetCounterLogically() throws Exception {
        String ip = "203.0.113.32";

        registerAndVerifyDefaultUser();

        performLoginFromIp(DEFAULT_EMAIL, "wrong-1", ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(DEFAULT_EMAIL, "wrong-2", ip)
                .andExpect(status().isBadRequest());

        MvcResult loginResult = performLoginWithDeviceFromIp(
                DEFAULT_EMAIL,
                DEFAULT_PASSWORD,
                DEFAULT_DEVICE_LABEL,
                ip
        )
                .andExpect(status().isOk())
                .andReturn();

        String refreshToken = extractRefreshCookie(loginResult);

        String emailHash = emailHash(DEFAULT_EMAIL);
        String ipHash = ipHash(ip);

        assertThat(countAttempts(emailHash, ipHash)).isEqualTo(3);

        performLogout(refreshToken)
                .andExpect(status().isNoContent());

        assertThat(countAttempts(emailHash, ipHash)).isEqualTo(3);

        performLoginFromIp(DEFAULT_EMAIL, "wrong-3", ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(DEFAULT_EMAIL, "wrong-4", ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(DEFAULT_EMAIL, "wrong-5", ip)
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void unverifiedUserWithCorrectCredentialsShouldRecordSuccessfulAttemptAndReturnVerificationChallenge() throws Exception {
        String ip = "203.0.113.40";

        performDefaultRegister()
                .andExpect(status().isCreated());

        performLoginFromIp(DEFAULT_EMAIL, DEFAULT_PASSWORD, ip)
                .andExpect(status().isConflict())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.email").value(DEFAULT_EMAIL))
                .andExpect(jsonPath("$.emailVerificationResendToken").exists())
                .andExpect(jsonPath("$.code").doesNotExist());

        String emailHash = emailHash(DEFAULT_EMAIL);
        String ipHash = ipHash(ip);

        assertThat(countSuccessful(emailHash, ipHash)).isEqualTo(1);
        assertThat(userSessionRepository.findAll()).isEmpty();
        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    @Test
    void softDeletedUserWithCorrectCredentialsShouldRecordSuccessfulAttemptAndReturnRestoreChallenge() throws Exception {
        String ip = "203.0.113.41";

        registerAndVerifyDefaultUser();

        User user = defaultActiveUser();

        user.markAsSoftDeleted();
        userRepository.saveAndFlush(user);

        performLoginFromIp(DEFAULT_EMAIL, DEFAULT_PASSWORD, ip)
                .andExpect(status().isConflict())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.restoreToken").exists())
                .andExpect(jsonPath("$.restoreTokenExpiresAt").exists())
                .andExpect(jsonPath("$.code").doesNotExist());

        String emailHash = emailHash(DEFAULT_EMAIL);
        String ipHash = ipHash(ip);

        assertThat(countSuccessful(emailHash, ipHash)).isEqualTo(1);
        assertThat(userSessionRepository.findAll()).isEmpty();
        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    @Test
    void differentEmailFromSameIpShouldNotBeBlockedByEmailIpLimit() throws Exception {
        String ip = "203.0.113.50";
        String firstEmail = "spray-a@example.com";
        String secondEmail = "spray-b@example.com";

        performLoginFromIp(firstEmail, WRONG_PASSWORD, ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(firstEmail, WRONG_PASSWORD, ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(secondEmail, WRONG_PASSWORD, ip)
                .andExpect(status().isBadRequest());

        assertThat(countInvalidCredentials(emailHash(firstEmail), ipHash(ip))).isEqualTo(2);
        assertThat(countInvalidCredentials(emailHash(secondEmail), ipHash(ip))).isEqualTo(1);
    }

    @Test
    void wrongCredentialsForSameEmailAcrossDifferentIpsShouldHitEmailLimit() throws Exception {
        registerAndVerifyDefaultUser();

        performLoginFromIp(DEFAULT_EMAIL, "wrong-1", "203.0.113.61")
                .andExpect(status().isBadRequest());

        performLoginFromIp(DEFAULT_EMAIL, "wrong-2", "203.0.113.62")
                .andExpect(status().isBadRequest());

        performLoginFromIp(DEFAULT_EMAIL, "wrong-3", "203.0.113.63")
                .andExpect(status().isBadRequest());

        performLoginFromIp(DEFAULT_EMAIL, "wrong-4", "203.0.113.64")
                .andExpect(status().isBadRequest());

        performLoginFromIp(DEFAULT_EMAIL, "wrong-5", "203.0.113.65")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("auth.login.tooManyAttempts"));
    }

    @Test
    void successfulCredentialsShouldResetEmailCounterLogically() throws Exception {
        registerAndVerifyDefaultUser();

        performLoginFromIp(DEFAULT_EMAIL, "wrong-1", "203.0.113.71")
                .andExpect(status().isBadRequest());

        performLoginFromIp(DEFAULT_EMAIL, "wrong-2", "203.0.113.72")
                .andExpect(status().isBadRequest());

        performLoginFromIp(DEFAULT_EMAIL, "wrong-3", "203.0.113.73")
                .andExpect(status().isBadRequest());

        performLoginFromIp(DEFAULT_EMAIL, "wrong-4", "203.0.113.74")
                .andExpect(status().isBadRequest());

        performLoginFromIp(DEFAULT_EMAIL, DEFAULT_PASSWORD, "203.0.113.75")
                .andExpect(status().isOk());

        performLoginFromIp(DEFAULT_EMAIL, "wrong-after-success", "203.0.113.76")
                .andExpect(status().isBadRequest());
    }

    @Test
    void wrongCredentialsForDifferentEmailsFromSameIpShouldHitIpLimit() throws Exception {
        String ip = "203.0.113.80";

        performLoginFromIp(uniqueEmail("spray1"), WRONG_PASSWORD, ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(uniqueEmail("spray2"), WRONG_PASSWORD, ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(uniqueEmail("spray3"), WRONG_PASSWORD, ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(uniqueEmail("spray4"), WRONG_PASSWORD, ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(uniqueEmail("spray5"), WRONG_PASSWORD, ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(uniqueEmail("spray6"), WRONG_PASSWORD, ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(uniqueEmail("spray7"), WRONG_PASSWORD, ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("auth.login.tooManyAttempts"));
    }

    @Test
    void successfulCredentialsShouldNotResetIpOnlyLimit() throws Exception {
        String ip = "203.0.113.81";

        registerAndVerifyDefaultUser();

        performLoginFromIp(uniqueEmail("spray1"), WRONG_PASSWORD, ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(uniqueEmail("spray2"), WRONG_PASSWORD, ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(uniqueEmail("spray3"), WRONG_PASSWORD, ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(uniqueEmail("spray4"), WRONG_PASSWORD, ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(uniqueEmail("spray5"), WRONG_PASSWORD, ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(uniqueEmail("spray6"), WRONG_PASSWORD, ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(DEFAULT_EMAIL, DEFAULT_PASSWORD, ip)
                .andExpect(status().isOk());

        performLoginFromIp(uniqueEmail("spray7"), WRONG_PASSWORD, ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("auth.login.tooManyAttempts"));
    }

    @Test
    void rateLimitedAttemptsShouldNotBeCountedAsInvalidCredentials() throws Exception {
        String ip = "203.0.113.90";

        registerAndVerifyDefaultUser();

        performLoginFromIp(DEFAULT_EMAIL, "wrong-1", ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(DEFAULT_EMAIL, "wrong-2", ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(DEFAULT_EMAIL, "wrong-3", ip)
                .andExpect(status().isTooManyRequests());

        performLoginFromIp(DEFAULT_EMAIL, "wrong-4", ip)
                .andExpect(status().isTooManyRequests());

        String emailHash = emailHash(DEFAULT_EMAIL);
        String ipHash = ipHash(ip);

        assertThat(countInvalidCredentials(emailHash, ipHash)).isEqualTo(2);
        assertThat(countRateLimited(emailHash, ipHash)).isEqualTo(2);
    }

    @Test
    void invalidCredentialsOutsideWindowShouldNotCountForEmailIpLimit() throws Exception {
        String ip = "203.0.113.100";

        registerAndVerifyDefaultUser();

        performLoginFromIp(DEFAULT_EMAIL, "wrong-1", ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(DEFAULT_EMAIL, "wrong-2", ip)
                .andExpect(status().isBadRequest());

        jdbcTemplate.update("""
                        update auth_login_attempts
                        set auth_login_attempt_at = now() - interval '20 minutes'
                        where email_hash = ?
                          and ip_address_hash = ?
                        """,
                emailHash(DEFAULT_EMAIL),
                ipHash(ip)
        );

        performLoginFromIp(DEFAULT_EMAIL, "wrong-3", ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(DEFAULT_EMAIL, "wrong-4", ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(DEFAULT_EMAIL, "wrong-5", ip)
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void correctCredentialsAfterRateLimitShouldStillAuthenticateUser() throws Exception {
        String ip = "203.0.113.101";

        registerAndVerifyDefaultUser();

        performLoginFromIp(DEFAULT_EMAIL, "wrong-1", ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(DEFAULT_EMAIL, "wrong-2", ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(DEFAULT_EMAIL, "wrong-3", ip)
                .andExpect(status().isTooManyRequests());

        performLoginFromIp(DEFAULT_EMAIL, DEFAULT_PASSWORD, ip)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());

        assertThat(countSuccessful(emailHash(DEFAULT_EMAIL), ipHash(ip))).isEqualTo(1);
    }

    @Test
    void correctCredentialsAfterRateLimitShouldStillReturnEmailVerificationChallenge() throws Exception {
        String ip = "203.0.113.102";

        performDefaultRegister()
                .andExpect(status().isCreated());

        performLoginFromIp(DEFAULT_EMAIL, "wrong-1", ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(DEFAULT_EMAIL, "wrong-2", ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(DEFAULT_EMAIL, "wrong-3", ip)
                .andExpect(status().isTooManyRequests());

        performLoginFromIp(DEFAULT_EMAIL, DEFAULT_PASSWORD, ip)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.emailVerificationResendToken").exists());

        assertThat(countSuccessful(emailHash(DEFAULT_EMAIL), ipHash(ip))).isEqualTo(1);
    }

    @Test
    void correctCredentialsAfterRateLimitShouldStillReturnRestoreChallenge() throws Exception {
        String ip = "203.0.113.103";

        registerAndVerifyDefaultUser();

        User user = defaultActiveUser();
        user.markAsSoftDeleted();
        userRepository.saveAndFlush(user);

        performLoginFromIp(DEFAULT_EMAIL, "wrong-1", ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(DEFAULT_EMAIL, "wrong-2", ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(DEFAULT_EMAIL, "wrong-3", ip)
                .andExpect(status().isTooManyRequests());

        performLoginFromIp(DEFAULT_EMAIL, DEFAULT_PASSWORD, ip)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.restoreToken").exists());

        assertThat(countSuccessful(emailHash(DEFAULT_EMAIL), ipHash(ip))).isEqualTo(1);
    }

    @Test
    void wrongPasswordForExistingUserShouldRecordAttemptWithUserId() throws Exception {
        String ip = "203.0.113.104";

        registerAndVerifyDefaultUser();

        User user = defaultActiveUser();

        performLoginFromIp(DEFAULT_EMAIL, WRONG_PASSWORD, ip)
                .andExpect(status().isBadRequest());

        Long count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from auth_login_attempts
                        where email_hash = ?
                          and ip_address_hash = ?
                          and user_id = ?
                          and login_successful = false
                          and failure_reason = 'INVALID_CREDENTIALS'
                        """,
                Long.class,
                emailHash(DEFAULT_EMAIL),
                ipHash(ip),
                user.getUserId()
        );

        assertThat(count).isEqualTo(1);
    }

    @Test
    void loginAttemptEmailHashShouldUseNormalizedEmail() throws Exception {
        String ip = "203.0.113.105";

        registerAndVerifyDefaultUser();

        performLoginFromIp("SAMUEL@EXAMPLE.COM", WRONG_PASSWORD, ip)
                .andExpect(status().isBadRequest());

        String normalizedEmailHash = emailHash(DEFAULT_EMAIL);
        String rawUppercaseEmailHash = sensitiveHashService.hash("SAMUEL@EXAMPLE.COM");

        assertThat(countInvalidCredentials(normalizedEmailHash, ipHash(ip))).isEqualTo(1);
        assertThat(countInvalidCredentials(rawUppercaseEmailHash, ipHash(ip))).isZero();
    }

    @Test
    void loginAttemptsShouldNotStoreRawEmailOrRawIp() throws Exception {
        String ip = "203.0.113.106";

        registerAndVerifyDefaultUser();

        performLoginFromIp(DEFAULT_EMAIL, WRONG_PASSWORD, ip)
                .andExpect(status().isBadRequest());

        Long rawValues = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from auth_login_attempts
                        where email_hash = ?
                           or ip_address_hash = ?
                        """,
                Long.class,
                DEFAULT_EMAIL,
                ip
        );

        assertThat(rawValues).isZero();
    }

    @Test
    void invalidLoginRequestShouldNotCreateLoginAttempt() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                        .content("""
                                {
                                  "email": "not-an-email",
                                  "password": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));

        Long attempts = jdbcTemplate.queryForObject(
                "select count(*) from auth_login_attempts",
                Long.class
        );

        assertThat(attempts).isZero();
    }

    @Test
    void tooManyLoginAttemptsShouldReturnItalianMessage() throws Exception {
        String ip = "203.0.113.107";

        registerAndVerifyDefaultUser();

        performLoginFromIp(DEFAULT_EMAIL, "wrong-1", ip)
                .andExpect(status().isBadRequest());

        performLoginFromIp(DEFAULT_EMAIL, "wrong-2", ip)
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/auth/login")
                        .with(remoteAddr(ip))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "it-IT")
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "wrong-3"
                                }
                                """.formatted(DEFAULT_EMAIL)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("auth.login.tooManyAttempts"))
                .andExpect(jsonPath("$.message").value("Troppi tentativi di accesso. Riprova più tardi."));
    }

    @Test
    void hardDeletionDueUserWithCorrectCredentialsShouldNotReceiveRestoreChallenge() throws Exception {
        String ip = "203.0.113.108";

        registerAndVerifyDefaultUser();

        User user = defaultActiveUser();
        user.markAsSoftDeleted();
        userRepository.saveAndFlush(user);

        jdbcTemplate.update("""
                update users
                set user_created_at = now() - interval '40 days',
                    user_deleted_at = now() - interval '31 days',
                    user_updated_at = now()
                where user_id = ?
                """, user.getUserId());

        performLoginFromIp(DEFAULT_EMAIL, DEFAULT_PASSWORD, ip)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.login.invalidCredentials"))
                .andExpect(jsonPath("$.restoreToken").doesNotExist());

        assertThat(userSessionRepository.findAll()).isEmpty();
        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    @Test
    void loginAttemptInvalidCredentialPartialIndexesShouldExist() {
        List<String> indexNames = jdbcTemplate.queryForList(
                """
                        select indexname
                        from pg_indexes
                        where schemaname = current_schema()
                          and tablename = 'auth_login_attempts'
                        """,
                String.class
        );

        assertThat(indexNames).contains(
                "idx_auth_login_attempts_invalid_email_ip_recent",
                "idx_auth_login_attempts_invalid_email_recent",
                "idx_auth_login_attempts_invalid_ip_recent"
        );
    }

    @Test
    void loginAttemptInvalidCredentialPartialIndexesShouldHaveExpectedDefinition() {
        List<String> indexDefinitions = jdbcTemplate.queryForList(
                """
                        select indexdef
                        from pg_indexes
                        where schemaname = current_schema()
                          and tablename = 'auth_login_attempts'
                          and indexname in (
                              'idx_auth_login_attempts_invalid_email_ip_recent',
                              'idx_auth_login_attempts_invalid_email_recent',
                              'idx_auth_login_attempts_invalid_ip_recent'
                          )
                        """,
                String.class
        );

        assertThat(indexDefinitions).hasSize(3);

        assertThat(indexDefinitions)
                .allSatisfy(indexDefinition -> {
                    assertThat(indexDefinition).contains("WHERE");
                    assertThat(indexDefinition).contains("login_successful = false");
                    assertThat(indexDefinition).contains("failure_reason");
                    assertThat(indexDefinition).contains("INVALID_CREDENTIALS");
                });
    }


    private ResultActions performDefaultRegister() throws Exception {
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
                        DEFAULT_USER_NAME,
                        DEFAULT_EMAIL,
                        DEFAULT_PASSWORD,
                        DEFAULT_LOCALE
                )));
    }

    private void registerAndVerifyDefaultUser() throws Exception {
        String token = registerAndExtractVerificationToken();

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                        .content("""
                                {
                                  "token": "%s"
                                }
                                """.formatted(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true));
    }

    private String registerAndExtractVerificationToken() throws Exception {
        performDefaultRegister()
                .andExpect(status().isCreated());

        EmailOutbox emailOutbox = emailOutboxRepository.findAll().getFirst();

        return extractTokenFromBody(decryptTextBody(emailOutbox));
    }

    private ResultActions performLoginFromIp(
            String email,
            String password,
            String ip
    ) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .with(remoteAddr(ip))
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .header(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT)
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

    private ResultActions performLoginWithDeviceFromIp(
            String email,
            String password,
            String deviceLabel,
            String ip
    ) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .with(remoteAddr(ip))
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

    private ResultActions performLogout(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .cookie(new Cookie(REFRESH_COOKIE_NAME, refreshToken)));
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

    private String extractRefreshCookie(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertThat(setCookie).isNotBlank();

        String prefix = REFRESH_COOKIE_NAME + "=";

        return Arrays.stream(setCookie.split(";"))
                .map(String::trim)
                .filter(part -> part.startsWith(prefix))
                .map(part -> part.substring(prefix.length()))
                .findFirst()
                .orElseThrow();
    }

    private User defaultActiveUser() {
        return userRepository.findByEmailAndUserDeletedAtIsNull(DEFAULT_EMAIL)
                .orElseThrow();
    }

    private String uniqueEmail(String prefix) {
        return "%s.%s@example.com".formatted(
                prefix,
                UUID.randomUUID().toString().replace("-", "")
        );
    }

    private String emailHash(String email) {
        return sensitiveHashService.hash(
                email.trim().toLowerCase(Locale.ROOT)
        );
    }

    private String ipHash(String ip) {
        return sensitiveHashService.hash(ip.trim());
    }

    private long countAttempts(String emailHash, String ipHash) {
        Long count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from auth_login_attempts
                        where email_hash = ?
                          and ip_address_hash = ?
                        """,
                Long.class,
                emailHash,
                ipHash
        );

        return count == null ? 0 : count;
    }

    private long countInvalidCredentials(String emailHash, String ipHash) {
        Long count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from auth_login_attempts
                        where email_hash = ?
                          and ip_address_hash = ?
                          and login_successful = false
                          and failure_reason = 'INVALID_CREDENTIALS'
                        """,
                Long.class,
                emailHash,
                ipHash
        );

        return count == null ? 0 : count;
    }

    private long countInvalidCredentialsWithNullUser(String emailHash, String ipHash) {
        Long count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from auth_login_attempts
                        where email_hash = ?
                          and ip_address_hash = ?
                          and login_successful = false
                          and failure_reason = 'INVALID_CREDENTIALS'
                          and user_id is null
                        """,
                Long.class,
                emailHash,
                ipHash
        );

        return count == null ? 0 : count;
    }

    private long countRateLimited(String emailHash, String ipHash) {
        Long count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from auth_login_attempts
                        where email_hash = ?
                          and ip_address_hash = ?
                          and login_successful = false
                          and failure_reason = 'RATE_LIMITED'
                        """,
                Long.class,
                emailHash,
                ipHash
        );

        return count == null ? 0 : count;
    }

    private long countSuccessful(String emailHash, String ipHash) {
        Long count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from auth_login_attempts
                        where email_hash = ?
                          and ip_address_hash = ?
                          and login_successful = true
                          and failure_reason is null
                        """,
                Long.class,
                emailHash,
                ipHash
        );

        return count == null ? 0 : count;
    }
}