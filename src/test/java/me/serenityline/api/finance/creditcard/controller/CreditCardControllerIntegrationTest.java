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
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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

    @Test
    void getCreditCardsShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get(CREDIT_CARDS_PATH)
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
    void getCreditCardsShouldReturnAllGroupCreditCardsForRolesWithGroupVisibility(UserRole userRole) throws Exception {
        User user = createVerifiedUser(userRole);
        UserGroup userGroup = user.getUserGroup();

        Account account = createAccount(userGroup, "Conto principale");

        createCreditCard(userGroup, account, "Carta B");
        createCreditCard(userGroup, account, "Carta A");

        User otherOwner = createVerifiedUser(UserRole.OWNER);
        Account otherAccount = createAccount(otherOwner.getUserGroup(), "Conto altro gruppo");
        createCreditCard(otherOwner.getUserGroup(), otherAccount, "Carta altro gruppo");

        mockMvc.perform(get(CREDIT_CARDS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].creditCardName", containsInAnyOrder(
                        "Carta A",
                        "Carta B"
                )))
                .andExpect(jsonPath("$[*].userGroupId", everyItem(is(userGroup.getUserGroupId().toString()))))
                .andExpect(jsonPath("$[*].accountId", everyItem(is(account.getAccountId().toString()))));
    }

    @Test
    void getCreditCardsShouldReturnOnlyCardsLinkedToAccessibleAccountsForCollaborator() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        User collaborator = createVerifiedUser(userGroup, UserRole.COLLABORATOR);

        Account linkedAccount = createAccount(userGroup, "Conto collegato");
        Account unlinkedAccount = createAccount(userGroup, "Conto non collegato");

        grantAccess(linkedAccount, collaborator);

        CreditCard linkedCreditCard = createCreditCard(userGroup, linkedAccount, "Carta collegata");
        createCreditCard(userGroup, unlinkedAccount, "Carta non collegata");

        User otherOwner = createVerifiedUser(UserRole.OWNER);
        Account otherAccount = createAccount(otherOwner.getUserGroup(), "Conto altro gruppo");
        createCreditCard(otherOwner.getUserGroup(), otherAccount, "Carta altro gruppo");

        mockMvc.perform(get(CREDIT_CARDS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].creditCardId").value(linkedCreditCard.getCreditCardId().toString()))
                .andExpect(jsonPath("$[0].creditCardName").value("Carta collegata"))
                .andExpect(jsonPath("$[0].accountId").value(linkedAccount.getAccountId().toString()))
                .andExpect(jsonPath("$[0].userGroupId").value(userGroup.getUserGroupId().toString()));
    }

    @Test
    void getCreditCardsShouldReturnEmptyListForCollaboratorWithoutLinkedAccounts() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        User collaborator = createVerifiedUser(userGroup, UserRole.COLLABORATOR);

        Account account = createAccount(userGroup, "Conto non collegato");
        createCreditCard(userGroup, account, "Carta non collegata");

        mockMvc.perform(get(CREDIT_CARDS_PATH)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getCreditCardShouldRequireAuthentication() throws Exception {
        UUID creditCardId = UUID.randomUUID();

        mockMvc.perform(get(CREDIT_CARDS_PATH + "/" + creditCardId)
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
    void getCreditCardShouldReturnGroupCreditCardForRolesWithGroupVisibility(UserRole userRole) throws Exception {
        User user = createVerifiedUser(userRole);
        UserGroup userGroup = user.getUserGroup();

        Account account = createAccount(userGroup, "Conto principale");
        CreditCard creditCard = createCreditCard(userGroup, account, "Carta visibile");

        mockMvc.perform(get(CREDIT_CARDS_PATH + "/" + creditCard.getCreditCardId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditCardId").value(creditCard.getCreditCardId().toString()))
                .andExpect(jsonPath("$.creditCardName").value("Carta visibile"))
                .andExpect(jsonPath("$.creditCardDescription").value("Descrizione Carta visibile"))
                .andExpect(jsonPath("$.creditCardChargeDay").value(15))
                .andExpect(jsonPath("$.accountId").value(account.getAccountId().toString()))
                .andExpect(jsonPath("$.userGroupId").value(userGroup.getUserGroupId().toString()))
                .andExpect(jsonPath("$.creditCardCreatedAt").isString())
                .andExpect(jsonPath("$.creditCardUpdatedAt").isString());
    }

    @Test
    void getCreditCardShouldReturnLinkedCreditCardForCollaborator() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        User collaborator = createVerifiedUser(userGroup, UserRole.COLLABORATOR);

        Account account = createAccount(userGroup, "Conto collegato");
        grantAccess(account, collaborator);

        CreditCard creditCard = createCreditCard(userGroup, account, "Carta collegata");

        mockMvc.perform(get(CREDIT_CARDS_PATH + "/" + creditCard.getCreditCardId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditCardId").value(creditCard.getCreditCardId().toString()))
                .andExpect(jsonPath("$.creditCardName").value("Carta collegata"))
                .andExpect(jsonPath("$.accountId").value(account.getAccountId().toString()))
                .andExpect(jsonPath("$.userGroupId").value(userGroup.getUserGroupId().toString()));
    }

    @Test
    void getCreditCardShouldReturnNotFoundForCollaboratorWhenLinkedAccountIsNotAccessible() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        User collaborator = createVerifiedUser(userGroup, UserRole.COLLABORATOR);

        Account unlinkedAccount = createAccount(userGroup, "Conto non collegato");
        CreditCard creditCard = createCreditCard(userGroup, unlinkedAccount, "Carta non collegata");

        mockMvc.perform(get(CREDIT_CARDS_PATH + "/" + creditCard.getCreditCardId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.creditCard.notFound"))
                .andExpect(jsonPath("$.message").value("Carta di credito non trovata."))
                .andExpect(jsonPath("$.path").value(CREDIT_CARDS_PATH + "/" + creditCard.getCreditCardId()))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void getCreditCardShouldReturnNotFoundForCreditCardFromAnotherUserGroup() throws Exception {
        User currentOwner = createVerifiedUser(UserRole.OWNER);

        User otherOwner = createVerifiedUser(UserRole.OWNER);
        Account otherAccount = createAccount(otherOwner.getUserGroup(), "Conto altro gruppo");
        CreditCard otherGroupCreditCard = createCreditCard(
                otherOwner.getUserGroup(),
                otherAccount,
                "Carta altro gruppo"
        );

        mockMvc.perform(get(CREDIT_CARDS_PATH + "/" + otherGroupCreditCard.getCreditCardId())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(currentOwner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.creditCard.notFound"))
                .andExpect(jsonPath("$.message").value("Carta di credito non trovata."))
                .andExpect(jsonPath("$.path").value(CREDIT_CARDS_PATH + "/" + otherGroupCreditCard.getCreditCardId()))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void getCreditCardShouldReturnNotFoundForUnknownCreditCardId() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UUID unknownCreditCardId = UUID.randomUUID();

        mockMvc.perform(get(CREDIT_CARDS_PATH + "/" + unknownCreditCardId)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.creditCard.notFound"))
                .andExpect(jsonPath("$.message").value("Carta di credito non trovata."))
                .andExpect(jsonPath("$.path").value(CREDIT_CARDS_PATH + "/" + unknownCreditCardId))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void updateCreditCardShouldRequireAuthentication() throws Exception {
        UUID creditCardId = UUID.randomUUID();

        mockMvc.perform(patch(CREDIT_CARDS_PATH + "/" + creditCardId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .content("""
                                {
                                  "creditCardName": "Carta aggiornata"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @EnumSource(
            value = UserRole.class,
            names = {
                    "OWNER",
                    "SUPER_COLLABORATOR"
            }
    )
    void updateCreditCardShouldUpdateGroupCreditCardForOwnerAndSuperCollaborator(UserRole userRole) throws Exception {
        User user = createVerifiedUser(userRole);
        UserGroup userGroup = user.getUserGroup();

        Account account = createAccount(userGroup, "Conto principale");
        CreditCard creditCard = createCreditCard(userGroup, account, "Carta vecchia");

        mockMvc.perform(patch(CREDIT_CARDS_PATH + "/" + creditCard.getCreditCardId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(user)))
                        .content("""
                                {
                                  "creditCardName": "  Carta aggiornata  ",
                                  "creditCardDescription": "  Descrizione aggiornata  ",
                                  "creditCardChargeDay": 20
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditCardId").value(creditCard.getCreditCardId().toString()))
                .andExpect(jsonPath("$.creditCardName").value("Carta aggiornata"))
                .andExpect(jsonPath("$.creditCardDescription").value("Descrizione aggiornata"))
                .andExpect(jsonPath("$.creditCardChargeDay").value(20))
                .andExpect(jsonPath("$.accountId").value(account.getAccountId().toString()))
                .andExpect(jsonPath("$.userGroupId").value(userGroup.getUserGroupId().toString()));

        CreditCard reloadedCreditCard = creditCardRepository.findById(creditCard.getCreditCardId())
                .orElseThrow();

        assertThat(reloadedCreditCard.getCreditCardName()).isEqualTo("Carta aggiornata");
        assertThat(reloadedCreditCard.getCreditCardDescription()).isEqualTo("Descrizione aggiornata");
        assertThat(reloadedCreditCard.getCreditCardChargeDay()).isEqualTo((short) 20);
        assertThat(reloadedCreditCard.getAccountId()).isEqualTo(account.getAccountId());
    }

    @ParameterizedTest
    @EnumSource(
            value = UserRole.class,
            names = {
                    "COLLABORATOR",
                    "VIEWER_COLLABORATOR"
            }
    )
    void updateCreditCardShouldUpdateLinkedCreditCardForCollaboratorAndViewerCollaborator(UserRole userRole) throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        User linkedUser = createVerifiedUser(userGroup, userRole);

        Account account = createAccount(userGroup, "Conto collegato");
        grantAccess(account, linkedUser);

        CreditCard creditCard = createCreditCard(userGroup, account, "Carta collegata");

        mockMvc.perform(patch(CREDIT_CARDS_PATH + "/" + creditCard.getCreditCardId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(linkedUser)))
                        .content("""
                                {
                                  "creditCardName": "Carta modificata da collegato",
                                  "creditCardChargeDay": 25
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditCardId").value(creditCard.getCreditCardId().toString()))
                .andExpect(jsonPath("$.creditCardName").value("Carta modificata da collegato"))
                .andExpect(jsonPath("$.creditCardChargeDay").value(25))
                .andExpect(jsonPath("$.accountId").value(account.getAccountId().toString()))
                .andExpect(jsonPath("$.userGroupId").value(userGroup.getUserGroupId().toString()));

        CreditCard reloadedCreditCard = creditCardRepository.findById(creditCard.getCreditCardId())
                .orElseThrow();

        assertThat(reloadedCreditCard.getCreditCardName()).isEqualTo("Carta modificata da collegato");
        assertThat(reloadedCreditCard.getCreditCardChargeDay()).isEqualTo((short) 25);
    }

    @ParameterizedTest
    @EnumSource(
            value = UserRole.class,
            names = {
                    "COLLABORATOR",
                    "VIEWER_COLLABORATOR"
            }
    )
    void updateCreditCardShouldReturnNotFoundForUnlinkedAccount(UserRole userRole) throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        User unlinkedUser = createVerifiedUser(userGroup, userRole);

        Account unlinkedAccount = createAccount(userGroup, "Conto non collegato");
        CreditCard creditCard = createCreditCard(userGroup, unlinkedAccount, "Carta non collegata");

        mockMvc.perform(patch(CREDIT_CARDS_PATH + "/" + creditCard.getCreditCardId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(unlinkedUser)))
                        .content("""
                                {
                                  "creditCardName": "Tentativo modifica"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.creditCard.notFound"))
                .andExpect(jsonPath("$.message").value("Carta di credito non trovata."))
                .andExpect(jsonPath("$.path").value(CREDIT_CARDS_PATH + "/" + creditCard.getCreditCardId()))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        CreditCard reloadedCreditCard = creditCardRepository.findById(creditCard.getCreditCardId())
                .orElseThrow();

        assertThat(reloadedCreditCard.getCreditCardName()).isEqualTo("Carta non collegata");
    }

    @Test
    void updateCreditCardShouldReturnNotFoundForCreditCardFromAnotherUserGroup() throws Exception {
        User currentOwner = createVerifiedUser(UserRole.OWNER);

        User otherOwner = createVerifiedUser(UserRole.OWNER);
        Account otherAccount = createAccount(otherOwner.getUserGroup(), "Conto altro gruppo");
        CreditCard otherGroupCreditCard = createCreditCard(
                otherOwner.getUserGroup(),
                otherAccount,
                "Carta altro gruppo"
        );

        mockMvc.perform(patch(CREDIT_CARDS_PATH + "/" + otherGroupCreditCard.getCreditCardId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(currentOwner)))
                        .content("""
                                {
                                  "creditCardName": "Tentativo modifica"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.creditCard.notFound"))
                .andExpect(jsonPath("$.message").value("Carta di credito non trovata."))
                .andExpect(jsonPath("$.path").value(CREDIT_CARDS_PATH + "/" + otherGroupCreditCard.getCreditCardId()))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        CreditCard reloadedCreditCard = creditCardRepository.findById(otherGroupCreditCard.getCreditCardId())
                .orElseThrow();

        assertThat(reloadedCreditCard.getCreditCardName()).isEqualTo("Carta altro gruppo");
    }

    @Test
    void updateCreditCardShouldReturnNotFoundForUnknownCreditCardId() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UUID unknownCreditCardId = UUID.randomUUID();

        mockMvc.perform(patch(CREDIT_CARDS_PATH + "/" + unknownCreditCardId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .content("""
                                {
                                  "creditCardName": "Carta aggiornata"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.creditCard.notFound"))
                .andExpect(jsonPath("$.message").value("Carta di credito non trovata."))
                .andExpect(jsonPath("$.path").value(CREDIT_CARDS_PATH + "/" + unknownCreditCardId))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void updateCreditCardShouldRejectDuplicateNormalizedNameInSameUserGroup() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        Account account = createAccount(userGroup, "Conto principale");

        createCreditCard(userGroup, account, "Carta principale");
        CreditCard creditCardToUpdate = createCreditCard(userGroup, account, "Carta secondaria");

        mockMvc.perform(patch(CREDIT_CARDS_PATH + "/" + creditCardToUpdate.getCreditCardId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .content("""
                                {
                                  "creditCardName": "   carta     PRINCIPALE   "
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("finance.creditCard.nameAlreadyExists"))
                .andExpect(jsonPath("$.message").value("Esiste già una carta di credito con questo nome."))
                .andExpect(jsonPath("$.path").value(CREDIT_CARDS_PATH + "/" + creditCardToUpdate.getCreditCardId()))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        CreditCard reloadedCreditCard = creditCardRepository.findById(creditCardToUpdate.getCreditCardId())
                .orElseThrow();

        assertThat(reloadedCreditCard.getCreditCardName()).isEqualTo("Carta secondaria");
    }

    @Test
    void updateCreditCardShouldAllowSameNormalizedNameForSameCreditCard() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        Account account = createAccount(userGroup, "Conto principale");
        CreditCard creditCard = createCreditCard(userGroup, account, "Carta principale");

        mockMvc.perform(patch(CREDIT_CARDS_PATH + "/" + creditCard.getCreditCardId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .content("""
                                {
                                  "creditCardName": "   CARTA     PRINCIPALE   "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditCardName").value("CARTA     PRINCIPALE"));

        CreditCard reloadedCreditCard = creditCardRepository.findById(creditCard.getCreditCardId())
                .orElseThrow();

        assertThat(reloadedCreditCard.getCreditCardName()).isEqualTo("CARTA     PRINCIPALE");
    }

    @Test
    void updateCreditCardShouldRejectBlankName() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        Account account = createAccount(owner.getUserGroup(), "Conto principale");
        CreditCard creditCard = createCreditCard(owner.getUserGroup(), account, "Carta principale");

        mockMvc.perform(patch(CREDIT_CARDS_PATH + "/" + creditCard.getCreditCardId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .content("""
                                {
                                  "creditCardName": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.creditCard.name.required"))
                .andExpect(jsonPath("$.message").value("Il nome della carta di credito è obbligatorio."))
                .andExpect(jsonPath("$.path").value(CREDIT_CARDS_PATH + "/" + creditCard.getCreditCardId()))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        CreditCard reloadedCreditCard = creditCardRepository.findById(creditCard.getCreditCardId())
                .orElseThrow();

        assertThat(reloadedCreditCard.getCreditCardName()).isEqualTo("Carta principale");
    }

    @Test
    void updateCreditCardShouldRejectValidationErrors() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        Account account = createAccount(owner.getUserGroup(), "Conto principale");
        CreditCard creditCard = createCreditCard(owner.getUserGroup(), account, "Carta principale");

        String tooLongName = "a".repeat(256);
        String tooLongDescription = "b".repeat(2001);

        mockMvc.perform(patch(CREDIT_CARDS_PATH + "/" + creditCard.getCreditCardId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .content("""
                                {
                                  "creditCardName": "%s",
                                  "creditCardDescription": "%s",
                                  "creditCardChargeDay": 32
                                }
                                """.formatted(tooLongName, tooLongDescription)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.message").value("Validazione fallita."))
                .andExpect(jsonPath("$.path").value(CREDIT_CARDS_PATH + "/" + creditCard.getCreditCardId()))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors[*].code", hasItems(
                        "finance.creditCard.name.tooLong",
                        "finance.creditCard.description.tooLong",
                        "finance.creditCard.chargeDay.invalid"
                )));

        CreditCard reloadedCreditCard = creditCardRepository.findById(creditCard.getCreditCardId())
                .orElseThrow();

        assertThat(reloadedCreditCard.getCreditCardName()).isEqualTo("Carta principale");
        assertThat(reloadedCreditCard.getCreditCardDescription()).isEqualTo("Descrizione Carta principale");
        assertThat(reloadedCreditCard.getCreditCardChargeDay()).isEqualTo((short) 15);
    }

    @Test
    void updateCreditCardShouldClearDescriptionWhenBlank() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        Account account = createAccount(owner.getUserGroup(), "Conto principale");
        CreditCard creditCard = createCreditCard(owner.getUserGroup(), account, "Carta principale");

        mockMvc.perform(patch(CREDIT_CARDS_PATH + "/" + creditCard.getCreditCardId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .content("""
                                {
                                  "creditCardDescription": "   "
                                }
                                """))
                .andExpect(status().isOk());

        CreditCard reloadedCreditCard = creditCardRepository.findById(creditCard.getCreditCardId())
                .orElseThrow();

        assertThat(reloadedCreditCard.getCreditCardDescription()).isNull();
    }

    @Test
    void updateCreditCardShouldLeaveOmittedFieldsUnchanged() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        Account account = createAccount(userGroup, "Conto principale");
        CreditCard creditCard = createCreditCard(userGroup, account, "Carta principale");

        mockMvc.perform(patch(CREDIT_CARDS_PATH + "/" + creditCard.getCreditCardId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .content("""
                                {
                                  "creditCardName": "Carta rinominata"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditCardName").value("Carta rinominata"))
                .andExpect(jsonPath("$.creditCardDescription").value("Descrizione Carta principale"))
                .andExpect(jsonPath("$.creditCardChargeDay").value(15))
                .andExpect(jsonPath("$.accountId").value(account.getAccountId().toString()))
                .andExpect(jsonPath("$.userGroupId").value(userGroup.getUserGroupId().toString()));

        CreditCard reloadedCreditCard = creditCardRepository.findById(creditCard.getCreditCardId())
                .orElseThrow();

        assertThat(reloadedCreditCard.getCreditCardName()).isEqualTo("Carta rinominata");
        assertThat(reloadedCreditCard.getCreditCardDescription()).isEqualTo("Descrizione Carta principale");
        assertThat(reloadedCreditCard.getCreditCardChargeDay()).isEqualTo((short) 15);
        assertThat(reloadedCreditCard.getAccountId()).isEqualTo(account.getAccountId());
    }

    @Test
    void updateCreditCardShouldAcceptBoundaryChargeDays() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        Account account = createAccount(owner.getUserGroup(), "Conto principale");
        CreditCard creditCard = createCreditCard(owner.getUserGroup(), account, "Carta principale");

        mockMvc.perform(patch(CREDIT_CARDS_PATH + "/" + creditCard.getCreditCardId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .content("""
                                {
                                  "creditCardChargeDay": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditCardChargeDay").value(1));

        mockMvc.perform(patch(CREDIT_CARDS_PATH + "/" + creditCard.getCreditCardId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .content("""
                                {
                                  "creditCardChargeDay": 31
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditCardChargeDay").value(31));

        CreditCard reloadedCreditCard = creditCardRepository.findById(creditCard.getCreditCardId())
                .orElseThrow();

        assertThat(reloadedCreditCard.getCreditCardChargeDay()).isEqualTo((short) 31);
    }

    @Test
    void creditCardEntityShouldRejectInvalidChargeDayOnUpdateWhenServiceIsBypassed() {
        User owner = createVerifiedUser(UserRole.OWNER);
        Account account = createAccount(owner.getUserGroup(), "Conto principale");
        CreditCard creditCard = createCreditCard(owner.getUserGroup(), account, "Carta principale");

        assertThatThrownBy(() -> creditCard.update(
                "Carta principale",
                null,
                (short) 0
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.creditCard.chargeDay.invalid");

        assertThatThrownBy(() -> creditCard.update(
                "Carta principale",
                null,
                (short) 32
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.creditCard.chargeDay.invalid");
    }

    @Test
    void databaseUniqueIndexShouldRejectDuplicateNormalizedCreditCardNameOnUpdateEvenIfServiceCheckIsBypassed() {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        Account account = createAccount(userGroup, "Conto principale");

        createCreditCard(userGroup, account, "Carta principale");

        CreditCard creditCardToUpdate = createCreditCard(
                userGroup,
                account,
                "Carta secondaria"
        );

        creditCardToUpdate.update(
                "   carta     PRINCIPALE   ",
                creditCardToUpdate.getCreditCardDescription(),
                creditCardToUpdate.getCreditCardChargeDay()
        );

        assertThatThrownBy(() -> creditCardRepository.saveAndFlush(creditCardToUpdate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void updateCreditCardShouldAcceptEmptyPatchAndLeaveEverythingUnchanged() throws Exception {
        User owner = createVerifiedUser(UserRole.OWNER);
        UserGroup userGroup = owner.getUserGroup();

        Account account = createAccount(userGroup, "Conto principale");
        CreditCard creditCard = createCreditCard(userGroup, account, "Carta principale");

        mockMvc.perform(patch(CREDIT_CARDS_PATH + "/" + creditCard.getCreditCardId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, IT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .content("""
                                {
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditCardName").value("Carta principale"))
                .andExpect(jsonPath("$.creditCardDescription").value("Descrizione Carta principale"))
                .andExpect(jsonPath("$.creditCardChargeDay").value(15))
                .andExpect(jsonPath("$.accountId").value(account.getAccountId().toString()))
                .andExpect(jsonPath("$.userGroupId").value(userGroup.getUserGroupId().toString()));

        CreditCard reloadedCreditCard = creditCardRepository.findById(creditCard.getCreditCardId())
                .orElseThrow();

        assertThat(reloadedCreditCard.getCreditCardName()).isEqualTo("Carta principale");
        assertThat(reloadedCreditCard.getCreditCardDescription()).isEqualTo("Descrizione Carta principale");
        assertThat(reloadedCreditCard.getCreditCardChargeDay()).isEqualTo((short) 15);
        assertThat(reloadedCreditCard.getAccountId()).isEqualTo(account.getAccountId());
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

    private CreditCard createCreditCard(UserGroup userGroup, Account account, String creditCardName) {
        return creditCardRepository.saveAndFlush(CreditCard.create(
                creditCardName,
                "Descrizione " + creditCardName,
                (short) 15,
                account,
                userGroup
        ));
    }
}