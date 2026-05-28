package me.serenityline.api.finance.transaction.service;

import me.serenityline.api.finance.calendar.AccountCurrencyPaymentBusinessCalendarProvider;
import me.serenityline.api.finance.calendar.CurrencyBusinessCalendarResolver;
import me.serenityline.api.finance.calendar.PaymentAccountCurrencySnapshot;
import me.serenityline.api.finance.calendar.PaymentBusinessCalendarProvider;
import me.serenityline.api.finance.transaction.entity.RecurringTransaction;
import me.serenityline.api.finance.transaction.entity.RecurringTransactionDetailsHistory;
import me.serenityline.api.finance.transaction.entity.RecurringTransactionHistory;
import me.serenityline.api.finance.transaction.repository.RecurringTransactionDetailsHistoryRepository;
import me.serenityline.api.finance.transaction.repository.RecurringTransactionHistoryRepository;
import me.serenityline.api.user.entity.UserGroup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class RecurringTransactionOccurrenceService {

    private final RecurringTransactionHistoryRepository recurringTransactionHistoryRepository;
    private final RecurringTransactionDetailsHistoryRepository recurringTransactionDetailsHistoryRepository;
    private final RecurringTransactionRuleSnapshotMapper recurringTransactionRuleSnapshotMapper;
    private final RecurringTransactionAccountCurrencySnapshotMapper recurringTransactionAccountCurrencySnapshotMapper;
    private final RecurringTransactionProjectedMovementAssembler recurringTransactionProjectedMovementAssembler;
    private final CurrencyBusinessCalendarResolver currencyBusinessCalendarResolver;
    private final RecurringTransactionOccurrenceGenerator recurringTransactionOccurrenceGenerator;

    public RecurringTransactionOccurrenceService(
            RecurringTransactionHistoryRepository recurringTransactionHistoryRepository,
            RecurringTransactionDetailsHistoryRepository recurringTransactionDetailsHistoryRepository,
            RecurringTransactionRuleSnapshotMapper recurringTransactionRuleSnapshotMapper,
            RecurringTransactionAccountCurrencySnapshotMapper recurringTransactionAccountCurrencySnapshotMapper,
            RecurringTransactionProjectedMovementAssembler recurringTransactionProjectedMovementAssembler,
            CurrencyBusinessCalendarResolver currencyBusinessCalendarResolver,
            RecurringTransactionOccurrenceGenerator recurringTransactionOccurrenceGenerator
    ) {
        this.recurringTransactionHistoryRepository = Objects.requireNonNull(
                recurringTransactionHistoryRepository,
                "recurringTransactionHistoryRepository"
        );
        this.recurringTransactionDetailsHistoryRepository = Objects.requireNonNull(
                recurringTransactionDetailsHistoryRepository,
                "recurringTransactionDetailsHistoryRepository"
        );
        this.recurringTransactionRuleSnapshotMapper = Objects.requireNonNull(
                recurringTransactionRuleSnapshotMapper,
                "recurringTransactionRuleSnapshotMapper"
        );
        this.recurringTransactionAccountCurrencySnapshotMapper = Objects.requireNonNull(
                recurringTransactionAccountCurrencySnapshotMapper,
                "recurringTransactionAccountCurrencySnapshotMapper"
        );
        this.recurringTransactionProjectedMovementAssembler = Objects.requireNonNull(
                recurringTransactionProjectedMovementAssembler,
                "recurringTransactionProjectedMovementAssembler"
        );
        this.currencyBusinessCalendarResolver = Objects.requireNonNull(
                currencyBusinessCalendarResolver,
                "currencyBusinessCalendarResolver"
        );
        this.recurringTransactionOccurrenceGenerator = Objects.requireNonNull(
                recurringTransactionOccurrenceGenerator,
                "recurringTransactionOccurrenceGenerator"
        );
    }

    @Transactional(readOnly = true)
    public List<RecurringTransactionOccurrence> generateOccurrences(
            RecurringTransaction recurringTransaction,
            LocalDate from,
            LocalDate to
    ) {
        RecurringTransactionGenerationContext context = validateAndExtractContext(
                recurringTransaction,
                from,
                to
        );

        RecurringTransactionGenerationData data = loadGenerationData(context);

        return generateOccurrences(
                context,
                data,
                from,
                to
        );
    }

    @Transactional(readOnly = true)
    public List<RecurringTransactionProjectedMovement> generateProjectedMovements(
            RecurringTransaction recurringTransaction,
            LocalDate from,
            LocalDate to
    ) {
        RecurringTransactionGenerationContext context = validateAndExtractContext(
                recurringTransaction,
                from,
                to
        );

        RecurringTransactionGenerationData data = loadGenerationData(context);

        List<RecurringTransactionOccurrence> occurrences = generateOccurrences(
                context,
                data,
                from,
                to
        );

        return recurringTransactionProjectedMovementAssembler.assemble(
                occurrences,
                data.detailsHistoryRows()
        );
    }

    private RecurringTransactionGenerationContext validateAndExtractContext(
            RecurringTransaction recurringTransaction,
            LocalDate from,
            LocalDate to
    ) {
        Objects.requireNonNull(recurringTransaction, "recurringTransaction");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");

        if (to.isBefore(from)) {
            throw new IllegalArgumentException("finance.calendar.dateRangeInvalid");
        }

        UUID recurringTransactionId = Objects.requireNonNull(
                recurringTransaction.getRecurringTransactionId(),
                "recurringTransactionId"
        );

        UserGroup userGroup = Objects.requireNonNull(
                recurringTransaction.getUserGroup(),
                "userGroup"
        );

        UUID userGroupId = Objects.requireNonNull(
                userGroup.getUserGroupId(),
                "userGroupId"
        );

        LocalDate firstPaymentDate = Objects.requireNonNull(
                recurringTransaction.getRecurringTransactionFirstPaymentDate(),
                "recurringTransactionFirstPaymentDate"
        );

        return new RecurringTransactionGenerationContext(
                recurringTransactionId,
                userGroupId,
                firstPaymentDate
        );
    }

    private RecurringTransactionGenerationData loadGenerationData(
            RecurringTransactionGenerationContext context
    ) {
        List<RecurringTransactionHistory> ruleHistoryRows =
                recurringTransactionHistoryRepository.findAllHistoryByRecurringTransactionId(
                        context.recurringTransactionId()
                );

        if (ruleHistoryRows.isEmpty()) {
            throw new IllegalStateException("finance.recurringTransaction.ruleHistoryRequired");
        }

        List<RecurringTransactionDetailsHistory> detailsHistoryRows =
                recurringTransactionDetailsHistoryRepository
                        .findAllHistoryWithLinkedAccountByRecurringTransactionIdAndUserGroupId(
                                context.recurringTransactionId(),
                                context.userGroupId()
                        );

        if (detailsHistoryRows.isEmpty()) {
            throw new IllegalStateException("finance.recurringTransaction.detailsHistoryRequired");
        }

        return new RecurringTransactionGenerationData(
                ruleHistoryRows,
                detailsHistoryRows
        );
    }

    private List<RecurringTransactionOccurrence> generateOccurrences(
            RecurringTransactionGenerationContext context,
            RecurringTransactionGenerationData data,
            LocalDate from,
            LocalDate to
    ) {
        List<RecurringTransactionRuleSnapshot> ruleSnapshots =
                recurringTransactionRuleSnapshotMapper.toSnapshots(data.ruleHistoryRows());

        List<PaymentAccountCurrencySnapshot> accountCurrencySnapshots =
                recurringTransactionAccountCurrencySnapshotMapper.toSnapshots(data.detailsHistoryRows());

        PaymentBusinessCalendarProvider paymentBusinessCalendarProvider =
                new AccountCurrencyPaymentBusinessCalendarProvider(
                        accountCurrencySnapshots,
                        currencyBusinessCalendarResolver
                );

        return recurringTransactionOccurrenceGenerator.generateOccurrences(
                context.recurringTransactionId(),
                context.firstPaymentDate(),
                ruleSnapshots,
                from,
                to,
                paymentBusinessCalendarProvider
        );
    }

    private record RecurringTransactionGenerationContext(
            UUID recurringTransactionId,
            UUID userGroupId,
            LocalDate firstPaymentDate
    ) {
    }

    private record RecurringTransactionGenerationData(
            List<RecurringTransactionHistory> ruleHistoryRows,
            List<RecurringTransactionDetailsHistory> detailsHistoryRows
    ) {
    }
}