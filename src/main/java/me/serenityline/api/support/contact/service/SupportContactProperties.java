package me.serenityline.api.support.contact.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@ConfigurationProperties(prefix = "serenityline.support.contact")
public class SupportContactProperties {

    private String recipientEmail;

    public String getRecipientEmail() {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            throw new IllegalStateException("support.contact.recipientEmail.required");
        }

        return recipientEmail.trim().toLowerCase(Locale.ROOT);
    }

    public void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail;
    }
}