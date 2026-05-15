package me.serenityline.api.email.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest(
        properties = {
                "serenityline.email.provider=resend"
        }
)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "RESEND_API_KEY", matches = ".+")
class ResendEmailSenderIntegrationTest {

    private static final String TEST_RECIPIENT = "test@serenityline.me";

    @Autowired
    private EmailSender emailSender;

    @Test
    void shouldSendRealEmailWithResend() {
        SentEmail sentEmail = emailSender.send(
                new OutboundEmail(
                        TEST_RECIPIENT,
                        "SerenityLine Resend integration test",
                        "Test invio email SerenityLine tramite Resend.",
                        "<p>Test invio email <strong>SerenityLine</strong> tramite Resend.</p>"
                )
        );

        assertThat(sentEmail.providerMessageId()).isNotBlank();
    }
}