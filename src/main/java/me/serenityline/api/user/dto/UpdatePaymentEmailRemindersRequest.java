package me.serenityline.api.user.dto;

import jakarta.validation.constraints.NotNull;

public record UpdatePaymentEmailRemindersRequest(
        @NotNull(message = "{user.paymentEmailReminders.enabled.required}")
        Boolean enabled
) {
}