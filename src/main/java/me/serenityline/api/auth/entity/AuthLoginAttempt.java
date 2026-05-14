package me.serenityline.api.auth.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import me.serenityline.api.user.entity.User;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "auth_login_attempts")
public class AuthLoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "auth_login_attempt_id", nullable = false, updatable = false)
    private UUID authLoginAttemptId;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "user_id", updatable = false)
    private User user;

    @Column(name = "email_hash", nullable = false, length = 255, updatable = false)
    @NotBlank(message = "authLoginAttempt.emailHash.required")
    @Size(max = 255, message = "authLoginAttempt.emailHash.tooLong")
    private String emailHash;

    @Column(name = "ip_address_hash", nullable = false, length = 255, updatable = false)
    @NotBlank(message = "authLoginAttempt.ipAddressHash.required")
    @Size(max = 255, message = "authLoginAttempt.ipAddressHash.tooLong")
    private String ipAddressHash;

    @Column(name = "login_successful", nullable = false, updatable = false)
    private boolean loginSuccessful;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 50, updatable = false)
    private AuthLoginFailureReason failureReason;

    @Column(name = "auth_login_attempt_at", nullable = false, updatable = false)
    @NotNull(message = "authLoginAttempt.at.required")
    private OffsetDateTime authLoginAttemptAt;

    protected AuthLoginAttempt() {
    }

    private AuthLoginAttempt(
            User user,
            String emailHash,
            String ipAddressHash,
            boolean loginSuccessful,
            AuthLoginFailureReason failureReason
    ) {
        this.user = user;
        this.emailHash = normalizeHash(emailHash, "authLoginAttempt.emailHash.required", "authLoginAttempt.emailHash.tooLong");
        this.ipAddressHash = normalizeHash(ipAddressHash, "authLoginAttempt.ipAddressHash.required", "authLoginAttempt.ipAddressHash.tooLong");
        this.loginSuccessful = loginSuccessful;
        this.failureReason = failureReason;
        this.authLoginAttemptAt = OffsetDateTime.now();

        validateState();
    }

    public static AuthLoginAttempt successful(User user, String emailHash, String ipAddressHash) {

        if (user == null) {
            throw new IllegalArgumentException("authLoginAttempt.user.requiredWhenSuccessful");
        }

        return new AuthLoginAttempt(
                user,
                emailHash,
                ipAddressHash,
                true,
                null
        );
    }

    public static AuthLoginAttempt failed(
            User user,
            String emailHash,
            String ipAddressHash,
            AuthLoginFailureReason failureReason
    ) {
        if (failureReason == null) {
            throw new IllegalArgumentException("authLoginAttempt.failureReason.requiredWhenFailed");
        }

        return new AuthLoginAttempt(
                user,
                emailHash,
                ipAddressHash,
                false,
                failureReason
        );
    }

    private static String normalizeHash(String hash, String requiredKey, String tooLongKey) {
        if (hash == null) {
            throw new IllegalArgumentException(requiredKey);
        }

        String normalizedHash = hash.trim();

        if (normalizedHash.isBlank()) {
            throw new IllegalArgumentException(requiredKey);
        }

        if (normalizedHash.length() > 255) {
            throw new IllegalArgumentException(tooLongKey);
        }

        return normalizedHash;
    }

    private static void validateHash(String hash, String requiredKey, String tooLongKey) {
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException(requiredKey);
        }

        if (hash.length() > 255) {
            throw new IllegalArgumentException(tooLongKey);
        }
    }

    @PrePersist
    protected void onCreate() {
        if (this.authLoginAttemptAt == null) {
            this.authLoginAttemptAt = OffsetDateTime.now();
        }

        validateState();
    }

    @PreUpdate
    protected void onUpdate() {
        validateState();
    }

    private void validateState() {
        validateHash(this.emailHash, "authLoginAttempt.emailHash.required", "authLoginAttempt.emailHash.tooLong");
        validateHash(this.ipAddressHash, "authLoginAttempt.ipAddressHash.required", "authLoginAttempt.ipAddressHash.tooLong");

        if (this.authLoginAttemptAt == null) {
            throw new IllegalArgumentException("authLoginAttempt.at.required");
        }

        if (this.loginSuccessful && this.failureReason != null) {
            throw new IllegalArgumentException("authLoginAttempt.failureReason.mustBeNullWhenSuccessful");
        }

        if (!this.loginSuccessful && this.failureReason == null) {
            throw new IllegalArgumentException("authLoginAttempt.failureReason.requiredWhenFailed");
        }
    }

    public UUID getAuthLoginAttemptId() {
        return authLoginAttemptId;
    }

    public User getUser() {
        return user;
    }

    public String getEmailHash() {
        return emailHash;
    }

    public String getIpAddressHash() {
        return ipAddressHash;
    }

    public boolean isLoginSuccessful() {
        return loginSuccessful;
    }

    public AuthLoginFailureReason getFailureReason() {
        return failureReason;
    }

    public OffsetDateTime getAuthLoginAttemptAt() {
        return authLoginAttemptAt;
    }
}