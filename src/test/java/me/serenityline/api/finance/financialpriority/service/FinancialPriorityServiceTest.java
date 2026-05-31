package me.serenityline.api.finance.financialpriority.service;

import me.serenityline.api.finance.financialpriority.dto.FinancialPriorityResponse;
import me.serenityline.api.finance.financialpriority.entity.FinancialPriority;
import me.serenityline.api.finance.financialpriority.entity.FinancialPriorityName;
import me.serenityline.api.finance.financialpriority.repository.FinancialPriorityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinancialPriorityServiceTest {

    private static final Locale IT = Locale.forLanguageTag("it-IT");
    private static final Locale EN = Locale.forLanguageTag("en-US");

    @Mock
    private FinancialPriorityRepository financialPriorityRepository;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private FinancialPriorityService financialPriorityService;


    private static FinancialPriority priorityWithNameOnly(FinancialPriorityName name) {
        FinancialPriority financialPriority = mock(FinancialPriority.class);

        given(financialPriority.getFinancialPriorityName()).willReturn(name);

        return financialPriority;
    }

    private static FinancialPriority priority(
            UUID id,
            FinancialPriorityName name,
            short ranking
    ) {
        FinancialPriority financialPriority = mock(FinancialPriority.class);

        given(financialPriority.getFinancialPriorityId()).willReturn(id);
        given(financialPriority.getFinancialPriorityName()).willReturn(name);
        given(financialPriority.getFinancialPriorityRanking()).willReturn(ranking);

        return financialPriority;
    }

    @Test
    void findFinancialPrioritiesShouldReturnLocalizedResponsesInRepositoryOrder() {
        UUID criticalId = UUID.randomUUID();
        UUID essentialId = UUID.randomUUID();
        UUID unclassifiedId = UUID.randomUUID();

        FinancialPriority critical = priority(
                criticalId,
                FinancialPriorityName.CRITICAL,
                (short) 80
        );

        FinancialPriority essential = priority(
                essentialId,
                FinancialPriorityName.ESSENTIAL,
                (short) 60
        );

        FinancialPriority unclassified = priority(
                unclassifiedId,
                FinancialPriorityName.UNCLASSIFIED,
                (short) 0
        );

        given(financialPriorityRepository.findAllByOrderByFinancialPriorityRankingDesc())
                .willReturn(List.of(critical, essential, unclassified));

        givenMessage("CRITICAL", IT, "Prioritario", "Descrizione prioritaria");
        givenMessage("ESSENTIAL", IT, "Essenziale", "Descrizione essenziale");
        givenMessage("UNCLASSIFIED", IT, "Non classificabile", "Descrizione non classificabile");

        List<FinancialPriorityResponse> response = financialPriorityService.findFinancialPriorities(IT);

        assertThat(response).hasSize(3);

        assertThat(response.get(0).financialPriorityId()).isEqualTo(criticalId);
        assertThat(response.get(0).financialPriorityCode()).isEqualTo("CRITICAL");
        assertThat(response.get(0).financialPriorityDisplayName()).isEqualTo("Prioritario");
        assertThat(response.get(0).financialPriorityDescription()).isEqualTo("Descrizione prioritaria");
        assertThat(response.get(0).financialPriorityRanking()).isEqualTo((short) 80);

        assertThat(response.get(1).financialPriorityId()).isEqualTo(essentialId);
        assertThat(response.get(1).financialPriorityCode()).isEqualTo("ESSENTIAL");
        assertThat(response.get(1).financialPriorityDisplayName()).isEqualTo("Essenziale");
        assertThat(response.get(1).financialPriorityDescription()).isEqualTo("Descrizione essenziale");
        assertThat(response.get(1).financialPriorityRanking()).isEqualTo((short) 60);

        assertThat(response.get(2).financialPriorityId()).isEqualTo(unclassifiedId);
        assertThat(response.get(2).financialPriorityCode()).isEqualTo("UNCLASSIFIED");
        assertThat(response.get(2).financialPriorityDisplayName()).isEqualTo("Non classificabile");
        assertThat(response.get(2).financialPriorityDescription()).isEqualTo("Descrizione non classificabile");
        assertThat(response.get(2).financialPriorityRanking()).isEqualTo((short) 0);
    }

    @Test
    void findFinancialPrioritiesShouldUseRequestedLocale() {
        UUID criticalId = UUID.randomUUID();

        FinancialPriority critical = priority(
                criticalId,
                FinancialPriorityName.CRITICAL,
                (short) 80
        );

        given(financialPriorityRepository.findAllByOrderByFinancialPriorityRankingDesc())
                .willReturn(List.of(critical));

        givenMessage("CRITICAL", EN, "Critical", "English critical description");

        List<FinancialPriorityResponse> response = financialPriorityService.findFinancialPriorities(EN);

        assertThat(response).singleElement().satisfies(priority -> {
            assertThat(priority.financialPriorityCode()).isEqualTo("CRITICAL");
            assertThat(priority.financialPriorityDisplayName()).isEqualTo("Critical");
            assertThat(priority.financialPriorityDescription()).isEqualTo("English critical description");
            assertThat(priority.financialPriorityRanking()).isEqualTo((short) 80);
        });

        verify(messageSource).getMessage(
                eq("finance.financialPriority.CRITICAL.displayName"),
                isNull(),
                eq(EN)
        );

        verify(messageSource).getMessage(
                eq("finance.financialPriority.CRITICAL.description"),
                isNull(),
                eq(EN)
        );
    }

    @Test
    void findFinancialPrioritiesShouldReturnEmptyListWhenRepositoryReturnsNoPriorities() {
        given(financialPriorityRepository.findAllByOrderByFinancialPriorityRankingDesc())
                .willReturn(List.of());

        List<FinancialPriorityResponse> response = financialPriorityService.findFinancialPriorities(IT);

        assertThat(response).isEmpty();
        verifyNoInteractions(messageSource);
    }

    @Test
    void findFinancialPrioritiesShouldFailFastWhenDisplayNameTranslationIsMissing() {
        FinancialPriority critical = priorityWithNameOnly(FinancialPriorityName.CRITICAL);

        given(financialPriorityRepository.findAllByOrderByFinancialPriorityRankingDesc())
                .willReturn(List.of(critical));

        given(messageSource.getMessage(
                eq("finance.financialPriority.CRITICAL.displayName"),
                isNull(),
                eq(IT)
        )).willThrow(new NoSuchMessageException("finance.financialPriority.CRITICAL.displayName"));

        assertThatThrownBy(() -> financialPriorityService.findFinancialPriorities(IT))
                .isInstanceOf(NoSuchMessageException.class);
    }

    @Test
    void findFinancialPrioritiesShouldFailFastWhenDescriptionTranslationIsMissing() {
        FinancialPriority critical = priorityWithNameOnly(FinancialPriorityName.CRITICAL);

        given(financialPriorityRepository.findAllByOrderByFinancialPriorityRankingDesc())
                .willReturn(List.of(critical));

        given(messageSource.getMessage(
                eq("finance.financialPriority.CRITICAL.displayName"),
                isNull(),
                eq(IT)
        )).willReturn("Prioritario");

        given(messageSource.getMessage(
                eq("finance.financialPriority.CRITICAL.description"),
                isNull(),
                eq(IT)
        )).willThrow(new NoSuchMessageException("finance.financialPriority.CRITICAL.description"));

        assertThatThrownBy(() -> financialPriorityService.findFinancialPriorities(IT))
                .isInstanceOf(NoSuchMessageException.class);
    }

    private void givenMessage(
            String code,
            Locale locale,
            String displayName,
            String description
    ) {
        given(messageSource.getMessage(
                eq("finance.financialPriority." + code + ".displayName"),
                isNull(),
                eq(locale)
        )).willReturn(displayName);

        given(messageSource.getMessage(
                eq("finance.financialPriority." + code + ".description"),
                isNull(),
                eq(locale)
        )).willReturn(description);
    }


}