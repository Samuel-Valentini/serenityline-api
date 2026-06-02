package me.serenityline.api.auth.controller;

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
import me.serenityline.api.email.service.OutboundEmail;
import me.serenityline.api.email.service.SentEmail;
import me.serenityline.api.finance.account.entity.AccountUser;
import me.serenityline.api.finance.account.repository.AccountUserRepository;
import me.serenityline.api.security.crypto.EmailOutboxEncryptionService;
import me.serenityline.api.security.crypto.EncryptedValue;
import me.serenityline.api.security.token.TokenHashingService;
import me.serenityline.api.support.IntegrationTestSupport;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.entity.UserRole;
import me.serenityline.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserInvitationIntegrationTest extends IntegrationTestSupport {

    private static final String DEFAULT_LOCALE = "en-US";
    private static final String IT_LOCALE = "it-IT";

    private static final String OWNER_EMAIL = "owner@example.com";
    private static final String OWNER_NAME = "Owner";
    private static final String OWNER_PASSWORD = "VeryStrongPassword-2026-SerenityLine!";

    private static final String INVITED_PASSWORD = "InvitedVeryStrongPassword-2026!";
    private static final String WEAK_PASSWORD = "password12345";

    private static final String DEFAULT_DEVICE_LABEL = "JUnit Device";
    private static final String DEFAULT_USER_AGENT = "JUnit Browser";

    private static final int OUTBOX_BATCH_SIZE = 10;
    private static final Duration OUTBOX_RETRY_DELAY = Duration.ofMinutes(5);

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
    private AccountUserRepository accountUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private EmailSender emailSender;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private static String jsonPathString(
            MvcResult result,
            String path
    ) throws Exception {
        return JsonPath.read(
                result.getResponse().getContentAsString(),
                path
        );
    }

    @BeforeEach
    void preventRealEmails() {
        when(emailSender.provider())
                .thenReturn("test-email-sender");

        when(emailSender.send(any(OutboundEmail.class)))
                .thenAnswer(invocation -> new SentEmail(
                        "test-message-" + UUID.randomUUID()
                ));
    }

    @Test
    void ownerShouldInviteCollaboratorWithAccountsAndCreateInvitationTokenAndEmail() throws Exception {
        AuthenticatedUser owner = registerVerifyAndLoginOwner();

        UUID accountId = createAccountAndExtractId(
                owner.accessToken(),
                "Main Account"
        );

        String invitedEmail = uniqueEmail("collaborator");

        performInviteUser(
                owner.accessToken(),
                "Mario Rossi",
                invitedEmail,
                "COLLABORATOR",
                IT_LOCALE,
                true,
                List.of(accountId)
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").isString())
                .andExpect(jsonPath("$.userName").value("Mario Rossi"))
                .andExpect(jsonPath("$.email").value(invitedEmail))
                .andExpect(jsonPath("$.userGroupId").value(owner.userGroupId().toString()))
                .andExpect(jsonPath("$.userGroupName").value("Owner's group"))
                .andExpect(jsonPath("$.userRole").value("COLLABORATOR"))
                .andExpect(jsonPath("$.preferredLocale").value(IT_LOCALE))
                .andExpect(jsonPath("$.accountIds[0]").value(accountId.toString()))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("password"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("token"))));

        User invitedUser = userRepository.findByEmailAndUserDeletedAtIsNull(invitedEmail)
                .orElseThrow();

        assertThat(invitedUser.getUserName()).isEqualTo("Mario Rossi");
        assertThat(invitedUser.getUserRole()).isEqualTo(UserRole.COLLABORATOR);
        assertThat(invitedUser.getUserGroup().getUserGroupId()).isEqualTo(owner.userGroupId());
        assertThat(invitedUser.isUserIsEnabled()).isFalse();
        assertThat(invitedUser.isPendingDeletion()).isFalse();
        assertThat(invitedUser.isPaymentEmailRemindersEnabled()).isTrue();
        assertThat(invitedUser.getUserPasswordHash()).isNotBlank();

        List<AccountUser> accountUsers = accountUserRepository.findAllByUser_UserId(invitedUser.getUserId());

        assertThat(accountUsers).hasSize(1);
        assertThat(accountUsers.getFirst().getAccount().getAccountId()).isEqualTo(accountId);

        List<AuthActionToken> invitationTokens = authActionTokenRepository.findAll()
                .stream()
                .filter(token -> token.getAuthActionTokenType() == AuthActionTokenType.USER_INVITATION)
                .toList();

        assertThat(invitationTokens).hasSize(1);

        AuthActionToken invitationToken = invitationTokens.getFirst();

        assertThat(invitationToken.getUser().getUserId()).isEqualTo(invitedUser.getUserId());
        assertThat(invitationToken.getAuthActionTokenHash()).isNotBlank();
        assertThat(invitationToken.getAuthActionUsedAt()).isNull();
        assertThat(invitationToken.getAuthActionRevokedAt()).isNull();
        assertThat(invitationToken.getAuthActionExpiresAt()).isAfter(OffsetDateTime.now());

        EmailOutbox invitationEmail = latestPendingEmailOutbox(
                EmailOutboxType.USER_INVITATION,
                invitedEmail
        );

        assertThat(invitationEmail.getEmailStatus()).isEqualTo(EmailOutboxStatus.PENDING);
        assertThat(invitationEmail.getRecipientEmail()).isEqualTo(invitedEmail);
        assertThat(invitationEmail.isDeleteBodyAfterSend()).isTrue();

        String subject = decryptSubject(invitationEmail);
        String body = decryptTextBody(invitationEmail);
        String plainToken = extractTokenFromBody(body);

        assertThat(subject).isEqualTo("Sei stato invitato su SerenityLine da Owner");
        assertThat(body).contains("Mario Rossi");
        assertThat(body).contains("Owner");
        assertThat(body).contains("Owner's group");
        assertThat(body).contains("/invito/accetta#token=");
        assertThat(body).contains("/invito/accetta");

        assertThat(plainToken).isNotBlank();
        assertThat(invitationToken.getAuthActionTokenHash())
                .isEqualTo(tokenHashingService.hash(plainToken));
        assertThat(invitationToken.getAuthActionTokenHash())
                .isNotEqualTo(plainToken);
    }

    @Test
    void acceptInvitationShouldSetPasswordEnableUserAndAllowLogin() throws Exception {
        AuthenticatedUser owner = registerVerifyAndLoginOwner();

        UUID accountId = createAccountAndExtractId(
                owner.accessToken(),
                "Main Account"
        );

        String invitedEmail = uniqueEmail("accept");

        inviteUser(
                owner.accessToken(),
                "Invited User",
                invitedEmail,
                "COLLABORATOR",
                IT_LOCALE,
                List.of(accountId)
        );

        String invitationToken = extractLatestInvitationToken(invitedEmail);

        performAcceptInvitation(invitationToken, INVITED_PASSWORD)
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        User invitedUser = userRepository.findByEmailAndUserDeletedAtIsNull(invitedEmail)
                .orElseThrow();

        assertThat(invitedUser.isUserIsEnabled()).isTrue();
        assertThat(invitedUser.isPendingDeletion()).isFalse();

        AuthActionToken actionToken = authActionTokenRepository
                .findByAuthActionTokenHash(tokenHashingService.hash(invitationToken))
                .orElseThrow();

        assertThat(actionToken.getAuthActionUsedAt()).isNotNull();
        assertThat(actionToken.getAuthActionRevokedAt()).isNull();

        performLogin(invitedEmail, INVITED_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.user.email").value(invitedEmail))
                .andExpect(jsonPath("$.user.userRole").value("COLLABORATOR"))
                .andExpect(jsonPath("$.user.userGroupId").value(owner.userGroupId().toString()));
    }

    @Test
    void acceptedCollaboratorShouldSeeOnlyLinkedAccounts() throws Exception {
        AuthenticatedUser owner = registerVerifyAndLoginOwner();

        UUID visibleAccountId = createAccountAndExtractId(
                owner.accessToken(),
                "Visible Account"
        );

        UUID hiddenAccountId = createAccountAndExtractId(
                owner.accessToken(),
                "Hidden Account"
        );

        String invitedEmail = uniqueEmail("visible-account");

        inviteUser(
                owner.accessToken(),
                "Limited User",
                invitedEmail,
                "COLLABORATOR",
                DEFAULT_LOCALE,
                List.of(visibleAccountId)
        );

        String invitationToken = extractLatestInvitationToken(invitedEmail);

        performAcceptInvitation(invitationToken, INVITED_PASSWORD)
                .andExpect(status().isNoContent());

        String collaboratorAccessToken = loginAndExtractAccessToken(
                invitedEmail,
                INVITED_PASSWORD
        );

        mockMvc.perform(get("/api/finance/accounts")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + collaboratorAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountId").value(visibleAccountId.toString()))
                .andExpect(jsonPath("$[*].accountId").value(org.hamcrest.Matchers.not(hasItems(hiddenAccountId.toString()))));
    }

    @Test
    void inviteUserShouldRequireAccessToken() throws Exception {
        performInviteUserWithoutAccessToken(
                "No Auth",
                uniqueEmail("no-auth"),
                "COLLABORATOR",
                List.of(UUID.randomUUID())
        )
                .andExpect(status().isUnauthorized());

        assertThat(userRepository.findAll()).isEmpty();
        assertThat(authActionTokenRepository.findAll()).isEmpty();
        assertThat(emailOutboxRepository.findAll()).isEmpty();
    }

    @Test
    void acceptInvitationShouldIgnoreInvalidAuthorizationHeader() throws Exception {
        AuthenticatedUser owner = registerVerifyAndLoginOwner();

        UUID accountId = createAccountAndExtractId(
                owner.accessToken(),
                "Main Account"
        );

        String invitedEmail = uniqueEmail("invalid-bearer");

        inviteUser(
                owner.accessToken(),
                "Invalid Bearer",
                invitedEmail,
                "COLLABORATOR",
                DEFAULT_LOCALE,
                List.of(accountId)
        );

        String invitationToken = extractLatestInvitationToken(invitedEmail);

        mockMvc.perform(post("/api/auth/user-invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                        .content("""
                                {
                                  "token": "%s",
                                  "password": "%s"
                                }
                                """.formatted(invitationToken, INVITED_PASSWORD)))
                .andExpect(status().isNoContent());

        User invitedUser = userRepository.findByEmailAndUserDeletedAtIsNull(invitedEmail)
                .orElseThrow();

        assertThat(invitedUser.isUserIsEnabled()).isTrue();
    }

    @Test
    void nonOwnerShouldNotInviteUsers() throws Exception {
        AuthenticatedUser owner = registerVerifyAndLoginOwner();

        UUID accountId = createAccountAndExtractId(
                owner.accessToken(),
                "Main Account"
        );

        String collaboratorEmail = uniqueEmail("non-owner-source");

        inviteUser(
                owner.accessToken(),
                "Collaborator",
                collaboratorEmail,
                "COLLABORATOR",
                DEFAULT_LOCALE,
                List.of(accountId)
        );

        performAcceptInvitation(
                extractLatestInvitationToken(collaboratorEmail),
                INVITED_PASSWORD
        )
                .andExpect(status().isNoContent());

        String collaboratorAccessToken = loginAndExtractAccessToken(
                collaboratorEmail,
                INVITED_PASSWORD
        );

        performInviteUser(
                collaboratorAccessToken,
                "Unauthorized Invite",
                uniqueEmail("unauthorized-invite"),
                "COLLABORATOR",
                DEFAULT_LOCALE,
                true,
                List.of(accountId)
        )
                .andExpect(status().isForbidden());

        assertThat(userRepository.findByEmailAndUserDeletedAtIsNull(uniqueEmail("never-created")))
                .isEmpty();

        List<AuthActionToken> invitationTokens = authActionTokenRepository.findAll()
                .stream()
                .filter(token -> token.getAuthActionTokenType() == AuthActionTokenType.USER_INVITATION)
                .toList();

        assertThat(invitationTokens).hasSize(1);
    }

    @Test
    void inviteUserShouldRejectOwnerRole() throws Exception {
        AuthenticatedUser owner = registerVerifyAndLoginOwner();

        performInviteUser(
                owner.accessToken(),
                "Owner Attempt",
                uniqueEmail("owner-attempt"),
                "OWNER",
                DEFAULT_LOCALE,
                true,
                List.of()
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.userInvitation.ownerRoleForbidden"));

        assertThat(authActionTokenRepository.findAll())
                .filteredOn(token -> token.getAuthActionTokenType() == AuthActionTokenType.USER_INVITATION)
                .isEmpty();
    }

    @Test
    void inviteCollaboratorShouldRequireAtLeastOneAccount() throws Exception {
        AuthenticatedUser owner = registerVerifyAndLoginOwner();

        performInviteUser(
                owner.accessToken(),
                "No Account Collaborator",
                uniqueEmail("no-account-collaborator"),
                "COLLABORATOR",
                DEFAULT_LOCALE,
                true,
                List.of()
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.userInvitation.accountIdsRequired"));

        assertThat(authActionTokenRepository.findAll())
                .filteredOn(token -> token.getAuthActionTokenType() == AuthActionTokenType.USER_INVITATION)
                .isEmpty();
    }

    @Test
    void inviteSuperCollaboratorShouldRejectAccountIds() throws Exception {
        AuthenticatedUser owner = registerVerifyAndLoginOwner();

        UUID accountId = createAccountAndExtractId(
                owner.accessToken(),
                "Main Account"
        );

        performInviteUser(
                owner.accessToken(),
                "Super Collaborator",
                uniqueEmail("super-with-account"),
                "SUPER_COLLABORATOR",
                DEFAULT_LOCALE,
                true,
                List.of(accountId)
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.userInvitation.accountIdsNotAllowedForRole"));

        assertThat(authActionTokenRepository.findAll())
                .filteredOn(token -> token.getAuthActionTokenType() == AuthActionTokenType.USER_INVITATION)
                .isEmpty();
    }

    @Test
    void inviteSuperCollaboratorShouldAllowEmptyAccountIds() throws Exception {
        AuthenticatedUser owner = registerVerifyAndLoginOwner();

        String invitedEmail = uniqueEmail("super-empty");

        performInviteUser(
                owner.accessToken(),
                "Super Collaborator",
                invitedEmail,
                "SUPER_COLLABORATOR",
                DEFAULT_LOCALE,
                true,
                List.of()
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userRole").value("SUPER_COLLABORATOR"))
                .andExpect(jsonPath("$.accountIds").isArray())
                .andExpect(jsonPath("$.accountIds").isEmpty());

        User invitedUser = userRepository.findByEmailAndUserDeletedAtIsNull(invitedEmail)
                .orElseThrow();

        assertThat(invitedUser.getUserRole()).isEqualTo(UserRole.SUPER_COLLABORATOR);
        assertThat(accountUserRepository.findAllByUser_UserId(invitedUser.getUserId())).isEmpty();
    }

    @Test
    void inviteViewerCollaboratorShouldAllowEmptyAccountIds() throws Exception {
        AuthenticatedUser owner = registerVerifyAndLoginOwner();

        String invitedEmail = uniqueEmail("viewer-empty");

        performInviteUser(
                owner.accessToken(),
                "Viewer Collaborator",
                invitedEmail,
                "VIEWER_COLLABORATOR",
                DEFAULT_LOCALE,
                true,
                List.of()
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userRole").value("VIEWER_COLLABORATOR"))
                .andExpect(jsonPath("$.accountIds").isArray())
                .andExpect(jsonPath("$.accountIds").isEmpty());

        User invitedUser = userRepository.findByEmailAndUserDeletedAtIsNull(invitedEmail)
                .orElseThrow();

        assertThat(invitedUser.getUserRole()).isEqualTo(UserRole.VIEWER_COLLABORATOR);
        assertThat(accountUserRepository.findAllByUser_UserId(invitedUser.getUserId())).isEmpty();
    }

    @Test
    void inviteUserShouldRejectAccountFromAnotherGroup() throws Exception {
        AuthenticatedUser owner = registerVerifyAndLoginOwner();

        AuthenticatedUser otherOwner = registerVerifyAndLoginUser(
                "Other Owner",
                uniqueEmail("other-owner"),
                "OtherVeryStrongPassword-2026!"
        );

        UUID foreignAccountId = createAccountAndExtractId(
                otherOwner.accessToken(),
                "Foreign Account"
        );

        performInviteUser(
                owner.accessToken(),
                "Foreign Account User",
                uniqueEmail("foreign-account"),
                "COLLABORATOR",
                DEFAULT_LOCALE,
                true,
                List.of(foreignAccountId)
        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("auth.userInvitation.accountNotFound"));

        assertThat(authActionTokenRepository.findAll())
                .filteredOn(token -> token.getAuthActionTokenType() == AuthActionTokenType.USER_INVITATION)
                .isEmpty();
    }

    @Test
    void inviteUserShouldRejectDuplicateActiveEmail() throws Exception {
        AuthenticatedUser owner = registerVerifyAndLoginOwner();

        UUID accountId = createAccountAndExtractId(
                owner.accessToken(),
                "Main Account"
        );

        performInviteUser(
                owner.accessToken(),
                "Duplicate Email",
                OWNER_EMAIL,
                "COLLABORATOR",
                DEFAULT_LOCALE,
                true,
                List.of(accountId)
        )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("auth.userInvitation.emailAlreadyExists"));

        assertThat(authActionTokenRepository.findAll())
                .filteredOn(token -> token.getAuthActionTokenType() == AuthActionTokenType.USER_INVITATION)
                .isEmpty();
    }

    @Test
    void inviteUserShouldNormalizeEmailToLowercase() throws Exception {
        AuthenticatedUser owner = registerVerifyAndLoginOwner();

        UUID accountId = createAccountAndExtractId(
                owner.accessToken(),
                "Main Account"
        );

        String uniquePart = UUID.randomUUID().toString().replace("-", "");
        String rawEmail = "INVITED.%s@EXAMPLE.COM".formatted(uniquePart);
        String expectedEmail = "invited.%s@example.com".formatted(uniquePart);

        performInviteUser(
                owner.accessToken(),
                "Upper Email",
                rawEmail,
                "COLLABORATOR",
                DEFAULT_LOCALE,
                true,
                List.of(accountId)
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(expectedEmail));

        assertThat(userRepository.findByEmailAndUserDeletedAtIsNull(expectedEmail)).isPresent();
        assertThat(userRepository.findByEmailAndUserDeletedAtIsNull(rawEmail)).isEmpty();
    }

    @Test
    void inviteUserShouldReturnValidationErrors() throws Exception {
        AuthenticatedUser owner = registerVerifyAndLoginOwner();

        mockMvc.perform(post("/api/auth/user-invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .content("""
                                {
                                  "userName": "",
                                  "email": "not-an-email",
                                  "userRole": null,
                                  "preferredLocale": "fr-FR",
                                  "accountIds": [null]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fieldErrors[*].code", hasItems(
                        "user.userName.required",
                        "user.email.invalid",
                        "auth.userInvitation.role.required",
                        "user.preferredLocale.invalid"
                )));
    }

    @Test
    void acceptInvitationShouldRejectInvalidToken() throws Exception {
        performAcceptInvitation("invalid-token", INVITED_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.userInvitation.invalidOrExpired"));
    }

    @Test
    void acceptInvitationShouldRejectAlreadyUsedToken() throws Exception {
        AuthenticatedUser owner = registerVerifyAndLoginOwner();

        UUID accountId = createAccountAndExtractId(
                owner.accessToken(),
                "Main Account"
        );

        String invitedEmail = uniqueEmail("used-token");

        inviteUser(
                owner.accessToken(),
                "Used Token",
                invitedEmail,
                "COLLABORATOR",
                DEFAULT_LOCALE,
                List.of(accountId)
        );

        String invitationToken = extractLatestInvitationToken(invitedEmail);

        performAcceptInvitation(invitationToken, INVITED_PASSWORD)
                .andExpect(status().isNoContent());

        performAcceptInvitation(invitationToken, "AnotherVeryStrongPassword-2026!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.userInvitation.invalidOrExpired"));
    }

    @Test
    void acceptInvitationShouldRejectExpiredTokenWithoutEnablingUser() throws Exception {
        AuthenticatedUser owner = registerVerifyAndLoginOwner();

        UUID accountId = createAccountAndExtractId(
                owner.accessToken(),
                "Main Account"
        );

        String invitedEmail = uniqueEmail("expired-token");

        inviteUser(
                owner.accessToken(),
                "Expired Token",
                invitedEmail,
                "COLLABORATOR",
                DEFAULT_LOCALE,
                List.of(accountId)
        );

        String invitationToken = extractLatestInvitationToken(invitedEmail);

        AuthActionToken actionToken = authActionTokenRepository
                .findByAuthActionTokenHash(tokenHashingService.hash(invitationToken))
                .orElseThrow();

        jdbcTemplate.update("""
                update auth_action_tokens
                set auth_action_created_at = now() - interval '8 days',
                    auth_action_expires_at = now() - interval '1 minute'
                where auth_action_token_id = ?
                """, actionToken.getAuthActionTokenId());

        performAcceptInvitation(invitationToken, INVITED_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.userInvitation.invalidOrExpired"));

        User invitedUser = userRepository.findByEmailAndUserDeletedAtIsNull(invitedEmail)
                .orElseThrow();

        assertThat(invitedUser.isUserIsEnabled()).isFalse();
    }

    @Test
    void acceptInvitationShouldRejectWeakPasswordWithoutUsingTokenOrEnablingUser() throws Exception {
        AuthenticatedUser owner = registerVerifyAndLoginOwner();

        UUID accountId = createAccountAndExtractId(
                owner.accessToken(),
                "Main Account"
        );

        String invitedEmail = uniqueEmail("weak-password");

        inviteUser(
                owner.accessToken(),
                "Weak Password",
                invitedEmail,
                "COLLABORATOR",
                DEFAULT_LOCALE,
                List.of(accountId)
        );

        String invitationToken = extractLatestInvitationToken(invitedEmail);

        performAcceptInvitation(invitationToken, WEAK_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.password.tooWeak"));

        User invitedUser = userRepository.findByEmailAndUserDeletedAtIsNull(invitedEmail)
                .orElseThrow();

        assertThat(invitedUser.isUserIsEnabled()).isFalse();

        AuthActionToken actionToken = authActionTokenRepository
                .findByAuthActionTokenHash(tokenHashingService.hash(invitationToken))
                .orElseThrow();

        assertThat(actionToken.getAuthActionUsedAt()).isNull();
        assertThat(actionToken.getAuthActionRevokedAt()).isNull();

        performAcceptInvitation(invitationToken, INVITED_PASSWORD)
                .andExpect(status().isNoContent());

        assertThat(userRepository.findByEmailAndUserDeletedAtIsNull(invitedEmail)
                .orElseThrow()
                .isUserIsEnabled()).isTrue();
    }

    @Test
    void acceptInvitationShouldReturnValidationErrors() throws Exception {
        mockMvc.perform(post("/api/auth/user-invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                        .content("""
                                {
                                  "token": "",
                                  "password": "short"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fieldErrors[*].code", hasItems(
                        "auth.token.required",
                        "auth.password.invalidLength"
                )));
    }

    @Test
    void inviteUserShouldRejectInvalidAccessToken() throws Exception {
        long authActionTokensBefore = authActionTokenRepository.count();
        long emailOutboxBefore = emailOutboxRepository.count();

        String invitedEmail = uniqueEmail("invalid-bearer-invite");

        performInviteUser(
                "invalid-access-token",
                "Invalid Bearer Invite",
                invitedEmail,
                "COLLABORATOR",
                DEFAULT_LOCALE,
                true,
                List.of(UUID.randomUUID())
        )
                .andExpect(status().isUnauthorized());

        assertThat(authActionTokenRepository.count())
                .isEqualTo(authActionTokensBefore);

        assertThat(emailOutboxRepository.count())
                .isEqualTo(emailOutboxBefore);

        assertThat(userRepository.findByEmailAndUserDeletedAtIsNull(invitedEmail))
                .isEmpty();
    }

    @Test
    void invitedUserShouldNotLoginBeforeAcceptingInvitation() throws Exception {
        AuthenticatedUser owner = registerVerifyAndLoginOwner();

        UUID accountId = createAccountAndExtractId(owner.accessToken(), "Main Account");

        String invitedEmail = uniqueEmail("pending-login");

        inviteUser(
                owner.accessToken(),
                "Pending User",
                invitedEmail,
                "COLLABORATOR",
                DEFAULT_LOCALE,
                List.of(accountId)
        );

        performLogin(invitedEmail, INVITED_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.login.invalidCredentials"));

        User invitedUser = userRepository.findByEmailAndUserDeletedAtIsNull(invitedEmail)
                .orElseThrow();

        assertThat(invitedUser.isUserIsEnabled()).isFalse();
    }

    @Test
    void inviteUserShouldRejectDuplicatePendingInvitationEmail() throws Exception {
        AuthenticatedUser owner = registerVerifyAndLoginOwner();

        UUID accountId = createAccountAndExtractId(owner.accessToken(), "Main Account");

        String invitedEmail = uniqueEmail("duplicate-pending");

        inviteUser(
                owner.accessToken(),
                "First Invite",
                invitedEmail,
                "COLLABORATOR",
                DEFAULT_LOCALE,
                List.of(accountId)
        );

        performInviteUser(
                owner.accessToken(),
                "Second Invite",
                invitedEmail,
                "COLLABORATOR",
                DEFAULT_LOCALE,
                true,
                List.of(accountId)
        )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("auth.userInvitation.emailAlreadyExists"));

        assertThat(userRepository.findAll().stream()
                .filter(user -> invitedEmail.equals(user.getEmail()))
                .toList()).hasSize(1);

        assertThat(authActionTokenRepository.findAll().stream()
                .filter(token -> token.getAuthActionTokenType() == AuthActionTokenType.USER_INVITATION)
                .toList()).hasSize(1);
    }

    @Test
    void inviteUserShouldRollbackWhenOneAccountIdIsInvalid() throws Exception {
        AuthenticatedUser owner = registerVerifyAndLoginOwner();

        UUID validAccountId = createAccountAndExtractId(owner.accessToken(), "Valid Account");
        UUID invalidAccountId = UUID.randomUUID();

        String invitedEmail = uniqueEmail("rollback-invalid-account");

        performInviteUser(
                owner.accessToken(),
                "Rollback User",
                invitedEmail,
                "COLLABORATOR",
                DEFAULT_LOCALE,
                true,
                List.of(validAccountId, invalidAccountId)
        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("auth.userInvitation.accountNotFound"));

        assertThat(userRepository.findByEmailAndUserDeletedAtIsNull(invitedEmail)).isEmpty();

        assertThat(authActionTokenRepository.findAll().stream()
                .filter(token -> token.getAuthActionTokenType() == AuthActionTokenType.USER_INVITATION)
                .toList()).isEmpty();

        assertThat(emailOutboxRepository.findAll().stream()
                .filter(email -> email.getEmailType() == EmailOutboxType.USER_INVITATION)
                .toList()).isEmpty();
    }

    @Test
    void acceptInvitationShouldRejectTokenWhenInvitedUserIsPendingDeletion() throws Exception {
        AuthenticatedUser owner = registerVerifyAndLoginOwner();

        UUID accountId = createAccountAndExtractId(owner.accessToken(), "Main Account");

        String invitedEmail = uniqueEmail("pending-deletion");

        inviteUser(
                owner.accessToken(),
                "Pending Deletion",
                invitedEmail,
                "COLLABORATOR",
                DEFAULT_LOCALE,
                List.of(accountId)
        );

        String invitationToken = extractLatestInvitationToken(invitedEmail);

        User invitedUser = userRepository.findByEmailAndUserDeletedAtIsNull(invitedEmail)
                .orElseThrow();

        invitedUser.markAsSoftDeleted();
        userRepository.saveAndFlush(invitedUser);

        performAcceptInvitation(invitationToken, INVITED_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.userInvitation.invalidOrExpired"));
    }

    @Test
    void acceptInvitationShouldRejectPendingTokenWhenUserIsAlreadyEnabled() throws Exception {
        AuthenticatedUser owner = registerVerifyAndLoginOwner();

        UUID accountId = createAccountAndExtractId(owner.accessToken(), "Main Account");

        String invitedEmail = uniqueEmail("already-enabled");

        inviteUser(
                owner.accessToken(),
                "Already Enabled",
                invitedEmail,
                "COLLABORATOR",
                DEFAULT_LOCALE,
                List.of(accountId)
        );

        String invitationToken = extractLatestInvitationToken(invitedEmail);

        User invitedUser = userRepository.findByEmailAndUserDeletedAtIsNull(invitedEmail)
                .orElseThrow();

        invitedUser.setUserIsEnabled(true);
        userRepository.saveAndFlush(invitedUser);

        performAcceptInvitation(invitationToken, INVITED_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.userInvitation.invalidOrExpired"));

        AuthActionToken actionToken = authActionTokenRepository
                .findByAuthActionTokenHash(tokenHashingService.hash(invitationToken))
                .orElseThrow();

        assertThat(actionToken.getAuthActionUsedAt()).isNull();
        assertThat(actionToken.getAuthActionRevokedAt()).isNull();
    }

    @Test
    void inviteViewerCollaboratorShouldApplyDefaultsWhenOptionalFieldsAreMissing() throws Exception {
        AuthenticatedUser owner = registerVerifyAndLoginOwner();

        String invitedEmail = uniqueEmail("defaults");

        mockMvc.perform(post("/api/auth/user-invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .content("""
                                {
                                  "userName": "Default Invited",
                                  "email": "%s",
                                  "userRole": "VIEWER_COLLABORATOR"
                                }
                                """.formatted(invitedEmail)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.preferredLocale").value(IT_LOCALE))
                .andExpect(jsonPath("$.accountIds").isArray())
                .andExpect(jsonPath("$.accountIds").isEmpty());

        User invitedUser = userRepository.findByEmailAndUserDeletedAtIsNull(invitedEmail)
                .orElseThrow();

        assertThat(invitedUser.getPreferredLocale()).isEqualTo(IT_LOCALE);
        assertThat(invitedUser.isPaymentEmailRemindersEnabled()).isTrue();
    }

    @Test
    void acceptedSuperCollaboratorShouldSeeAllGroupAccounts() throws Exception {
        AuthenticatedUser owner = registerVerifyAndLoginOwner();

        UUID firstAccountId = createAccountAndExtractId(owner.accessToken(), "First Account");
        UUID secondAccountId = createAccountAndExtractId(owner.accessToken(), "Second Account");

        String invitedEmail = uniqueEmail("super-see-all");

        inviteUser(
                owner.accessToken(),
                "Super User",
                invitedEmail,
                "SUPER_COLLABORATOR",
                DEFAULT_LOCALE,
                List.of()
        );

        performAcceptInvitation(
                extractLatestInvitationToken(invitedEmail),
                INVITED_PASSWORD
        )
                .andExpect(status().isNoContent());

        String accessToken = loginAndExtractAccessToken(invitedEmail, INVITED_PASSWORD);

        mockMvc.perform(get("/api/finance/accounts")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].accountId", hasItems(
                        firstAccountId.toString(),
                        secondAccountId.toString()
                )));
    }

    @Test
    void acceptInvitationShouldStoreChosenPasswordOnlyAsHash() throws Exception {
        AuthenticatedUser owner = registerVerifyAndLoginOwner();

        UUID accountId = createAccountAndExtractId(
                owner.accessToken(),
                "Main Account"
        );

        String invitedEmail = uniqueEmail("password-hash");

        inviteUser(
                owner.accessToken(),
                "Password Hash User",
                invitedEmail,
                "COLLABORATOR",
                DEFAULT_LOCALE,
                List.of(accountId)
        );

        User invitedUserBeforeAccept = userRepository.findByEmailAndUserDeletedAtIsNull(invitedEmail)
                .orElseThrow();

        String temporaryPasswordHash = invitedUserBeforeAccept.getUserPasswordHash();

        assertThat(temporaryPasswordHash).isNotBlank();
        assertThat(temporaryPasswordHash).isNotEqualTo(INVITED_PASSWORD);
        assertThat(passwordEncoder.matches(INVITED_PASSWORD, temporaryPasswordHash)).isFalse();

        String invitationToken = extractLatestInvitationToken(invitedEmail);

        performAcceptInvitation(invitationToken, INVITED_PASSWORD)
                .andExpect(status().isNoContent());

        User invitedUserAfterAccept = userRepository.findByEmailAndUserDeletedAtIsNull(invitedEmail)
                .orElseThrow();

        String savedPasswordHash = invitedUserAfterAccept.getUserPasswordHash();

        assertThat(savedPasswordHash).isNotBlank();
        assertThat(savedPasswordHash).isNotEqualTo(INVITED_PASSWORD);
        assertThat(savedPasswordHash).isNotEqualTo(temporaryPasswordHash);
        assertThat(savedPasswordHash).doesNotContain(INVITED_PASSWORD);
        assertThat(passwordEncoder.matches(INVITED_PASSWORD, savedPasswordHash)).isTrue();

        performLogin(invitedEmail, INVITED_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    void userInvitationEmailShouldBeProcessedByOutboxSender() throws Exception {
        AuthenticatedUser owner = registerVerifyAndLoginOwner();

        int processedBeforeInvitation = processDueEmails();

        assertThat(processedBeforeInvitation).isGreaterThanOrEqualTo(1);

        UUID accountId = createAccountAndExtractId(
                owner.accessToken(),
                "Main Account"
        );

        String invitedEmail = uniqueEmail("outbox-send");

        inviteUser(
                owner.accessToken(),
                "Outbox Send User",
                invitedEmail,
                "COLLABORATOR",
                DEFAULT_LOCALE,
                List.of(accountId)
        );

        EmailOutbox invitationEmailBeforeSend = latestPendingEmailOutbox(
                EmailOutboxType.USER_INVITATION,
                invitedEmail
        );

        assertThat(invitationEmailBeforeSend.getEmailStatus())
                .isEqualTo(EmailOutboxStatus.PENDING);
        assertThat(invitationEmailBeforeSend.getAttempts()).isZero();
        assertThat(invitationEmailBeforeSend.getEmailSentAt()).isNull();
        assertThat(invitationEmailBeforeSend.getProvider()).isNull();
        assertThat(invitationEmailBeforeSend.getProviderMessageId()).isNull();

        String bodyBeforeSend = decryptTextBody(invitationEmailBeforeSend);
        String invitationToken = extractTokenFromBody(bodyBeforeSend);

        AuthActionToken invitationActionToken = authActionTokenRepository
                .findByAuthActionTokenHash(tokenHashingService.hash(invitationToken))
                .orElseThrow();

        assertThat(invitationActionToken.getAuthActionTokenType())
                .isEqualTo(AuthActionTokenType.USER_INVITATION);

        int processedEmails = processDueEmails();

        assertThat(processedEmails).isEqualTo(1);

        EmailOutbox invitationEmailAfterSend = emailOutboxRepository
                .findById(invitationEmailBeforeSend.getEmailOutboxId())
                .orElseThrow();

        assertThat(invitationEmailAfterSend.getEmailType())
                .isEqualTo(EmailOutboxType.USER_INVITATION);
        assertThat(invitationEmailAfterSend.getEmailStatus())
                .isEqualTo(EmailOutboxStatus.SENT);
        assertThat(invitationEmailAfterSend.getAttempts()).isEqualTo(1);
        assertThat(invitationEmailAfterSend.getEmailSentAt()).isNotNull();
        assertThat(invitationEmailAfterSend.getLastError()).isNull();
        assertThat(invitationEmailAfterSend.getProvider()).isNotBlank();
        assertThat(invitationEmailAfterSend.getProviderMessageId()).isNotBlank();

        assertThat(invitationEmailAfterSend.getBodyTextEncrypted()).isNull();
        assertThat(invitationEmailAfterSend.getBodyTextIv()).isNull();
        assertThat(invitationEmailAfterSend.getBodyTextTag()).isNull();
        assertThat(invitationEmailAfterSend.getBodyHtmlEncrypted()).isNull();
        assertThat(invitationEmailAfterSend.getBodyHtmlIv()).isNull();
        assertThat(invitationEmailAfterSend.getBodyHtmlTag()).isNull();
        assertThat(invitationEmailAfterSend.getEmailBodyDeletedAt()).isNotNull();

        performAcceptInvitation(invitationToken, INVITED_PASSWORD)
                .andExpect(status().isNoContent());
    }

    private AuthenticatedUser registerVerifyAndLoginOwner() throws Exception {
        return registerVerifyAndLoginUser(
                OWNER_NAME,
                OWNER_EMAIL,
                OWNER_PASSWORD
        );
    }

    private AuthenticatedUser registerVerifyAndLoginUser(
            String userName,
            String email,
            String password
    ) throws Exception {
        performRegister(userName, email, password, DEFAULT_LOCALE)
                .andExpect(status().isCreated());

        String verificationToken = extractLatestEmailVerificationToken(email);

        performVerifyEmail(verificationToken)
                .andExpect(status().isOk());

        User user = userRepository.findByEmailAndUserDeletedAtIsNull(email)
                .orElseThrow();

        MvcResult loginResult = performLoginWithDevice(email, password)
                .andExpect(status().isOk())
                .andReturn();

        return new AuthenticatedUser(
                user.getUserId(),
                user.getUserGroup().getUserGroupId(),
                email,
                jsonPathString(loginResult, "$.accessToken")
        );
    }

    private ResultActions performRegister(
            String userName,
            String email,
            String password,
            String preferredLocale
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
                        password,
                        preferredLocale
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
                        """.formatted(email, password)));
    }

    private ResultActions performLoginWithDevice(
            String email,
            String password
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
                        DEFAULT_DEVICE_LABEL
                )));
    }

    private String loginAndExtractAccessToken(
            String email,
            String password
    ) throws Exception {
        MvcResult result = performLoginWithDevice(email, password)
                .andExpect(status().isOk())
                .andReturn();

        return jsonPathString(result, "$.accessToken");
    }

    private UUID createAccountAndExtractId(
            String accessToken,
            String accountName
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/finance/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .content("""
                                {
                                  "accountName": "%s",
                                  "currency": "EUR",
                                  "openingBalance": 0.00,
                                  "openingBalanceDate": "2026-01-01"
                                }
                                """.formatted(accountName)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").isString())
                .andReturn();

        return UUID.fromString(jsonPathString(result, "$.accountId"));
    }

    private void inviteUser(
            String ownerAccessToken,
            String userName,
            String email,
            String userRole,
            String preferredLocale,
            List<UUID> accountIds
    ) throws Exception {
        performInviteUser(
                ownerAccessToken,
                userName,
                email,
                userRole,
                preferredLocale,
                true,
                accountIds
        )
                .andExpect(status().isCreated());
    }

    private ResultActions performInviteUser(
            String accessToken,
            String userName,
            String email,
            String userRole,
            String preferredLocale,
            Boolean paymentEmailRemindersEnabled,
            List<UUID> accountIds
    ) throws Exception {
        return mockMvc.perform(post("/api/auth/user-invitations")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .content(inviteJson(
                        userName,
                        email,
                        userRole,
                        preferredLocale,
                        paymentEmailRemindersEnabled,
                        accountIds
                )));
    }

    private ResultActions performInviteUserWithoutAccessToken(
            String userName,
            String email,
            String userRole,
            List<UUID> accountIds
    ) throws Exception {
        return mockMvc.perform(post("/api/auth/user-invitations")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .content(inviteJson(
                        userName,
                        email,
                        userRole,
                        DEFAULT_LOCALE,
                        true,
                        accountIds
                )));
    }

    private String inviteJson(
            String userName,
            String email,
            String userRole,
            String preferredLocale,
            Boolean paymentEmailRemindersEnabled,
            List<UUID> accountIds
    ) {
        String accountIdsJson = accountIds == null
                ? "null"
                : accountIds.stream()
                  .map(UUID::toString)
                  .map(value -> "\"" + value + "\"")
                  .reduce((left, right) -> left + ", " + right)
                  .map(value -> "[" + value + "]")
                  .orElse("[]");

        return """
                {
                  "userName": "%s",
                  "email": "%s",
                  "userRole": "%s",
                  "preferredLocale": "%s",
                  "paymentEmailRemindersEnabled": %s,
                  "accountIds": %s
                }
                """.formatted(
                userName,
                email,
                userRole,
                preferredLocale,
                paymentEmailRemindersEnabled,
                accountIdsJson
        );
    }

    private ResultActions performAcceptInvitation(
            String token,
            String password
    ) throws Exception {
        return mockMvc.perform(post("/api/auth/user-invitations/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                .content("""
                        {
                          "token": "%s",
                          "password": "%s"
                        }
                        """.formatted(token, password)));
    }

    private String extractLatestEmailVerificationToken(String email) {
        EmailOutbox emailOutbox = latestPendingEmailOutbox(
                EmailOutboxType.EMAIL_VERIFICATION,
                email
        );

        return extractTokenFromBody(decryptTextBody(emailOutbox));
    }

    private String extractLatestInvitationToken(String email) {
        EmailOutbox emailOutbox = latestPendingEmailOutbox(
                EmailOutboxType.USER_INVITATION,
                email
        );

        return extractTokenFromBody(decryptTextBody(emailOutbox));
    }

    private EmailOutbox latestPendingEmailOutbox(
            EmailOutboxType emailType,
            String recipientEmail
    ) {
        return emailOutboxRepository.findAll()
                .stream()
                .filter(email -> email.getEmailType() == emailType)
                .filter(email -> recipientEmail.equals(email.getRecipientEmail()))
                .filter(email -> email.getEmailStatus() == EmailOutboxStatus.PENDING)
                .max(Comparator.comparing(EmailOutbox::getEmailCreatedAt))
                .orElseThrow();
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

    private String uniqueEmail(String prefix) {
        return "%s.%s@example.com".formatted(
                prefix,
                UUID.randomUUID().toString().replace("-", "")
        );
    }

    private int processDueEmails() {
        EmailOutboxProcessor processor = new EmailOutboxProcessor(
                emailOutboxRepository,
                emailOutboxEncryptionService,
                emailSender,
                OUTBOX_BATCH_SIZE,
                OUTBOX_RETRY_DELAY
        );

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        return transactionTemplate.execute(status -> processor.processDueEmails());
    }

    private record AuthenticatedUser(
            UUID userId,
            UUID userGroupId,
            String email,
            String accessToken
    ) {
    }
}