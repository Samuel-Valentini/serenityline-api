package me.serenityline.api.finance.account.service;

import me.serenityline.api.finance.account.entity.Account;
import me.serenityline.api.finance.account.entity.AccountUser;
import me.serenityline.api.finance.account.repository.AccountRepository;
import me.serenityline.api.finance.account.repository.AccountUserRepository;
import me.serenityline.api.support.IntegrationTestSupport;
import me.serenityline.api.user.entity.*;
import me.serenityline.api.user.repository.UserGroupRepository;
import me.serenityline.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountCreationServiceIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private AccountCreationService accountCreationService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountUserRepository accountUserRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserGroupRepository userGroupRepository;

    @Test
    void shouldCreateAccountAndGrantAccessToCreator() {
        User owner = createUser(UserRole.OWNER);

        Account account = accountCreationService.createAccount(
                owner.getUserId(),
                new CreateAccountCommand(
                        "  Conto principale  ",
                        "  Conto usato per le spese quotidiane  ",
                        " eur ",
                        "  Fineco  ",
                        new BigDecimal("1234.50"),
                        LocalDate.of(2026, 1, 15)
                )
        );

        Account reloadedAccount = accountRepository.findById(account.getAccountId())
                .orElseThrow();

        assertThat(reloadedAccount.getAccountName()).isEqualTo("Conto principale");
        assertThat(reloadedAccount.getAccountDescription()).isEqualTo("Conto usato per le spese quotidiane");
        assertThat(reloadedAccount.getCurrency()).isEqualTo("EUR");
        assertThat(reloadedAccount.getIssuingInstitution()).isEqualTo("Fineco");
        assertThat(reloadedAccount.getOpeningBalance()).isEqualByComparingTo("1234.50");
        assertThat(reloadedAccount.getOpeningBalanceDate()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(reloadedAccount.getUserGroupId()).isEqualTo(owner.getUserGroup().getUserGroupId());
        assertThat(reloadedAccount.getAccountCreatedAt()).isNotNull();
        assertThat(reloadedAccount.getAccountUpdatedAt()).isNotNull();

        List<AccountUser> accountUsers =
                accountUserRepository.findAllByAccount_AccountId(account.getAccountId());

        assertThat(accountUsers).hasSize(1);

        AccountUser accountUser = accountUsers.getFirst();

        assertThat(accountUser.getAccountId()).isEqualTo(account.getAccountId());
        assertThat(accountUser.getUserId()).isEqualTo(owner.getUserId());
        assertThat(accountUser.getUserGroupId()).isEqualTo(owner.getUserGroup().getUserGroupId());
        assertThat(accountUser.getAccountAccessGrantedAt()).isNotNull();
    }

    @Test
    void shouldNormalizeBlankOptionalFieldsToNullAndNullOpeningBalanceToZero() {
        User owner = createUser(UserRole.OWNER);

        Account account = accountCreationService.createAccount(
                owner.getUserId(),
                new CreateAccountCommand(
                        "Conto zero",
                        "   ",
                        "EUR",
                        "   ",
                        null,
                        LocalDate.of(2026, 2, 1)
                )
        );

        Account reloadedAccount = accountRepository.findById(account.getAccountId())
                .orElseThrow();

        assertThat(reloadedAccount.getAccountDescription()).isNull();
        assertThat(reloadedAccount.getIssuingInstitution()).isNull();
        assertThat(reloadedAccount.getOpeningBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldAllowSuperCollaboratorToCreateAccount() {
        User superCollaborator = createUser(UserRole.SUPER_COLLABORATOR);

        Account account = accountCreationService.createAccount(
                superCollaborator.getUserId(),
                validCommand("Conto super")
        );

        assertThat(accountRepository.findById(account.getAccountId())).isPresent();

        assertThat(accountUserRepository.existsByAccount_AccountIdAndUser_UserId(
                account.getAccountId(),
                superCollaborator.getUserId()
        )).isTrue();
    }

    @Test
    void shouldRejectCollaboratorAccountCreation() {
        User collaborator = createUser(UserRole.COLLABORATOR);

        assertThatThrownBy(() -> accountCreationService.createAccount(
                collaborator.getUserId(),
                validCommand("Conto non autorizzato")
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("finance.account.create.forbidden");

        assertThat(accountRepository.countByUserGroup_UserGroupId(
                collaborator.getUserGroup().getUserGroupId()
        )).isZero();
    }

    @Test
    void shouldRejectBlankAccountName() {
        User owner = createUser(UserRole.OWNER);

        assertThatThrownBy(() -> accountCreationService.createAccount(
                owner.getUserId(),
                new CreateAccountCommand(
                        "   ",
                        null,
                        "EUR",
                        null,
                        BigDecimal.ZERO,
                        LocalDate.of(2026, 1, 1)
                )
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.account.name.required");
    }

    @Test
    void shouldRejectInvalidCurrency() {
        User owner = createUser(UserRole.OWNER);

        assertThatThrownBy(() -> accountCreationService.createAccount(
                owner.getUserId(),
                new CreateAccountCommand(
                        "Conto",
                        null,
                        "EURO",
                        null,
                        BigDecimal.ZERO,
                        LocalDate.of(2026, 1, 1)
                )
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.account.currency.invalid");
    }

    @Test
    void shouldRejectOpeningBalanceWithMoreThanTwoDecimals() {
        User owner = createUser(UserRole.OWNER);

        assertThatThrownBy(() -> accountCreationService.createAccount(
                owner.getUserId(),
                new CreateAccountCommand(
                        "Conto",
                        null,
                        "EUR",
                        null,
                        new BigDecimal("10.999"),
                        LocalDate.of(2026, 1, 1)
                )
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.account.openingBalance.invalidScale");
    }

    @Test
    void shouldRejectMissingOpeningBalanceDate() {
        User owner = createUser(UserRole.OWNER);

        assertThatThrownBy(() -> accountCreationService.createAccount(
                owner.getUserId(),
                new CreateAccountCommand(
                        "Conto",
                        null,
                        "EUR",
                        null,
                        BigDecimal.ZERO,
                        null
                )
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.account.openingBalanceDate.required");
    }

    @Test
    void shouldRejectDuplicateAccountNameWithSameExactNameInSameUserGroup() {
        User owner = createUser(UserRole.OWNER);

        accountCreationService.createAccount(
                owner.getUserId(),
                validCommand("Conto principale")
        );

        assertThatThrownBy(() -> accountCreationService.createAccount(
                owner.getUserId(),
                validCommand("Conto principale")
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("finance.account.nameAlreadyExists");

        assertThat(accountRepository.countByUserGroup_UserGroupId(
                owner.getUserGroup().getUserGroupId()
        )).isEqualTo(1);
    }

    @Test
    void shouldRejectDuplicateAccountNameIgnoringLeadingAndTrailingSpaces() {
        User owner = createUser(UserRole.OWNER);

        accountCreationService.createAccount(
                owner.getUserId(),
                validCommand("Conto principale")
        );

        assertThatThrownBy(() -> accountCreationService.createAccount(
                owner.getUserId(),
                validCommand("   Conto principale   ")
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("finance.account.nameAlreadyExists");

        assertThat(accountRepository.countByUserGroup_UserGroupId(
                owner.getUserGroup().getUserGroupId()
        )).isEqualTo(1);
    }

    @Test
    void shouldRejectDuplicateAccountNameIgnoringCase() {
        User owner = createUser(UserRole.OWNER);

        accountCreationService.createAccount(
                owner.getUserId(),
                validCommand("Conto principale")
        );

        assertThatThrownBy(() -> accountCreationService.createAccount(
                owner.getUserId(),
                validCommand("CONTO PRINCIPALE")
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("finance.account.nameAlreadyExists");

        assertThat(accountRepository.countByUserGroup_UserGroupId(
                owner.getUserGroup().getUserGroupId()
        )).isEqualTo(1);
    }

    @Test
    void shouldRejectDuplicateAccountNameIgnoringRepeatedInternalWhitespace() {
        User owner = createUser(UserRole.OWNER);

        accountCreationService.createAccount(
                owner.getUserId(),
                validCommand("Conto principale")
        );

        assertThatThrownBy(() -> accountCreationService.createAccount(
                owner.getUserId(),
                validCommand("Conto     principale")
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("finance.account.nameAlreadyExists");

        assertThat(accountRepository.countByUserGroup_UserGroupId(
                owner.getUserGroup().getUserGroupId()
        )).isEqualTo(1);
    }

    @Test
    void shouldRejectDuplicateAccountNameIgnoringTabsAndNewLines() {
        User owner = createUser(UserRole.OWNER);

        accountCreationService.createAccount(
                owner.getUserId(),
                validCommand("Conto principale")
        );

        assertThatThrownBy(() -> accountCreationService.createAccount(
                owner.getUserId(),
                validCommand("Conto\t\nprincipale")
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("finance.account.nameAlreadyExists");

        assertThat(accountRepository.countByUserGroup_UserGroupId(
                owner.getUserGroup().getUserGroupId()
        )).isEqualTo(1);
    }

    @Test
    void shouldAllowSameNormalizedAccountNameInDifferentUserGroups() {
        User firstOwner = createUser(UserRole.OWNER);
        User secondOwner = createUser(UserRole.OWNER);

        Account firstAccount = accountCreationService.createAccount(
                firstOwner.getUserId(),
                validCommand("Conto principale")
        );

        Account secondAccount = accountCreationService.createAccount(
                secondOwner.getUserId(),
                validCommand("   conto     PRINCIPALE   ")
        );

        assertThat(firstAccount.getAccountId()).isNotEqualTo(secondAccount.getAccountId());
        assertThat(firstAccount.getUserGroupId()).isEqualTo(firstOwner.getUserGroup().getUserGroupId());
        assertThat(secondAccount.getUserGroupId()).isEqualTo(secondOwner.getUserGroup().getUserGroupId());

        assertThat(accountRepository.countByUserGroup_UserGroupId(
                firstOwner.getUserGroup().getUserGroupId()
        )).isEqualTo(1);

        assertThat(accountRepository.countByUserGroup_UserGroupId(
                secondOwner.getUserGroup().getUserGroupId()
        )).isEqualTo(1);
    }

    @Test
    void shouldStoreAccountNameTrimmedButKeepInternalWhitespaceAndCase() {
        User owner = createUser(UserRole.OWNER);

        Account account = accountCreationService.createAccount(
                owner.getUserId(),
                validCommand("   Conto   PRINCIPALE   ")
        );

        Account reloadedAccount = accountRepository.findById(account.getAccountId())
                .orElseThrow();

        assertThat(reloadedAccount.getAccountName()).isEqualTo("Conto   PRINCIPALE");
    }

    @Test
    void repositoryShouldFindExistingAccountByNormalizedName() {
        User owner = createUser(UserRole.OWNER);

        accountCreationService.createAccount(
                owner.getUserId(),
                validCommand("   Conto   PRINCIPALE   ")
        );

        assertThat(accountRepository.existsByUserGroupIdAndNormalizedAccountName(
                owner.getUserGroup().getUserGroupId(),
                "conto principale"
        )).isTrue();

        assertThat(accountRepository.existsByUserGroupIdAndNormalizedAccountName(
                owner.getUserGroup().getUserGroupId(),
                "conto secondario"
        )).isFalse();
    }

    @Test
    void databaseUniqueIndexShouldRejectDuplicateNormalizedAccountNameEvenIfServiceCheckIsBypassed() {
        User owner = createUser(UserRole.OWNER);

        accountRepository.saveAndFlush(Account.create(
                "Conto principale",
                null,
                "EUR",
                null,
                new BigDecimal("100.00"),
                LocalDate.of(2026, 1, 1),
                owner.getUserGroup()
        ));

        assertThatThrownBy(() -> accountRepository.saveAndFlush(Account.create(
                "   conto     PRINCIPALE   ",
                null,
                "EUR",
                null,
                new BigDecimal("200.00"),
                LocalDate.of(2026, 1, 2),
                owner.getUserGroup()
        )))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private CreateAccountCommand validCommand(String accountName) {
        return new CreateAccountCommand(
                accountName,
                "Descrizione conto",
                "EUR",
                "Banca test",
                new BigDecimal("100.00"),
                LocalDate.of(2026, 1, 1)
        );
    }

    private User createUser(UserRole userRole) {
        UUID uniqueId = UUID.randomUUID();

        UserGroup userGroup = userGroupRepository.save(new UserGroup(
                "Test group " + uniqueId
        ));

        User user = new User(
                "User " + uniqueId,
                "user.%s@example.com".formatted(uniqueId.toString().replace("-", "")),
                userGroup,
                userRole,
                UserPlatformRole.USER,
                "it-IT",
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