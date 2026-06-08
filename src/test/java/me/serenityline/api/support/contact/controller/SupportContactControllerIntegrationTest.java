package me.serenityline.api.support.contact.controller;

import me.serenityline.api.email.outbox.entity.EmailOutbox;
import me.serenityline.api.email.outbox.entity.EmailOutboxStatus;
import me.serenityline.api.email.outbox.entity.EmailOutboxType;
import me.serenityline.api.email.outbox.repository.EmailOutboxRepository;
import me.serenityline.api.security.crypto.EmailOutboxEncryptionService;
import me.serenityline.api.security.crypto.EncryptedValue;
import me.serenityline.api.security.jwt.JwtTokenService;
import me.serenityline.api.support.IntegrationTestSupport;
import me.serenityline.api.user.entity.*;
import me.serenityline.api.user.repository.UserGroupRepository;
import me.serenityline.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "serenityline.support.contact.recipient-email=test@serenityline.me"
})
class SupportContactControllerIntegrationTest extends IntegrationTestSupport {

    private static final String SUPPORT_EMAIL = "test@serenityline.me";
    private static final String IT_LOCALE = "it-IT";
    private static final String EN_LOCALE = "en-US";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

    @Autowired
    private EmailOutboxEncryptionService emailOutboxEncryptionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserGroupRepository userGroupRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void anonymousContactShouldCreateSupportContactOutbox() throws Exception {
        performAnonymousContact(
                "203.0.113.10",
                IT_LOCALE,
                """
                        {
                          "name": "Mario Rossi",
                          "email": "mario.rossi@example.com",
                          "topic": "BUG",
                          "subject": "Problema accesso account",
                          "message": "Prima riga\\nSeconda riga",
                          "privacyAccepted": true,
                          "website": ""
                        }
                        """
        )
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.message").value("La tua richiesta è stata accettata."));

        EmailOutbox emailOutbox = singleOutbox(EmailOutboxType.SUPPORT_CONTACT);

        assertThat(emailOutbox.getRecipientEmail()).isEqualTo(SUPPORT_EMAIL);
        assertThat(emailOutbox.getEmailStatus()).isEqualTo(EmailOutboxStatus.PENDING);
        assertThat(emailOutbox.isDeleteBodyAfterSend()).isFalse();
        assertThat(emailOutbox.getBodyHtmlEncrypted()).isNull();
        assertThat(emailOutbox.getBodyHtmlIv()).isNull();
        assertThat(emailOutbox.getBodyHtmlTag()).isNull();

        assertThat(userIdFor(emailOutbox)).isNull();

        String subject = decryptSubject(emailOutbox);
        String body = decryptTextBody(emailOutbox);

        String shortReference = emailOutbox.getEmailOutboxId()
                .toString()
                .substring(0, 8)
                .toUpperCase();

        assertThat(subject)
                .startsWith("[SL-SUPPORT " + shortReference + "]")
                .contains("Problema accesso account");

        assertThat(body)
                .contains("Nuova richiesta di supporto SerenityLine")
                .contains("Riferimento breve: " + shortReference)
                .contains("Email outbox ID: " + emailOutbox.getEmailOutboxId())
                .contains("Origine: form pubblico anonimo")
                .contains("Nome: Mario Rossi")
                .contains("Email: mario.rossi@example.com")
                .contains("Argomento: BUG")
                .contains("Oggetto: Problema accesso account")
                .contains("Remote address: 203.0.113.10")
                .contains("Messaggio:")
                .contains("Prima riga\nSeconda riga");
    }

    @Test
    void honeypotContactShouldReturnAcceptedWithoutCreatingOutbox() throws Exception {
        performAnonymousContact(
                "203.0.113.11",
                IT_LOCALE,
                """
                        {
                          "name": "Spam Bot",
                          "email": "bot@example.com",
                          "topic": "OTHER",
                          "subject": "Spam",
                          "message": "Spam message",
                          "privacyAccepted": true,
                          "website": "https://spam.example.com"
                        }
                        """
        )
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(true));

