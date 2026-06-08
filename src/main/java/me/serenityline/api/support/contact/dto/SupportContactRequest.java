package me.serenityline.api.support.contact.dto;

import jakarta.validation.constraints.*;

public record SupportContactRequest(
        @Size(max = 120, message = "support.contact.name.tooLong")
        String name,

        @Email(message = "support.contact.email.invalid")
        @Size(max = 320, message = "support.contact.email.tooLong")
        String email,

        @NotNull(message = "support.contact.topic.required")
        SupportContactTopic topic,

        @NotBlank(message = "support.contact.subject.required")
        @Size(max = 160, message = "support.contact.subject.tooLong")
        String subject,

        @NotBlank(message = "support.contact.message.required")
        @Size(max = 8000, message = "support.contact.message.tooLong")
        String message,

        @AssertTrue(message = "support.contact.privacyAcceptance.required")
        boolean privacyAccepted,

        @Size(max = 255, message = "support.contact.honeypot.tooLong")
        String website //honeypot
) {
}