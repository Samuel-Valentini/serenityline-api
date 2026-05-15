package me.serenityline.api.email.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        prefix = "serenityline.email",
        name = "provider",
        havingValue = "resend"
)
public class ResendEmailSender implements EmailSender {

    private final Resend resend;
    private final String from;

    public ResendEmailSender(
            @Value("${serenityline.email.resend.api-key}") String apiKey,
            @Value("${serenityline.email.from}") String from
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("email.resend.apiKey.required");
        }

        if (from == null || from.isBlank()) {
            throw new IllegalStateException("email.from.required");
        }

        this.resend = new Resend(apiKey.trim());
        this.from = from.trim();
    }

    @Override
    public String provider() {
        return "resend";
    }

    @Override
    public SentEmail send(OutboundEmail email) {
        CreateEmailOptions.Builder builder = CreateEmailOptions.builder()
                .from(from)
                .to(email.to())
                .subject(email.subject());

        if (email.html() != null && !email.html().isBlank()) {
            builder.html(email.html());
        }

        if (email.text() != null && !email.text().isBlank()) {
            builder.text(email.text());
        }

        try {
            CreateEmailResponse response = resend.emails().send(builder.build());

            return new SentEmail(response.getId());
        } catch (ResendException ex) {
            throw new EmailSendException("email.provider.resend.sendFailed", ex);
        }
    }
}