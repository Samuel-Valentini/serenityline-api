package me.serenityline.api.finance.account.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import me.serenityline.api.finance.account.service.UpdateAccountCommand;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateAccountRequest(
        @Size(max = 255, message = "finance.account.name.tooLong")
        String accountName,

        @Size(max = 1000, message = "finance.account.description.tooLong")
        String accountDescription,

        @Size(max = 255, message = "finance.account.issuingInstitution.tooLong")
        String issuingInstitution,

        @Digits(integer = 17, fraction = 2, message = "finance.account.openingBalance.invalidScale")
        BigDecimal openingBalance,

        LocalDate openingBalanceDate
) {

    public UpdateAccountCommand toCommand() {
        return new UpdateAccountCommand(
                accountName,
                accountDescription,
                issuingInstitution,
                openingBalance,
                openingBalanceDate
        );
    }
}