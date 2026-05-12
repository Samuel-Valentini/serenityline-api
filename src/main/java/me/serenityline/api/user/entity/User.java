package me.serenityline.api.user.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "users")
public class User {

    private static final int USER_NAME_MAX_LENGTH = 255;
    private static final int EMAIL_MAX_LENGTH = 320;
    private static final int PASSWORD_HASH_MAX_LENGTH = 255;

    private static final Set<String> SUPPORTED_LOCALES = Set.of("it-IT", "en-US");

    private static final Pattern BASIC_EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final int SOFT_DELETE_GRACE_PERIOD_DAYS = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @NotBlank(message = "{user.name.required}")
    @Column(name = "user_name", nullable = false, length = 255)
    private String userName;

    @NotBlank(message = "{user.email.required}")
    @Email(message = "{user.email.invalid}")
    @Column(name = "email", nullable = false, length = 320, unique = true)
    private String email;

    @NotNull(message = "{user.group.required}")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_group_id", nullable = false)
    private UserGroup userGroup;

    @NotNull(message = "{user.role.required}")
    @Enumerated(EnumType.STRING)
    @Column(name = "user_role", nullable = false, length = 50)
    private UserRole userRole;

    @NotNull(message = "{user.platformRole.required}")
    @Enumerated(EnumType.STRING)
    @Column(name = "user_platform_role", nullable = false, length = 50)
    private UserPlatformRole userPlatformRole;

    @NotBlank(message = "{user.preferredLocale.required}")
    @Column(name = "preferred_locale", nullable = false, length = 10)
    private String preferredLocale;

    @NotNull(message = "{user.preferredTheme.required}")
    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_theme", nullable = false, length = 30)
    private PreferredTheme preferredTheme;

    @Column(name = "wants_invoice", nullable = false)
    private boolean wantsInvoice;

    @NotBlank(message = "{user.passwordHash.required}")
    @Column(name = "user_password_hash", nullable = false, length = 255)
    private String userPasswordHash;

    @Column(name = "user_created_at", nullable = false, updatable = false)
    private OffsetDateTime userCreatedAt;

    @Column(name = "user_updated_at", nullable = false)
    private OffsetDateTime userUpdatedAt;

    @Column(name = "user_deleted_at")
    private OffsetDateTime userDeletedAt;

    @Column(name = "user_is_enabled", nullable = false)
    private boolean userIsEnabled;

    @Column(name = "token_version", nullable = false)
    private Long tokenVersion;

    @Column(name = "user_last_login_at")
    private OffsetDateTime userLastLoginAt;

    protected User() {
    }

    public User(String userName, String email, UserGroup userGroup, UserRole userRole, UserPlatformRole userPlatformRole, String preferredLocale, PreferredTheme preferredTheme, boolean wantsInvoice, String userPasswordHash, boolean userIsEnabled, Long tokenVersion) {
        setUserName(userName);
        setEmail(email);
        setUserGroup(userGroup);
        setUserRole(userRole);
        setUserPlatformRole(userPlatformRole);
        setPreferredLocale(preferredLocale);
        setPreferredTheme(preferredTheme);
        setWantsInvoice(wantsInvoice);
        setUserPasswordHash(userPasswordHash);
        setUserIsEnabled(userIsEnabled);
        setTokenVersion(tokenVersion);

    }

    public User(
            String userName,
            String email,
            UserGroup userGroup,
            UserRole userRole,
            String userPasswordHash
    ) {
        this(userName, email, userGroup, userRole, UserPlatformRole.USER, "it-IT", PreferredTheme.DEFAULT, false, userPasswordHash, false, 0L);

    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }

