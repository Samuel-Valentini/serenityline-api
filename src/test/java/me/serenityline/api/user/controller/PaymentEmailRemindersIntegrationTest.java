package me.serenityline.api.user.controller;

import com.jayway.jsonpath.JsonPath;
import me.serenityline.api.email.outbox.entity.EmailOutbox;
import me.serenityline.api.email.outbox.entity.EmailOutboxType;
import me.serenityline.api.email.outbox.repository.EmailOutboxRepository;
import me.serenityline.api.security.crypto.EmailOutboxEncryptionService;
import me.serenityline.api.security.crypto.EncryptedValue;
import me.serenityline.api.support.IntegrationTestSupport;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Comparator;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentEmailRemindersIntegrationTest extends IntegrationTestSupport {

    private static final String DEFAULT_PASSWORD = "VeryStrongPassword-2026-SerenityLine!";
    private static final String DEFAULT_LOCALE = "en-US";
    private static final String DEFAULT_USER_AGENT = "JUnit Browser";
    private static final String DEFAULT_DEVICE_LABEL = "Payment reminders test device";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

    @Autowired
    private EmailOutboxEncryptionService emailOutboxEncryptionService;

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
    void updatePaymentEmailRemindersShouldRequireAccessToken() throws Exception {
        performUpdatePaymentEmailRemindersWithoutAccessToken(false)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMeShouldExposePaymentEmailRemindersEnabledByDefaultWhenFieldIsOmittedAtRegistration() throws Exception {
        TestUserSession session = registerVerifyAndLogin(null);

        performGetMe(session.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(session.email()))
                .andExpect(jsonPath("$.paymentEmailRemindersEnabled").value(true));

        User user = userByEmail(session.email());

        assertThat(user.isPaymentEmailRemindersEnabled()).isTrue();
    }

    @Test
    void getMeShouldExposePaymentEmailRemindersDisabledWhenUserOptsOutAtRegistration() throws Exception {
        TestUserSession session = registerVerifyAndLogin(false);

        performGetMe(session.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(session.email()))
                .andExpect(jsonPath("$.paymentEmailRemindersEnabled").value(false));

        User user = userByEmail(session.email());

        assertThat(user.isPaymentEmailRemindersEnabled()).isFalse();
    }

    @Test
    void updatePaymentEmailRemindersShouldDisablePreference() throws Exception {
        TestUserSession session = registerVerifyAndLogin(null);

        performUpdatePaymentEmailReminders(session.accessToken(), false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentEmailRemindersEnabled").value(false))
                .andExpect(jsonPath("$.enabled").doesNotExist());

        User user = userByEmail(session.email());

        assertThat(user.isPaymentEmailRemindersEnabled()).isFalse();

        performGetMe(session.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(session.email()))
                .andExpect(jsonPath("$.paymentEmailRemindersEnabled").value(false));
    }

    @Test
    void updatePaymentEmailRemindersShouldEnablePreference() throws Exception {
        TestUserSession session = registerVerifyAndLogin(false);

        performUpdatePaymentEmailReminders(session.accessToken(), true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentEmailRemindersEnabled").value(true))
                .andExpect(jsonPath("$.enabled").doesNotExist());

        User user = userByEmail(session.email());

        assertThat(user.isPaymentEmailRemindersEnabled()).isTrue();

        performGetMe(session.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(session.email()))
                .andExpect(jsonPath("$.paymentEmailRemindersEnabled").value(true));
    }

    @Test
    void updatePaymentEmailRemindersShouldBeIdempotentWhenDisablingAlreadyDisabledPreference() throws Exception {
        TestUserSession session = registerVerifyAndLogin(false);

        performUpdatePaymentEmailReminders(session.accessToken(), false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentEmailRemindersEnabled").value(false));

        performUpdatePaymentEmailReminders(session.accessToken(), false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentEmailRemindersEnabled").value(false));

        User user = userByEmail(session.email());

        assertThat(user.isPaymentEmailRemindersEnabled()).isFalse();
    }

    @Test
    void updatePaymentEmailRemindersShouldBeIdempotentWhenEnablingAlreadyEnabledPreference() throws Exception {
        TestUserSession session = registerVerifyAndLogin(null);

        performUpdatePaymentEmailReminders(session.accessToken(), true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentEmailRemindersEnabled").value(true));

        performUpdatePaymentEmailReminders(session.accessToken(), true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentEmailRemindersEnabled").value(true));

        User user = userByEmail(session.email());

        assertThat(user.isPaymentEmailRemindersEnabled()).isTrue();
    }

    @Test
    void updatePaymentEmailRemindersShouldNotInvalidateCurrentAccessToken() throws Exception {
        TestUserSession session = registerVerifyAndLogin(null);

        performUpdatePaymentEmailReminders(session.accessToken(), false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentEmailRemindersEnabled").value(false));

        performGetMe(session.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(session.email()))
                .andExpect(jsonPath("$.paymentEmailRemindersEnabled").value(false));

        User user = userByEmail(session.email());

        assertThat(user.getTokenVersion()).isZero();
    }

    @Test
    void updatePaymentEmailRemindersResponseShouldNotLeakSensitiveFields() throws Exception {
        TestUserSession session = registerVerifyAndLogin(null);

        MvcResult result = performUpdatePaymentEmailReminders(session.accessToken(), false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentEmailRemindersEnabled").value(false))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.currentPassword").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.tokenHash").doesNotExist())
                .andExpect(jsonPath("$.userPasswordHash").doesNotExist())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();

        assertThat(responseBody)
                .doesNotContain(DEFAULT_PASSWORD)
                .doesNotContain("password")
                .doesNotContain("currentPassword")
                .doesNotContain("accessToken")
                .doesNotContain("refreshToken")
                .doesNotContain("tokenHash")
                .doesNotContain("userPasswordHash")
                .doesNotContain("hash");
    }

    @Test
    void updatePaymentEmailRemindersShouldRejectMalformedJsonWithoutChangingPreference() throws Exception {
        TestUserSession session = registerVerifyAndLogin(null);

        performUpdatePaymentEmailRemindersWithBody(session.accessToken(), "{")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.body.invalid"));

        User user = userByEmail(session.email());

        assertThat(user.isPaymentEmailRemindersEnabled()).isTrue();
    }

    @Test
    void updatePaymentEmailRemindersShouldRejectEmptyBodyWithoutChangingPreference() throws Exception {
        TestUserSession session = registerVerifyAndLogin(null);

        performUpdatePaymentEmailRemindersWithBody(session.accessToken(), "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.body.invalid"));

        User user = userByEmail(session.email());

        assertThat(user.isPaymentEmailRemindersEnabled()).isTrue();
    }

    @Test
    void updatePaymentEmailRemindersShouldRejectMissingEnabledFieldWithoutChangingPreference() throws Exception {
        TestUserSession session = registerVerifyAndLogin(null);

        performUpdatePaymentEmailRemindersWithBody(session.accessToken(), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fieldErrors[*].code", hasItem("user.paymentEmailReminders.enabled.required")));

        User user = userByEmail(session.email());

        assertThat(user.isPaymentEmailRemindersEnabled()).isTrue();
    }

    @Test
    void updatePaymentEmailRemindersShouldRejectNullEnabledFieldWithoutChangingPreference() throws Exception {
        TestUserSession session = registerVerifyAndLogin(null);

        performUpdatePaymentEmailRemindersWithBody(session.accessToken(), """
                {
                  "enabled": null
                }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fieldErrors[*].code", hasItem("user.paymentEmailReminders.enabled.required")));

        User user = userByEmail(session.email());

        assertThat(user.isPaymentEmailRemindersEnabled()).isTrue();
    }

    @Test
    void updatePaymentEmailRemindersShouldRejectInvalidEnabledJsonTypeWithoutChangingPreference() throws Exception {
        TestUserSession session = registerVerifyAndLogin(null);

        performUpdatePaymentEmailRemindersWithBody(session.accessToken(), """
                {
                  "enabled": {}
                }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.body.invalid"));

        User user = userByEmail(session.email());

        assertThat(user.isPaymentEmailRemindersEnabled()).isTrue();
    }

    @Test
    void updatePaymentEmailRemindersShouldRejectSoftDeletedUser() throws Exception {
        TestUserSession session = registerVerifyAndLogin(false);

        User user = userByEmail(session.email());

        user.markAsSoftDeleted();

        userRepository.saveAndFlush(user);

        performUpdatePaymentEmailReminders(session.accessToken(), true)
                .andExpect(status().isUnauthorized());

        User updatedUser = userRepository
                .findLoginCandidateByEmail(session.email())
                .orElseThrow();

        assertThat(updatedUser.isPendingDeletion()).isTrue();
        assertThat(updatedUser.isPaymentEmailRemindersEnabled()).isFalse();
    }

    @Test
    void updatePaymentEmailRemindersShouldRejectDisabledUser() throws Exception {
        TestUserSession session = registerVerifyAndLogin(false);

        User user = userByEmail(session.email());

        user.setUserIsEnabled(false);

        userRepository.saveAndFlush(user);

        performUpdatePaymentEmailReminders(session.accessToken(), true)
                .andExpect(status().isUnauthorized());

        User updatedUser = userRepository
                .findLoginCandidateByEmail(session.email())
                .orElseThrow();

        assertThat(updatedUser.isUserIsEnabled()).isFalse();
        assertThat(updatedUser.isPaymentEmailRemindersEnabled()).isFalse();
    }

    @Test
    void updatePaymentEmailRemindersShouldRejectAccessTokenWithStaleTokenVersion() throws Exception {
        TestUserSession session = registerVerifyAndLogin(false);

        User user = userByEmail(session.email());

        user.incrementTokenVersion();

        userRepository.saveAndFlush(user);

        performUpdatePaymentEmailReminders(session.accessToken(), true)
                .andExpect(status().isUnauthorized());

        User updatedUser = userByEmail(session.email());

        assertThat(updatedUser.getTokenVersion()).isEqualTo(1L);
        assertThat(updatedUser.isPaymentEmailRemindersEnabled()).isFalse();
    }

    private TestUserSession registerVerifyAndLogin(Boolean paymentEmailRemindersEnabled) throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String email = "payment.reminders.%s@example.com".formatted(suffix);
        String userName = "Payment Reminders User " + suffix.substring(0, 8);

        performRegister(email, userName, paymentEmailRemindersEnabled)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email));

        String verificationToken = extractLatestEmailVerificationToken(email);

        performVerifyEmail(verificationToken)
                .andExpect(status().isOk());

        MvcResult loginResult = performLogin(email)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.user.email").value(email))
                .andReturn();

        String accessToken = jsonPathString(loginResult, "$.accessToken");

        return new TestUserSession(
                email,
                userName,
                accessToken
        );
    }

    private String extractLatestEmailVerificationToken(String email) {
        EmailOutbox emailOutbox = emailOutboxRepository.findAll()
                .stream()
                .filter(outbox -> outbox.getEmailType() == EmailOutboxType.EMAIL_VERIFICATION)
                .filter(outbox -> outbox.getRecipientEmail().equals(email))
                .max(Comparator.comparing(EmailOutbox::getEmailCreatedAt))
                .orElseThrow();

        return extractTokenFromBody(decryptTextBody(emailOutbox));
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

    private User userByEmail(String email) {
        return userRepository
                .findByEmailAndUserDeletedAtIsNull(email)
                .orElseThrow();
    }

    private ResultActions performRegister(
            String email,
            String userName,
            Boolean paymentEmailRemindersEnabled
    ) throws Exception {
        String paymentEmailRemindersJson = paymentEmailRemindersEnabled == null
                ? ""
                : """
                  ,
                  "paymentEmailRemindersEnabled": %s
                """.formatted(paymentEmailRemindersEnabled);

        return mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .content("""
                        {
                          "userName": "%s",
                          "email": "%s",
                          "password": "%s",
                          "preferredLocale": "%s"
                          %s
                        }
                        """.formatted(
                        userName,
                        email,
                        DEFAULT_PASSWORD,
                        DEFAULT_LOCALE,
                        paymentEmailRemindersJson
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

    private ResultActions performLogin(String email) throws Exception {
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
                        DEFAULT_PASSWORD,
                        DEFAULT_DEVICE_LABEL
                )));
    }

    private ResultActions performGetMe(String accessToken) throws Exception {
        return mockMvc.perform(get("/api/me")
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    private ResultActions performUpdatePaymentEmailReminders(
            String accessToken,
            boolean enabled
    ) throws Exception {
        return performUpdatePaymentEmailRemindersWithBody(
                accessToken,
                """
                        {
                          "enabled": %s
                        }
                        """.formatted(enabled)
        );
    }

    private ResultActions performUpdatePaymentEmailRemindersWithoutAccessToken(
            boolean enabled
    ) throws Exception {
        return mockMvc.perform(patch("/api/me/payment-email-reminders")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .content("""
                        {
                          "enabled": %s
                        }
                        """.formatted(enabled)));
    }

    private ResultActions performUpdatePaymentEmailRemindersWithBody(
            String accessToken,
            String body
    ) throws Exception {
        return mockMvc.perform(patch("/api/me/payment-email-reminders")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .content(body));
    }

    private record TestUserSession(
            String email,
            String userName,
            String accessToken
    ) {
    }
}