package me.serenityline.api.auth.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import me.serenityline.api.user.entity.User;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_sessions")
public class UserSession {

    private static final int HASH_MAX_LENGTH = 255;
    private static final int USER_AGENT_MAX_LENGTH = 2000;
    private static final int DEVICE_LABEL_MAX_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_session_id", nullable = false)
    private UUID userSessionId;

    @NotNull(message = "{userSession.user.required}")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "session_created_at", nullable = false, updatable = false)
    private OffsetDateTime sessionCreatedAt;

    @Column(name = "session_last_seen_at", nullable = false)
    private OffsetDateTime sessionLastSeenAt;

    @NotNull(message = "{userSession.expiresAt.required}")
    @Column(name = "session_expires_at", nullable = false)
    private OffsetDateTime sessionExpiresAt;

    @Column(name = "session_revoked_at")
    private OffsetDateTime sessionRevokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_revoke_reason", length = 50)
    private SessionRevokeReason sessionRevokeReason;

    @Column(name = "ip_address_hash", length = 255)
    private String ipAddressHash;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    @Column(name = "device_label", length = 255)
    private String deviceLabel;

    protected UserSession() {
    }

    public UserSession(
            User user,
            OffsetDateTime sessionExpiresAt,
            String ipAddressHash,
            String userAgent,
            String deviceLabel
    ) {
        setUser(user);
        setSessionExpiresAt(sessionExpiresAt);
        setIpAddressHash(ipAddressHash);
        setUserAgent(userAgent);
        setDeviceLabel(deviceLabel);
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }

        return value.trim();
    }

    private static String validateOptionalText(
            String value,
            int maxLength,
            String blankMessageKey,
            String tooLongMessageKey
    ) {
        String normalized = normalizeText(value);

        if (normalized == null) {
            return null;
        }

        if (normalized.isBlank()) {
            throw validationError(blankMessageKey);
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
        OffsetDateTime now = OffsetDateTime.now();

        if (this.sessionCreatedAt == null) {
            this.sessionCreatedAt = now;
        }

        if (this.sessionLastSeenAt == null) {
            this.sessionLastSeenAt = now;
        }

        validateForPersistence();
    }

    @PreUpdate
    protected void onUpdate() {
        validateForPersistence();
    }

    private void validateForPersistence() {
        this.user = requireNotNull(this.user, "userSession.user.required");
        this.sessionExpiresAt = requireNotNull(this.sessionExpiresAt, "userSession.expiresAt.required");

        this.ipAddressHash = validateOptionalText(
                this.ipAddressHash,
                HASH_MAX_LENGTH,
                "userSession.ipAddressHash.blank",
                "userSession.ipAddressHash.tooLong"
        );

        this.userAgent = validateOptionalText(
                this.userAgent,
                USER_AGENT_MAX_LENGTH,
                "userSession.userAgent.blank",
                "userSession.userAgent.tooLong"
        );

        this.deviceLabel = validateOptionalText(
                this.deviceLabel,
                DEVICE_LABEL_MAX_LENGTH,
                "userSession.deviceLabel.blank",
                "userSession.deviceLabel.tooLong"
        );

        validateRevocationConsistency();
        validateDates();
    }

    private void validateRevocationConsistency() {
        boolean hasRevokedAt = this.sessionRevokedAt != null;
        boolean hasReason = this.sessionRevokeReason != null;

        if (hasRevokedAt != hasReason) {
            throw validationError("userSession.revocation.inconsistent");
        }
    }

    private void validateDates() {
        if (sessionCreatedAt != null && sessionExpiresAt != null && !sessionExpiresAt.isAfter(sessionCreatedAt)) {
            throw validationError("userSession.expiresAt.beforeOrEqualCreatedAt");
        }

        if (sessionCreatedAt != null && sessionLastSeenAt != null && sessionLastSeenAt.isBefore(sessionCreatedAt)) {
            throw validationError("userSession.lastSeenAt.beforeCreatedAt");
        }

        if (sessionCreatedAt != null && sessionRevokedAt != null && sessionRevokedAt.isBefore(sessionCreatedAt)) {
            throw validationError("userSession.revokedAt.beforeCreatedAt");
        }
    }

    public UUID getUserSessionId() {
        return userSessionId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = requireNotNull(user, "userSession.user.required");
    }

    public OffsetDateTime getSessionCreatedAt() {
        return sessionCreatedAt;
    }

    public OffsetDateTime getSessionLastSeenAt() {
        return sessionLastSeenAt;
    }

    public OffsetDateTime getSessionExpiresAt() {
        return sessionExpiresAt;
    }

    public void setSessionExpiresAt(OffsetDateTime sessionExpiresAt) {
        this.sessionExpiresAt = requireNotNull(sessionExpiresAt, "userSession.expiresAt.required");
    }

    public OffsetDateTime getSessionRevokedAt() {
        return sessionRevokedAt;
    }

    public SessionRevokeReason getSessionRevokeReason() {
        return sessionRevokeReason;
    }

    public String getIpAddressHash() {
        return ipAddressHash;
    }

    public void setIpAddressHash(String ipAddressHash) {
        this.ipAddressHash = validateOptionalText(
                ipAddressHash,
                HASH_MAX_LENGTH,
                "userSession.ipAddressHash.blank",
                "userSession.ipAddressHash.tooLong"
        );
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = validateOptionalText(
                userAgent,
                USER_AGENT_MAX_LENGTH,
                "userSession.userAgent.blank",
                "userSession.userAgent.tooLong"
        );
    }

    public String getDeviceLabel() {
        return deviceLabel;
    }

    public void setDeviceLabel(String deviceLabel) {
        this.deviceLabel = validateOptionalText(
                deviceLabel,
                DEVICE_LABEL_MAX_LENGTH,
                "userSession.deviceLabel.blank",
                "userSession.deviceLabel.tooLong"
        );
    }

    public void markSeen() {
        this.sessionLastSeenAt = OffsetDateTime.now();
    }

    public void revoke(SessionRevokeReason reason) {
        if (this.sessionRevokedAt != null) {
            throw validationError("userSession.alreadyRevoked");
        }

        this.sessionRevokeReason = requireNotNull(reason, "userSession.revokeReason.required");
        this.sessionRevokedAt = OffsetDateTime.now();
    }

    public boolean isRevoked() {
        return this.sessionRevokedAt != null;
    }

    public boolean isExpired() {
        return !this.sessionExpiresAt.isAfter(OffsetDateTime.now());
    }

    public boolean isActive() {
        return !isRevoked() && !isExpired();
    }
}