        return value.trim();
    }

    private static String requireNotBlankAndMaxLength(
            String value,
            int maxLength,
            String requiredMessageKey,
            String tooLongMessageKey
    ) {
        String normalized = normalizeText(value);

        if (normalized == null || normalized.isBlank()) {
            throw validationError(requiredMessageKey);
        }

        if (normalized.length() > maxLength) {
            throw validationError(tooLongMessageKey);
        }

        return normalized;
    }

    private static String normalizeAndValidateEmail(String email) {
        String normalized = requireNotBlankAndMaxLength(
                email,
                EMAIL_MAX_LENGTH,
                "user.email.required",
                "user.email.tooLong"
        ).toLowerCase(Locale.ROOT);

        if (!BASIC_EMAIL_PATTERN.matcher(normalized).matches()) {
            throw validationError("user.email.invalid");
        }

        return normalized;
    }

    private static String normalizeAndValidatePreferredLocale(String preferredLocale) {
        String normalized = requireNotBlankAndMaxLength(
                preferredLocale,
                10,
                "user.preferredLocale.required",
                "user.preferredLocale.invalid"
        );

        if (!SUPPORTED_LOCALES.contains(normalized)) {
            throw validationError("user.preferredLocale.invalid");
        }

        return normalized;
    }

    private static Long validateTokenVersion(Long tokenVersion) {
        if (tokenVersion == null) {
            throw validationError("user.tokenVersion.required");
        }

        if (tokenVersion < 0) {
            throw validationError("user.tokenVersion.negative");
        }

        return tokenVersion;
    }

    private static <T> T requireNotNull(T value, String messageKey) {
        if (value == null) {
            throw validationError(messageKey);
        }

        return value;
    }

    private static IllegalArgumentException validationError(String messageKey) {
        return new IllegalArgumentException(messageKey);
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();

        if (this.userCreatedAt == null) {
            this.userCreatedAt = now;
        }

        if (this.userUpdatedAt == null) {
            this.userUpdatedAt = now;
        }

        if (this.userPlatformRole == null) {
            this.userPlatformRole = UserPlatformRole.USER;
        }

        if (this.preferredLocale == null) {
            this.preferredLocale = "it-IT";
        }

        if (this.preferredTheme == null) {
            this.preferredTheme = PreferredTheme.DEFAULT;
        }

        if (this.tokenVersion == null) {
            this.tokenVersion = 0L;
        }

        validateForPersistence();
    }

    @PreUpdate
    protected void onUpdate() {
        this.userUpdatedAt = OffsetDateTime.now();
        validateForPersistence();
    }

    private void validateForPersistence() {
        this.userName = requireNotBlankAndMaxLength(
                this.userName,
                USER_NAME_MAX_LENGTH,
                "user.name.required",
                "user.name.tooLong"
        );

        this.email = normalizeAndValidateEmail(this.email);

        this.userGroup = requireNotNull(
                this.userGroup,
                "user.group.required"
        );

        this.userRole = requireNotNull(
                this.userRole,
                "user.role.required"
        );

        this.userPlatformRole = requireNotNull(
                this.userPlatformRole,
                "user.platformRole.required"
        );

        this.preferredLocale = normalizeAndValidatePreferredLocale(this.preferredLocale);

        this.preferredTheme = requireNotNull(
                this.preferredTheme,
                "user.preferredTheme.required"
        );

        this.userPasswordHash = requireNotBlankAndMaxLength(
                this.userPasswordHash,
                PASSWORD_HASH_MAX_LENGTH,
                "user.passwordHash.required",
                "user.passwordHash.tooLong"
        );

        this.tokenVersion = validateTokenVersion(this.tokenVersion);

        validateDates();
    }

    private void validateDates() {
        if (userCreatedAt != null && userUpdatedAt != null && userUpdatedAt.isBefore(userCreatedAt)) {
            throw validationError("user.updatedAt.beforeCreatedAt");
        }

        if (userCreatedAt != null && userDeletedAt != null && userDeletedAt.isBefore(userCreatedAt)) {
            throw validationError("user.deletedAt.beforeCreatedAt");
        }

        if (userCreatedAt != null && userLastLoginAt != null && userLastLoginAt.isBefore(userCreatedAt)) {
            throw validationError("user.lastLoginAt.beforeCreatedAt");
        }
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = requireNotBlankAndMaxLength(
                userName,
                USER_NAME_MAX_LENGTH,
                "user.name.required",
                "user.name.tooLong"
        );
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = normalizeAndValidateEmail(email);
    }

    public UserGroup getUserGroup() {
        return userGroup;
    }

    public void setUserGroup(UserGroup userGroup) {
        this.userGroup = requireNotNull(userGroup, "user.group.required");
    }

    public UserRole getUserRole() {
        return userRole;
    }

    public void setUserRole(UserRole userRole) {
        this.userRole = requireNotNull(userRole, "user.role.required");
    }

    public UserPlatformRole getUserPlatformRole() {
        return userPlatformRole;
    }

    public void setUserPlatformRole(UserPlatformRole userPlatformRole) {
        this.userPlatformRole = requireNotNull(userPlatformRole, "user.platformRole.required");
    }

    public String getPreferredLocale() {
        return preferredLocale;
    }

    public void setPreferredLocale(String preferredLocale) {
        this.preferredLocale = normalizeAndValidatePreferredLocale(preferredLocale);
    }

    public PreferredTheme getPreferredTheme() {
        return preferredTheme;
    }

    public void setPreferredTheme(PreferredTheme preferredTheme) {
        this.preferredTheme = requireNotNull(preferredTheme, "user.preferredTheme.required");
    }

    public boolean isWantsInvoice() {
        return wantsInvoice;
    }

    public void setWantsInvoice(boolean wantsInvoice) {
        this.wantsInvoice = wantsInvoice;
    }

    public String getUserPasswordHash() {
        return userPasswordHash;
    }

    public void setUserPasswordHash(String userPasswordHash) {
        this.userPasswordHash = requireNotBlankAndMaxLength(
                userPasswordHash,
                PASSWORD_HASH_MAX_LENGTH,
                "user.passwordHash.required",
                "user.passwordHash.tooLong"
        );
    }

    public OffsetDateTime getUserCreatedAt() {
        return userCreatedAt;
    }

    public OffsetDateTime getUserUpdatedAt() {
        return userUpdatedAt;
    }

    public OffsetDateTime getUserDeletedAt() {
        return userDeletedAt;
    }

    public boolean isUserIsEnabled() {
        return userIsEnabled;
    }

    public void setUserIsEnabled(boolean userIsEnabled) {
        this.userIsEnabled = userIsEnabled;
    }

    public Long getTokenVersion() {
        return tokenVersion;
    }

    public void setTokenVersion(Long tokenVersion) {
        this.tokenVersion = validateTokenVersion(tokenVersion);
    }

    public OffsetDateTime getUserLastLoginAt() {
        return userLastLoginAt;
    }

    public void setUserLastLoginAt(OffsetDateTime userLastLoginAt) {
        if (this.userCreatedAt != null && userLastLoginAt != null && userLastLoginAt.isBefore(this.userCreatedAt)) {
            throw validationError("user.lastLoginAt.beforeCreatedAt");
        }

        this.userLastLoginAt = userLastLoginAt;
    }

    public void markAsSoftDeleted() {
        if (isPendingDeletion()) {
            throw new IllegalStateException("user.alreadyPendingDeletion");
        }

        OffsetDateTime now = OffsetDateTime.now();

        this.userDeletedAt = now;
        this.userUpdatedAt = now;

        incrementTokenVersion();
    }

    public void restoreFromSoftDelete() {
        if (!isPendingDeletion()) {
            throw new IllegalStateException("user.notPendingDeletion");
        }

        if (isHardDeletionDue()) {
            throw new IllegalStateException("user.hardDeletionDue");
        }

        this.userDeletedAt = null;
        this.userUpdatedAt = OffsetDateTime.now();

        incrementTokenVersion();
    }

    public boolean isPendingDeletion() {
        return this.userDeletedAt != null;
    }

    public boolean isHardDeletionDue() {
        if (this.userDeletedAt == null) {
            return false;
        }

        OffsetDateTime hardDeletionScheduledAt = getHardDeletionScheduledAt();

        return !hardDeletionScheduledAt.isAfter(OffsetDateTime.now());
    }

    public OffsetDateTime getHardDeletionScheduledAt() {
        if (this.userDeletedAt == null) {
            return null;
        }

        return this.userDeletedAt.plusDays(SOFT_DELETE_GRACE_PERIOD_DAYS);
    }

    public void incrementTokenVersion() {
        if (this.tokenVersion == null) {
            this.tokenVersion = 0L;
        }

        this.tokenVersion++;
    }

    public void markSuccessfulLogin() {
        this.userLastLoginAt = OffsetDateTime.now();
    }

}