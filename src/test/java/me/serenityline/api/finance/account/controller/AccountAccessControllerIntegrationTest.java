package me.serenityline.api.finance.account.controller;

import me.serenityline.api.finance.account.entity.Account;
import me.serenityline.api.finance.account.entity.AccountUser;
import me.serenityline.api.finance.account.repository.AccountRepository;
import me.serenityline.api.finance.account.repository.AccountUserRepository;
import me.serenityline.api.security.jwt.JwtTokenService;
import me.serenityline.api.support.IntegrationTestSupport;
import me.serenityline.api.user.entity.*;
import me.serenityline.api.user.repository.UserGroupRepository;
import me.serenityline.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountAccessControllerIntegrationTest extends IntegrationTestSupport {

    private static final String ACCOUNTS_PATH = "/api/finance/accounts";
    private static final String IT_LOCALE = "it-IT";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserGroupRepository userGroupRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountUserRepository accountUserRepository;

    private static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private static String accountUserPath(UUID accountId, UUID targetUserId) {
        return ACCOUNTS_PATH + "/" + accountId + "/users/" + targetUserId;
    }

    @Test
    void grantAccountAccessShouldRequireAuthentication() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();

        mockMvc.perform(post(accountUserPath(accountId, targetUserId))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revokeAccountAccessShouldRequireAuthentication() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();

        mockMvc.perform(delete(accountUserPath(accountId, targetUserId))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void grantAccountAccessShouldAllowOwnerToGrantAccessToCollaborator() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        User collaborator = createVerifiedUser(userGroup, UserRole.COLLABORATOR);
        Account account = createAccount(userGroup, "Conto condiviso");

        mockMvc.perform(post(accountUserPath(account.getAccountId(), collaborator.getUserId()))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNoContent());

        assertThat(accountAccessExists(account, collaborator)).isTrue();
    }

    @Test
    void grantAccountAccessShouldAllowSuperCollaboratorToGrantAccessToViewerCollaborator() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        User superCollaborator = createVerifiedUser(userGroup, UserRole.SUPER_COLLABORATOR);
        User viewerCollaborator = createVerifiedUser(userGroup, UserRole.VIEWER_COLLABORATOR);
        Account account = createAccount(userGroup, "Conto condiviso");

        mockMvc.perform(post(accountUserPath(account.getAccountId(), viewerCollaborator.getUserId()))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(superCollaborator))))
                .andExpect(status().isNoContent());

        assertThat(accountAccessExists(account, viewerCollaborator)).isTrue();
    }

    @Test
    void grantAccountAccessShouldBeIdempotentWhenAccessAlreadyExists() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        User collaborator = createVerifiedUser(userGroup, UserRole.COLLABORATOR);
        Account account = createAccount(userGroup, "Conto condiviso");

        grantAccess(account, collaborator);

        mockMvc.perform(post(accountUserPath(account.getAccountId(), collaborator.getUserId()))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNoContent());

        assertThat(countAccountAccesses(account, collaborator)).isEqualTo(1);
    }

    @Test
    void grantAccountAccessShouldReturnNotFoundWhenTargetUserBelongsToAnotherUserGroup() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        Account account = createAccount(userGroup, "Conto condiviso");

        User otherGroupUser = createVerifiedUser(UserRole.COLLABORATOR);

        mockMvc.perform(post(accountUserPath(account.getAccountId(), otherGroupUser.getUserId()))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.accountAccess.targetUserNotFound"))
                .andExpect(jsonPath("$.message").value("Utente destinatario non trovato."))
                .andExpect(jsonPath("$.path").value(accountUserPath(account.getAccountId(), otherGroupUser.getUserId())))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        assertThat(accountAccessExists(account, otherGroupUser)).isFalse();
    }

    @Test
    void grantAccountAccessShouldReturnNotFoundWhenAccountBelongsToAnotherUserGroup() throws Exception {
        User currentOwner = createVerifiedUser(UserRole.OWNER);
        User targetUser = createVerifiedUser(currentOwner.getUserGroup(), UserRole.COLLABORATOR);

        User otherOwner = createVerifiedUser(UserRole.OWNER);
        Account otherGroupAccount = createAccount(otherOwner.getUserGroup(), "Conto altro gruppo");

        mockMvc.perform(post(accountUserPath(otherGroupAccount.getAccountId(), targetUser.getUserId()))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(currentOwner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.account.notFound"))
                .andExpect(jsonPath("$.message").value("Conto non trovato."))
                .andExpect(jsonPath("$.path").value(accountUserPath(otherGroupAccount.getAccountId(), targetUser.getUserId())))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        assertThat(accountAccessExists(otherGroupAccount, targetUser)).isFalse();
    }

    @Test
    void grantAccountAccessShouldRejectCollaborator() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        User collaborator = createVerifiedUser(userGroup, UserRole.COLLABORATOR);
        User targetUser = createVerifiedUser(userGroup, UserRole.VIEWER_COLLABORATOR);
        Account account = createAccount(userGroup, "Conto condiviso");

        mockMvc.perform(post(accountUserPath(account.getAccountId(), targetUser.getUserId()))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("finance.accountAccess.manageForbidden"))
                .andExpect(jsonPath("$.message").value("Non hai i permessi per gestire gli accessi al conto."))
                .andExpect(jsonPath("$.path").value(accountUserPath(account.getAccountId(), targetUser.getUserId())))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        assertThat(accountAccessExists(account, targetUser)).isFalse();
    }

    @Test
    void grantAccountAccessShouldRejectViewerCollaborator() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        User viewerCollaborator = createVerifiedUser(userGroup, UserRole.VIEWER_COLLABORATOR);
        User targetUser = createVerifiedUser(userGroup, UserRole.COLLABORATOR);
        Account account = createAccount(userGroup, "Conto condiviso");

        mockMvc.perform(post(accountUserPath(account.getAccountId(), targetUser.getUserId()))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(viewerCollaborator))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("finance.accountAccess.manageForbidden"))
                .andExpect(jsonPath("$.message").value("Non hai i permessi per gestire gli accessi al conto."))
                .andExpect(jsonPath("$.path").value(accountUserPath(account.getAccountId(), targetUser.getUserId())))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        assertThat(accountAccessExists(account, targetUser)).isFalse();
    }

    @Test
    void grantAccountAccessShouldTreatOwnerTargetAsNoOp() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        Account account = createAccount(userGroup, "Conto condiviso");

        mockMvc.perform(post(accountUserPath(account.getAccountId(), owner.getUserId()))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNoContent());

        assertThat(accountAccessExists(account, owner)).isFalse();
    }

    @Test
    void revokeAccountAccessShouldAllowOwnerToRevokeCollaboratorAccess() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        User collaborator = createVerifiedUser(userGroup, UserRole.COLLABORATOR);
        Account account = createAccount(userGroup, "Conto condiviso");

        grantAccess(account, collaborator);

        mockMvc.perform(delete(accountUserPath(account.getAccountId(), collaborator.getUserId()))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNoContent());

        assertThat(accountAccessExists(account, collaborator)).isFalse();
    }

    @Test
    void revokeAccountAccessShouldAllowSuperCollaboratorToRevokeViewerCollaboratorAccess() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        User superCollaborator = createVerifiedUser(userGroup, UserRole.SUPER_COLLABORATOR);
        User viewerCollaborator = createVerifiedUser(userGroup, UserRole.VIEWER_COLLABORATOR);
        Account account = createAccount(userGroup, "Conto condiviso");

        grantAccess(account, viewerCollaborator);

        mockMvc.perform(delete(accountUserPath(account.getAccountId(), viewerCollaborator.getUserId()))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(superCollaborator))))
                .andExpect(status().isNoContent());

        assertThat(accountAccessExists(account, viewerCollaborator)).isFalse();
    }

    @Test
    void revokeAccountAccessShouldBeIdempotentWhenAccessDoesNotExist() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        User collaborator = createVerifiedUser(userGroup, UserRole.COLLABORATOR);
        Account account = createAccount(userGroup, "Conto condiviso");

        mockMvc.perform(delete(accountUserPath(account.getAccountId(), collaborator.getUserId()))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNoContent());

        assertThat(accountAccessExists(account, collaborator)).isFalse();
    }

    @Test
    void revokeAccountAccessShouldRejectOwnerRemovingSelf() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        Account account = createAccount(userGroup, "Conto condiviso");

        grantAccess(account, owner);

        mockMvc.perform(delete(accountUserPath(account.getAccountId(), owner.getUserId()))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("finance.accountAccess.ownerCannotBeRemoved"))
                .andExpect(jsonPath("$.message").value("L'utente proprietario non può essere rimosso dagli accessi del conto."))
                .andExpect(jsonPath("$.path").value(accountUserPath(account.getAccountId(), owner.getUserId())))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        assertThat(accountAccessExists(account, owner)).isTrue();
    }

    @Test
    void revokeAccountAccessShouldRejectSuperCollaboratorRemovingOwner() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        User superCollaborator = createVerifiedUser(userGroup, UserRole.SUPER_COLLABORATOR);
        Account account = createAccount(userGroup, "Conto condiviso");

        grantAccess(account, owner);

        mockMvc.perform(delete(accountUserPath(account.getAccountId(), owner.getUserId()))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(superCollaborator))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("finance.accountAccess.ownerCannotBeRemoved"))
                .andExpect(jsonPath("$.message").value("L'utente proprietario non può essere rimosso dagli accessi del conto."))
                .andExpect(jsonPath("$.path").value(accountUserPath(account.getAccountId(), owner.getUserId())))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        assertThat(accountAccessExists(account, owner)).isTrue();
    }

    @Test
    void revokeAccountAccessShouldReturnNotFoundWhenTargetUserBelongsToAnotherUserGroup() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        Account account = createAccount(userGroup, "Conto condiviso");

        User otherGroupUser = createVerifiedUser(UserRole.COLLABORATOR);

        mockMvc.perform(delete(accountUserPath(account.getAccountId(), otherGroupUser.getUserId()))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.accountAccess.targetUserNotFound"))
                .andExpect(jsonPath("$.message").value("Utente destinatario non trovato."))
                .andExpect(jsonPath("$.path").value(accountUserPath(account.getAccountId(), otherGroupUser.getUserId())))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void revokeAccountAccessShouldReturnNotFoundWhenAccountBelongsToAnotherUserGroup() throws Exception {
        User currentOwner = createVerifiedUser(UserRole.OWNER);

        User otherOwner = createVerifiedUser(UserRole.OWNER);
        User otherGroupTargetUser = createVerifiedUser(
                otherOwner.getUserGroup(),
                UserRole.COLLABORATOR
        );

        Account otherGroupAccount = createAccount(
                otherOwner.getUserGroup(),
                "Conto altro gruppo"
        );

        grantAccess(otherGroupAccount, otherGroupTargetUser);

        mockMvc.perform(delete(accountUserPath(otherGroupAccount.getAccountId(), otherGroupTargetUser.getUserId()))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(currentOwner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.account.notFound"))
                .andExpect(jsonPath("$.message").value("Conto non trovato."))
                .andExpect(jsonPath("$.path").value(accountUserPath(
                        otherGroupAccount.getAccountId(),
                        otherGroupTargetUser.getUserId()
                )))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        assertThat(accountAccessExists(otherGroupAccount, otherGroupTargetUser)).isTrue();
    }

    @Test
    void revokeAccountAccessShouldRejectCollaborator() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        User collaborator = createVerifiedUser(userGroup, UserRole.COLLABORATOR);
        User targetUser = createVerifiedUser(userGroup, UserRole.VIEWER_COLLABORATOR);
        Account account = createAccount(userGroup, "Conto condiviso");

        grantAccess(account, targetUser);

        mockMvc.perform(delete(accountUserPath(account.getAccountId(), targetUser.getUserId()))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("finance.accountAccess.manageForbidden"))
                .andExpect(jsonPath("$.message").value("Non hai i permessi per gestire gli accessi al conto."))
                .andExpect(jsonPath("$.path").value(accountUserPath(account.getAccountId(), targetUser.getUserId())))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        assertThat(accountAccessExists(account, targetUser)).isTrue();
    }

    @Test
    void revokeAccountAccessShouldRejectViewerCollaborator() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        User viewerCollaborator = createVerifiedUser(userGroup, UserRole.VIEWER_COLLABORATOR);
        User targetUser = createVerifiedUser(userGroup, UserRole.COLLABORATOR);
        Account account = createAccount(userGroup, "Conto condiviso");

        grantAccess(account, targetUser);

        mockMvc.perform(delete(accountUserPath(account.getAccountId(), targetUser.getUserId()))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(viewerCollaborator))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("finance.accountAccess.manageForbidden"))
                .andExpect(jsonPath("$.message").value("Non hai i permessi per gestire gli accessi al conto."))
                .andExpect(jsonPath("$.path").value(accountUserPath(account.getAccountId(), targetUser.getUserId())))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        assertThat(accountAccessExists(account, targetUser)).isTrue();
    }

    @Test
    void grantAccountAccessShouldMakeAccountVisibleToCollaborator() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        User collaborator = createVerifiedUser(userGroup, UserRole.COLLABORATOR);
        Account account = createAccount(userGroup, "Conto condiviso");

        mockMvc.perform(post(accountUserPath(account.getAccountId(), collaborator.getUserId()))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ACCOUNTS_PATH + "/" + account.getAccountId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(account.getAccountId().toString()))
                .andExpect(jsonPath("$.accountName").value("Conto condiviso"));
    }

    @Test
    void revokeAccountAccessShouldMakeAccountInvisibleToCollaborator() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        User collaborator = createVerifiedUser(userGroup, UserRole.COLLABORATOR);
        Account account = createAccount(userGroup, "Conto condiviso");

        grantAccess(account, collaborator);

        mockMvc.perform(delete(accountUserPath(account.getAccountId(), collaborator.getUserId()))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ACCOUNTS_PATH + "/" + account.getAccountId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.account.notFound"))
                .andExpect(jsonPath("$.message").value("Conto non trovato."));
    }

    @Test
    void grantAccountAccessShouldReturnNotFoundWhenTargetUserDoesNotExist() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        Account account = createAccount(owner.getUserGroup(), "Conto condiviso");

        UUID unknownUserId = UUID.randomUUID();

        mockMvc.perform(post(accountUserPath(account.getAccountId(), unknownUserId))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.accountAccess.targetUserNotFound"))
                .andExpect(jsonPath("$.message").value("Utente destinatario non trovato."));

        assertThat(accountUserRepository.findAll()).isEmpty();
    }

    @Test
    void grantAccountAccessShouldReturnNotFoundWhenAccountDoesNotExist() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        User targetUser = createVerifiedUser(owner.getUserGroup(), UserRole.COLLABORATOR);

        UUID unknownAccountId = UUID.randomUUID();

        mockMvc.perform(post(accountUserPath(unknownAccountId, targetUser.getUserId()))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.account.notFound"))
                .andExpect(jsonPath("$.message").value("Conto non trovato."));

        assertThat(accountUserRepository.findAll()).isEmpty();
    }

    private String accessTokenFor(User user) {
        return jwtTokenService.createAccessToken(user)
                .token();
    }

    private User createVerifiedUser(UserRole userRole) {
        UUID uniqueId = UUID.randomUUID();

        UserGroup userGroup = userGroupRepository.saveAndFlush(new UserGroup(
                "Account access group " + uniqueId
        ));

        return createVerifiedUser(userGroup, userRole);
    }

    private User createVerifiedUser(UserGroup userGroup, UserRole userRole) {
        UUID uniqueId = UUID.randomUUID();

        User user = new User(
                "User " + uniqueId,
                "account-access-%s@example.com".formatted(uniqueId.toString().replace("-", "")),
                userGroup,
                userRole,
                UserPlatformRole.USER,
                IT_LOCALE,
                PreferredTheme.DEFAULT,
                false,
                true,
                "encoded-password-" + uniqueId,
                true,
                0L
        );

        return userRepository.saveAndFlush(user);
    }

    private Account createAccount(UserGroup userGroup, String accountName) {
        return accountRepository.saveAndFlush(Account.create(
                accountName,
                "Descrizione " + accountName,
                "EUR",
                "Banca test",
                new BigDecimal("100.00"),
                LocalDate.of(2026, 1, 1),
                userGroup
        ));
    }

    private void grantAccess(Account account, User user) {
        accountUserRepository.saveAndFlush(AccountUser.grant(
                account,
                user
        ));
    }

    private boolean accountAccessExists(Account account, User user) {
        return accountUserRepository.existsByAccount_AccountIdAndUser_UserId(
                account.getAccountId(),
                user.getUserId()
        );
    }

    private long countAccountAccesses(Account account, User user) {
        return accountUserRepository.findAll()
                .stream()
                .filter(accountUser -> accountUser.getAccountId().equals(account.getAccountId()))
                .filter(accountUser -> accountUser.getUserId().equals(user.getUserId()))
                .count();
    }
}