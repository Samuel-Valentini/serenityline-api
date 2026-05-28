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
    private final CurrencyBusinessCalendarResolver currencyBusinessCalendarResolver;
    private final RecurringTransactionOccurrenceGenerator recurringTransactionOccurrenceGenerator;

    public RecurringTransactionOccurrenceService(
            RecurringTransactionHistoryRepository recurringTransactionHistoryRepository,
            RecurringTransactionDetailsHistoryRepository recurringTransactionDetailsHistoryRepository,
            RecurringTransactionRuleSnapshotMapper recurringTransactionRuleSnapshotMapper,
            RecurringTransactionAccountCurrencySnapshotMapper recurringTransactionAccountCurrencySnapshotMapper,
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

        List<RecurringTransactionHistory> ruleHistoryRows =
                recurringTransactionHistoryRepository.findAllHistoryByRecurringTransactionId(
                        recurringTransactionId
                );

        if (ruleHistoryRows.isEmpty()) {
            throw new IllegalStateException("finance.recurringTransaction.ruleHistoryRequired");
        }

        List<RecurringTransactionDetailsHistory> detailsHistoryRows =
                recurringTransactionDetailsHistoryRepository
                        .findAllHistoryWithLinkedAccountByRecurringTransactionIdAndUserGroupId(
                                recurringTransactionId,
                                userGroupId
                        );

        if (detailsHistoryRows.isEmpty()) {
            throw new IllegalStateException("finance.recurringTransaction.detailsHistoryRequired");
        }

        List<RecurringTransactionRuleSnapshot> ruleSnapshots =
                recurringTransactionRuleSnapshotMapper.toSnapshots(ruleHistoryRows);

        List<PaymentAccountCurrencySnapshot> accountCurrencySnapshots =
                recurringTransactionAccountCurrencySnapshotMapper.toSnapshots(detailsHistoryRows);

        PaymentBusinessCalendarProvider paymentBusinessCalendarProvider =
                new AccountCurrencyPaymentBusinessCalendarProvider(
                        accountCurrencySnapshots,
                        currencyBusinessCalendarResolver
                );

        return recurringTransactionOccurrenceGenerator.generateOccurrences(
                recurringTransactionId,
                firstPaymentDate,
                ruleSnapshots,
                from,
                to,
                paymentBusinessCalendarProvider
        );
    }
}