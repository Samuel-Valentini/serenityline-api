package me.serenityline.api.auth.service;

import me.serenityline.api.auth.dto.UserInvitationAcceptRequest;
import me.serenityline.api.auth.dto.UserInvitationCreateRequest;
import me.serenityline.api.auth.dto.UserInvitationResponse;
import me.serenityline.api.auth.entity.AuthActionToken;
import me.serenityline.api.auth.entity.AuthActionTokenType;
import me.serenityline.api.auth.repository.AuthActionTokenRepository;
import me.serenityline.api.common.error.ResourceNotFoundException;
import me.serenityline.api.email.outbox.entity.EmailOutbox;
import me.serenityline.api.email.outbox.entity.EmailOutboxType;
import me.serenityline.api.email.outbox.repository.EmailOutboxRepository;
import me.serenityline.api.finance.account.entity.Account;
import me.serenityline.api.finance.account.entity.AccountUser;
import me.serenityline.api.finance.account.repository.AccountRepository;
import me.serenityline.api.finance.account.repository.AccountUserRepository;
import me.serenityline.api.security.crypto.EmailOutboxEncryptionService;
import me.serenityline.api.security.crypto.EncryptedValue;
import me.serenityline.api.security.token.SecureTokenGenerator;
import me.serenityline.api.security.token.TokenHashingService;
import me.serenityline.api.user.entity.PreferredTheme;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.entity.UserPlatformRole;
import me.serenityline.api.user.entity.UserRole;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UserInvitationService {

    private static final String DEFAULT_LOCALE = "it-IT";
    private static final String ENGLISH_LOCALE = "en-US";
    private static final Duration MIN_TOKEN_TTL = Duration.ofMinutes(1);

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final AccountUserRepository accountUserRepository;
    private final AuthActionTokenRepository authActionTokenRepository;
    private final EmailOutboxRepository emailOutboxRepository;
    private final EmailOutboxEncryptionService emailOutboxEncryptionService;
    private final SecureTokenGenerator secureTokenGenerator;
    private final TokenHashingService tokenHashingService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyService passwordPolicyService;
    private final MessageSource messageSource;
    private final Duration invitationTokenTtl;
    private final String frontendBaseUrl;

    public UserInvitationService(
            UserRepository userRepository,
            AccountRepository accountRepository,
            AccountUserRepository accountUserRepository,
            AuthActionTokenRepository authActionTokenRepository,
            EmailOutboxRepository emailOutboxRepository,
            EmailOutboxEncryptionService emailOutboxEncryptionService,
            SecureTokenGenerator secureTokenGenerator,
            TokenHashingService tokenHashingService,
            PasswordEncoder passwordEncoder,
            PasswordPolicyService passwordPolicyService,
            MessageSource messageSource,
            @Value("${serenityline.auth.user-invitation.token-ttl}") Duration invitationTokenTtl,
            @Value("${serenityline.frontend.base-url}") String frontendBaseUrl
    ) {
        validateInvitationTokenTtl(invitationTokenTtl);

        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
        this.accountUserRepository = Objects.requireNonNull(accountUserRepository, "accountUserRepository");
        this.authActionTokenRepository = Objects.requireNonNull(authActionTokenRepository, "authActionTokenRepository");
        this.emailOutboxRepository = Objects.requireNonNull(emailOutboxRepository, "emailOutboxRepository");
        this.emailOutboxEncryptionService = Objects.requireNonNull(emailOutboxEncryptionService, "emailOutboxEncryptionService");
        this.secureTokenGenerator = Objects.requireNonNull(secureTokenGenerator, "secureTokenGenerator");
        this.tokenHashingService = Objects.requireNonNull(tokenHashingService, "tokenHashingService");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
        this.passwordPolicyService = Objects.requireNonNull(passwordPolicyService, "passwordPolicyService");
        this.messageSource = Objects.requireNonNull(messageSource, "messageSource");
        this.invitationTokenTtl = invitationTokenTtl;
        this.frontendBaseUrl = normalizeFrontendBaseUrl(frontendBaseUrl);
    }

    private static IllegalArgumentException invalidInvitationToken() {
        return new IllegalArgumentException("auth.userInvitation.invalidOrExpired");
    }

    private static void validateInvitationTokenTtl(Duration tokenTtl) {
        if (tokenTtl == null
                || tokenTtl.isZero()
                || tokenTtl.isNegative()
                || tokenTtl.compareTo(MIN_TOKEN_TTL) < 0) {
            throw new IllegalStateException("auth.userInvitation.tokenTtl.invalid");
        }
    }

    private static String normalizeFrontendBaseUrl(String frontendBaseUrl) {
        if (frontendBaseUrl == null || frontendBaseUrl.isBlank()) {
            throw new IllegalStateException("auth.userInvitation.frontendBaseUrl.required");
        }

        String normalized = frontendBaseUrl.trim();

        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            throw new IllegalStateException("auth.userInvitation.frontendBaseUrl.invalid");
        }

        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }

    @Transactional
    public UserInvitationResponse inviteUser(
            UUID currentUserId,
            UserInvitationCreateRequest request
    ) {
        Objects.requireNonNull(currentUserId, "currentUserId");

        if (request == null) {
            throw new IllegalArgumentException("auth.userInvitation.request.required");
        }

        User owner = findCurrentOwner(currentUserId);

        String userName = normalizeRequiredText(
                request.userName(),
                "user.name.required"
        );

        String email = normalizeEmail(request.email());
        String preferredLocale = resolvePreferredLocale(request.preferredLocale());
        boolean paymentEmailRemindersEnabled =
                request.paymentEmailRemindersEnabled() == null || request.paymentEmailRemindersEnabled();

        UserRole invitedRole = validateInvitedRole(request.userRole());
        Set<UUID> requestedAccountIds = normalizeAccountIds(request.accountIds());

        validateAccountIdsForRole(invitedRole, requestedAccountIds);
        ensureEmailAvailable(email);

        List<Account> linkedAccounts = findLinkedAccountsForInvitation(
                owner,
                requestedAccountIds
        );

        String temporaryUnusablePasswordHash = passwordEncoder.encode(
                secureTokenGenerator.generate()
        );

        User invitedUser = new User(
                userName,
                email,
                owner.getUserGroup(),
                invitedRole,
                UserPlatformRole.USER,
                preferredLocale,
                PreferredTheme.DEFAULT,
                false,
                paymentEmailRemindersEnabled,
                temporaryUnusablePasswordHash,
                false,
                0L
        );

        User savedUser = userRepository.saveAndFlush(invitedUser);

        List<AccountUser> accountUsers = linkedAccounts.stream()
                .map(account -> AccountUser.grant(account, savedUser))
                .toList();

        accountUserRepository.saveAll(accountUsers);

        createInvitationTokenAndEmail(
                savedUser,
                owner,
                OffsetDateTime.now()
        );

        return UserInvitationResponse.from(
                savedUser,
                linkedAccounts.stream()
                        .map(Account::getAccountId)
                        .toList()
        );
    }

    @Transactional
    public void acceptInvitation(UserInvitationAcceptRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("auth.userInvitation.acceptRequest.required");
        }

        String plainToken = normalizeRequiredText(
                request.token(),
                "auth.token.required"
        );

        String tokenHash = tokenHashingService.hash(plainToken);

        AuthActionToken actionToken = authActionTokenRepository
                .findByAuthActionTokenHashForUpdate(tokenHash)
                .orElseThrow(UserInvitationService::invalidInvitationToken);

        if (actionToken.getAuthActionTokenType() != AuthActionTokenType.USER_INVITATION) {
            throw invalidInvitationToken();
        }

        if (!actionToken.isPending()) {
            throw invalidInvitationToken();
        }

        User user = actionToken.getUser();

        if (user.isPendingDeletion() || user.isUserIsEnabled()) {
            throw invalidInvitationToken();
        }

        String newPassword = request.password();

        passwordPolicyService.validateRegistrationPassword(
                newPassword,
                user.getUserName(),
                user.getEmail()
        );

        String newPasswordHash = passwordEncoder.encode(newPassword);

        try {
            actionToken.markUsed(AuthActionTokenType.USER_INVITATION);
        } catch (IllegalStateException ex) {
            throw invalidInvitationToken();
        }

        user.setUserPasswordHash(newPasswordHash);
        user.setUserIsEnabled(true);
    }

    private User findCurrentOwner(UUID currentUserId) {
        User currentUser = userRepository.findAuthenticationUserById(currentUserId)
                .orElseThrow(() -> new AccessDeniedException("auth.userInvitation.ownerRequired"));

        if (currentUser.getUserRole() != UserRole.OWNER) {
            throw new AccessDeniedException("auth.userInvitation.ownerRequired");
        }

        return currentUser;
    }

    private UserRole validateInvitedRole(UserRole userRole) {
        if (userRole == null) {
            throw new IllegalArgumentException("auth.userInvitation.role.required");
        }

        if (userRole == UserRole.OWNER) {
            throw new IllegalArgumentException("auth.userInvitation.ownerRoleForbidden");
        }

        return switch (userRole) {
            case SUPER_COLLABORATOR, VIEWER_COLLABORATOR, COLLABORATOR -> userRole;
            case OWNER -> throw new IllegalArgumentException("auth.userInvitation.ownerRoleForbidden");
        };
    }

    private void validateAccountIdsForRole(
            UserRole invitedRole,
            Set<UUID> accountIds
    ) {
        if (invitedRole == UserRole.SUPER_COLLABORATOR && !accountIds.isEmpty()) {
            throw new IllegalArgumentException("auth.userInvitation.accountIdsNotAllowedForRole");
        }

        if (invitedRole == UserRole.COLLABORATOR && accountIds.isEmpty()) {
            throw new IllegalArgumentException("auth.userInvitation.accountIdsRequired");
        }
    }

    private List<Account> findLinkedAccountsForInvitation(
            User owner,
            Set<UUID> requestedAccountIds
    ) {
        if (requestedAccountIds.isEmpty()) {
            return List.of();
        }

        List<Account> accounts = accountRepository
                .findAllByUserGroup_UserGroupIdAndAccountIdInOrderByAccountNameAsc(
                        owner.getUserGroup().getUserGroupId(),
                        requestedAccountIds
                );

        if (accounts.size() != requestedAccountIds.size()) {
            throw new ResourceNotFoundException("auth.userInvitation.accountNotFound");
        }

        Map<UUID, Account> accountsById = accounts.stream()
                .collect(Collectors.toMap(Account::getAccountId, Function.identity()));

        return requestedAccountIds.stream()
                .map(accountsById::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private void ensureEmailAvailable(String normalizedEmail) {
        if (userRepository.findByEmailAndUserDeletedAtIsNull(normalizedEmail).isPresent()) {
            throw new IllegalStateException("auth.userInvitation.emailAlreadyExists");
        }

        if (userRepository.findByEmailAndUserDeletedAtIsNotNull(normalizedEmail).isPresent()) {
            throw new IllegalStateException("auth.userInvitation.accountPendingDeletion");
        }
    }

    private void createInvitationTokenAndEmail(
            User invitedUser,
            User owner,
            OffsetDateTime now
    ) {
        String plainToken = secureTokenGenerator.generate();
        String tokenHash = tokenHashingService.hash(plainToken);
        OffsetDateTime expiresAt = now.plus(invitationTokenTtl);

        AuthActionToken actionToken = new AuthActionToken(
                invitedUser,
                tokenHash,
                AuthActionTokenType.USER_INVITATION,
                expiresAt
        );

        authActionTokenRepository.save(actionToken);

        EmailOutbox emailOutbox = createInvitationEmailOutbox(
                invitedUser,
                owner,
                plainToken,
                now
        );

        emailOutboxRepository.save(emailOutbox);
    }

    private EmailOutbox createInvitationEmailOutbox(
            User invitedUser,
            User owner,
            String plainToken,
            OffsetDateTime scheduledAt
    ) {
        String subject = buildSubject(invitedUser, owner);
        String textBody = buildTextBody(invitedUser, owner, plainToken);

        EncryptedValue encryptedSubject = emailOutboxEncryptionService.encrypt(subject);
        EncryptedValue encryptedTextBody = emailOutboxEncryptionService.encrypt(textBody);

        return new EmailOutbox(
                invitedUser,
                invitedUser.getEmail(),
                EmailOutboxType.USER_INVITATION,
                emailOutboxEncryptionService.getEncryptionKeyId(),
                encryptedSubject.encrypted(),
                encryptedSubject.iv(),
                encryptedSubject.tag(),
                null,
                null,
                null,
                encryptedTextBody.encrypted(),
                encryptedTextBody.iv(),
                encryptedTextBody.tag(),
                true,
                scheduledAt
        );
    }

    private String buildSubject(User invitedUser, User owner) {
        return messageSource.getMessage(
                "auth.userInvitation.email.subject",
                new Object[]{owner.getUserName()},
                resolveUserLocale(invitedUser)
        );
    }

    private String buildTextBody(
            User invitedUser,
            User owner,
            String plainToken
    ) {
        Locale locale = resolveUserLocale(invitedUser);

        return messageSource.getMessage(
                "auth.userInvitation.email.body.text",
                new Object[]{
                        invitedUser.getUserName(),
                        owner.getUserName(),
                        owner.getUserGroup().getUserGroupName(),
                        buildInvitationUrl(plainToken),
                        buildManualInvitationUrl(),
                        plainToken,
                        formatTokenTtl(locale)
                },
                locale
        );
    }

    private String buildInvitationUrl(String plainToken) {
        return frontendBaseUrl + "/user-invitations/accept#token=" + plainToken;
    }

    private String buildManualInvitationUrl() {
        return frontendBaseUrl + "/user-invitations/accept";
    }

    private String formatTokenTtl(Locale locale) {
        long totalMinutes = invitationTokenTtl.toMinutes();

        if (totalMinutes <= 0) {
            throw new IllegalStateException("auth.userInvitation.tokenTtl.invalid");
        }

        if (totalMinutes % (24 * 60) == 0) {
            long days = totalMinutes / (24 * 60);

            return messageSource.getMessage(
                    days == 1
                            ? "auth.emailVerification.ttl.days.singular"
                            : "auth.emailVerification.ttl.days.plural",
                    new Object[]{days},
                    locale
            );
        }

        if (totalMinutes % 60 == 0) {
            long hours = totalMinutes / 60;

            return messageSource.getMessage(
                    hours == 1
                            ? "auth.emailVerification.ttl.hours.singular"
                            : "auth.emailVerification.ttl.hours.plural",
                    new Object[]{hours},
                    locale
            );
        }

        return messageSource.getMessage(
                totalMinutes == 1
                        ? "auth.emailVerification.ttl.minutes.singular"
                        : "auth.emailVerification.ttl.minutes.plural",
                new Object[]{totalMinutes},
                locale
        );
    }

    private Locale resolveUserLocale(User user) {
        String preferredLocale = user.getPreferredLocale();

        if (preferredLocale == null || preferredLocale.isBlank()) {
            return Locale.forLanguageTag(DEFAULT_LOCALE);
        }

        return Locale.forLanguageTag(preferredLocale);
    }

    private Set<UUID> normalizeAccountIds(Set<UUID> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return Set.of();
        }

        LinkedHashSet<UUID> normalizedAccountIds = new LinkedHashSet<>();

        for (UUID accountId : accountIds) {
            if (accountId == null) {
                throw new IllegalArgumentException("auth.userInvitation.accountId.required");
            }

            normalizedAccountIds.add(accountId);
        }

        return Collections.unmodifiableSet(normalizedAccountIds);
    }

    private String normalizeEmail(String email) {
        return normalizeRequiredText(
                email,
                "user.email.required"
        ).toLowerCase(Locale.ROOT);
    }

    private String normalizeRequiredText(
            String value,
            String requiredMessageKey
    ) {
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
}