package me.serenityline.api.auth.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import me.serenityline.api.user.entity.User;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "auth_action_tokens")
public class AuthActionToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "auth_action_token_id", nullable = false, updatable = false)
    private UUID authActionTokenId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @NotNull(message = "authActionToken.user.required")
    private User user;

    @Column(name = "auth_action_token_hash", nullable = false, length = 255, unique = true)
    @NotBlank(message = "authActionToken.hash.required")
    @Size(max = 255, message = "authActionToken.hash.tooLong")
    private String authActionTokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_action_token_type", nullable = false, length = 50)
    @NotNull(message = "authActionToken.type.required")
    private AuthActionTokenType authActionTokenType;

    @Column(name = "auth_action_expires_at", nullable = false)
    @NotNull(message = "authActionToken.expiresAt.required")
    private OffsetDateTime authActionExpiresAt;

    @Column(name = "auth_action_used_at")
    private OffsetDateTime authActionUsedAt;

    @Column(name = "auth_action_revoked_at")
    private OffsetDateTime authActionRevokedAt;

    @Column(name = "auth_action_created_at", nullable = false, updatable = false)
    private OffsetDateTime authActionCreatedAt;

    protected AuthActionToken() {
    }

    public AuthActionToken(
            User user,
            String authActionTokenHash,
            AuthActionTokenType authActionTokenType,
            OffsetDateTime authActionExpiresAt
    ) {
        setUser(user);
        setAuthActionTokenHash(authActionTokenHash);
        setAuthActionTokenType(authActionTokenType);
        setAuthActionExpiresAt(authActionExpiresAt);
    }

    private static void validateUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("authActionToken.user.required");
        }
    }

    private static void validateHash(String hash) {
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("authActionToken.hash.required");
        }

        if (hash.length() > 255) {
            throw new IllegalArgumentException("authActionToken.hash.tooLong");
        }
    }

    private static void validateType(AuthActionTokenType type) {
        if (type == null) {
            throw new IllegalArgumentException("authActionToken.type.required");
        }
    }

    private static void validateExpiresAt(OffsetDateTime expiresAt) {
        if (expiresAt == null) {
            throw new IllegalArgumentException("authActionToken.expiresAt.required");
        }
    }

    private static String normalizeHash(String hash) {
        if (hash == null) {
            throw new IllegalArgumentException("authActionToken.hash.required");
        }

        String normalizedHash = hash.trim();
        validateHash(normalizedHash);

        return normalizedHash;
    }

    private static void validateExpectedType(AuthActionTokenType expectedType) {
        if (expectedType == null) {
            throw new IllegalArgumentException("authActionToken.expectedType.required");
        }
    }

    @PrePersist
    protected void onCreate() {
        if (this.authActionCreatedAt == null) {
            this.authActionCreatedAt = OffsetDateTime.now();
        }

        validateState();
    }

    @PreUpdate
    protected void onUpdate() {
        validateState();
    }

    public void markUsed(AuthActionTokenType expectedType) {
        validateExpectedType(expectedType);

        if (isUsed()) {
            throw new IllegalStateException("authActionToken.alreadyUsed");
        }

        if (isRevoked()) {
            throw new IllegalStateException("authActionToken.revoked");
        }

        if (isExpired()) {
            throw new IllegalStateException("authActionToken.expired");
        }

        if (this.authActionTokenType != expectedType) {
            throw new IllegalStateException("authActionToken.typeMismatch");
        }

        this.authActionUsedAt = OffsetDateTime.now();
        validateState();
    }

     /*
         Revocation is intentionally idempotent:
         revoking an already revoked token has no additional effect.
     */

      /* (Italian)
         La revoca è intenzionalmente idempotente:
         revocare un token già revocato non produce ulteriori effetti.
     */

    public void revoke() {
        if (isUsed()) {
            throw new IllegalStateException("authActionToken.alreadyUsed");
        }

        if (isRevoked()) {
            return;
        }

        this.authActionRevokedAt = OffsetDateTime.now();
        validateState();
    }

    public boolean isUsed() {
        return this.authActionUsedAt != null;
    }

    public boolean isRevoked() {
        return this.authActionRevokedAt != null;
    }

    public boolean isExpired() {
        validateExpiresAt(this.authActionExpiresAt);
        return !this.authActionExpiresAt.isAfter(OffsetDateTime.now());
    }

    public boolean isPending() {
        return !isUsed() && !isRevoked() && !isExpired();
    }

    private void validateState() {
        validateUser(this.user);
        validateHash(this.authActionTokenHash);
        validateType(this.authActionTokenType);
        validateExpiresAt(this.authActionExpiresAt);

        if (this.authActionCreatedAt == null) {
            throw new IllegalArgumentException("authActionToken.createdAt.required");
        }

        if (!this.authActionExpiresAt.isAfter(this.authActionCreatedAt)) {
            throw new IllegalArgumentException("authActionToken.expiresAt.beforeOrEqualCreatedAt");
        }

        if (this.authActionUsedAt != null && this.authActionUsedAt.isBefore(this.authActionCreatedAt)) {
            throw new IllegalArgumentException("authActionToken.usedAt.beforeCreatedAt");
        }

        if (this.authActionRevokedAt != null && this.authActionRevokedAt.isBefore(this.authActionCreatedAt)) {
            throw new IllegalArgumentException("authActionToken.revokedAt.beforeCreatedAt");
        }

        if (this.authActionUsedAt != null && this.authActionRevokedAt != null) {
            throw new IllegalArgumentException("authActionToken.usedAndRevoked");
        }
    }

    public UUID getAuthActionTokenId() {
        return authActionTokenId;
    }

    public User getUser() {
        return user;
    }

    private void setUser(User user) {
        validateUser(user);
        this.user = user;
    }

    public String getAuthActionTokenHash() {
        return authActionTokenHash;
    }

    private void setAuthActionTokenHash(String authActionTokenHash) {
        this.authActionTokenHash = normalizeHash(authActionTokenHash);
    }

    public AuthActionTokenType getAuthActionTokenType() {
        return authActionTokenType;
    }

    private void setAuthActionTokenType(AuthActionTokenType authActionTokenType) {
        validateType(authActionTokenType);
        this.authActionTokenType = authActionTokenType;
    }

    public OffsetDateTime getAuthActionExpiresAt() {
        return authActionExpiresAt;
    }

    private void setAuthActionExpiresAt(OffsetDateTime authActionExpiresAt) {
        validateExpiresAt(authActionExpiresAt);
        this.authActionExpiresAt = authActionExpiresAt;
    }

    public OffsetDateTime getAuthActionUsedAt() {
        return authActionUsedAt;
    }

    public OffsetDateTime getAuthActionRevokedAt() {
        return authActionRevokedAt;
    }

    public OffsetDateTime getAuthActionCreatedAt() {
        return authActionCreatedAt;
    }
}