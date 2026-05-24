package me.serenityline.api.finance.account.dto;

import me.serenityline.api.finance.account.entity.Account;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AccountResponse(
        UUID accountId,
        String accountName,
        String accountDescription,
        String currency,
        String issuingInstitution,
        BigDecimal openingBalance,
        LocalDate openingBalanceDate,
        UUID userGroupId,
        OffsetDateTime accountCreatedAt,
        OffsetDateTime accountUpdatedAt
) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getAccountId(),
                account.getAccountName(),
                account.getAccountDescription(),
                account.getCurrency(),
                account.getIssuingInstitution(),
                account.getOpeningBalance(),
                account.getOpeningBalanceDate(),
                account.getUserGroupId(),
                account.getAccountCreatedAt(),
                account.getAccountUpdatedAt()
        );
    }
}