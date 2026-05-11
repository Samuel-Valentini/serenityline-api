package me.serenityline.api.auth.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import me.serenityline.api.user.entity.User;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    private static final int TOKEN_HASH_MAX_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "refresh_token_id", nullable = false)
    private UUID refreshTokenId;

    @NotNull(message = "{refreshToken.user.required}")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull(message = "{refreshToken.session.required}")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_session_id", nullable = false)
    private UserSession userSession;

    @NotBlank(message = "{refreshToken.hash.required}")
    @Column(name = "refresh_token_hash", nullable = false, length = 255, unique = true)
    private String refreshTokenHash;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_refresh_token_id")
    private RefreshToken parentRefreshToken;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaced_by_refresh_token_id")
    private RefreshToken replacedByRefreshToken;

    @Column(name = "refresh_token_created_at", nullable = false, updatable = false)
    private OffsetDateTime refreshTokenCreatedAt;

    @NotNull(message = "{refreshToken.expiresAt.required}")
    @Column(name = "refresh_token_expires_at", nullable = false)
    private OffsetDateTime refreshTokenExpiresAt;

    @Column(name = "refresh_token_used_at")
    private OffsetDateTime refreshTokenUsedAt;

    @Column(name = "refresh_token_revoked_at")
    private OffsetDateTime refreshTokenRevokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "refresh_token_revoke_reason", length = 50)
    private RefreshTokenRevokeReason refreshTokenRevokeReason;

    @Column(name = "refresh_token_reuse_detected_at")
    private OffsetDateTime refreshTokenReuseDetectedAt;

    protected RefreshToken() {
    }

    public RefreshToken(
            User user,
            UserSession userSession,
            String refreshTokenHash,
            OffsetDateTime refreshTokenExpiresAt,
            RefreshToken parentRefreshToken
    ) {
        setUser(user);
        setUserSession(userSession);
        setRefreshTokenHash(refreshTokenHash);
        setRefreshTokenExpiresAt(refreshTokenExpiresAt);
        setParentRefreshToken(parentRefreshToken);
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
        if (this.refreshTokenCreatedAt == null) {
            this.refreshTokenCreatedAt = OffsetDateTime.now();
        }

        validateForPersistence();
    }

    @PreUpdate
    protected void onUpdate() {
        validateForPersistence();
    }

    private void validateForPersistence() {
        this.user = requireNotNull(this.user, "refreshToken.user.required");
        this.userSession = requireNotNull(this.userSession, "refreshToken.session.required");

        this.refreshTokenHash = requireNotBlankAndMaxLength(
                this.refreshTokenHash,
                TOKEN_HASH_MAX_LENGTH,
                "refreshToken.hash.required",
                "refreshToken.hash.tooLong"
        );

        this.refreshTokenExpiresAt = requireNotNull(
                this.refreshTokenExpiresAt,
                "refreshToken.expiresAt.required"
        );

        validateDates();
        validateRevocationConsistency();
        validateReplacementConsistency();
        validateReuseConsistency();
        validateSelfReferences();
    }

    private void validateDates() {
        if (refreshTokenCreatedAt != null
                && refreshTokenExpiresAt != null
                && !refreshTokenExpiresAt.isAfter(refreshTokenCreatedAt)) {
            throw validationError("refreshToken.expiresAt.beforeOrEqualCreatedAt");
        }

        if (refreshTokenCreatedAt != null
                && refreshTokenUsedAt != null
                && refreshTokenUsedAt.isBefore(refreshTokenCreatedAt)) {
            throw validationError("refreshToken.usedAt.beforeCreatedAt");
        }

        if (refreshTokenCreatedAt != null
                && refreshTokenRevokedAt != null
                && refreshTokenRevokedAt.isBefore(refreshTokenCreatedAt)) {
            throw validationError("refreshToken.revokedAt.beforeCreatedAt");
        }

        if (refreshTokenCreatedAt != null
                && refreshTokenReuseDetectedAt != null
                && refreshTokenReuseDetectedAt.isBefore(refreshTokenCreatedAt)) {
            throw validationError("refreshToken.reuseDetectedAt.beforeCreatedAt");
        }
    }

    private void validateRevocationConsistency() {
        boolean hasRevokedAt = this.refreshTokenRevokedAt != null;
        boolean hasReason = this.refreshTokenRevokeReason != null;

        if (hasRevokedAt != hasReason) {
            throw validationError("refreshToken.revocation.inconsistent");
        }
    }

    private void validateReplacementConsistency() {
        if (this.replacedByRefreshToken != null && this.refreshTokenUsedAt == null) {
            throw validationError("refreshToken.replacedBy.requiresUsedAt");
        }
    }

    private void validateReuseConsistency() {
        if (this.refreshTokenReuseDetectedAt == null) {
            return;
        }

        if (this.refreshTokenRevokedAt == null) {
            throw validationError("refreshToken.reuseDetected.requiresRevokedAt");
        }

        if (this.refreshTokenRevokeReason != RefreshTokenRevokeReason.REUSE_DETECTED) {
            throw validationError("refreshToken.reuseDetected.requiresReuseReason");
        }
    }

    private void validateSelfReferences() {
        if (this.refreshTokenId == null) {
            return;
        }

        if (this.parentRefreshToken != null
                && this.refreshTokenId.equals(this.parentRefreshToken.getRefreshTokenId())) {
            throw validationError("refreshToken.parent.selfReference");
        }

        if (this.replacedByRefreshToken != null
                && this.refreshTokenId.equals(this.replacedByRefreshToken.getRefreshTokenId())) {
            throw validationError("refreshToken.replacedBy.selfReference");
        }
    }

    public UUID getRefreshTokenId() {
        return refreshTokenId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = requireNotNull(user, "refreshToken.user.required");
    }

    public UserSession getUserSession() {
        return userSession;
    }

    public void setUserSession(UserSession userSession) {
        this.userSession = requireNotNull(userSession, "refreshToken.session.required");
    }

    public String getRefreshTokenHash() {
        return refreshTokenHash;
    }

    public void setRefreshTokenHash(String refreshTokenHash) {
        this.refreshTokenHash = requireNotBlankAndMaxLength(
                refreshTokenHash,
                TOKEN_HASH_MAX_LENGTH,
                "refreshToken.hash.required",
                "refreshToken.hash.tooLong"
        );
    }

    public RefreshToken getParentRefreshToken() {
        return parentRefreshToken;
    }

    public void setParentRefreshToken(RefreshToken parentRefreshToken) {
        this.parentRefreshToken = parentRefreshToken;
    }

    public RefreshToken getReplacedByRefreshToken() {
        return replacedByRefreshToken;
    }

    public OffsetDateTime getRefreshTokenCreatedAt() {
        return refreshTokenCreatedAt;
    }

    public OffsetDateTime getRefreshTokenExpiresAt() {
        return refreshTokenExpiresAt;
    }

    public void setRefreshTokenExpiresAt(OffsetDateTime refreshTokenExpiresAt) {
        this.refreshTokenExpiresAt = requireNotNull(
                refreshTokenExpiresAt,
                "refreshToken.expiresAt.required"
        );
    }

    public OffsetDateTime getRefreshTokenUsedAt() {
        return refreshTokenUsedAt;
    }

    public OffsetDateTime getRefreshTokenRevokedAt() {
        return refreshTokenRevokedAt;
    }

    public RefreshTokenRevokeReason getRefreshTokenRevokeReason() {
        return refreshTokenRevokeReason;
    }

    public OffsetDateTime getRefreshTokenReuseDetectedAt() {
        return refreshTokenReuseDetectedAt;
    }

    public void markUsedAndReplacedBy(RefreshToken replacementToken) {
        if (this.refreshTokenUsedAt != null) {
            throw validationError("refreshToken.alreadyUsed");
        }

        if (this.refreshTokenRevokedAt != null) {
            throw validationError("refreshToken.alreadyRevoked");
        }

        this.refreshTokenUsedAt = OffsetDateTime.now();
        this.replacedByRefreshToken = requireNotNull(
                replacementToken,
                "refreshToken.replacement.required"
        );
    }

    public void revoke(RefreshTokenRevokeReason reason) {
        if (this.refreshTokenRevokedAt != null) {
            throw validationError("refreshToken.alreadyRevoked");
        }

        this.refreshTokenRevokeReason = requireNotNull(
                reason,
                "refreshToken.revokeReason.required"
        );
        this.refreshTokenRevokedAt = OffsetDateTime.now();
    }

    public void markReuseDetected() {
        if (this.refreshTokenReuseDetectedAt != null) {
            throw validationError("refreshToken.reuseAlreadyDetected");
        }

        OffsetDateTime now = OffsetDateTime.now();

        this.refreshTokenReuseDetectedAt = now;
        this.refreshTokenRevokedAt = now;
        this.refreshTokenRevokeReason = RefreshTokenRevokeReason.REUSE_DETECTED;
    }

    public boolean isUsed() {
        return this.refreshTokenUsedAt != null;
    }

    public boolean isRevoked() {
        return this.refreshTokenRevokedAt != null;
    }

    public boolean isExpired() {
        return !this.refreshTokenExpiresAt.isAfter(OffsetDateTime.now());
    }

    public boolean isActive() {
        return !isUsed() && !isRevoked() && !isExpired();
    }
}