        assertThat(emailOutboxRepository.findAll()).isEmpty();
    }

    @Test
    void anonymousContactShouldRejectMissingEmail() throws Exception {
        performAnonymousContact(
                "203.0.113.12",
                IT_LOCALE,
                """
                        {
                          "name": "Mario Rossi",
                          "topic": "BUG",
                          "subject": "Problema",
                          "message": "Messaggio valido",
                          "privacyAccepted": true,
                          "website": ""
                        }
                        """
        )
                .andExpect(status().isBadRequest());

        assertThat(emailOutboxRepository.findAll()).isEmpty();
    }

    @Test
    void contactShouldRejectMissingPrivacyAcceptance() throws Exception {
        performAnonymousContact(
                "203.0.113.13",
                IT_LOCALE,
                """
                        {
                          "name": "Mario Rossi",
                          "email": "mario.rossi@example.com",
                          "topic": "BUG",
                          "subject": "Problema",
                          "message": "Messaggio valido",
                          "privacyAccepted": false,
                          "website": ""
                        }
                        """
        )
                .andExpect(status().isBadRequest());

        assertThat(emailOutboxRepository.findAll()).isEmpty();
    }

    @Test
    void contactShouldRejectMissingSubject() throws Exception {
        performAnonymousContact(
                "203.0.113.14",
                IT_LOCALE,
                """
                        {
                          "name": "Mario Rossi",
                          "email": "mario.rossi@example.com",
                          "topic": "BUG",
                          "subject": "",
                          "message": "Messaggio valido",
                          "privacyAccepted": true,
                          "website": ""
                        }
                        """
        )
                .andExpect(status().isBadRequest());

        assertThat(emailOutboxRepository.findAll()).isEmpty();
    }

    @Test
    void contactShouldRejectInvalidTopic() throws Exception {
        performAnonymousContact(
                "203.0.113.15",
                IT_LOCALE,
                """
                        {
                          "name": "Mario Rossi",
                          "email": "mario.rossi@example.com",
                          "topic": "NOT_A_TOPIC",
                          "subject": "Problema",
                          "message": "Messaggio valido",
                          "privacyAccepted": true,
                          "website": ""
                        }
                        """
        )
                .andExpect(status().isBadRequest());

        assertThat(emailOutboxRepository.findAll()).isEmpty();
    }

    @Test
    void authenticatedContactShouldIgnoreBodyNameAndEmailAndUseAuthenticatedUser() throws Exception {
        AuthenticatedTestUser user = givenEnabledUser("auth-contact");

        performAuthenticatedContact(
                user.accessToken(),
                "203.0.113.16",
                IT_LOCALE,
                """
                        {
                          "name": "Nome Falso",
                          "email": "fake@example.com",
                          "topic": "ACCOUNT",
                          "subject": "Problema profilo",
                          "message": "Vorrei assistenza sul mio account.",
                          "privacyAccepted": true,
                          "website": ""
                        }
                        """
        )
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(true));

        EmailOutbox emailOutbox = singleOutbox(EmailOutboxType.SUPPORT_CONTACT);

        assertThat(emailOutbox.getRecipientEmail()).isEqualTo(SUPPORT_EMAIL);
        assertThat(emailOutbox.isDeleteBodyAfterSend()).isFalse();
        assertThat(userIdFor(emailOutbox)).isEqualTo(user.userId());

        String body = decryptTextBody(emailOutbox);

        assertThat(body)
                .contains("Origine: utente autenticato")
                .contains("User ID: " + user.userId())
                .contains("User group ID: " + user.userGroupId())
                .contains("Nome: " + user.userName())
                .contains("Email: " + user.email())
                .contains("Argomento: ACCOUNT")
                .contains("Oggetto: Problema profilo")
                .doesNotContain("Nome Falso")
                .doesNotContain("fake@example.com");
    }

    @Test
    void contactShouldBePublicWithoutAuthorizationHeader() throws Exception {
        performAnonymousContact(
                "203.0.113.17",
                EN_LOCALE,
                """
                        {
                          "name": "Public User",
                          "email": "public@example.com",
                          "topic": "FEEDBACK",
                          "subject": "Public contact",
                          "message": "This endpoint should be public.",
                          "privacyAccepted": true,
                          "website": ""
                        }
                        """
        )
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.message").value("Your request has been accepted."));

        assertThat(emailOutboxRepository.findAll())
                .filteredOn(email -> email.getEmailType() == EmailOutboxType.SUPPORT_CONTACT)
                .hasSize(1);
    }

    @Test
    void sixthContactInSameWindowShouldReturnTooManyRequests() throws Exception {
        String email = "rate-limit-" + UUID.randomUUID() + "@example.com";
        String remoteAddress = "203.0.113.200";

        for (int index = 1; index <= 5; index++) {
            performAnonymousContact(
                    remoteAddress,
                    IT_LOCALE,
                    validAnonymousPayload(email, "Richiesta " + index)
            )
                    .andExpect(status().isAccepted());
        }

        performAnonymousContact(
                remoteAddress,
                IT_LOCALE,
                validAnonymousPayload(email, "Richiesta 6")
        )
                .andExpect(status().isTooManyRequests());

        assertThat(emailOutboxRepository.findAll())
                .filteredOn(emailOutbox -> emailOutbox.getEmailType() == EmailOutboxType.SUPPORT_CONTACT)
                .hasSize(5);
    }

    @Test
    void anonymousContactShouldTruncateLongSubjectInInternalEmailSubject() throws Exception {
        String longSubject = "A".repeat(200);

        performAnonymousContact(
                "203.0.113.30",
                IT_LOCALE,
                """
                        {
                          "name": "Mario Rossi",
                          "email": "mario.long@example.com",
                          "topic": "BUG",
                          "subject": "%s",
                          "message": "Messaggio valido.",
                          "privacyAccepted": true,
                          "website": ""
                        }
                        """.formatted(longSubject)
        )
                .andExpect(status().isBadRequest());
    }

    @Test
    void anonymousContactShouldTruncateVisibleSubjectInInternalEmailSubject() throws Exception {
        String longButValidSubject = "A".repeat(160);

        performAnonymousContact(
                "203.0.113.30",
                IT_LOCALE,
                """
                        {
                          "name": "Mario Rossi",
                          "email": "mario.long@example.com",
                          "topic": "BUG",
                          "subject": "%s",
                          "message": "Messaggio valido.",
                          "privacyAccepted": true,
                          "website": ""
                        }
                        """.formatted(longButValidSubject)
        )
                .andExpect(status().isAccepted());

        EmailOutbox emailOutbox = singleOutbox(EmailOutboxType.SUPPORT_CONTACT);
        String subject = decryptSubject(emailOutbox);

        assertThat(subject).endsWith("...");
        assertThat(subject.length()).isLessThanOrEqualTo("[SL-SUPPORT XXXXXXXX] ".length() + 99);
    }

    @Test
    void anonymousContactShouldSanitizeSubjectNewlines() throws Exception {
        performAnonymousContact(
                "203.0.113.31",
                IT_LOCALE,
                """
                        {
                          "name": "Mario Rossi",
                          "email": "mario.newline@example.com",
                          "topic": "BUG",
                          "subject": "Problema\\nBCC: attacker@example.com",
                          "message": "Messaggio valido.",
                          "privacyAccepted": true,
                          "website": ""
                        }
                        """
        )
                .andExpect(status().isAccepted());

        EmailOutbox emailOutbox = singleOutbox(EmailOutboxType.SUPPORT_CONTACT);
        String subject = decryptSubject(emailOutbox);

        assertThat(subject).doesNotContain("\n");
        assertThat(subject).doesNotContain("\r");
        assertThat(subject).contains("Problema BCC: attacker@example.com");
    }

    @Test
    void authenticatedContactShouldBeRateLimitedByUserId() throws Exception {
        AuthenticatedTestUser user = givenEnabledUser("auth-rate-limit");

        for (int index = 1; index <= 5; index++) {
            performAuthenticatedContact(
                    user.accessToken(),
                    "203.0.113." + index,
                    IT_LOCALE,
                    """
                            {
                              "name": "Ignored",
                              "email": "ignored@example.com",
                              "topic": "ACCOUNT",
                              "subject": "Richiesta %d",
                              "message": "Messaggio valido.",
                              "privacyAccepted": true,
                              "website": ""
                            }
                            """.formatted(index)
            )
                    .andExpect(status().isAccepted());
        }

        performAuthenticatedContact(
                user.accessToken(),
                "203.0.113.250",
                IT_LOCALE,
                """
                        {
                          "name": "Ignored",
                          "email": "ignored@example.com",
                          "topic": "ACCOUNT",
                          "subject": "Richiesta 6",
                          "message": "Messaggio valido.",
                          "privacyAccepted": true,
                          "website": ""
                        }
                        """
        )
                .andExpect(status().isTooManyRequests());
    }

    private ResultActions performAnonymousContact(
            String remoteAddress,
            String acceptLanguage,
            String json
    ) throws Exception {
        return mockMvc.perform(post("/api/support/contact")
                .with(request -> {
                    request.setRemoteAddr(remoteAddress);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage)
                .header(HttpHeaders.USER_AGENT, "JUnit support contact browser")
                .content(json));
    }

    private ResultActions performAuthenticatedContact(
            String accessToken,
            String remoteAddress,
            String acceptLanguage,
            String json
    ) throws Exception {
        return mockMvc.perform(post("/api/support/contact")
                .with(request -> {
                    request.setRemoteAddr(remoteAddress);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage)
                .header(HttpHeaders.USER_AGENT, "JUnit authenticated support browser")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .content(json));
    }

    private String validAnonymousPayload(String email, String subject) {
        return """
                {
                  "name": "Rate Limit User",
                  "email": "%s",
                  "topic": "OTHER",
                  "subject": "%s",
                  "message": "Messaggio valido per rate limit.",
                  "privacyAccepted": true,
                  "website": ""
                }
                """.formatted(email, subject);
    }

    private EmailOutbox singleOutbox(EmailOutboxType type) {
        List<EmailOutbox> matches = emailOutboxRepository.findAll()
                .stream()
                .filter(emailOutbox -> emailOutbox.getEmailType() == type)
                .sorted(Comparator.comparing(EmailOutbox::getEmailCreatedAt))
                .toList();

        assertThat(matches).hasSize(1);

        return matches.getFirst();
    }

    private UUID userIdFor(EmailOutbox emailOutbox) {
        return jdbcTemplate.queryForObject(
                """
                        select user_id
                        from email_outbox
                        where email_outbox_id = ?
                        """,
                UUID.class,
                emailOutbox.getEmailOutboxId()
        );
    }

    private String decryptSubject(EmailOutbox emailOutbox) {
        return emailOutboxEncryptionService.decrypt(new EncryptedValue(
                emailOutbox.getSubjectEncrypted(),
                emailOutbox.getSubjectIv(),
                emailOutbox.getSubjectTag()
        ));
    }

    private String decryptTextBody(EmailOutbox emailOutbox) {
        return emailOutboxEncryptionService.decrypt(new EncryptedValue(
                emailOutbox.getBodyTextEncrypted(),
                emailOutbox.getBodyTextIv(),
                emailOutbox.getBodyTextTag()
        ));
    }

    private AuthenticatedTestUser givenEnabledUser(String prefix) {
        String unique = prefix + "-" + UUID.randomUUID();
        String email = unique + "@example.com";
        String userName = "Real User " + prefix;

        UserGroup userGroup = userGroupRepository.saveAndFlush(
                new UserGroup("Group " + unique)
        );

        User user = new User(
                userName,
                email,
                userGroup,
                UserRole.OWNER,
                UserPlatformRole.USER,
                "it-IT",
                PreferredTheme.DEFAULT,
                false,
                true,
                "{noop}password",
                true,
                0L
        );

        User savedUser = userRepository.saveAndFlush(user);

        return new AuthenticatedTestUser(
                savedUser.getUserId(),
                savedUser.getUserGroup().getUserGroupId(),
                savedUser.getUserName(),
                savedUser.getEmail(),
                jwtTokenService.createAccessToken(savedUser).token()
        );
    }

    private record AuthenticatedTestUser(
            UUID userId,
            UUID userGroupId,
            String userName,
            String email,
            String accessToken
    ) {
    }
}