package me.serenityline.api.finance.calendar;

import me.serenityline.api.common.error.ResourceNotFoundException;
import me.serenityline.api.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class FinanceCalendarDailyBalanceServiceIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private FinanceCalendarService financeCalendarService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FinanceCalendarProperties financeCalendarProperties;

    private UUID financialPriorityId;

    @BeforeEach
    void setUp() {
        financialPriorityId = jdbcTemplate.queryForObject("""
                SELECT financial_priority_id
                FROM financial_priorities
                WHERE financial_priority_name = 'ESSENTIAL'
                """, UUID.class);
    }

    @Test
    void dailyBalancesShouldUseOpeningBalancePreviousMovementsFlagsBucketsAndDeduplicatedRecurring() {
        UUID userGroupId = givenUserGroup("Daily balance group");
        UUID ownerId = givenUser(userGroupId, "OWNER", "daily.owner@example.test");
        UUID categoryId = givenCategory(userGroupId, ownerId, "Daily category");
        UUID accountId = givenAccount(
                userGroupId,
                "Daily EUR account",
                "EUR",
                BigDecimal.valueOf(1000),
                LocalDate.of(2026, 1, 1)
        );
        UUID bucketId = givenBucket(userGroupId, "Emergency bucket");

        givenTransaction(
                userGroupId,
                accountId,
                categoryId,
                null,
                null,
                BigDecimal.valueOf(-100),
                LocalDate.of(2026, 1, 3),
                true,
                true,
                false,
                false,
                null,
                true,
                null,
                null
        );

        givenTransaction(
                userGroupId,
                accountId,
                categoryId,
                null,
                null,
                BigDecimal.valueOf(500),
                LocalDate.of(2026, 1, 4),
                true,
                false,
                false,
                false,
                null,
                true,
                null,
                null
        );

        givenTransaction(
                userGroupId,
                accountId,
                categoryId,
                null,
                null,
                BigDecimal.valueOf(-80),
                LocalDate.of(2026, 1, 5),
                false,
                true,
                false,
                false,
                null,
                true,
                null,
                null
        );

        givenTransaction(
                userGroupId,
                accountId,
                categoryId,
                null,
                null,
                BigDecimal.valueOf(-80),
                LocalDate.of(2026, 1, 6),
                true,
                false,
                false,
                false,
                null,
                true,
                null,
                null
        );

        givenTransaction(
                userGroupId,
                accountId,
                categoryId,
                null,
                bucketId,
                BigDecimal.valueOf(-200),
                LocalDate.of(2026, 1, 7),
                false,
                true,
                false,
                false,
                null,
                true,
                null,
                null
        );

        givenTransaction(
                userGroupId,
                accountId,
                categoryId,
                null,
                bucketId,
                BigDecimal.valueOf(-50),
                LocalDate.of(2026, 1, 8),
                true,
                false,
                false,
                false,
                null,
                true,
                null,
                null
        );

        UUID recurringTransactionId = givenRecurringTransaction(
                userGroupId,
                accountId,
                categoryId,
                null,
                BigDecimal.valueOf(-300),
                LocalDate.of(2026, 1, 10),
                10,
                false,
                null,
                "Recurring rent",
                true,
                true
        );

        givenTransaction(
                userGroupId,
                accountId,
                categoryId,
                null,
                null,
                BigDecimal.valueOf(-333),
                LocalDate.of(2026, 1, 10),
                true,
                true,
                true,
                false,
                null,
                false,
                recurringTransactionId,
                LocalDate.of(2026, 1, 10)
        );

        List<FinanceCalendarDailyBalance> result =
                financeCalendarService.getDailyBalances(
                        ownerId,
                        new FinanceCalendarSearchRequest(
                                LocalDate.of(2026, 1, 5),
                                LocalDate.of(2026, 1, 10),
                                null,
                                null
                        )
                );

        assertThat(result).hasSize(6);

        FinanceCalendarAccountDailyBalance jan5 =
                accountBalanceOn(result, LocalDate.of(2026, 1, 5), accountId);

        assertThat(jan5.endOfDayAccountBalance()).isEqualByComparingTo("1400.00");
        assertThat(jan5.endOfDaySerenityline()).isEqualByComparingTo("820.00");
        assertThat(jan5.endOfDayBucketsBalance()).isEqualByComparingTo("0.00");

        FinanceCalendarAccountDailyBalance jan7 =
                accountBalanceOn(result, LocalDate.of(2026, 1, 7), accountId);

        assertThat(jan7.endOfDayAccountBalance()).isEqualByComparingTo("1320.00");
        assertThat(jan7.endOfDaySerenityline()).isEqualByComparingTo("620.00");
        assertThat(jan7.endOfDayBucketsBalance()).isEqualByComparingTo("200.00");
        assertThat(accountBucketBalance(jan7, bucketId).endOfDayBucketBalance())
                .isEqualByComparingTo("200.00");

        FinanceCalendarAccountDailyBalance jan8 =
                accountBalanceOn(result, LocalDate.of(2026, 1, 8), accountId);

        assertThat(jan8.endOfDayAccountBalance()).isEqualByComparingTo("1270.00");
        assertThat(jan8.endOfDaySerenityline()).isEqualByComparingTo("620.00");
        assertThat(jan8.endOfDayBucketsBalance()).isEqualByComparingTo("150.00");

        FinanceCalendarDailyBalance jan10Day =
                balanceOn(result, LocalDate.of(2026, 1, 10));

        FinanceCalendarAccountDailyBalance jan10 =
                accountBalance(jan10Day, accountId);

        assertThat(jan10.endOfDayAccountBalance()).isEqualByComparingTo("937.00");
        assertThat(jan10.endOfDaySerenityline()).isEqualByComparingTo("287.00");
        assertThat(jan10.endOfDayBucketsBalance()).isEqualByComparingTo("150.00");

        FinanceCalendarCurrencyDailyBalance eurTotal =
                currencyBalance(jan10Day, "EUR");

        assertThat(eurTotal.endOfDayAccountsBalance()).isEqualByComparingTo("937.00");
        assertThat(eurTotal.endOfDaySerenityline()).isEqualByComparingTo("287.00");
        assertThat(eurTotal.endOfDayBucketsBalance()).isEqualByComparingTo("150.00");

        FinanceCalendarBucketDailyBalance bucketTotal =
                bucketBalance(jan10Day, bucketId, "EUR");

        assertThat(bucketTotal.endOfDayBucketBalance()).isEqualByComparingTo("150.00");
    }

    @Test
    void accountShouldAppearOnlyFromOpeningBalanceDate() {
        UUID userGroupId = givenUserGroup("Opening date group");
        UUID ownerId = givenUser(userGroupId, "OWNER", "opening.owner@example.test");
        UUID accountId = givenAccount(
                userGroupId,
                "Future opening account",
                "EUR",
                BigDecimal.valueOf(250),
                LocalDate.of(2026, 1, 7)
        );

        List<FinanceCalendarDailyBalance> result =
                financeCalendarService.getDailyBalances(
                        ownerId,
                        new FinanceCalendarSearchRequest(
                                LocalDate.of(2026, 1, 5),
                                LocalDate.of(2026, 1, 8),
                                null,
                                null
                        )
                );

        assertThat(balanceOn(result, LocalDate.of(2026, 1, 5)).accounts()).isEmpty();
        assertThat(balanceOn(result, LocalDate.of(2026, 1, 6)).accounts()).isEmpty();

        FinanceCalendarAccountDailyBalance jan7 =
                accountBalanceOn(result, LocalDate.of(2026, 1, 7), accountId);

        assertThat(jan7.endOfDayAccountBalance()).isEqualByComparingTo("250.00");
        assertThat(jan7.endOfDaySerenityline()).isEqualByComparingTo("250.00");

        FinanceCalendarAccountDailyBalance jan8 =
                accountBalanceOn(result, LocalDate.of(2026, 1, 8), accountId);

        assertThat(jan8.endOfDayAccountBalance()).isEqualByComparingTo("250.00");
        assertThat(jan8.endOfDaySerenityline()).isEqualByComparingTo("250.00");
    }

    @Test
    void dailyBalancesShouldReturnTotalsByCurrencySeparately() {
        UUID userGroupId = givenUserGroup("Currency group");
        UUID ownerId = givenUser(userGroupId, "OWNER", "currency.owner@example.test");
        UUID categoryId = givenCategory(userGroupId, ownerId, "Currency category");

        UUID eurAccountId = givenAccount(
                userGroupId,
                "EUR account",
                "EUR",
                BigDecimal.valueOf(100),
                LocalDate.of(2026, 1, 1)
        );

        UUID usdAccountId = givenAccount(
                userGroupId,
                "USD account",
                "USD",
                BigDecimal.valueOf(200),
                LocalDate.of(2026, 1, 1)
        );

        givenTransaction(
                userGroupId,
                eurAccountId,
                categoryId,
                null,
                null,
                BigDecimal.valueOf(50),
                LocalDate.of(2026, 1, 2),
                true,
                true,
                false,
                false,
                null,
                true,
                null,
                null
        );

        givenTransaction(
                userGroupId,
                usdAccountId,
                categoryId,
                null,
                null,
                BigDecimal.valueOf(-20),
                LocalDate.of(2026, 1, 2),
                true,
                true,
                false,
                false,
                null,
                true,
                null,
                null
        );

        FinanceCalendarDailyBalance jan2 =
                balanceOn(
                        financeCalendarService.getDailyBalances(
                                ownerId,
                                new FinanceCalendarSearchRequest(
                                        LocalDate.of(2026, 1, 2),
                                        LocalDate.of(2026, 1, 2),
                                        null,
                                        null
                                )
                        ),
                        LocalDate.of(2026, 1, 2)
                );

        assertThat(jan2.totalsByCurrency()).hasSize(2);

        assertThat(currencyBalance(jan2, "EUR").endOfDayAccountsBalance())
                .isEqualByComparingTo("150.00");
        assertThat(currencyBalance(jan2, "EUR").endOfDaySerenityline())
                .isEqualByComparingTo("150.00");

        assertThat(currencyBalance(jan2, "USD").endOfDayAccountsBalance())
                .isEqualByComparingTo("180.00");
        assertThat(currencyBalance(jan2, "USD").endOfDaySerenityline())
                .isEqualByComparingTo("180.00");
    }

    @Test
    void accountIdsShouldLimitAccountsAndMovements() {
        UUID userGroupId = givenUserGroup("Account filter group");
        UUID ownerId = givenUser(userGroupId, "OWNER", "filter.owner@example.test");
        UUID categoryId = givenCategory(userGroupId, ownerId, "Filter category");

        UUID includedAccountId = givenAccount(
                userGroupId,
                "Included account",
                "EUR",
                BigDecimal.valueOf(100),
                LocalDate.of(2026, 1, 1)
        );

        UUID excludedAccountId = givenAccount(
                userGroupId,
                "Excluded account",
                "EUR",
                BigDecimal.valueOf(1000),
                LocalDate.of(2026, 1, 1)
        );

        givenTransaction(
                userGroupId,
                includedAccountId,
                categoryId,
                null,
                null,
                BigDecimal.valueOf(10),
                LocalDate.of(2026, 1, 2),
                true,
                true,
                false,
                false,
                null,
                true,
                null,
                null
        );

        givenTransaction(
                userGroupId,
                excludedAccountId,
                categoryId,
                null,
                null,
                BigDecimal.valueOf(500),
                LocalDate.of(2026, 1, 2),
                true,
                true,
                false,
                false,
                null,
                true,
                null,
                null
        );

        FinanceCalendarDailyBalance jan2 =
                balanceOn(
                        financeCalendarService.getDailyBalances(
                                ownerId,
                                new FinanceCalendarSearchRequest(
                                        LocalDate.of(2026, 1, 2),
                                        LocalDate.of(2026, 1, 2),
                                        List.of(includedAccountId),
                                        null
                                )
                        ),
                        LocalDate.of(2026, 1, 2)
                );

        assertThat(jan2.accounts())
                .extracting(FinanceCalendarAccountDailyBalance::accountId)
                .containsExactly(includedAccountId)
                .doesNotContain(excludedAccountId);

        assertThat(currencyBalance(jan2, "EUR").endOfDayAccountsBalance())
                .isEqualByComparingTo("110.00");
    }

    @Test
    void collaboratorShouldSeeOnlyLinkedAccountDailyBalances() {
        UUID userGroupId = givenUserGroup("Collaborator group");
        UUID ownerId = givenUser(userGroupId, "OWNER", "collab.owner@example.test");
        UUID collaboratorId = givenUser(
                userGroupId,
                "COLLABORATOR",
                "collab.user@example.test"
        );
        UUID categoryId = givenCategory(userGroupId, ownerId, "Collaborator category");

        UUID visibleAccountId = givenAccount(
                userGroupId,
                "Visible account",
                "EUR",
                BigDecimal.valueOf(100),
                LocalDate.of(2026, 1, 1)
        );

        UUID hiddenAccountId = givenAccount(
                userGroupId,
                "Hidden account",
                "EUR",
                BigDecimal.valueOf(1000),
                LocalDate.of(2026, 1, 1)
        );

        givenAccountUser(visibleAccountId, collaboratorId, userGroupId);

        givenTransaction(
                userGroupId,
                visibleAccountId,
                categoryId,
                null,
                null,
                BigDecimal.valueOf(25),
                LocalDate.of(2026, 1, 2),
                true,
                true,
                false,
                false,
                null,
                true,
                null,
                null
        );

        givenTransaction(
                userGroupId,
                hiddenAccountId,
                categoryId,
                null,
                null,
                BigDecimal.valueOf(999),
                LocalDate.of(2026, 1, 2),
                true,
                true,
                false,
                false,
                null,
                true,
                null,
                null
        );

        FinanceCalendarDailyBalance jan2 =
                balanceOn(
                        financeCalendarService.getDailyBalances(
                                collaboratorId,
                                new FinanceCalendarSearchRequest(
                                        LocalDate.of(2026, 1, 2),
                                        LocalDate.of(2026, 1, 2),
                                        null,
                                        null
                                )
                        ),
                        LocalDate.of(2026, 1, 2)
                );

        assertThat(jan2.accounts())
                .extracting(FinanceCalendarAccountDailyBalance::accountId)
                .containsExactly(visibleAccountId)
                .doesNotContain(hiddenAccountId);

        assertThat(currencyBalance(jan2, "EUR").endOfDayAccountsBalance())
                .isEqualByComparingTo("125.00");
    }

    @Test
    void simulationGroupsShouldIncludeOnlySelectedSimulatedMovements() {
        UUID userGroupId = givenUserGroup("Simulation group");
        UUID ownerId = givenUser(userGroupId, "OWNER", "simulation.owner@example.test");
        UUID categoryId = givenCategory(userGroupId, ownerId, "Simulation category");

        UUID accountId = givenAccount(
                userGroupId,
                "Simulation account",
                "EUR",
                BigDecimal.valueOf(1000),
                LocalDate.of(2026, 1, 1)
        );

        UUID selectedSimulationGroupId =
                givenSimulationGroup(userGroupId, "Selected simulation");

        UUID excludedSimulationGroupId =
                givenSimulationGroup(userGroupId, "Excluded simulation");

        givenTransaction(
                userGroupId,
                accountId,
                categoryId,
                null,
                null,
                BigDecimal.valueOf(-100),
                LocalDate.of(2026, 1, 2),
                true,
                true,
                false,
                false,
                null,
                true,
                null,
                null
        );

        givenTransaction(
                userGroupId,
                accountId,
                categoryId,
                null,
                null,
                BigDecimal.valueOf(-200),
                LocalDate.of(2026, 1, 2),
                true,
                true,
                false,
                true,
                selectedSimulationGroupId,
                true,
                null,
                null
        );

        givenTransaction(
                userGroupId,
                accountId,
                categoryId,
                null,
                null,
                BigDecimal.valueOf(-300),
                LocalDate.of(2026, 1, 2),
                true,
                true,
                false,
                true,
                excludedSimulationGroupId,
                true,
                null,
                null
        );

        givenRecurringTransaction(
                userGroupId,
                accountId,
                categoryId,
                null,
                BigDecimal.valueOf(-50),
                LocalDate.of(2026, 1, 10),
                10,
                true,
                selectedSimulationGroupId,
                "Selected simulated recurring",
                true,
                true
        );

        givenRecurringTransaction(
                userGroupId,
                accountId,
                categoryId,
                null,
                BigDecimal.valueOf(-60),
                LocalDate.of(2026, 1, 10),
                10,
                true,
                excludedSimulationGroupId,
                "Excluded simulated recurring",
                true,
                true
        );

        FinanceCalendarDailyBalance jan10 =
                balanceOn(
                        financeCalendarService.getDailyBalances(
                                ownerId,
                                new FinanceCalendarSearchRequest(
                                        LocalDate.of(2026, 1, 10),
                                        LocalDate.of(2026, 1, 10),
                                        null,
                                        List.of(selectedSimulationGroupId)
                                )
                        ),
                        LocalDate.of(2026, 1, 10)
                );

        FinanceCalendarAccountDailyBalance account =
                accountBalance(jan10, accountId);

        assertThat(account.endOfDayAccountBalance()).isEqualByComparingTo("650.00");
        assertThat(account.endOfDaySerenityline()).isEqualByComparingTo("650.00");
    }

    @Test
    void dailyBalancesShouldIncludeRecurringOccurrencesBeforeRequestedFrom() {
        UUID userGroupId = givenUserGroup("Recurring before range group");
        UUID ownerId = givenUser(userGroupId, "OWNER", "recurring.before.range.owner@example.test");
        UUID categoryId = givenCategory(userGroupId, ownerId, "Recurring before range category");

        UUID accountId = givenAccount(
                userGroupId,
                "Recurring before range account",
                "EUR",
                BigDecimal.valueOf(1000),
                LocalDate.of(2026, 1, 1)
        );

        givenRecurringTransaction(
                userGroupId,
                accountId,
                categoryId,
                null,
                BigDecimal.valueOf(-100),
                LocalDate.of(2026, 1, 5),
                5,
                false,
                null,
                "Monthly recurring before requested range",
                true,
                true
        );

        List<FinanceCalendarDailyBalance> result =
                financeCalendarService.getDailyBalances(
                        ownerId,
                        new FinanceCalendarSearchRequest(
                                LocalDate.of(2026, 3, 10),
                                LocalDate.of(2026, 3, 10),
                                null,
                                null
                        )
                );

        FinanceCalendarAccountDailyBalance account =
                accountBalanceOn(result, LocalDate.of(2026, 3, 10), accountId);

        assertThat(account.endOfDayAccountBalance()).isEqualByComparingTo("700.00");
        assertThat(account.endOfDaySerenityline()).isEqualByComparingTo("700.00");

        FinanceCalendarCurrencyDailyBalance eurTotal =
                currencyBalance(balanceOn(result, LocalDate.of(2026, 3, 10)), "EUR");

        assertThat(eurTotal.endOfDayAccountsBalance()).isEqualByComparingTo("700.00");
        assertThat(eurTotal.endOfDaySerenityline()).isEqualByComparingTo("700.00");
    }

    @Test
    void dailyBalancesShouldIncludePersistedTransactionsBeforeRequestedFrom() {
        UUID userGroupId = givenUserGroup("Persisted before range group");
        UUID ownerId = givenUser(userGroupId, "OWNER", "persisted.before.range.owner@example.test");
        UUID categoryId = givenCategory(userGroupId, ownerId, "Persisted before range category");

        UUID accountId = givenAccount(
                userGroupId,
                "Persisted before range account",
                "EUR",
                BigDecimal.valueOf(1000),
                LocalDate.of(2026, 1, 1)
        );

        givenTransaction(
                userGroupId,
                accountId,
                categoryId,
                null,
                null,
                BigDecimal.valueOf(-200),
                LocalDate.of(2026, 1, 10),
                true,
                true,
                false,
                false,
                null,
                true,
                null,
                null
        );

        List<FinanceCalendarDailyBalance> result =
                financeCalendarService.getDailyBalances(
                        ownerId,
                        new FinanceCalendarSearchRequest(
                                LocalDate.of(2026, 2, 1),
                                LocalDate.of(2026, 2, 1),
                                null,
                                null
                        )
                );

        FinanceCalendarAccountDailyBalance account =
                accountBalanceOn(result, LocalDate.of(2026, 2, 1), accountId);

        assertThat(account.endOfDayAccountBalance()).isEqualByComparingTo("800.00");
        assertThat(account.endOfDaySerenityline()).isEqualByComparingTo("800.00");
    }

    @Test
    void collaboratorShouldNotRequestUnlinkedAccountDailyBalances() {
        UUID userGroupId = givenUserGroup("Collaborator hidden account group");
        UUID ownerId = givenUser(userGroupId, "OWNER", "collaborator.hidden.owner@example.test");
        UUID collaboratorId = givenUser(
                userGroupId,
                "COLLABORATOR",
                "collaborator.hidden.user@example.test"
        );

        UUID visibleAccountId = givenAccount(
                userGroupId,
                "Visible collaborator account",
                "EUR",
                BigDecimal.valueOf(100),
                LocalDate.of(2026, 1, 1)
        );

        UUID hiddenAccountId = givenAccount(
                userGroupId,
                "Hidden collaborator account",
                "EUR",
                BigDecimal.valueOf(1000),
                LocalDate.of(2026, 1, 1)
        );

        givenAccountUser(visibleAccountId, collaboratorId, userGroupId);

        assertThatThrownBy(() -> financeCalendarService.getDailyBalances(
                collaboratorId,
                new FinanceCalendarSearchRequest(
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 1),
                        List.of(hiddenAccountId),
                        null
                )
        )).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void dailyBalancesShouldReturnEmptyDaysWhenNoAccountsAreReadable() {
        UUID userGroupId = givenUserGroup("No readable accounts group");
        UUID collaboratorId = givenUser(
                userGroupId,
                "COLLABORATOR",
                "no.readable.accounts.collaborator@example.test"
        );

        List<FinanceCalendarDailyBalance> result =
                financeCalendarService.getDailyBalances(
                        collaboratorId,
                        new FinanceCalendarSearchRequest(
                                LocalDate.of(2026, 1, 1),
                                LocalDate.of(2026, 1, 3),
                                null,
                                null
                        )
                );

        assertThat(result).hasSize(3);

        assertThat(balanceOn(result, LocalDate.of(2026, 1, 1)).accounts()).isEmpty();
        assertThat(balanceOn(result, LocalDate.of(2026, 1, 1)).buckets()).isEmpty();
        assertThat(balanceOn(result, LocalDate.of(2026, 1, 1)).totalsByCurrency()).isEmpty();

        assertThat(balanceOn(result, LocalDate.of(2026, 1, 2)).accounts()).isEmpty();
        assertThat(balanceOn(result, LocalDate.of(2026, 1, 2)).buckets()).isEmpty();
        assertThat(balanceOn(result, LocalDate.of(2026, 1, 2)).totalsByCurrency()).isEmpty();

        assertThat(balanceOn(result, LocalDate.of(2026, 1, 3)).accounts()).isEmpty();
        assertThat(balanceOn(result, LocalDate.of(2026, 1, 3)).buckets()).isEmpty();
        assertThat(balanceOn(result, LocalDate.of(2026, 1, 3)).totalsByCurrency()).isEmpty();
    }

    @Test
    void dailyBalancesShouldIgnoreMovementsBeforeAccountOpeningDate() {
        UUID userGroupId = givenUserGroup("Before opening movement group");
        UUID ownerId = givenUser(userGroupId, "OWNER", "before.opening.owner@example.test");
        UUID categoryId = givenCategory(userGroupId, ownerId, "Before opening category");

        UUID accountId = givenAccount(
                userGroupId,
                "Before opening account",
                "EUR",
                BigDecimal.valueOf(500),
                LocalDate.of(2026, 1, 10)
        );

        givenTransaction(
                userGroupId,
                accountId,
                categoryId,
                null,
                null,
                BigDecimal.valueOf(999),
                LocalDate.of(2026, 1, 5),
                true,
                true,
                false,
                false,
                null,
                true,
                null,
                null
        );

        List<FinanceCalendarDailyBalance> result =
                financeCalendarService.getDailyBalances(
                        ownerId,
                        new FinanceCalendarSearchRequest(
                                LocalDate.of(2026, 1, 10),
                                LocalDate.of(2026, 1, 10),
                                null,
                                null
                        )
                );

        FinanceCalendarAccountDailyBalance account =
                accountBalanceOn(result, LocalDate.of(2026, 1, 10), accountId);

        assertThat(account.endOfDayAccountBalance()).isEqualByComparingTo("500.00");
        assertThat(account.endOfDaySerenityline()).isEqualByComparingTo("500.00");
    }

    @Test
    void bucketBalancesShouldBeSeparatedByCurrency() {
        UUID userGroupId = givenUserGroup("Bucket currency group");
        UUID ownerId = givenUser(userGroupId, "OWNER", "bucket.currency.owner@example.test");
        UUID categoryId = givenCategory(userGroupId, ownerId, "Bucket currency category");
        UUID bucketId = givenBucket(userGroupId, "Multi currency bucket");

        UUID eurAccountId = givenAccount(
                userGroupId,
                "Bucket EUR account",
                "EUR",
                BigDecimal.valueOf(1000),
                LocalDate.of(2026, 1, 1)
        );

        UUID usdAccountId = givenAccount(
                userGroupId,
                "Bucket USD account",
                "USD",
                BigDecimal.valueOf(1000),
                LocalDate.of(2026, 1, 1)
        );

        givenTransaction(
                userGroupId,
                eurAccountId,
                categoryId,
                null,
                bucketId,
                BigDecimal.valueOf(-100),
                LocalDate.of(2026, 1, 2),
                false,
                true,
                false,
                false,
                null,
                true,
                null,
                null
        );

        givenTransaction(
                userGroupId,
                usdAccountId,
                categoryId,
                null,
                bucketId,
                BigDecimal.valueOf(-50),
                LocalDate.of(2026, 1, 2),
                false,
                true,
                false,
                false,
                null,
                true,
                null,
                null
        );

        FinanceCalendarDailyBalance jan2 =
                balanceOn(
                        financeCalendarService.getDailyBalances(
                                ownerId,
                                new FinanceCalendarSearchRequest(
                                        LocalDate.of(2026, 1, 2),
                                        LocalDate.of(2026, 1, 2),
                                        null,
                                        null
                                )
                        ),
                        LocalDate.of(2026, 1, 2)
                );

        assertThat(jan2.buckets()).hasSize(2);

        assertThat(bucketBalance(jan2, bucketId, "EUR").endOfDayBucketBalance())
                .isEqualByComparingTo("100.00");

        assertThat(bucketBalance(jan2, bucketId, "USD").endOfDayBucketBalance())
                .isEqualByComparingTo("50.00");

        assertThat(currencyBalance(jan2, "EUR").endOfDayBucketsBalance())
                .isEqualByComparingTo("100.00");

        assertThat(currencyBalance(jan2, "USD").endOfDayBucketsBalance())
                .isEqualByComparingTo("50.00");
    }

    @Test
    void dailyBalancesShouldCarryForwardBalancesOnDaysWithoutMovements() {
        UUID userGroupId = givenUserGroup("Carry forward group");
        UUID ownerId = givenUser(userGroupId, "OWNER", "carry.forward.owner@example.test");
        UUID categoryId = givenCategory(userGroupId, ownerId, "Carry forward category");

        UUID accountId = givenAccount(
                userGroupId,
                "Carry forward account",
                "EUR",
                BigDecimal.valueOf(1000),
                LocalDate.of(2026, 1, 1)
        );

        givenTransaction(
                userGroupId,
                accountId,
                categoryId,
                null,
                null,
                BigDecimal.valueOf(-100),
                LocalDate.of(2026, 1, 2),
                true,
                true,
                false,
                false,
                null,
                true,
                null,
                null
        );

        List<FinanceCalendarDailyBalance> result =
                financeCalendarService.getDailyBalances(
                        ownerId,
                        new FinanceCalendarSearchRequest(
                                LocalDate.of(2026, 1, 1),
                                LocalDate.of(2026, 1, 4),
                                null,
                                null
                        )
                );

        assertThat(accountBalanceOn(result, LocalDate.of(2026, 1, 1), accountId)
                .endOfDayAccountBalance()).isEqualByComparingTo("1000.00");

        assertThat(accountBalanceOn(result, LocalDate.of(2026, 1, 2), accountId)
                .endOfDayAccountBalance()).isEqualByComparingTo("900.00");

        assertThat(accountBalanceOn(result, LocalDate.of(2026, 1, 3), accountId)
                .endOfDayAccountBalance()).isEqualByComparingTo("900.00");

        assertThat(accountBalanceOn(result, LocalDate.of(2026, 1, 4), accountId)
                .endOfDayAccountBalance()).isEqualByComparingTo("900.00");

        assertThat(accountBalanceOn(result, LocalDate.of(2026, 1, 4), accountId)
                .endOfDaySerenityline()).isEqualByComparingTo("900.00");
    }

    @Test
    void dailyBalancesShouldDeduplicateRecurringOccurrencesBeforeRequestedFrom() {
        UUID userGroupId = givenUserGroup("Historical recurring dedup group");
        UUID ownerId = givenUser(userGroupId, "OWNER", "historical.dedup.owner@example.test");
        UUID categoryId = givenCategory(userGroupId, ownerId, "Historical recurring dedup category");

        UUID accountId = givenAccount(
                userGroupId,
                "Historical recurring dedup account",
                "EUR",
                BigDecimal.valueOf(1000),
                LocalDate.of(2026, 1, 1)
        );

        UUID recurringTransactionId = givenRecurringTransaction(
                userGroupId,
                accountId,
                categoryId,
                null,
                BigDecimal.valueOf(-100),
                LocalDate.of(2026, 1, 5),
                5,
                false,
                null,
                "Monthly recurring to deduplicate before requested range",
                true,
                true
        );

        givenTransaction(
                userGroupId,
                accountId,
                categoryId,
                null,
                null,
                BigDecimal.valueOf(-120),
                LocalDate.of(2026, 1, 5),
                true,
                true,
                true,
                false,
                null,
                false,
                recurringTransactionId,
                LocalDate.of(2026, 1, 5)
        );

        List<FinanceCalendarDailyBalance> result =
                financeCalendarService.getDailyBalances(
                        ownerId,
                        new FinanceCalendarSearchRequest(
                                LocalDate.of(2026, 3, 10),
                                LocalDate.of(2026, 3, 10),
                                null,
                                null
                        )
                );

        FinanceCalendarAccountDailyBalance account =
                accountBalanceOn(result, LocalDate.of(2026, 3, 10), accountId);

        assertThat(account.endOfDayAccountBalance()).isEqualByComparingTo("680.00");
        assertThat(account.endOfDaySerenityline()).isEqualByComparingTo("680.00");

        FinanceCalendarCurrencyDailyBalance eurTotal =
                currencyBalance(balanceOn(result, LocalDate.of(2026, 3, 10)), "EUR");

        assertThat(eurTotal.endOfDayAccountsBalance()).isEqualByComparingTo("680.00");
        assertThat(eurTotal.endOfDaySerenityline()).isEqualByComparingTo("680.00");
    }

    @Test
    void dailyBalancesShouldAllowRequestedRangeWithinLimitEvenWhenCalculationRangeIsLonger() {
        UUID userGroupId = givenUserGroup("Long calculation range group");
        UUID ownerId = givenUser(userGroupId, "OWNER", "long.calculation.owner@example.test");

        UUID accountId = givenAccount(
                userGroupId,
                "Old account",
                "EUR",
                BigDecimal.valueOf(1000),
                LocalDate.of(2020, 1, 1)
        );

        List<FinanceCalendarDailyBalance> result =
                financeCalendarService.getDailyBalances(
                        ownerId,
                        new FinanceCalendarSearchRequest(
                                LocalDate.of(2026, 1, 1),
                                LocalDate.of(2026, 1, 10),
                                null,
                                null
                        )
                );

        assertThat(result).hasSize(10);

        assertThat(accountBalanceOn(result, LocalDate.of(2026, 1, 1), accountId)
                .endOfDayAccountBalance()).isEqualByComparingTo("1000.00");

        assertThat(accountBalanceOn(result, LocalDate.of(2026, 1, 10), accountId)
                .endOfDayAccountBalance()).isEqualByComparingTo("1000.00");
    }

    @Test
    void dailyBalancesShouldAllowLongCalculationRange() {
        UUID userGroupId = givenUserGroup("Long recurring calculation range group");
        UUID ownerId = givenUser(userGroupId, "OWNER", "long.recurring.calculation.owner@example.test");
        UUID categoryId = givenCategory(userGroupId, ownerId, "Long recurring category");

        UUID accountId = givenAccount(
                userGroupId,
                "Old recurring account",
                "EUR",
                BigDecimal.valueOf(1000),
                LocalDate.of(2020, 1, 1)
        );

        givenRecurringTransaction(
                userGroupId,
                accountId,
                categoryId,
                null,
                BigDecimal.valueOf(-10),
                LocalDate.of(2020, 1, 5),
                5,
                false,
                null,
                "Old monthly recurring",
                true,
                true
        );

        List<FinanceCalendarDailyBalance> result =
                financeCalendarService.getDailyBalances(
                        ownerId,
                        new FinanceCalendarSearchRequest(
                                LocalDate.of(2026, 1, 10),
                                LocalDate.of(2026, 1, 10),
                                null,
                                null
                        )
                );

        FinanceCalendarAccountDailyBalance account =
                accountBalanceOn(result, LocalDate.of(2026, 1, 10), accountId);

        assertThat(account.endOfDayAccountBalance()).isEqualByComparingTo("270.00");
        assertThat(account.endOfDaySerenityline()).isEqualByComparingTo("270.00");
    }

    @Test
    void dailyBalancesShouldAllowLongCalculationRangeWithRecurringTransactions() {
        UUID userGroupId = givenUserGroup("Long recurring calculation range group");
        UUID ownerId = givenUser(userGroupId, "OWNER", "long.recurring.calculation.owner@example.test");
        UUID categoryId = givenCategory(userGroupId, ownerId, "Long recurring category");

        UUID accountId = givenAccount(
                userGroupId,
                "Old recurring account",
                "EUR",
                BigDecimal.valueOf(1000),
                LocalDate.of(2020, 1, 1)
        );

        givenRecurringTransaction(
                userGroupId,
                accountId,
                categoryId,
                null,
                BigDecimal.valueOf(-10),
                LocalDate.of(2020, 1, 5),
                5,
                false,
                null,
                "Old monthly recurring",
                true,
                true
        );

        List<FinanceCalendarDailyBalance> result =
                financeCalendarService.getDailyBalances(
                        ownerId,
                        new FinanceCalendarSearchRequest(
                                LocalDate.of(2026, 1, 10),
                                LocalDate.of(2026, 1, 10),
                                null,
                                null
                        )
                );

        FinanceCalendarAccountDailyBalance account =
                accountBalanceOn(result, LocalDate.of(2026, 1, 10), accountId);

        assertThat(account.endOfDayAccountBalance()).isEqualByComparingTo("270.00");
        assertThat(account.endOfDaySerenityline()).isEqualByComparingTo("270.00");
    }

    @Test
    void dailyBalancesShouldRejectRequestedRangeOverLimit() {
        UUID userGroupId = givenUserGroup("Too long requested range group");
        UUID ownerId = givenUser(userGroupId, "OWNER", "too.long.range.owner@example.test");

        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = from.plusDays(financeCalendarProperties.getMaxRangeDays() + 1);

        assertThatThrownBy(() -> financeCalendarService.getDailyBalances(
                ownerId,
                new FinanceCalendarSearchRequest(
                        from,
                        to,
                        null,
                        null
                )
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.calendar.dateRangeTooLarge");
    }

    @Test
    void bucketReturnedToZeroShouldNotAppearInDailyBalances() {
        UUID userGroupId = givenUserGroup("Bucket zero group");
        UUID ownerId = givenUser(userGroupId, "OWNER", "bucket.zero.owner@example.test");
        UUID categoryId = givenCategory(userGroupId, ownerId, "Bucket zero category");
        UUID bucketId = givenBucket(userGroupId, "Temporary bucket");

        UUID accountId = givenAccount(
                userGroupId,
                "Bucket zero account",
                "EUR",
                BigDecimal.valueOf(1000),
                LocalDate.of(2026, 1, 1)
        );

        givenTransaction(
                userGroupId,
                accountId,
                categoryId,
                null,
                bucketId,
                BigDecimal.valueOf(-100),
                LocalDate.of(2026, 1, 2),
                false,
                true,
                false,
                false,
                null,
                true,
                null,
                null
        );

        givenTransaction(
                userGroupId,
                accountId,
                categoryId,
                null,
                bucketId,
                BigDecimal.valueOf(-100),
                LocalDate.of(2026, 1, 3),
                true,
                false,
                false,
                false,
                null,
                true,
                null,
                null
        );

        FinanceCalendarDailyBalance jan3 =
                balanceOn(
                        financeCalendarService.getDailyBalances(
                                ownerId,
                                new FinanceCalendarSearchRequest(
                                        LocalDate.of(2026, 1, 3),
                                        LocalDate.of(2026, 1, 3),
                                        null,
                                        null
                                )
                        ),
                        LocalDate.of(2026, 1, 3)
                );

        FinanceCalendarAccountDailyBalance account =
                accountBalance(jan3, accountId);

        assertThat(account.endOfDayAccountBalance()).isEqualByComparingTo("900.00");
        assertThat(account.endOfDaySerenityline()).isEqualByComparingTo("900.00");
        assertThat(account.endOfDayBucketsBalance()).isEqualByComparingTo("0.00");
        assertThat(account.buckets()).isEmpty();

        assertThat(jan3.buckets()).isEmpty();

        assertThat(currencyBalance(jan3, "EUR").endOfDayBucketsBalance())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void ownerShouldNotRequestUnknownAccountDailyBalances() {
        UUID userGroupId = givenUserGroup("Unknown account group");
        UUID ownerId = givenUser(userGroupId, "OWNER", "unknown.account.owner@example.test");

        UUID unknownAccountId = UUID.randomUUID();

        assertThatThrownBy(() -> financeCalendarService.getDailyBalances(
                ownerId,
                new FinanceCalendarSearchRequest(
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 1),
                        List.of(unknownAccountId),
                        null
                )
        )).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void viewerCollaboratorShouldSeeAllGroupAccountDailyBalances() {
        UUID userGroupId = givenUserGroup("Viewer collaborator group");
        UUID ownerId = givenUser(userGroupId, "OWNER", "viewer.owner@example.test");
        UUID viewerId = givenUser(
                userGroupId,
                "VIEWER_COLLABORATOR",
                "viewer.collaborator@example.test"
        );
        UUID categoryId = givenCategory(userGroupId, ownerId, "Viewer category");

        UUID firstAccountId = givenAccount(
                userGroupId,
                "Viewer account A",
                "EUR",
                BigDecimal.valueOf(100),
                LocalDate.of(2026, 1, 1)
        );

        UUID secondAccountId = givenAccount(
                userGroupId,
                "Viewer account B",
                "EUR",
                BigDecimal.valueOf(200),
                LocalDate.of(2026, 1, 1)
        );

        givenTransaction(
                userGroupId,
                firstAccountId,
                categoryId,
                null,
                null,
                BigDecimal.valueOf(10),
                LocalDate.of(2026, 1, 2),
                true,
                true,
                false,
                false,
                null,
                true,
                null,
                null
        );

        givenTransaction(
                userGroupId,
                secondAccountId,
                categoryId,
                null,
                null,
                BigDecimal.valueOf(-20),
                LocalDate.of(2026, 1, 2),
                true,
                true,
                false,
                false,
                null,
                true,
                null,
                null
        );

        FinanceCalendarDailyBalance jan2 =
                balanceOn(
                        financeCalendarService.getDailyBalances(
                                viewerId,
                                new FinanceCalendarSearchRequest(
                                        LocalDate.of(2026, 1, 2),
                                        LocalDate.of(2026, 1, 2),
                                        null,
                                        null
                                )
                        ),
                        LocalDate.of(2026, 1, 2)
                );

        assertThat(jan2.accounts())
                .extracting(FinanceCalendarAccountDailyBalance::accountId)
                .containsExactlyInAnyOrder(firstAccountId, secondAccountId);

        assertThat(accountBalance(jan2, firstAccountId).endOfDayAccountBalance())
                .isEqualByComparingTo("110.00");

        assertThat(accountBalance(jan2, secondAccountId).endOfDayAccountBalance())
                .isEqualByComparingTo("180.00");

        assertThat(currencyBalance(jan2, "EUR").endOfDayAccountsBalance())
                .isEqualByComparingTo("290.00");

        assertThat(currencyBalance(jan2, "EUR").endOfDaySerenityline())
                .isEqualByComparingTo("290.00");
    }

    @Test
    void dailyBalancesShouldIncludeNextBusinessDayRecurringWhenLogicalDateIsAtChunkEndAndChargeDateIsInNextChunk() {
        UUID userGroupId = givenUserGroup("Next business day chunk boundary group");
        UUID ownerId = givenUser(
                userGroupId,
                "OWNER",
                "next.business.day.chunk.boundary.owner@example.test"
        );
        UUID categoryId = givenCategory(
                userGroupId,
                ownerId,
                "Next business day chunk boundary category"
        );

        LocalDate logicalDateAtChunkEnd = LocalDate.of(2026, 1, 31); // Saturday
        LocalDate expectedChargeDate = LocalDate.of(2026, 2, 2); // Monday

        LocalDate openingBalanceDate = logicalDateAtChunkEnd.minusDays(
                financeCalendarProperties.getMaxRangeDays()
        );

        UUID accountId = givenAccount(
                userGroupId,
                "Next business day boundary account",
                "EUR",
                BigDecimal.valueOf(1000),
                openingBalanceDate
        );

        givenSingleDailyRecurringTransactionWithAdjustmentPolicy(
                userGroupId,
                accountId,
                categoryId,
                BigDecimal.valueOf(-100),
                logicalDateAtChunkEnd,
                "NEXT_BUSINESS_DAY",
                "Recurring at chunk end adjusted to next business day"
        );

        List<FinanceCalendarDailyBalance> result =
                financeCalendarService.getDailyBalances(
                        ownerId,
                        new FinanceCalendarSearchRequest(
                                expectedChargeDate,
                                expectedChargeDate,
                                null,
                                null
                        )
                );

        FinanceCalendarAccountDailyBalance account =
                accountBalanceOn(result, expectedChargeDate, accountId);

        assertThat(account.endOfDayAccountBalance()).isEqualByComparingTo("900.00");
        assertThat(account.endOfDaySerenityline()).isEqualByComparingTo("900.00");

        FinanceCalendarCurrencyDailyBalance eurTotal =
                currencyBalance(balanceOn(result, expectedChargeDate), "EUR");

        assertThat(eurTotal.endOfDayAccountsBalance()).isEqualByComparingTo("900.00");
        assertThat(eurTotal.endOfDaySerenityline()).isEqualByComparingTo("900.00");
    }

    @Test
    void dailyBalancesShouldIncludePreviousBusinessDayRecurringWhenLogicalDateIsAtChunkStartAndChargeDateIsInPreviousChunk() {
        UUID userGroupId = givenUserGroup("Previous business day chunk boundary group");
        UUID ownerId = givenUser(
                userGroupId,
                "OWNER",
                "previous.business.day.chunk.boundary.owner@example.test"
        );
        UUID categoryId = givenCategory(
                userGroupId,
                ownerId,
                "Previous business day chunk boundary category"
        );

        LocalDate logicalDateAtChunkStart = LocalDate.of(2026, 2, 1); // Sunday
        LocalDate expectedChargeDate = LocalDate.of(2026, 1, 30); // Friday

        LocalDate openingBalanceDate = logicalDateAtChunkStart.minusDays(
                financeCalendarProperties.getMaxRangeDays() + 1
        );

        UUID accountId = givenAccount(
                userGroupId,
                "Previous business day boundary account",
                "EUR",
                BigDecimal.valueOf(1000),
                openingBalanceDate
        );

        givenSingleDailyRecurringTransactionWithAdjustmentPolicy(
                userGroupId,
                accountId,
                categoryId,
                BigDecimal.valueOf(-100),
                logicalDateAtChunkStart,
                "PREVIOUS_BUSINESS_DAY",
                "Recurring at chunk start adjusted to previous business day"
        );

        List<FinanceCalendarDailyBalance> result =
                financeCalendarService.getDailyBalances(
                        ownerId,
                        new FinanceCalendarSearchRequest(
                                expectedChargeDate,
                                logicalDateAtChunkStart,
                                null,
                                null
                        )
                );

        FinanceCalendarAccountDailyBalance chargeDateAccount =
                accountBalanceOn(result, expectedChargeDate, accountId);

        assertThat(chargeDateAccount.endOfDayAccountBalance()).isEqualByComparingTo("900.00");
        assertThat(chargeDateAccount.endOfDaySerenityline()).isEqualByComparingTo("900.00");

        FinanceCalendarAccountDailyBalance logicalDateAccount =
                accountBalanceOn(result, logicalDateAtChunkStart, accountId);

        assertThat(logicalDateAccount.endOfDayAccountBalance()).isEqualByComparingTo("900.00");
        assertThat(logicalDateAccount.endOfDaySerenityline()).isEqualByComparingTo("900.00");
    }

    private UUID givenUserGroup(String name) {
        UUID userGroupId = UUID.randomUUID();

        jdbcTemplate.update("""
                INSERT INTO user_groups (
                    user_group_id,
                    user_group_name
                )
                VALUES (?, ?)
                """, userGroupId, name);

        return userGroupId;
    }

    private UUID givenUser(UUID userGroupId, String role, String email) {
        UUID userId = UUID.randomUUID();

        jdbcTemplate.update("""
                        INSERT INTO users (
                            user_id,
                            user_name,
                            email,
                            user_group_id,
                            user_role,
                            user_password_hash,
                            user_is_enabled
                        )
                        VALUES (?, ?, ?, ?, ?, ?, TRUE)
                        """,
                userId,
                "Daily Balance Test User",
                email,
                userGroupId,
                role,
                "test-password-hash"
        );

        return userId;
    }

    private UUID givenCategory(UUID userGroupId, UUID createdByUserId, String name) {
        UUID categoryId = UUID.randomUUID();

        jdbcTemplate.update("""
                INSERT INTO categories (
                    category_id,
                    user_group_id,
                    category_created_by_user_id,
                    category_current_name
                )
                VALUES (?, ?, ?, ?)
                """, categoryId, userGroupId, createdByUserId, name);

        jdbcTemplate.update("""
                INSERT INTO category_status_history (
                    category_id,
                    category_is_active
                )
                VALUES (?, TRUE)
                """, categoryId);

        jdbcTemplate.update("""
                INSERT INTO category_details_history (
                    category_id,
                    category_name,
                    category_description
                )
                VALUES (?, ?, ?)
                """, categoryId, name, "Test category");

        return categoryId;
    }

    private UUID givenAccount(
            UUID userGroupId,
            String name,
            String currency,
            BigDecimal openingBalance,
            LocalDate openingBalanceDate
    ) {
        UUID accountId = UUID.randomUUID();

        jdbcTemplate.update("""
                        INSERT INTO accounts (
                            account_id,
                            user_group_id,
                            account_name,
                            currency,
                            opening_balance,
                            opening_balance_date
                        )
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                accountId,
                userGroupId,
                name,
                currency,
                openingBalance,
                openingBalanceDate
        );

        return accountId;
    }

    private void givenAccountUser(
            UUID accountId,
            UUID userId,
            UUID userGroupId
    ) {
        jdbcTemplate.update("""
                INSERT INTO accounts_users (
                    account_id,
                    user_id,
                    user_group_id
                )
                VALUES (?, ?, ?)
                """, accountId, userId, userGroupId);
    }

    private UUID givenBucket(UUID userGroupId, String name) {
        UUID bucketId = UUID.randomUUID();

        jdbcTemplate.update("""
                INSERT INTO buckets (
                    bucket_id,
                    user_group_id,
                    bucket_name
                )
                VALUES (?, ?, ?)
                """, bucketId, userGroupId, name);

        return bucketId;
    }

    private UUID givenSimulationGroup(UUID userGroupId, String name) {
        UUID simulationGroupId = UUID.randomUUID();

        jdbcTemplate.update("""
                INSERT INTO simulation_groups (
                    simulation_group_id,
                    user_group_id,
                    simulation_group_name
                )
                VALUES (?, ?, ?)
                """, simulationGroupId, userGroupId, name);

        return simulationGroupId;
    }

    private UUID givenTransaction(
            UUID userGroupId,
            UUID accountId,
            UUID categoryId,
            UUID creditCardId,
            UUID bucketId,
            BigDecimal amount,
            LocalDate chargeDate,
            boolean affectsAccountBalance,
            boolean affectsSerenityline,
            boolean confirmed,
            boolean simulated,
            UUID simulationGroupId,
            boolean userEntered,
            UUID recurringTransactionId,
            LocalDate recurringTransactionLogicalDate
    ) {
        UUID transactionId = UUID.randomUUID();

        OffsetDateTime recurringConfirmedAt = userEntered
                ? null
                : OffsetDateTime.now();

        jdbcTemplate.update("""
                        INSERT INTO transactions (
                            transaction_id,
                            transaction_description,
                            transaction_amount,
                            transaction_affects_account_balance,
                            transaction_affects_serenityline,
                            category_id,
                            transaction_charge_date,
                            transaction_is_confirmed,
                            account_id,
                            credit_card_id,
                            bucket_id,
                            transaction_is_simulated,
                            simulation_group_id,
                            transaction_is_user_entered,
                            recurring_transaction_id,
                            recurring_transaction_logical_date,
                            recurring_transaction_confirmed_at,
                            transaction_reminder_enabled,
                            transaction_reminder_days_before,
                            user_group_id
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, 7, ?)
                        """,
                transactionId,
                "Test transaction " + transactionId,
                amount,
                affectsAccountBalance,
                affectsSerenityline,
                categoryId,
                chargeDate,
                confirmed,
                accountId,
                creditCardId,
                bucketId,
                simulated,
                simulationGroupId,
                userEntered,
                recurringTransactionId,
                recurringTransactionLogicalDate,
                recurringConfirmedAt,
                userGroupId
        );

        return transactionId;
    }

    private UUID givenRecurringTransaction(
            UUID userGroupId,
            UUID accountId,
            UUID categoryId,
            UUID bucketId,
            BigDecimal amount,
            LocalDate firstPaymentDate,
            int dayOfMonth,
            boolean simulated,
            UUID simulationGroupId,
            String description,
            boolean affectsAccountBalance,
            boolean affectsSerenityline
    ) {
        UUID recurringTransactionId = UUID.randomUUID();

        jdbcTemplate.update("""
                        INSERT INTO recurring_transactions (
                            recurring_transaction_id,
                            recurring_transaction_amount_is_adjustable,
                            recurring_transaction_first_payment_date,
                            recurring_transaction_is_simulated,
                            simulation_group_id,
                            recurring_transaction_reminder_enabled,
                            recurring_transaction_reminder_days_before,
                            user_group_id
                        )
                        VALUES (?, TRUE, ?, ?, ?, TRUE, 7, ?)
                        """,
                recurringTransactionId,
                firstPaymentDate,
                simulated,
                simulationGroupId,
                userGroupId
        );

        jdbcTemplate.update("""
                        INSERT INTO recurring_transaction_history (
                            recurring_transaction_id,
                            effective_from,
                            effective_to,
                            day_of_unit,
                            recurrence_interval,
                            recurrence_unit,
                            payment_date_adjustment_policy,
                            payment_amount
                        )
                        VALUES (?, ?, NULL, ?, 1, 'MONTH', 'NONE', ?)
                        """,
                recurringTransactionId,
                firstPaymentDate.withDayOfMonth(1),
                dayOfMonth,
                amount
        );

        jdbcTemplate.update("""
                        INSERT INTO recurring_transaction_details_history (
                            recurring_transaction_id,
                            recurring_transaction_description,
                            category_id,
                            financial_priority_id,
                            linked_account_id,
                            linked_bucket_id,
                            recurring_transaction_affects_account_balance,
                            recurring_transaction_affects_serenityline,
                            recurring_transaction_details_effective_from,
                            user_group_id
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                recurringTransactionId,
                description,
                categoryId,
                financialPriorityId,
                accountId,
                bucketId,
                affectsAccountBalance,
                affectsSerenityline,
                firstPaymentDate.withDayOfMonth(1),
                userGroupId
        );

        return recurringTransactionId;
    }

    private FinanceCalendarDailyBalance balanceOn(
            List<FinanceCalendarDailyBalance> balances,
            LocalDate date
    ) {
        return balances.stream()
                .filter(balance -> balance.date().equals(date))
                .findFirst()
                .orElseThrow();
    }

    private FinanceCalendarAccountDailyBalance accountBalanceOn(
            List<FinanceCalendarDailyBalance> balances,
            LocalDate date,
            UUID accountId
    ) {
        return accountBalance(
                balanceOn(balances, date),
                accountId
        );
    }

    private FinanceCalendarAccountDailyBalance accountBalance(
            FinanceCalendarDailyBalance balance,
            UUID accountId
    ) {
        return balance.accounts()
                .stream()
                .filter(account -> account.accountId().equals(accountId))
                .findFirst()
                .orElseThrow();
    }

    private FinanceCalendarAccountBucketDailyBalance accountBucketBalance(
            FinanceCalendarAccountDailyBalance account,
            UUID bucketId
    ) {
        return account.buckets()
                .stream()
                .filter(bucket -> bucket.bucketId().equals(bucketId))
                .findFirst()
                .orElseThrow();
    }

    private FinanceCalendarCurrencyDailyBalance currencyBalance(
            FinanceCalendarDailyBalance balance,
            String currency
    ) {
        return balance.totalsByCurrency()
                .stream()
                .filter(total -> total.currency().equals(currency))
                .findFirst()
                .orElseThrow();
    }

    private FinanceCalendarBucketDailyBalance bucketBalance(
            FinanceCalendarDailyBalance balance,
            UUID bucketId,
            String currency
    ) {
        return balance.buckets()
                .stream()
                .filter(bucket -> bucket.bucketId().equals(bucketId))
                .filter(bucket -> bucket.currency().equals(currency))
                .findFirst()
                .orElseThrow();
    }

    private UUID givenSingleDailyRecurringTransactionWithAdjustmentPolicy(
            UUID userGroupId,
            UUID accountId,
            UUID categoryId,
            BigDecimal amount,
            LocalDate logicalDate,
            String paymentDateAdjustmentPolicy,
            String description
    ) {
        UUID recurringTransactionId = UUID.randomUUID();

        jdbcTemplate.update("""
                        INSERT INTO recurring_transactions (
                            recurring_transaction_id,
                            recurring_transaction_amount_is_adjustable,
                            recurring_transaction_first_payment_date,
                            recurring_transaction_is_simulated,
                            simulation_group_id,
                            recurring_transaction_reminder_enabled,
                            recurring_transaction_reminder_days_before,
                            user_group_id
                        )
                        VALUES (?, TRUE, ?, FALSE, NULL, TRUE, 7, ?)
                        """,
                recurringTransactionId,
                logicalDate,
                userGroupId
        );

        jdbcTemplate.update("""
                        INSERT INTO recurring_transaction_history (
                            recurring_transaction_id,
                            effective_from,
                            effective_to,
                            day_of_unit,
                            recurrence_interval,
                            recurrence_unit,
                            payment_date_adjustment_policy,
                            payment_amount,
                            recurring_transaction_end_date
                        )
                        VALUES (?, ?, NULL, 1, 1, 'DAY', ?, ?, ?)
                        """,
                recurringTransactionId,
                logicalDate,
                paymentDateAdjustmentPolicy,
                amount,
                logicalDate
        );

        jdbcTemplate.update("""
                        INSERT INTO recurring_transaction_details_history (
                            recurring_transaction_id,
                            recurring_transaction_description,
                            category_id,
                            financial_priority_id,
                            linked_account_id,
                            linked_bucket_id,
                            recurring_transaction_affects_account_balance,
                            recurring_transaction_affects_serenityline,
                            recurring_transaction_details_effective_from,
                            user_group_id
                        )
                        VALUES (?, ?, ?, ?, ?, NULL, TRUE, TRUE, ?, ?)
                        """,
                recurringTransactionId,
                description,
                categoryId,
                financialPriorityId,
                accountId,
                logicalDate,
                userGroupId
        );

        return recurringTransactionId;
    }


}