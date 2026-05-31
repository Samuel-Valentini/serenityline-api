package me.serenityline.api.finance.financialpriority.service;

import me.serenityline.api.finance.financialpriority.dto.FinancialPriorityResponse;
import me.serenityline.api.finance.financialpriority.entity.FinancialPriority;
import me.serenityline.api.finance.financialpriority.repository.FinancialPriorityRepository;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class FinancialPriorityService {

    private final FinancialPriorityRepository financialPriorityRepository;
    private final MessageSource messageSource;

    public FinancialPriorityService(
            FinancialPriorityRepository financialPriorityRepository,
            MessageSource messageSource
    ) {
        this.financialPriorityRepository = financialPriorityRepository;
        this.messageSource = messageSource;
    }

    @Transactional(readOnly = true)
    public List<FinancialPriorityResponse> findFinancialPriorities(Locale locale) {
        return financialPriorityRepository.findAllByOrderByFinancialPriorityRankingDesc()
                .stream()
                .map(financialPriority -> toResponse(financialPriority, locale))
                .toList();
    }

    private FinancialPriorityResponse toResponse(FinancialPriority financialPriority, Locale locale) {
        String code = financialPriority.getFinancialPriorityName().name();

        return new FinancialPriorityResponse(
                financialPriority.getFinancialPriorityId(),
                code,
                message("finance.financialPriority." + code + ".displayName", locale),
                message("finance.financialPriority." + code + ".description", locale),
                financialPriority.getFinancialPriorityRanking()
        );
    }

    private String message(String key, Locale locale) {
        return messageSource.getMessage(key, null, locale);
    }
}