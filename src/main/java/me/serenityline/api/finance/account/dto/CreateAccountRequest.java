package me.serenityline.api.finance.account.dto;

import jakarta.validation.constraints.*;
import me.serenityline.api.finance.account.service.CreateAccountCommand;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateAccountRequest(
        @NotBlank(message = "finance.account.name.required")
        @Size(max = 255, message = "finance.account.name.tooLong")
        String accountName,

        @Size(max = 1000, message = "finance.account.description.tooLong")
        String accountDescription,

        @NotBlank(message = "finance.account.currency.required")
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "finance.account.currency.invalid")
        String currency,

        @Size(max = 255, message = "finance.account.issuingInstitution.tooLong")
        String issuingInstitution,

        @Digits(integer = 17, fraction = 2, message = "finance.account.openingBalance.invalidScale")
        BigDecimal openingBalance,

        @NotNull(message = "finance.account.openingBalanceDate.required")
        LocalDate openingBalanceDate
) {

    public CreateAccountCommand toCommand() {
        return new CreateAccountCommand(
                accountName,
                accountDescription,
                currency,
                issuingInstitution,
                openingBalance,
                openingBalanceDate
        );
    }
}