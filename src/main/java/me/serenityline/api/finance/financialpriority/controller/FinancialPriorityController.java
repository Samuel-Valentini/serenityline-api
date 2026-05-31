package me.serenityline.api.finance.financialpriority.controller;

import me.serenityline.api.finance.financialpriority.dto.FinancialPriorityResponse;
import me.serenityline.api.finance.financialpriority.service.FinancialPriorityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/finance/financial-priorities")
public class FinancialPriorityController {

    private final FinancialPriorityService financialPriorityService;

    public FinancialPriorityController(FinancialPriorityService financialPriorityService) {
        this.financialPriorityService = financialPriorityService;
    }

    @GetMapping
    public ResponseEntity<List<FinancialPriorityResponse>> findFinancialPriorities(Locale locale) {
        return ResponseEntity.ok(financialPriorityService.findFinancialPriorities(locale));
    }
}