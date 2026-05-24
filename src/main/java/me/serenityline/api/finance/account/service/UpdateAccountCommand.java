package me.serenityline.api.finance.account.service;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateAccountCommand(
        String accountName,
        String accountDescription,
        String issuingInstitution,
        BigDecimal openingBalance,
        LocalDate openingBalanceDate
) {
}