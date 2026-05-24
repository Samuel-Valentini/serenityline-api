package me.serenityline.api.finance.account.service;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateAccountCommand(
        String accountName,
        String accountDescription,
        String currency,
        String issuingInstitution,
        BigDecimal openingBalance,
        LocalDate openingBalanceDate
) {
}