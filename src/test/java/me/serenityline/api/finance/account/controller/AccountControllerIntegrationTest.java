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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AccountControllerIntegrationTest extends IntegrationTestSupport {

    private static final String CREATE_ACCOUNT_PATH = "/api/finance/accounts";
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
        mockMvc.perform(post(CREATE_ACCOUNT_PATH)
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

        mockMvc.perform(post(CREATE_ACCOUNT_PATH)
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

        mockMvc.perform(post(CREATE_ACCOUNT_PATH)
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
                .andExpect(jsonPath("$.path").value(CREATE_ACCOUNT_PATH))
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

        mockMvc.perform(post(CREATE_ACCOUNT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .content(validCreateAccountJson("Conto principale")))
                .andExpect(status().isCreated());

        mockMvc.perform(post(CREATE_ACCOUNT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .content(validCreateAccountJson("   conto     PRINCIPALE   ")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("finance.account.nameAlreadyExists"))
                .andExpect(jsonPath("$.message").value("Esiste già un conto con questo nome."))
                .andExpect(jsonPath("$.path").value(CREATE_ACCOUNT_PATH))
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

        mockMvc.perform(post(CREATE_ACCOUNT_PATH)
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
}