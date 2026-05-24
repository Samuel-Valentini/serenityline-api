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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AccountControllerIntegrationTest extends IntegrationTestSupport {

    private static final String ACCOUNT_PATH = "/api/finance/accounts";
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

    @Test
    void createAccountShouldRequireAuthentication() throws Exception {
        mockMvc.perform(post(ACCOUNT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .content(validCreateAccountJson("Conto principale")))
                .andExpect(status().isUnauthorized());

        assertThat(accountRepository.findAll()).isEmpty();
        assertThat(accountUserRepository.findAll()).isEmpty();
    }

    @Test
    void createAccountShouldCreateAccountForAuthenticatedOwner() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        String accessToken = accessTokenFor(owner);

        mockMvc.perform(post(ACCOUNT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .content("""
                                {
                                  "accountName": "  Conto principale  ",
                                  "accountDescription": "  Conto usato per le spese quotidiane  ",
                                  "currency": "eur",
                                  "issuingInstitution": "  Fineco  ",
                                  "openingBalance": 1234.50,
                                  "openingBalanceDate": "2026-01-15"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").isString())
                .andExpect(jsonPath("$.accountName").value("Conto principale"))
                .andExpect(jsonPath("$.accountDescription").value("Conto usato per le spese quotidiane"))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.issuingInstitution").value("Fineco"))
                .andExpect(jsonPath("$.openingBalance").value(1234.50))
                .andExpect(jsonPath("$.openingBalanceDate").value("2026-01-15"))
                .andExpect(jsonPath("$.userGroupId").value(owner.getUserGroup().getUserGroupId().toString()))
                .andExpect(jsonPath("$.accountCreatedAt").isString())
                .andExpect(jsonPath("$.accountUpdatedAt").isString())
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString("userPasswordHash"))));

        List<Account> accounts = accountRepository.findAll();

        assertThat(accounts).hasSize(1);

        Account account = accounts.getFirst();

        assertThat(account.getAccountName()).isEqualTo("Conto principale");
        assertThat(account.getAccountDescription()).isEqualTo("Conto usato per le spese quotidiane");
        assertThat(account.getCurrency()).isEqualTo("EUR");
        assertThat(account.getIssuingInstitution()).isEqualTo("Fineco");
        assertThat(account.getOpeningBalance()).isEqualByComparingTo("1234.50");
        assertThat(account.getOpeningBalanceDate()).isEqualTo("2026-01-15");
        assertThat(account.getUserGroupId()).isEqualTo(owner.getUserGroup().getUserGroupId());

        List<AccountUser> accountUsers = accountUserRepository.findAll();

        assertThat(accountUsers).hasSize(1);
        assertThat(accountUsers.getFirst().getAccountId()).isEqualTo(account.getAccountId());
        assertThat(accountUsers.getFirst().getUserId()).isEqualTo(owner.getUserId());
        assertThat(accountUsers.getFirst().getUserGroupId()).isEqualTo(owner.getUserGroup().getUserGroupId());
    }

    @Test
    void createAccountShouldRejectValidationErrors() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        String accessToken = accessTokenFor(owner);

        mockMvc.perform(post(ACCOUNT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .content("""
                                {
                                  "accountName": "",
                                  "currency": "EURO",
                                  "openingBalance": 10.999
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.message").value("Validazione fallita."))
                .andExpect(jsonPath("$.path").value(ACCOUNT_PATH))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors[*].code", hasItems(
                        "finance.account.name.required",
                        "finance.account.currency.invalid",
                        "finance.account.openingBalance.invalidScale",
                        "finance.account.openingBalanceDate.required"
                )))
                .andExpect(content().string(not(containsString("exception"))))
                .andExpect(content().string(not(containsString("stacktrace"))))
                .andExpect(content().string(not(containsString("hibernate"))))
                .andExpect(content().string(not(containsString("sql"))));

        assertThat(accountRepository.findAll()).isEmpty();
        assertThat(accountUserRepository.findAll()).isEmpty();
    }

    @Test
    void createAccountShouldRejectDuplicateNormalizedNameInSameUserGroup() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        String accessToken = accessTokenFor(owner);

        mockMvc.perform(post(ACCOUNT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .content(validCreateAccountJson("Conto principale")))
                .andExpect(status().isCreated());

        mockMvc.perform(post(ACCOUNT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .content(validCreateAccountJson("   conto     PRINCIPALE   ")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("finance.account.nameAlreadyExists"))
                .andExpect(jsonPath("$.message").value("Esiste già un conto con questo nome."))
                .andExpect(jsonPath("$.path").value(ACCOUNT_PATH))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        assertThat(accountRepository.countByUserGroup_UserGroupId(
                owner.getUserGroup().getUserGroupId()
        )).isEqualTo(1);
    }

    @Test
    void createAccountShouldRejectCollaborator() throws Exception {
        User collaborator = createVerifiedUser(UserRole.COLLABORATOR);
        String accessToken = accessTokenFor(collaborator);

        mockMvc.perform(post(ACCOUNT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .content(validCreateAccountJson("Conto collaboratore")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("finance.account.create.forbidden"))
                .andExpect(jsonPath("$.message").value("Non hai i permessi per creare conti."));

        assertThat(accountRepository.findAll()).isEmpty();
        assertThat(accountUserRepository.findAll()).isEmpty();
    }

    @Test
    void getAccountsShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get(ACCOUNT_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @EnumSource(
            value = UserRole.class,
            names = {
                    "OWNER",
                    "SUPER_COLLABORATOR",
                    "VIEWER_COLLABORATOR"
            }
    )
    void getAccountsShouldReturnAllGroupAccountsForRolesWithGroupVisibility(UserRole userRole) throws Exception {
        User user = createVerifiedUser(userRole);
        UserGroup userGroup = user.getUserGroup();

        createAccount(userGroup, "Conto B");
        createAccount(userGroup, "Conto A");

        User otherUser = createVerifiedUser(UserRole.OWNER);
        createAccount(otherUser.getUserGroup(), "Conto altro gruppo");

        mockMvc.perform(get(ACCOUNT_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].accountName", containsInAnyOrder(
                        "Conto A",
                        "Conto B"
                )))
                .andExpect(jsonPath("$[*].userGroupId", everyItem(is(userGroup.getUserGroupId().toString()))));
    }

    @Test
    void getAccountsShouldReturnOnlyLinkedAccountsForCollaborator() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        User collaborator = createVerifiedUser(userGroup, UserRole.COLLABORATOR);

        Account linkedAccount = createAccount(userGroup, "Conto collegato");
        Account unlinkedAccount = createAccount(userGroup, "Conto non collegato");

        grantAccess(linkedAccount, collaborator);

        User otherUser = createVerifiedUser(UserRole.OWNER);
        createAccount(otherUser.getUserGroup(), "Conto altro gruppo");

        mockMvc.perform(get(ACCOUNT_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].accountId").value(linkedAccount.getAccountId().toString()))
                .andExpect(jsonPath("$[0].accountName").value("Conto collegato"))
                .andExpect(jsonPath("$[0].userGroupId").value(userGroup.getUserGroupId().toString()));

        assertThat(unlinkedAccount.getUserGroupId()).isEqualTo(userGroup.getUserGroupId());
    }

    @Test
    void getAccountsShouldReturnEmptyListForCollaboratorWithoutLinkedAccounts() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        User collaborator = createVerifiedUser(userGroup, UserRole.COLLABORATOR);

        createAccount(userGroup, "Conto non collegato");

        mockMvc.perform(get(ACCOUNT_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @ParameterizedTest
    @EnumSource(
            value = UserRole.class,
            names = {
                    "OWNER",
                    "SUPER_COLLABORATOR"
            }
    )
    void updateAccountShouldUpdateGroupAccountForOwnerAndSuperCollaborator(UserRole userRole) throws Exception {
        User user = createVerifiedUser(userRole);
        UserGroup userGroup = user.getUserGroup();

        Account account = createAccount(userGroup, "Conto vecchio");

        mockMvc.perform(patch(ACCOUNT_PATH + "/" + account.getAccountId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountName": "  Conto aggiornato  ",
                                  "accountDescription": "  Descrizione aggiornata  ",
                                  "issuingInstitution": "  Banca aggiornata  ",
                                  "openingBalance": 1500.25,
                                  "openingBalanceDate": "2026-02-15"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(account.getAccountId().toString()))
                .andExpect(jsonPath("$.accountName").value("Conto aggiornato"))
                .andExpect(jsonPath("$.accountDescription").value("Descrizione aggiornata"))
                .andExpect(jsonPath("$.issuingInstitution").value("Banca aggiornata"))
                .andExpect(jsonPath("$.openingBalance").value(1500.25))
                .andExpect(jsonPath("$.openingBalanceDate").value("2026-02-15"))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.userGroupId").value(userGroup.getUserGroupId().toString()));

        Account reloadedAccount = accountRepository.findById(account.getAccountId())
                .orElseThrow();

        assertThat(reloadedAccount.getAccountName()).isEqualTo("Conto aggiornato");
        assertThat(reloadedAccount.getAccountDescription()).isEqualTo("Descrizione aggiornata");
        assertThat(reloadedAccount.getIssuingInstitution()).isEqualTo("Banca aggiornata");
        assertThat(reloadedAccount.getOpeningBalance()).isEqualByComparingTo("1500.25");
        assertThat(reloadedAccount.getOpeningBalanceDate()).isEqualTo(LocalDate.of(2026, 2, 15));
        assertThat(reloadedAccount.getCurrency()).isEqualTo("EUR");
    }

    @ParameterizedTest
    @EnumSource(
            value = UserRole.class,
            names = {
                    "COLLABORATOR",
                    "VIEWER_COLLABORATOR"
            }
    )
    void updateAccountShouldUpdateLinkedAccountForCollaboratorAndViewerCollaborator(UserRole userRole) throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        User linkedUser = createVerifiedUser(userGroup, userRole);

        Account account = createAccount(userGroup, "Conto collegato");

        grantAccess(account, linkedUser);

        mockMvc.perform(patch(ACCOUNT_PATH + "/" + account.getAccountId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(linkedUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountName": "Conto modificato da collegato",
                                  "openingBalance": 300.00,
                                  "openingBalanceDate": "2026-03-01"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(account.getAccountId().toString()))
                .andExpect(jsonPath("$.accountName").value("Conto modificato da collegato"))
                .andExpect(jsonPath("$.openingBalance").value(300.00))
                .andExpect(jsonPath("$.openingBalanceDate").value("2026-03-01"));

        Account reloadedAccount = accountRepository.findById(account.getAccountId())
                .orElseThrow();

        assertThat(reloadedAccount.getAccountName()).isEqualTo("Conto modificato da collegato");
        assertThat(reloadedAccount.getOpeningBalance()).isEqualByComparingTo("300.00");
        assertThat(reloadedAccount.getOpeningBalanceDate()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @ParameterizedTest
    @EnumSource(
            value = UserRole.class,
            names = {
                    "COLLABORATOR",
                    "VIEWER_COLLABORATOR"
            }
    )
    void updateAccountShouldReturnNotFoundForUnlinkedAccount(UserRole userRole) throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        User unlinkedUser = createVerifiedUser(userGroup, userRole);

        Account account = createAccount(userGroup, "Conto non collegato");

        mockMvc.perform(patch(ACCOUNT_PATH + "/" + account.getAccountId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(unlinkedUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountName": "Tentativo modifica"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.account.notFound"))
                .andExpect(jsonPath("$.message").value("Conto non trovato."))
                .andExpect(jsonPath("$.path").value(ACCOUNT_PATH + "/" + account.getAccountId()))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        Account reloadedAccount = accountRepository.findById(account.getAccountId())
                .orElseThrow();

        assertThat(reloadedAccount.getAccountName()).isEqualTo("Conto non collegato");
    }

    @Test
    void updateAccountShouldReturnNotFoundForAccountFromAnotherUserGroup() throws Exception {
        User currentUser = createVerifiedUser(UserRole.OWNER);

        User otherUser = createVerifiedUser(UserRole.OWNER);
        Account otherGroupAccount = createAccount(otherUser.getUserGroup(), "Conto altro gruppo");

        mockMvc.perform(patch(ACCOUNT_PATH + "/" + otherGroupAccount.getAccountId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(currentUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountName": "Tentativo modifica"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.account.notFound"))
                .andExpect(jsonPath("$.message").value("Conto non trovato."));

        Account reloadedAccount = accountRepository.findById(otherGroupAccount.getAccountId())
                .orElseThrow();

        assertThat(reloadedAccount.getAccountName()).isEqualTo("Conto altro gruppo");
    }

    @Test
    void updateAccountShouldRejectDuplicateNormalizedAccountName() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        createAccount(userGroup, "Conto principale");
        Account accountToUpdate = createAccount(userGroup, "Risparmi");

        mockMvc.perform(patch(ACCOUNT_PATH + "/" + accountToUpdate.getAccountId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountName": "   conto     PRINCIPALE   "
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("finance.account.nameAlreadyExists"))
                .andExpect(jsonPath("$.message").value("Esiste già un conto con questo nome."));

        Account reloadedAccount = accountRepository.findById(accountToUpdate.getAccountId())
                .orElseThrow();

        assertThat(reloadedAccount.getAccountName()).isEqualTo("Risparmi");
    }

    @Test
    void updateAccountShouldAllowSameNormalizedNameForSameAccount() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        Account account = createAccount(userGroup, "Conto principale");

        mockMvc.perform(patch(ACCOUNT_PATH + "/" + account.getAccountId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountName": "   CONTO     PRINCIPALE   "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountName").value("CONTO     PRINCIPALE"));

        Account reloadedAccount = accountRepository.findById(account.getAccountId())
                .orElseThrow();

        assertThat(reloadedAccount.getAccountName()).isEqualTo("CONTO     PRINCIPALE");
    }

    @Test
    void updateAccountShouldRejectOpeningBalanceWithMoreThanTwoDecimals() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        Account account = createAccount(userGroup, "Conto principale");

        mockMvc.perform(patch(ACCOUNT_PATH + "/" + account.getAccountId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "openingBalance": 100.999
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fieldErrors[*].code").value(hasItem("finance.account.openingBalance.invalidScale")));

        Account reloadedAccount = accountRepository.findById(account.getAccountId())
                .orElseThrow();

        assertThat(reloadedAccount.getOpeningBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void updateAccountShouldClearOptionalTextFieldsWhenBlank() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        Account account = createAccount(userGroup, "Conto principale");

        mockMvc.perform(patch(ACCOUNT_PATH + "/" + account.getAccountId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountDescription": "   ",
                                  "issuingInstitution": ""
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountDescription").doesNotExist())
                .andExpect(jsonPath("$.issuingInstitution").doesNotExist());

        Account reloadedAccount = accountRepository.findById(account.getAccountId())
                .orElseThrow();

        assertThat(reloadedAccount.getAccountDescription()).isNull();
        assertThat(reloadedAccount.getIssuingInstitution()).isNull();
    }

    @Test
    void updateAccountShouldLeaveOmittedFieldsUnchanged() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        Account account = createAccount(userGroup, "Conto principale");

        mockMvc.perform(patch(ACCOUNT_PATH + "/" + account.getAccountId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountName": "Conto rinominato"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountName").value("Conto rinominato"))
                .andExpect(jsonPath("$.accountDescription").value("Descrizione Conto principale"))
                .andExpect(jsonPath("$.issuingInstitution").value("Banca test"))
                .andExpect(jsonPath("$.openingBalance").value(100.00))
                .andExpect(jsonPath("$.openingBalanceDate").value("2026-01-01"));

        Account reloadedAccount = accountRepository.findById(account.getAccountId())
                .orElseThrow();

        assertThat(reloadedAccount.getAccountName()).isEqualTo("Conto rinominato");
        assertThat(reloadedAccount.getAccountDescription()).isEqualTo("Descrizione Conto principale");
        assertThat(reloadedAccount.getIssuingInstitution()).isEqualTo("Banca test");
        assertThat(reloadedAccount.getOpeningBalance()).isEqualByComparingTo("100.00");
        assertThat(reloadedAccount.getOpeningBalanceDate()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    private String accessTokenFor(User user) {
        return jwtTokenService.createAccessToken(user)
                .token();
    }

    private String validCreateAccountJson(String accountName) {
        return """
                {
                  "accountName": "%s",
                  "accountDescription": "Conto usato per le spese quotidiane",
                  "currency": "EUR",
                  "issuingInstitution": "Fineco",
                  "openingBalance": 100.00,
                  "openingBalanceDate": "2026-01-01"
                }
                """.formatted(accountName);
    }

    private User createVerifiedUser(UserRole userRole) {
        UUID uniqueId = UUID.randomUUID();

        UserGroup userGroup = userGroupRepository.save(new UserGroup(
                "Account controller group " + uniqueId
        ));

        User user = new User(
                "User " + uniqueId,
                "account-controller-%s@example.com".formatted(uniqueId.toString().replace("-", "")),
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

    private User createVerifiedUser(UserGroup userGroup, UserRole userRole) {
        UUID uniqueId = UUID.randomUUID();

        User user = new User(
                "User " + uniqueId,
                "account-controller-%s@example.com".formatted(uniqueId.toString().replace("-", "")),
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
}