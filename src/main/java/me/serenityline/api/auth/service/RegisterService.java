package me.serenityline.api.auth.service;

import me.serenityline.api.auth.dto.RegisterRequest;
import me.serenityline.api.auth.dto.RegisterResponse;
import me.serenityline.api.user.entity.*;
import me.serenityline.api.user.repository.UserGroupRepository;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class RegisterService {

    private static final String DEFAULT_LOCALE = "it-IT";
    private static final String ENGLISH_LOCALE = "en-US";

    private final UserRepository userRepository;
    private final UserGroupRepository userGroupRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyService passwordPolicyService;

    public RegisterService(
            UserRepository userRepository,
            UserGroupRepository userGroupRepository,
            PasswordEncoder passwordEncoder,
            PasswordPolicyService passwordPolicyService
    ) {
        this.userRepository = userRepository;
        this.userGroupRepository = userGroupRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyService = passwordPolicyService;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("auth.register.request.required");
        }

        String normalizedUserName = normalizeRequiredText(
                request.userName(),
                "user.name.required"
        );

        String normalizedEmail = normalizeEmail(request.email());
        String preferredLocale = resolvePreferredLocale(request.preferredLocale());
        boolean wantsInvoice = Boolean.TRUE.equals(request.wantsInvoice());

        ensureEmailAvailable(normalizedEmail);

        String rawPassword = request.password();

        passwordPolicyService.validateRegistrationPassword(
                rawPassword,
                normalizedUserName,
                normalizedEmail
        );

        String passwordHash = passwordEncoder.encode(rawPassword);

        UserGroup userGroup = new UserGroup(
                buildDefaultGroupName(normalizedUserName, preferredLocale)
        );

        User userGroupOwner = new User(
                normalizedUserName,
                normalizedEmail,
                userGroup,
                UserRole.OWNER,
                UserPlatformRole.USER,
                preferredLocale,
                PreferredTheme.DEFAULT,
                wantsInvoice,
                passwordHash,
                false,
                0L
        );

        userGroupRepository.save(userGroup);

        User savedUser = userRepository.save(userGroupOwner);

        return RegisterResponse.from(savedUser);
    }

    private void ensureEmailAvailable(String normalizedEmail) {
        if (userRepository.findByEmailAndUserDeletedAtIsNull(normalizedEmail).isPresent()) {
            throw new IllegalStateException("auth.register.emailAlreadyExists");
        }

        if (userRepository.findByEmailAndUserDeletedAtIsNotNull(normalizedEmail).isPresent()) {
            throw new IllegalStateException("auth.register.accountPendingDeletion");
        }
    }

    private String normalizeEmail(String email) {
        String normalizedEmail = normalizeRequiredText(
                email,
                "user.email.required"
        ).toLowerCase(Locale.ROOT);

        return normalizedEmail;
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

    private String resolvePreferredLocale(String preferredLocale) {
        if (preferredLocale == null || preferredLocale.isBlank()) {
            return DEFAULT_LOCALE;
        }

        String normalizedLocale = preferredLocale.trim();

        if (!DEFAULT_LOCALE.equals(normalizedLocale) && !ENGLISH_LOCALE.equals(normalizedLocale)) {
            throw new IllegalArgumentException("user.preferredLocale.invalid");
        }

        return normalizedLocale;
    }

    private String buildDefaultGroupName(String userName, String preferredLocale) {
        if (ENGLISH_LOCALE.equals(preferredLocale)) {
            return userName + "'s group";
        }

        return "Gruppo di " + userName;
    }
}