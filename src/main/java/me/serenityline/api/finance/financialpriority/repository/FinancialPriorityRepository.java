package me.serenityline.api.finance.financialpriority.repository;

import me.serenityline.api.finance.financialpriority.entity.FinancialPriority;
import me.serenityline.api.finance.financialpriority.entity.FinancialPriorityName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinancialPriorityRepository extends JpaRepository<FinancialPriority, UUID> {

    Optional<FinancialPriority> findByFinancialPriorityName(FinancialPriorityName financialPriorityName);

    List<FinancialPriority> findAllByOrderByFinancialPriorityRankingDesc();
}