package me.serenityline.api.user.service;

import me.serenityline.api.user.dto.PaymentEmailRemindersResponse;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class PaymentEmailRemindersService {

    private final UserRepository userRepository;

    public PaymentEmailRemindersService(UserRepository userRepository) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
    }

    @Transactional
    public PaymentEmailRemindersResponse updatePaymentEmailReminders(
            UUID userId,
            Boolean enabled
    ) {
        if (userId == null) {
            throw new IllegalArgumentException("auth.authentication.user.required");
        }

        if (enabled == null) {
            throw new IllegalArgumentException("user.paymentEmailReminders.enabled.required");
        }

        User user = userRepository
                .findActiveUserByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("auth.authentication.user.invalid"));

        if (enabled) {
            user.enablePaymentEmailReminders();
        } else {
            user.disablePaymentEmailReminders();
        }

        return new PaymentEmailRemindersResponse(
                user.isPaymentEmailRemindersEnabled()
        );
    }
}