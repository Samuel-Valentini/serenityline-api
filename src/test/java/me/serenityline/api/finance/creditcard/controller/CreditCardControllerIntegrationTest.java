package me.serenityline.api.finance.creditcard.controller;

import me.serenityline.api.finance.account.entity.Account;
import me.serenityline.api.finance.account.entity.AccountUser;
import me.serenityline.api.finance.account.repository.AccountRepository;
import me.serenityline.api.finance.account.repository.AccountUserRepository;
import me.serenityline.api.finance.creditcard.entity.CreditCard;
import me.serenityline.api.finance.creditcard.repository.CreditCardRepository;
import me.serenityline.api.security.jwt.JwtTokenService;
import me.serenityline.api.support.IntegrationTestSupport;
import me.serenityline.api.user.entity.*;
import me.serenityline.api.user.repository.UserGroupRepository;
import me.serenityline.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CreditCardControllerIntegrationTest extends IntegrationTestSupport {

    private static final String CREDIT_CARDS_PATH = "/api/finance/credit-cards";
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

    @Autowired
    private CreditCardRepository creditCardRepository;

    private static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    @Test
    void createCreditCardShouldRequireAuthentication() throws Exception {
        UUID accountId = UUID.randomUUID();

        mockMvc.perform(post(CREDIT_CARDS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .content(validCreateCreditCardJson("Carta principale", accountId)))
                .andExpect(status().isUnauthorized());

        assertThat(creditCardRepository.findAll()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(
            value = UserRole.class,
            names = {
                    "OWNER",
                    "SUPER_COLLABORATOR"
            }
    )
    void createCreditCardShouldAllowOwnerAndSuperCollaboratorOnGroupAccount(UserRole userRole) throws Exception {
        User user = createVerifiedUser(userRole);
        UserGroup userGroup = user.getUserGroup();

        Account account = createAccount(userGroup, "Conto principale");

        mockMvc.perform(post(CREDIT_CARDS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(user)))
                        .content("""
                                {
                                  "creditCardName": "  Carta principale  ",
                                  "creditCardDescription": "  Carta usata per spese quotidiane  ",
                                  "creditCardChargeDay": 15,
                                  "accountId": "%s"
                                }
                                """.formatted(account.getAccountId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creditCardId").isString())
                .andExpect(jsonPath("$.creditCardName").value("Carta principale"))
                .andExpect(jsonPath("$.creditCardDescription").value("Carta usata per spese quotidiane"))
                .andExpect(jsonPath("$.creditCardChargeDay").value(15))
                .andExpect(jsonPath("$.accountId").value(account.getAccountId().toString()))
                .andExpect(jsonPath("$.userGroupId").value(userGroup.getUserGroupId().toString()))
                .andExpect(jsonPath("$.creditCardCreatedAt").isString())
                .andExpect(jsonPath("$.creditCardUpdatedAt").isString());

        List<CreditCard> creditCards = creditCardRepository.findAll();

        assertThat(creditCards).hasSize(1);

        CreditCard creditCard = creditCards.getFirst();

        assertThat(creditCard.getCreditCardName()).isEqualTo("Carta principale");
        assertThat(creditCard.getCreditCardDescription()).isEqualTo("Carta usata per spese quotidiane");
        assertThat(creditCard.getCreditCardChargeDay()).isEqualTo((short) 15);
        assertThat(creditCard.getAccountId()).isEqualTo(account.getAccountId());
        assertThat(creditCard.getUserGroupId()).isEqualTo(userGroup.getUserGroupId());
        assertThat(creditCard.getCreditCardCreatedAt()).isNotNull();
        assertThat(creditCard.getCreditCardUpdatedAt()).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(
            value = UserRole.class,
            names = {
                    "COLLABORATOR",
                    "VIEWER_COLLABORATOR"
            }
    )
    void createCreditCardShouldAllowCollaboratorAndViewerCollaboratorOnLinkedAccount(UserRole userRole) throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        User linkedUser = createVerifiedUser(userGroup, userRole);
        Account account = createAccount(userGroup, "Conto collegato");

        grantAccess(account, linkedUser);

        mockMvc.perform(post(CREDIT_CARDS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(linkedUser)))
                        .content(validCreateCreditCardJson("Carta collegata", account.getAccountId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creditCardName").value("Carta collegata"))
                .andExpect(jsonPath("$.accountId").value(account.getAccountId().toString()))
                .andExpect(jsonPath("$.userGroupId").value(userGroup.getUserGroupId().toString()));

        assertThat(creditCardRepository.countByUserGroup_UserGroupId(userGroup.getUserGroupId())).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(
            value = UserRole.class,
            names = {
                    "COLLABORATOR",
                    "VIEWER_COLLABORATOR"
            }
    )
    void createCreditCardShouldReturnNotFoundForUnlinkedAccount(UserRole userRole) throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        User unlinkedUser = createVerifiedUser(userGroup, userRole);
        Account account = createAccount(userGroup, "Conto non collegato");

        mockMvc.perform(post(CREDIT_CARDS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(unlinkedUser)))
                        .content(validCreateCreditCardJson("Carta non autorizzata", account.getAccountId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.account.notFound"))
                .andExpect(jsonPath("$.message").value("Conto non trovato."))
                .andExpect(jsonPath("$.path").value(CREDIT_CARDS_PATH))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        assertThat(creditCardRepository.findAll()).isEmpty();
    }

    @Test
    void createCreditCardShouldReturnNotFoundForAccountFromAnotherUserGroup() throws Exception {
        User currentOwner = createVerifiedUser(UserRole.OWNER);

        User otherOwner = createVerifiedUser(UserRole.OWNER);
        Account otherGroupAccount = createAccount(otherOwner.getUserGroup(), "Conto altro gruppo");

        mockMvc.perform(post(CREDIT_CARDS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(currentOwner)))
                        .content(validCreateCreditCardJson("Carta altro gruppo", otherGroupAccount.getAccountId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.account.notFound"))
                .andExpect(jsonPath("$.message").value("Conto non trovato."))
                .andExpect(jsonPath("$.path").value(CREDIT_CARDS_PATH))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        assertThat(creditCardRepository.findAll()).isEmpty();
    }

    @Test
    void createCreditCardShouldRejectDuplicateNormalizedNameInSameUserGroup() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        Account account = createAccount(userGroup, "Conto principale");

        mockMvc.perform(post(CREDIT_CARDS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .content(validCreateCreditCardJson("Carta principale", account.getAccountId())))
                .andExpect(status().isCreated());

        mockMvc.perform(post(CREDIT_CARDS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .content(validCreateCreditCardJson("   carta     PRINCIPALE   ", account.getAccountId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("finance.creditCard.nameAlreadyExists"))
                .andExpect(jsonPath("$.message").value("Esiste già una carta di credito con questo nome."))
                .andExpect(jsonPath("$.path").value(CREDIT_CARDS_PATH))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        assertThat(creditCardRepository.countByUserGroup_UserGroupId(userGroup.getUserGroupId())).isEqualTo(1);
    }

    @Test
    void createCreditCardShouldAllowSameNormalizedNameInDifferentUserGroups() throws Exception {
        User firstOwner = createVerifiedUser(UserRole.OWNER);
        Account firstAccount = createAccount(firstOwner.getUserGroup(), "Conto primo gruppo");

        User secondOwner = createVerifiedUser(UserRole.OWNER);
        Account secondAccount = createAccount(secondOwner.getUserGroup(), "Conto secondo gruppo");

        mockMvc.perform(post(CREDIT_CARDS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(firstOwner)))
                        .content(validCreateCreditCardJson("Carta principale", firstAccount.getAccountId())))
                .andExpect(status().isCreated());

        mockMvc.perform(post(CREDIT_CARDS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(secondOwner)))
                        .content(validCreateCreditCardJson("   carta     PRINCIPALE   ", secondAccount.getAccountId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creditCardName").value("carta     PRINCIPALE"))
                .andExpect(jsonPath("$.userGroupId").value(secondOwner.getUserGroup().getUserGroupId().toString()));

        assertThat(creditCardRepository.findAll()).hasSize(2);
        assertThat(creditCardRepository.countByUserGroup_UserGroupId(firstOwner.getUserGroup().getUserGroupId())).isEqualTo(1);
        assertThat(creditCardRepository.countByUserGroup_UserGroupId(secondOwner.getUserGroup().getUserGroupId())).isEqualTo(1);
    }

    @Test
    void createCreditCardShouldStoreNameTrimmedButKeepInternalWhitespaceAndCase() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        Account account = createAccount(owner.getUserGroup(), "Conto principale");

        mockMvc.perform(post(CREDIT_CARDS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .content(validCreateCreditCardJson("   Carta   PRINCIPALE   ", account.getAccountId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creditCardName").value("Carta   PRINCIPALE"));

        CreditCard creditCard = creditCardRepository.findAll().getFirst();

        assertThat(creditCard.getCreditCardName()).isEqualTo("Carta   PRINCIPALE");
    }

    @Test
    void createCreditCardShouldNormalizeBlankDescriptionToNull() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        Account account = createAccount(owner.getUserGroup(), "Conto principale");

        mockMvc.perform(post(CREDIT_CARDS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .content("""
                                {
                                  "creditCardName": "Carta principale",
                                  "creditCardDescription": "   ",
                                  "creditCardChargeDay": 10,
                                  "accountId": "%s"
                                }
                                """.formatted(account.getAccountId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creditCardName").value("Carta principale"));

        CreditCard creditCard = creditCardRepository.findAll().getFirst();

        assertThat(creditCard.getCreditCardDescription()).isNull();
    }

    @Test
    void createCreditCardShouldRejectValidationErrors() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);

        mockMvc.perform(post(CREDIT_CARDS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .content("""
                                {
                                  "creditCardName": "",
                                  "creditCardChargeDay": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.message").value("Validazione fallita."))
                .andExpect(jsonPath("$.path").value(CREDIT_CARDS_PATH))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors[*].code", hasItems(
                        "finance.creditCard.name.required",
                        "finance.creditCard.chargeDay.invalid",
                        "finance.creditCard.account.required"
                )));

        assertThat(creditCardRepository.findAll()).isEmpty();
    }

    @Test
    void createCreditCardShouldRejectChargeDayGreaterThanThirtyOne() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        Account account = createAccount(owner.getUserGroup(), "Conto principale");

        mockMvc.perform(post(CREDIT_CARDS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .content("""
                                {
                                  "creditCardName": "Carta principale",
                                  "creditCardChargeDay": 32,
                                  "accountId": "%s"
                                }
                                """.formatted(account.getAccountId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fieldErrors[*].code", hasItems(
                        "finance.creditCard.chargeDay.invalid"
                )));

        assertThat(creditCardRepository.findAll()).isEmpty();
    }

    @Test
    void createCreditCardShouldRejectTooLongNameAndDescription() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        Account account = createAccount(owner.getUserGroup(), "Conto principale");

        String tooLongName = "a".repeat(256);
        String tooLongDescription = "b".repeat(2001);

        mockMvc.perform(post(CREDIT_CARDS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .content("""
                                {
                                  "creditCardName": "%s",
                                  "creditCardDescription": "%s",
                                  "creditCardChargeDay": 15,
                                  "accountId": "%s"
                                }
                                """.formatted(tooLongName, tooLongDescription, account.getAccountId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fieldErrors[*].code", hasItems(
                        "finance.creditCard.name.tooLong",
                        "finance.creditCard.description.tooLong"
                )));

        assertThat(creditCardRepository.findAll()).isEmpty();
    }

    @Test
    void creditCardEntityShouldRejectInvalidChargeDayWhenServiceIsBypassed() {
        User owner = createVerifiedUser(UserRole.OWNER);
        Account account = createAccount(owner.getUserGroup(), "Conto principale");

        assertThatThrownBy(() -> CreditCard.create(
                "Carta non valida",
                null,
                (short) 0,
                account,
                owner.getUserGroup()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.creditCard.chargeDay.invalid");

        assertThatThrownBy(() -> CreditCard.create(
                "Carta non valida",
                null,
                (short) 32,
                account,
                owner.getUserGroup()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.creditCard.chargeDay.invalid");

        assertThat(creditCardRepository.findAll()).isEmpty();
    }

    @Test
    void creditCardEntityShouldRejectAccountFromDifferentUserGroupWhenServiceIsBypassed() {
        User firstOwner = createVerifiedUser(UserRole.OWNER);
        User secondOwner = createVerifiedUser(UserRole.OWNER);

        Account firstGroupAccount = createAccount(firstOwner.getUserGroup(), "Conto primo gruppo");

        assertThatThrownBy(() -> CreditCard.create(
                "Carta non valida",
                null,
                (short) 15,
                firstGroupAccount,
                secondOwner.getUserGroup()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.creditCard.userGroupMismatch");

        assertThat(creditCardRepository.findAll()).isEmpty();
    }

    @Test
    void databaseUniqueIndexShouldRejectDuplicateNormalizedCreditCardNameEvenIfServiceCheckIsBypassed() {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        Account account = createAccount(userGroup, "Conto principale");

        creditCardRepository.saveAndFlush(CreditCard.create(
                "Carta principale",
                null,
                (short) 15,
                account,
                userGroup
        ));

        assertThatThrownBy(() -> creditCardRepository.saveAndFlush(CreditCard.create(
                "   carta     PRINCIPALE   ",
                null,
                (short) 20,
                account,
                userGroup
        )))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(creditCardRepository.countByUserGroup_UserGroupId(
                userGroup.getUserGroupId()
        )).isEqualTo(1);
    }

    @Test
    void createCreditCardShouldAcceptBoundaryChargeDays() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        Account account = createAccount(owner.getUserGroup(), "Conto principale");

        mockMvc.perform(post(CREDIT_CARDS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .content("""
                                {
                                  "creditCardName": "Carta giorno uno",
                                  "creditCardDescription": "Carta con addebito al primo giorno",
                                  "creditCardChargeDay": 1,
                                  "accountId": "%s"
                                }
                                """.formatted(account.getAccountId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creditCardChargeDay").value(1));

        mockMvc.perform(post(CREDIT_CARDS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .content("""
                                {
                                  "creditCardName": "Carta giorno trentuno",
                                  "creditCardDescription": "Carta con addebito all'ultimo giorno massimo",
                                  "creditCardChargeDay": 31,
                                  "accountId": "%s"
                                }
                                """.formatted(account.getAccountId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creditCardChargeDay").value(31));

        assertThat(creditCardRepository.countByUserGroup_UserGroupId(
                owner.getUserGroup().getUserGroupId()
        )).isEqualTo(2);
    }

    @Test
    void createCreditCardShouldAllowMissingDescription() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        Account account = createAccount(owner.getUserGroup(), "Conto principale");

        mockMvc.perform(post(CREDIT_CARDS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .content("""
                                {
                                  "creditCardName": "Carta senza descrizione",
                                  "creditCardChargeDay": 15,
                                  "accountId": "%s"
                                }
                                """.formatted(account.getAccountId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creditCardName").value("Carta senza descrizione"));

        CreditCard creditCard = creditCardRepository.findAll().getFirst();

        assertThat(creditCard.getCreditCardDescription()).isNull();
    }

    private String accessTokenFor(User user) {
        return jwtTokenService.createAccessToken(user)
                .token();
    }

    private String validCreateCreditCardJson(String creditCardName, UUID accountId) {
        return """
                {
                  "creditCardName": "%s",
                  "creditCardDescription": "Carta usata per spese quotidiane",
                  "creditCardChargeDay": 15,
                  "accountId": "%s"
                }
                """.formatted(creditCardName, accountId);
    }

    private User createVerifiedUser(UserRole userRole) {
        UUID uniqueId = UUID.randomUUID();

        UserGroup userGroup = userGroupRepository.saveAndFlush(new UserGroup(
                "Credit card group " + uniqueId
        ));

        return createVerifiedUser(userGroup, userRole);
    }

    private User createVerifiedUser(UserGroup userGroup, UserRole userRole) {
        UUID uniqueId = UUID.randomUUID();

        User user = new User(
                "User " + uniqueId,
                "credit-card-%s@example.com".formatted(uniqueId.toString().replace("-", "")),
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