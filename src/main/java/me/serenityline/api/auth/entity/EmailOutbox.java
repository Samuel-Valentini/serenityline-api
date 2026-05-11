package me.serenityline.api.auth.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import me.serenityline.api.user.entity.User;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "email_outbox")
public class EmailOutbox {

    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BYTES = 16;
    private static final int DEFAULT_MAX_ATTEMPTS = 6;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "email_outbox_id", nullable = false, updatable = false)
    private UUID emailOutboxId;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "recipient_email", nullable = false, length = 320)
    @NotBlank(message = "emailOutbox.recipientEmail.required")
    @Size(max = 320, message = "emailOutbox.recipientEmail.tooLong")
    @Email(message = "emailOutbox.recipientEmail.invalid")
    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_type", nullable = false, length = 50)
    @NotNull(message = "emailOutbox.type.required")
    private EmailOutboxType emailType;

    @Column(name = "encryption_key_id", nullable = false, length = 100)
    @NotBlank(message = "emailOutbox.encryptionKeyId.required")
    @Size(max = 100, message = "emailOutbox.encryptionKeyId.tooLong")
    private String encryptionKeyId;

    @Column(name = "subject_encrypted", nullable = false)
    @NotNull(message = "emailOutbox.subjectEncrypted.required")
    private byte[] subjectEncrypted;

    @Column(name = "subject_iv", nullable = false)
    @NotNull(message = "emailOutbox.subjectIv.required")
    private byte[] subjectIv;

    @Column(name = "subject_tag", nullable = false)
    @NotNull(message = "emailOutbox.subjectTag.required")
    private byte[] subjectTag;

    @Column(name = "body_html_encrypted")
    private byte[] bodyHtmlEncrypted;

    @Column(name = "body_html_iv")
    private byte[] bodyHtmlIv;

    @Column(name = "body_html_tag")
    private byte[] bodyHtmlTag;

    @Column(name = "body_text_encrypted")
    private byte[] bodyTextEncrypted;

    @Column(name = "body_text_iv")
    private byte[] bodyTextIv;

    @Column(name = "body_text_tag")
    private byte[] bodyTextTag;

    @Column(name = "delete_body_after_send", nullable = false)
    private boolean deleteBodyAfterSend;

    @Column(name = "email_body_deleted_at")
    private OffsetDateTime emailBodyDeletedAt;

    @Column(name = "provider", length = 100)
    @Size(max = 100, message = "emailOutbox.provider.tooLong")
    private String provider;

    @Column(name = "provider_message_id", length = 255)
    @Size(max = 255, message = "emailOutbox.providerMessageId.tooLong")
    private String providerMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_status", nullable = false, length = 30)
    @NotNull(message = "emailOutbox.status.required")
    private EmailOutboxStatus emailStatus;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "email_scheduled_at", nullable = false)
    @NotNull(message = "emailOutbox.scheduledAt.required")
    private OffsetDateTime emailScheduledAt;

    @Column(name = "email_sent_at")
    private OffsetDateTime emailSentAt;

    @Column(name = "email_last_failed_at")
    private OffsetDateTime emailLastFailedAt;

    @Column(name = "email_created_at", nullable = false, updatable = false)
    @NotNull(message = "emailOutbox.createdAt.required")
    private OffsetDateTime emailCreatedAt;

    @Column(name = "email_updated_at", nullable = false)
    @NotNull(message = "emailOutbox.updatedAt.required")
    private OffsetDateTime emailUpdatedAt;

    @Column(name = "email_cancelled_at")
    private OffsetDateTime emailCancelledAt;

    protected EmailOutbox() {
    }

    public EmailOutbox(
            User user,
            String recipientEmail,
            EmailOutboxType emailType,
            String encryptionKeyId,
            byte[] subjectEncrypted,
            byte[] subjectIv,
            byte[] subjectTag,
            byte[] bodyHtmlEncrypted,
            byte[] bodyHtmlIv,
            byte[] bodyHtmlTag,
            byte[] bodyTextEncrypted,
            byte[] bodyTextIv,
            byte[] bodyTextTag,
            boolean deleteBodyAfterSend,
            OffsetDateTime emailScheduledAt
    ) {
        OffsetDateTime now = OffsetDateTime.now();

        this.user = user;
        this.recipientEmail = normalizeRecipientEmail(recipientEmail);
        this.emailType = requireEmailType(emailType);
        this.encryptionKeyId = normalizeRequiredString(
                encryptionKeyId,
                100,
                "emailOutbox.encryptionKeyId.required",
                "emailOutbox.encryptionKeyId.tooLong"
        );

        this.subjectEncrypted = copyRequiredBytes(subjectEncrypted, "emailOutbox.subjectEncrypted.required");
        this.subjectIv = copyRequiredBytes(subjectIv, "emailOutbox.subjectIv.required");
        this.subjectTag = copyRequiredBytes(subjectTag, "emailOutbox.subjectTag.required");

        this.bodyHtmlEncrypted = copyNullableBytes(bodyHtmlEncrypted);
        this.bodyHtmlIv = copyNullableBytes(bodyHtmlIv);
        this.bodyHtmlTag = copyNullableBytes(bodyHtmlTag);

        this.bodyTextEncrypted = copyNullableBytes(bodyTextEncrypted);
        this.bodyTextIv = copyNullableBytes(bodyTextIv);
        this.bodyTextTag = copyNullableBytes(bodyTextTag);

        this.deleteBodyAfterSend = deleteBodyAfterSend;
        this.emailStatus = EmailOutboxStatus.PENDING;
        this.attempts = 0;
        this.maxAttempts = DEFAULT_MAX_ATTEMPTS;
        this.emailScheduledAt = emailScheduledAt != null ? emailScheduledAt : now;
        this.emailCreatedAt = now;
        this.emailUpdatedAt = now;

        validateState();
    }

    private static void validateOptionalBodyTriplet(
            byte[] encrypted,
            byte[] iv,
            byte[] tag,
            String incompleteKey,
            String ivLengthKey,
            String tagLengthKey,
            String encryptedEmptyKey
    ) {
        boolean allNull = encrypted == null && iv == null && tag == null;
        boolean allPresent = encrypted != null && iv != null && tag != null;

        if (!allNull && !allPresent) {
            throw new IllegalArgumentException(incompleteKey);
        }

        if (allPresent) {
            validateRequiredBytes(encrypted, encryptedEmptyKey);
            validateExactBytes(iv, GCM_IV_LENGTH_BYTES, ivLengthKey);
            validateExactBytes(tag, GCM_TAG_LENGTH_BYTES, tagLengthKey);
        }
    }

    private static String normalizeRecipientEmail(String recipientEmail) {
        if (recipientEmail == null) {
            throw new IllegalArgumentException("emailOutbox.recipientEmail.required");
        }

        String normalizedEmail = recipientEmail.trim().toLowerCase(Locale.ROOT);

        validateRecipientEmail(normalizedEmail);

        return normalizedEmail;
    }

    private static void validateRecipientEmail(String recipientEmail) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            throw new IllegalArgumentException("emailOutbox.recipientEmail.required");
        }

        if (!recipientEmail.equals(recipientEmail.trim().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("emailOutbox.recipientEmail.notNormalized");
        }

        if (recipientEmail.length() > 320) {
            throw new IllegalArgumentException("emailOutbox.recipientEmail.tooLong");
        }
    }

    private static EmailOutboxType requireEmailType(EmailOutboxType emailType) {
        if (emailType == null) {
            throw new IllegalArgumentException("emailOutbox.type.required");
        }

        return emailType;
    }

    private static String normalizeRequiredString(String value, int maxLength, String requiredKey, String tooLongKey) {
        if (value == null) {
            throw new IllegalArgumentException(requiredKey);
        }

        String normalizedValue = value.trim();
        validateRequiredString(normalizedValue, maxLength, requiredKey, tooLongKey);

        return normalizedValue;
    }

    private static void validateRequiredString(String value, int maxLength, String requiredKey, String tooLongKey) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(requiredKey);
        }

        if (value.length() > maxLength) {
            throw new IllegalArgumentException(tooLongKey);
        }
    }

    private static String normalizeNullableString(String value, int maxLength, String tooLongKey) {
        if (value == null) {
            return null;
        }

        String normalizedValue = value.trim();

        if (normalizedValue.isBlank()) {
            return null;
        }

        if (normalizedValue.length() > maxLength) {
            throw new IllegalArgumentException(tooLongKey);
        }

        return normalizedValue;
    }

    private static byte[] copyRequiredBytes(byte[] value, String requiredKey) {
        validateRequiredBytes(value, requiredKey);
        return Arrays.copyOf(value, value.length);
    }

    private static byte[] copyNullableBytes(byte[] value) {
        if (value == null) {
            return null;
        }

        return Arrays.copyOf(value, value.length);
    }

    private static void validateRequiredBytes(byte[] value, String requiredKey) {
        if (value == null || value.length == 0) {
            throw new IllegalArgumentException(requiredKey);
        }
    }

    private static void validateExactBytes(byte[] value, int expectedLength, String invalidLengthKey) {
        if (value == null || value.length != expectedLength) {
            throw new IllegalArgumentException(invalidLengthKey);
        }
    }

    private static void validateNextScheduledAt(OffsetDateTime nextScheduledAt, OffsetDateTime failedAt) {
        if (nextScheduledAt == null) {
            throw new IllegalArgumentException("emailOutbox.nextScheduledAt.requiredWhenRetrying");
        }

        if (!nextScheduledAt.isAfter(failedAt)) {
            throw new IllegalArgumentException("emailOutbox.nextScheduledAt.mustBeFuture");
        }
    }

    private static String normalizeNullableLastError(String lastError) {
        if (lastError == null) {
            return null;
        }

        String normalizedLastError = lastError.trim();

        if (normalizedLastError.isBlank()) {
            return null;
        }

        return normalizedLastError;
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();

        if (this.emailCreatedAt == null) {
            this.emailCreatedAt = now;
        }

        if (this.emailUpdatedAt == null) {
            this.emailUpdatedAt = this.emailCreatedAt;
        }

        if (this.emailScheduledAt == null) {
            this.emailScheduledAt = now;
        }

        if (this.emailStatus == null) {
            this.emailStatus = EmailOutboxStatus.PENDING;
        }

        if (this.maxAttempts == 0) {
            this.maxAttempts = DEFAULT_MAX_ATTEMPTS;
        }

        validateState();
    }

    @PreUpdate
    protected void onUpdate() {
        this.emailUpdatedAt = OffsetDateTime.now();
        validateState();
    }

    public void markSent(String provider, String providerMessageId) {
        ensureCanAttempt();

        String normalizedProvider = normalizeNullableString(
                provider,
                100,
                "emailOutbox.provider.tooLong"
        );

        String normalizedProviderMessageId = normalizeNullableString(
                providerMessageId,
                255,
                "emailOutbox.providerMessageId.tooLong"
        );

        OffsetDateTime sentAt = OffsetDateTime.now();

        this.attempts++;
        this.emailStatus = EmailOutboxStatus.SENT;
        this.emailSentAt = sentAt;
        this.provider = normalizedProvider;
        this.providerMessageId = normalizedProviderMessageId;

        if (this.deleteBodyAfterSend) {
            deleteBody();
        }

        validateState();
    }

    public void markFailed(String lastError, OffsetDateTime nextScheduledAt) {
        ensureCanAttempt();

        OffsetDateTime failedAt = OffsetDateTime.now();
        int nextAttempts = this.attempts + 1;
        boolean willRetry = nextAttempts < this.maxAttempts;

        if (willRetry) {
            validateNextScheduledAt(nextScheduledAt, failedAt);
        }

        this.attempts = nextAttempts;
        this.emailLastFailedAt = failedAt;
        this.lastError = normalizeNullableLastError(lastError);

        if (willRetry) {
            this.emailScheduledAt = nextScheduledAt;
        } else {
            this.emailStatus = EmailOutboxStatus.FAILED;
        }

        validateState();
    }

    public void cancel() {
        if (this.emailStatus == EmailOutboxStatus.CANCELLED) {
            return;
        }

        if (this.emailStatus == EmailOutboxStatus.SENT) {
            throw new IllegalStateException("emailOutbox.alreadySent");
        }

        if (this.emailStatus == EmailOutboxStatus.FAILED) {
            throw new IllegalStateException("emailOutbox.alreadyFailed");
        }

        this.emailStatus = EmailOutboxStatus.CANCELLED;
        this.emailCancelledAt = OffsetDateTime.now();

        validateState();
    }

    public boolean isPending() {
        return this.emailStatus == EmailOutboxStatus.PENDING;
    }

    public boolean isTerminal() {
        return this.emailStatus == EmailOutboxStatus.SENT
                || this.emailStatus == EmailOutboxStatus.FAILED
                || this.emailStatus == EmailOutboxStatus.CANCELLED;
    }

    public boolean canBeAttemptedAt(OffsetDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("emailOutbox.now.required");
        }

        return isPending()
                && hasRemainingAttempts()
                && !this.emailScheduledAt.isAfter(now);
    }

    private void deleteBody() {
        if (!isTerminal()) {
            throw new IllegalStateException("emailOutbox.bodyCannotBeDeletedBeforeTerminalStatus");
        }

        this.bodyHtmlEncrypted = null;
        this.bodyHtmlIv = null;
        this.bodyHtmlTag = null;
        this.bodyTextEncrypted = null;
        this.bodyTextIv = null;
        this.bodyTextTag = null;
        this.emailBodyDeletedAt = OffsetDateTime.now();
    }

    private void validateState() {
        validateRecipientEmail(this.recipientEmail);
        validateRequiredString(this.encryptionKeyId, 100, "emailOutbox.encryptionKeyId.required", "emailOutbox.encryptionKeyId.tooLong");

        if (this.emailType == null) {
            throw new IllegalArgumentException("emailOutbox.type.required");
        }

        if (this.emailStatus == null) {
            throw new IllegalArgumentException("emailOutbox.status.required");
        }

        validateRequiredBytes(this.subjectEncrypted, "emailOutbox.subjectEncrypted.required");
        validateExactBytes(this.subjectIv, GCM_IV_LENGTH_BYTES, "emailOutbox.subjectIv.invalidLength");
        validateExactBytes(this.subjectTag, GCM_TAG_LENGTH_BYTES, "emailOutbox.subjectTag.invalidLength");

        validateOptionalBodyTriplet(
                this.bodyHtmlEncrypted,
                this.bodyHtmlIv,
                this.bodyHtmlTag,
                "emailOutbox.htmlBody.incomplete",
                "emailOutbox.htmlIv.invalidLength",
                "emailOutbox.htmlTag.invalidLength",
                "emailOutbox.htmlEncrypted.empty"
        );

        validateOptionalBodyTriplet(
                this.bodyTextEncrypted,
                this.bodyTextIv,
                this.bodyTextTag,
                "emailOutbox.textBody.incomplete",
                "emailOutbox.textIv.invalidLength",
                "emailOutbox.textTag.invalidLength",
                "emailOutbox.textEncrypted.empty"
        );

        if (!hasHtmlBody() && !hasTextBody()) {
            validateDeletedBodyAllowed();
        }

        if (this.emailBodyDeletedAt != null && (hasHtmlBody() || hasTextBody())) {
            throw new IllegalArgumentException("emailOutbox.bodyDeletedWithBodyStillPresent");
        }

        if (this.attempts < 0) {
            throw new IllegalArgumentException("emailOutbox.attempts.negative");
        }

        if (this.maxAttempts <= 0) {
            throw new IllegalArgumentException("emailOutbox.maxAttempts.notPositive");
        }

        if (this.attempts > this.maxAttempts) {
            throw new IllegalArgumentException("emailOutbox.attempts.greaterThanMax");
        }

        validateTimestamps();
        validateStatusConsistency();
    }

    private void validateStatusConsistency() {
        if (this.emailStatus == EmailOutboxStatus.SENT && this.emailSentAt == null) {
            throw new IllegalArgumentException("emailOutbox.sentAt.requiredWhenSent");
        }

        if (this.emailSentAt != null && this.emailStatus != EmailOutboxStatus.SENT) {
            throw new IllegalArgumentException("emailOutbox.sentAt.onlyWhenSent");
        }

        if (this.emailStatus == EmailOutboxStatus.FAILED) {
            if (this.emailLastFailedAt == null) {
                throw new IllegalArgumentException("emailOutbox.lastFailedAt.requiredWhenFailed");
            }

            if (this.attempts < this.maxAttempts) {
                throw new IllegalArgumentException("emailOutbox.failedRequiresMaxAttempts");
            }
        }

        if (this.emailStatus == EmailOutboxStatus.CANCELLED && this.emailCancelledAt == null) {
            throw new IllegalArgumentException("emailOutbox.cancelledAt.requiredWhenCancelled");
        }

        if (this.emailCancelledAt != null && this.emailStatus != EmailOutboxStatus.CANCELLED) {
            throw new IllegalArgumentException("emailOutbox.cancelledAt.onlyWhenCancelled");
        }
    }

    private void validateTimestamps() {
        if (this.emailScheduledAt == null) {
            throw new IllegalArgumentException("emailOutbox.scheduledAt.required");
        }

        if (this.emailCreatedAt == null) {
            throw new IllegalArgumentException("emailOutbox.createdAt.required");
        }

        if (this.emailUpdatedAt == null) {
            throw new IllegalArgumentException("emailOutbox.updatedAt.required");
        }

        if (this.emailUpdatedAt.isBefore(this.emailCreatedAt)) {
            throw new IllegalArgumentException("emailOutbox.updatedAt.beforeCreatedAt");
        }

        if (this.emailSentAt != null && this.emailSentAt.isBefore(this.emailCreatedAt)) {
            throw new IllegalArgumentException("emailOutbox.sentAt.beforeCreatedAt");
        }

        if (this.emailLastFailedAt != null && this.emailLastFailedAt.isBefore(this.emailCreatedAt)) {
            throw new IllegalArgumentException("emailOutbox.lastFailedAt.beforeCreatedAt");
        }

        if (this.emailCancelledAt != null && this.emailCancelledAt.isBefore(this.emailCreatedAt)) {
            throw new IllegalArgumentException("emailOutbox.cancelledAt.beforeCreatedAt");
        }

        if (this.emailBodyDeletedAt != null && this.emailBodyDeletedAt.isBefore(this.emailCreatedAt)) {
            throw new IllegalArgumentException("emailOutbox.bodyDeletedAt.beforeCreatedAt");
        }
    }

    private void validateDeletedBodyAllowed() {
        if (this.emailBodyDeletedAt == null) {
            throw new IllegalArgumentException("emailOutbox.body.required");
        }

        boolean validSentDeletion = this.emailStatus == EmailOutboxStatus.SENT && this.emailSentAt != null;
        boolean validFailedDeletion = this.emailStatus == EmailOutboxStatus.FAILED
                && this.emailLastFailedAt != null
                && this.attempts >= this.maxAttempts;
        boolean validCancelledDeletion = this.emailStatus == EmailOutboxStatus.CANCELLED && this.emailCancelledAt != null;

        if (!validSentDeletion && !validFailedDeletion && !validCancelledDeletion) {
            throw new IllegalArgumentException("emailOutbox.bodyDeletedOnlyAfterTerminalStatus");
        }
    }

    private boolean hasHtmlBody() {
        return this.bodyHtmlEncrypted != null;
    }

    private boolean hasTextBody() {
        return this.bodyTextEncrypted != null;
    }

    public UUID getEmailOutboxId() {
        return emailOutboxId;
    }

    public User getUser() {
        return user;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public EmailOutboxType getEmailType() {
        return emailType;
    }

    public String getEncryptionKeyId() {
        return encryptionKeyId;
    }

    public byte[] getSubjectEncrypted() {
        return copyNullableBytes(subjectEncrypted);
    }

    public byte[] getSubjectIv() {
        return copyNullableBytes(subjectIv);
    }

    public byte[] getSubjectTag() {
        return copyNullableBytes(subjectTag);
    }

    public byte[] getBodyHtmlEncrypted() {
        return copyNullableBytes(bodyHtmlEncrypted);
    }

    public byte[] getBodyHtmlIv() {
        return copyNullableBytes(bodyHtmlIv);
    }

    public byte[] getBodyHtmlTag() {
        return copyNullableBytes(bodyHtmlTag);
    }

    public byte[] getBodyTextEncrypted() {
        return copyNullableBytes(bodyTextEncrypted);
    }

    public byte[] getBodyTextIv() {
        return copyNullableBytes(bodyTextIv);
    }

    public byte[] getBodyTextTag() {
        return copyNullableBytes(bodyTextTag);
    }

    public boolean isDeleteBodyAfterSend() {
        return deleteBodyAfterSend;
    }

    public OffsetDateTime getEmailBodyDeletedAt() {
        return emailBodyDeletedAt;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public EmailOutboxStatus getEmailStatus() {
        return emailStatus;
    }

    public int getAttempts() {
        return attempts;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public String getLastError() {
        return lastError;
    }

    public OffsetDateTime getEmailScheduledAt() {
        return emailScheduledAt;
    }

    public OffsetDateTime getEmailSentAt() {
        return emailSentAt;
    }

    public OffsetDateTime getEmailLastFailedAt() {
        return emailLastFailedAt;
    }

    public OffsetDateTime getEmailCreatedAt() {
        return emailCreatedAt;
    }

    public OffsetDateTime getEmailUpdatedAt() {
        return emailUpdatedAt;
    }

    public OffsetDateTime getEmailCancelledAt() {
        return emailCancelledAt;
    }

    private void ensureCanAttempt() {
        if (!isPending()) {
            throw new IllegalStateException("emailOutbox.notPending");
        }

        if (!hasRemainingAttempts()) {
            throw new IllegalStateException("emailOutbox.maxAttemptsReached");
        }
    }

    private boolean hasRemainingAttempts() {
        return this.attempts < this.maxAttempts;
    }


}