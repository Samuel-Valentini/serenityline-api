package me.serenityline.api.finance.financialpriority.repository;

import me.serenityline.api.finance.financialpriority.entity.FinancialPriority;
import me.serenityline.api.finance.financialpriority.entity.FinancialPriorityName;
import me.serenityline.api.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialPriorityRepositoryIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private FinancialPriorityRepository financialPriorityRepository;

    @Test
    void shouldLoadSeededFinancialPriorities() {
        List<FinancialPriority> priorities = financialPriorityRepository.findAll();

        assertThat(priorities).hasSize(5);

        assertThat(priorities)
                .extracting(FinancialPriority::getFinancialPriorityName)
                .containsExactlyInAnyOrder(
                        FinancialPriorityName.CRITICAL,
                        FinancialPriorityName.ESSENTIAL,
                        FinancialPriorityName.OPTIONAL,
                        FinancialPriorityName.LEISURE_WELLBEING,
                        FinancialPriorityName.UNCLASSIFIED
                );
    }

    @Test
    void shouldFindFinancialPriorityByName() {
        FinancialPriority priority = financialPriorityRepository
                .findByFinancialPriorityName(FinancialPriorityName.CRITICAL)
                .orElseThrow();

        assertThat(priority.getFinancialPriorityName()).isEqualTo(FinancialPriorityName.CRITICAL);
        assertThat(priority.getFinancialPriorityRanking()).isEqualTo((short) 80);
        assertThat(priority.getFinancialPriorityDescription()).isNotBlank();
    }

    @Test
    void shouldReturnFinancialPrioritiesOrderedByRankingDescending() {
        List<FinancialPriority> priorities =
                financialPriorityRepository.findAllByOrderByFinancialPriorityRankingDesc();

        assertThat(priorities)
                .extracting(FinancialPriority::getFinancialPriorityName)
                .containsExactly(
                        FinancialPriorityName.CRITICAL,
                        FinancialPriorityName.ESSENTIAL,
                        FinancialPriorityName.OPTIONAL,
                        FinancialPriorityName.LEISURE_WELLBEING,
                        FinancialPriorityName.UNCLASSIFIED
                );

        assertThat(priorities)
                .extracting(FinancialPriority::getFinancialPriorityRanking)
                .containsExactly((short) 80, (short) 60, (short) 40, (short) 20, (short) 0);
    }
}