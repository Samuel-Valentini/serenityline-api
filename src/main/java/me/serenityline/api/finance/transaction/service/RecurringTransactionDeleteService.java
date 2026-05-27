package me.serenityline.api.finance.transaction.service;

import me.serenityline.api.common.error.ResourceNotFoundException;
import me.serenityline.api.finance.transaction.dto.RecurringTransactionDeleteCommand;
import me.serenityline.api.finance.transaction.entity.RecurringTransaction;
import me.serenityline.api.finance.transaction.entity.RecurringTransactionHistory;
import me.serenityline.api.finance.transaction.repository.RecurringTransactionHistoryRepository;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
public class RecurringTransactionDeleteService {

    private final RecurringTransactionDeleteParser parser;
    private final UserRepository userRepository;
    private final RecurringTransactionAccessService recurringTransactionAccessService;
    private final RecurringTransactionHistoryRepository recurringTransactionHistoryRepository;
    private final Clock clock;

    public RecurringTransactionDeleteService(
            RecurringTransactionDeleteParser parser,
            UserRepository userRepository,
            RecurringTransactionAccessService recurringTransactionAccessService,
            RecurringTransactionHistoryRepository recurringTransactionHistoryRepository,
            Clock clock
    ) {
        this.parser = Objects.requireNonNull(parser, "parser");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.recurringTransactionAccessService = Objects.requireNonNull(
                recurringTransactionAccessService,
                "recurringTransactionAccessService"
        );
        this.recurringTransactionHistoryRepository = Objects.requireNonNull(
                recurringTransactionHistoryRepository,
                "recurringTransactionHistoryRepository"
        );
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void deleteRecurringTransaction(
            UUID userId,
            UUID recurringTransactionId,
            JsonNode body
    ) {
        RecurringTransactionDeleteCommand command = parser.parse(body);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("user.notFound"));

        UUID userGroupId = user.getUserGroup().getUserGroupId();

        RecurringTransaction recurringTransaction = recurringTransactionAccessService
                .findOperableRecurringTransaction(user, userGroupId, recurringTransactionId);

        RecurringTransactionHistory currentRule = recurringTransactionHistoryRepository
                .findCurrentOpenByRecurringTransactionId(recurringTransactionId)
                .orElseThrow(() -> new ResourceNotFoundException("finance.recurringTransaction.historyNotFound"));

        LocalDate endDate = command.endDate().isPresent()
                ? command.endDate().value()
                : LocalDate.now(clock);

        BigDecimal finalPaymentAmount = command.finalPaymentAmount().isPresent()
                ? command.finalPaymentAmount().value()
                : currentRule.getFinalPaymentAmount();

        RecurringTransactionHistory deleteRule = RecurringTransactionHistory.create(
                recurringTransaction,
                endDate,
                null,
                currentRule.getDayOfUnit(),
                currentRule.getRecurrenceInterval(),
                currentRule.getRecurrenceUnit(),
                currentRule.getPaymentDateAdjustmentPolicy(),
                currentRule.getPaymentAmount(),
                endDate,
                finalPaymentAmount
        );

        recurringTransactionHistoryRepository.save(deleteRule);
        recurringTransaction.touch();
    }
}