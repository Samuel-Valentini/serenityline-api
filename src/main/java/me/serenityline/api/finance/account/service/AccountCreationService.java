package me.serenityline.api.finance.account.service;

import me.serenityline.api.finance.account.entity.Account;
import me.serenityline.api.finance.account.entity.AccountUser;
import me.serenityline.api.finance.account.repository.AccountRepository;
import me.serenityline.api.finance.account.repository.AccountUserRepository;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.entity.UserRole;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class AccountCreationService {

    private static final BigDecimal DEFAULT_OPENING_BALANCE = BigDecimal.ZERO.setScale(2);

    private static final int MAX_ACCOUNT_NAME_LENGTH = 255;
    private static final int MAX_ACCOUNT_DESCRIPTION_LENGTH = 1000;
    private static final int MAX_ISSUING_INSTITUTION_LENGTH = 255;

    private final AccountRepository accountRepository;
    private final AccountUserRepository accountUserRepository;
    private final UserRepository userRepository;

    public AccountCreationService(
            AccountRepository accountRepository,
            AccountUserRepository accountUserRepository,
            UserRepository userRepository
    ) {
        this.accountRepository = accountRepository;
        this.accountUserRepository = accountUserRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Account createAccount(UUID currentUserId, CreateAccountCommand command) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        Objects.requireNonNull(command, "command");

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("user.notFound"));

        ensureCanCreateAccount(currentUser);

        String accountName = normalizeAccountNameForStorage(command.accountName());
        String normalizedAccountName = normalizeAccountNameForComparison(accountName);

        if (accountRepository.existsByUserGroupIdAndNormalizedAccountName(
                currentUser.getUserGroup().getUserGroupId(),
                normalizedAccountName
        )) {
            throw new IllegalStateException("finance.account.nameAlreadyExists");
        }

        String accountDescription = normalizeOptionalText(
                command.accountDescription(),
                MAX_ACCOUNT_DESCRIPTION_LENGTH,
                "finance.account.description.tooLong"
        );

        String currency = normalizeCurrency(command.currency());

        String issuingInstitution = normalizeOptionalText(
                command.issuingInstitution(),
                MAX_ISSUING_INSTITUTION_LENGTH,
                "finance.account.issuingInstitution.tooLong"
        );

        BigDecimal openingBalance = normalizeOpeningBalance(command.openingBalance());
        LocalDate openingBalanceDate = requireOpeningBalanceDate(command.openingBalanceDate());

        Account account;

        try {
            account = accountRepository.saveAndFlush(Account.create(
                    accountName,
                    accountDescription,
                    currency,
                    issuingInstitution,
                    openingBalance,
                    openingBalanceDate,
                    currentUser.getUserGroup()
            ));
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalStateException("finance.account.nameAlreadyExists", exception);
        }

        accountUserRepository.save(AccountUser.grant(
                account,
                currentUser
        ));

        return account;
    }

    private void ensureCanCreateAccount(User user) {
        if (!user.isUserIsEnabled() || user.isPendingDeletion()) {
            throw new IllegalStateException("finance.account.create.forbidden");
        }

        UserRole userRole = user.getUserRole();

        if (userRole != UserRole.OWNER && userRole != UserRole.SUPER_COLLABORATOR) {
            throw new IllegalStateException("finance.account.create.forbidden");
        }
    }

    private String normalizeAccountNameForStorage(String value) {
        String normalized = normalizeRequiredText(
                value,
                "finance.account.name.required"
        );

        validateMaxLength(
                normalized,
                MAX_ACCOUNT_NAME_LENGTH,
                "finance.account.name.tooLong"
        );

        return normalized;
    }

    private String normalizeAccountNameForComparison(String value) {
        return value
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private String normalizeOptionalText(String value, int maxLength, String tooLongMessageKey) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        if (normalized.isBlank()) {
            return null;
        }

        validateMaxLength(normalized, maxLength, tooLongMessageKey);

        return normalized;
    }

    private String normalizeCurrency(String value) {
        String normalizedCurrency = normalizeRequiredText(
                value,
                "finance.account.currency.required"
        ).toUpperCase(Locale.ROOT);

        if (!normalizedCurrency.matches("^[A-Z]{3}$")) {
            throw new IllegalArgumentException("finance.account.currency.invalid");
        }

        return normalizedCurrency;
    }

    private String normalizeRequiredText(String value, String requiredMessageKey) {
        if (value == null) {
            throw new IllegalArgumentException(requiredMessageKey);
        }

        String normalized = value.trim();

        if (normalized.isBlank()) {
            throw new IllegalArgumentException(requiredMessageKey);
        }

        return normalized;
    }

    private void validateMaxLength(String value, int maxLength, String tooLongMessageKey) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(tooLongMessageKey);
        }
    }

    private BigDecimal normalizeOpeningBalance(BigDecimal value) {
        if (value == null) {
            return DEFAULT_OPENING_BALANCE;
        }

        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("finance.account.openingBalance.invalidScale", exception);
        }
    }

    private LocalDate requireOpeningBalanceDate(LocalDate openingBalanceDate) {
        if (openingBalanceDate == null) {
            throw new IllegalArgumentException("finance.account.openingBalanceDate.required");
        }

        return openingBalanceDate;
    }
}