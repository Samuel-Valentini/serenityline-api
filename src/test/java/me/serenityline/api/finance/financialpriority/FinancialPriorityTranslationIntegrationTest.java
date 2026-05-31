package me.serenityline.api.finance.financialpriority;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class FinancialPriorityTranslationIntegrationTest {

    private static final List<String> CODES = List.of(
            "CRITICAL",
            "ESSENTIAL",
            "OPTIONAL",
            "LEISURE_WELLBEING",
            "UNCLASSIFIED"
    );

    private static final List<String> SUFFIXES = List.of(
            "displayName",
            "description"
    );

    private static final List<Locale> SUPPORTED_LOCALES = List.of(
            Locale.ITALIAN,
            Locale.ENGLISH
    );

    private ResourceBundleMessageSource messageSource;

    @BeforeEach
    void setUp() {
        messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setUseCodeAsDefaultMessage(false);
        messageSource.setFallbackToSystemLocale(false);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "CRITICAL",
            "ESSENTIAL",
            "OPTIONAL",
            "LEISURE_WELLBEING",
            "UNCLASSIFIED"
    })
    void financialPriorityTranslationsShouldExistForEverySupportedLocale(String code) {
        for (Locale locale : SUPPORTED_LOCALES) {
            for (String suffix : SUFFIXES) {
                String key = "finance.financialPriority." + code + "." + suffix;

                assertThatNoException()
                        .as("Missing translation for key <%s> and locale <%s>", key, locale)
                        .isThrownBy(() -> messageSource.getMessage(key, null, locale));
            }
        }
    }

    @Test
    void italianDisplayNamesShouldBeCorrect() {
        assertThat(message("finance.financialPriority.CRITICAL.displayName", Locale.ITALIAN))
                .isEqualTo("Prioritario");

        assertThat(message("finance.financialPriority.ESSENTIAL.displayName", Locale.ITALIAN))
                .isEqualTo("Essenziale");

        assertThat(message("finance.financialPriority.OPTIONAL.displayName", Locale.ITALIAN))
                .isEqualTo("Accessorio");

        assertThat(message("finance.financialPriority.LEISURE_WELLBEING.displayName", Locale.ITALIAN))
                .isEqualTo("Svago e benessere");

        assertThat(message("finance.financialPriority.UNCLASSIFIED.displayName", Locale.ITALIAN))
                .isEqualTo("Non classificabile");
    }

    @Test
    void englishDisplayNamesShouldBeCorrect() {
        assertThat(message("finance.financialPriority.CRITICAL.displayName", Locale.ENGLISH))
                .isEqualTo("Critical");

        assertThat(message("finance.financialPriority.ESSENTIAL.displayName", Locale.ENGLISH))
                .isEqualTo("Essential");

        assertThat(message("finance.financialPriority.OPTIONAL.displayName", Locale.ENGLISH))
                .isEqualTo("Optional");

        assertThat(message("finance.financialPriority.LEISURE_WELLBEING.displayName", Locale.ENGLISH))
                .isEqualTo("Leisure & Wellbeing");

        assertThat(message("finance.financialPriority.UNCLASSIFIED.displayName", Locale.ENGLISH))
                .isEqualTo("Unclassified");
    }

    @Test
    void descriptionsShouldBePresentAndNotFallbackToKeys() {
        for (Locale locale : SUPPORTED_LOCALES) {
            for (String code : CODES) {
                String key = "finance.financialPriority." + code + ".description";
                String description = message(key, locale);

                assertThat(description)
                        .as("Description should not be blank for key <%s> and locale <%s>", key, locale)
                        .isNotBlank();

                assertThat(description)
                        .as("Description should not fallback to key <%s>", key)
                        .isNotEqualTo(key);
            }
        }
    }

    @Test
    void missingTranslationShouldThrowNoSuchMessageException() {
        assertThatNoException()
                .isThrownBy(() -> message("finance.financialPriority.CRITICAL.displayName", Locale.ITALIAN));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        messageSource.getMessage("finance.financialPriority.DOES_NOT_EXIST.displayName", null, Locale.ITALIAN)
                )
                .isInstanceOf(NoSuchMessageException.class);
    }

    private String message(String key, Locale locale) {
        return messageSource.getMessage(key, null, locale);
    }
}