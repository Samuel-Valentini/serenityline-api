package me.serenityline.api.finance.financialpriority.entity;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "financial_priorities")
public class FinancialPriority {

    @Id
    @Column(name = "financial_priority_id", nullable = false)
    private UUID financialPriorityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "financial_priority_name", nullable = false, unique = true, length = 100)
    private FinancialPriorityName financialPriorityName;

    @Column(name = "financial_priority_description", nullable = false, length = 500)
    private String financialPriorityDescription;

    @Column(name = "financial_priority_ranking", nullable = false, unique = true)
    private Short financialPriorityRanking;

    protected FinancialPriority() {
    }

    public UUID getFinancialPriorityId() {
        return financialPriorityId;
    }

    public FinancialPriorityName getFinancialPriorityName() {
        return financialPriorityName;
    }

    public String getFinancialPriorityDescription() {
        return financialPriorityDescription;
    }

    public Short getFinancialPriorityRanking() {
        return financialPriorityRanking;
    }
}