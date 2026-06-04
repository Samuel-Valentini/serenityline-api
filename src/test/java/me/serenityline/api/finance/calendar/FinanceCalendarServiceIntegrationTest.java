package me.serenityline.api.finance.calendar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class FinanceCalendarServiceIntegrationTest {

    private final UUID groupId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID collaboratorId = UUID.randomUUID();
    private final UUID accountAId = UUID.randomUUID();
    private final UUID accountBId = UUID.randomUUID();
    private final UUID accountCId = UUID.randomUUID();
    private final UUID inaccessibleAccountId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final UUID simulationGroupAId = UUID.randomUUID();
    private final UUID simulationGroupBId = UUID.randomUUID();
    private final UUID creditCardId = UUID.randomUUID();
    @Autowired
    private FinanceCalendarService financeCalendarService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
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
    @DisplayName("Owner with multiple accountIds should get stable and projected recurring movements only for requested accounts")
    void ownerWithMultipleAccountIdsShouldGetStableAndProjectedRecurringMovementsOnlyForRequestedAccounts() {
        givenUserGroup(groupId, "Owner group");
        givenUser(ownerId, groupId, "OWNER", "owner@example.com");

        givenCategory(categoryId, groupId, ownerId, "Casa");

        givenAccount(accountAId, groupId, "Conto A");
        givenAccount(accountBId, groupId, "Conto B");
        givenAccount(accountCId, groupId, "Conto C");

        UUID stableA = givenTransaction(
                accountAId,
                groupId,
                categoryId,
                LocalDate.of(2026, 6, 5),
                false,
                null,
                "Stable A"
        );

        UUID stableB = givenTransaction(
                accountBId,
                groupId,
                categoryId,
                LocalDate.of(2026, 6, 6),
                false,
                null,
                "Stable B"
        );

        givenTransaction(
                accountCId,
                groupId,
                categoryId,
                LocalDate.of(2026, 6, 7),
                false,
                null,
                "Stable C excluded"
        );

        UUID recurringA = givenRecurringTransaction(
                groupId,
                accountAId,
                false,
                null,
                "Recurring A"
        );

        UUID recurringC = givenRecurringTransaction(
                groupId,
                accountCId,
                false,
                null,
                "Recurring C excluded"
        );

        List<FinanceCalendarMovement> result = financeCalendarService.getCalendarMovements(
                ownerId,
                new FinanceCalendarSearchRequest(
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 6, 30),
                        List.of(accountAId, accountBId),
                        null
                )
        );

        assertThat(result)
                .extracting(FinanceCalendarMovement::accountId)
                .containsOnly(accountAId, accountBId);

        List<UUID> persistedTransactionIds = result.stream()
                .map(FinanceCalendarMovement::transactionId)
                .filter(Objects::nonNull)
                .toList();

        List<UUID> projectedRecurringTransactionIds = result.stream()
                .map(FinanceCalendarMovement::recurringTransactionId)
                .filter(Objects::nonNull)
                .toList();

        assertThat(persistedTransactionIds)
                .containsExactlyInAnyOrder(stableA, stableB);

        assertThat(projectedRecurringTransactionIds)
                .contains(recurringA)
                .doesNotContain(recurringC);
    }

    @Test
    @DisplayName("Collaborator with multiple accountIds should get only movements for linked accounts")
    void collaboratorWithMultipleAccountIdsShouldGetOnlyLinkedAccountMovements() {
        givenUserGroup(groupId, "Owner group");
        givenUser(ownerId, groupId, "OWNER", "owner@example.com");
        givenUser(collaboratorId, groupId, "COLLABORATOR", "collaborator@example.com");

        givenCategory(categoryId, groupId, ownerId, "Casa");

        givenAccount(accountAId, groupId, "Conto A");
        givenAccount(accountBId, groupId, "Conto B");
        givenAccount(inaccessibleAccountId, groupId, "Conto non accessibile");

        givenAccountUser(accountAId, collaboratorId, groupId);
        givenAccountUser(accountBId, collaboratorId, groupId);

        UUID stableA = givenTransaction(
                accountAId,
                groupId,
                categoryId,
                LocalDate.of(2026, 6, 5),
                false,
                null,
                "Stable A"
        );

        UUID stableB = givenTransaction(
                accountBId,
                groupId,
                categoryId,
                LocalDate.of(2026, 6, 6),
                false,
                null,
                "Stable B"
        );

        givenRecurringTransaction(
                groupId,
                accountAId,
                false,
                null,
                "Recurring A"
        );

        givenTransaction(
                inaccessibleAccountId,
                groupId,
                categoryId,
                LocalDate.of(2026, 6, 7),
                false,
                null,
                "Inaccessible excluded"
        );

        List<FinanceCalendarMovement> result = financeCalendarService.getCalendarMovements(
                collaboratorId,
                new FinanceCalendarSearchRequest(
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 6, 30),
                        List.of(accountAId, accountBId),
                        null
                )
        );

        assertThat(result)
                .extracting(FinanceCalendarMovement::accountId)
                .containsOnly(accountAId, accountBId);

        assertThat(result)
                .extracting(FinanceCalendarMovement::transactionId)
                .contains(stableA, stableB);
    }

    @Test
    @DisplayName("Selected simulation groups should include base and selected simulated movements")
    void selectedSimulationGroupsShouldIncludeBaseAndSelectedSimulatedMovements() {
        givenUserGroup(groupId, "Owner group");
        givenUser(ownerId, groupId, "OWNER", "owner@example.com");

        givenCategory(categoryId, groupId, ownerId, "Casa");

        givenAccount(accountAId, groupId, "Conto A");

        givenSimulationGroup(simulationGroupAId, groupId, "Simulation A");
        givenSimulationGroup(simulationGroupBId, groupId, "Simulation B");

        UUID base = givenTransaction(
                accountAId,
                groupId,
                categoryId,
                LocalDate.of(2026, 6, 5),
                false,
                null,
                "Base"
        );

        UUID selectedSimulation = givenTransaction(
                accountAId,
                groupId,
                categoryId,
                LocalDate.of(2026, 6, 6),
                true,
                simulationGroupAId,
                "Selected simulation"
        );

        UUID excludedSimulation = givenTransaction(
                accountAId,
                groupId,
                categoryId,
                LocalDate.of(2026, 6, 7),
                true,
                simulationGroupBId,
                "Excluded simulation"
        );

        UUID selectedRecurringSimulation = givenRecurringTransaction(
                groupId,
                accountAId,
                true,
                simulationGroupAId,
                "Selected recurring simulation"
        );

        UUID excludedRecurringSimulation = givenRecurringTransaction(
                groupId,
                accountAId,
                true,
                simulationGroupBId,
                "Excluded recurring simulation"
        );

        List<FinanceCalendarMovement> result = financeCalendarService.getCalendarMovements(
                ownerId,
                new FinanceCalendarSearchRequest(
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 6, 30),
                        List.of(accountAId),
                        List.of(simulationGroupAId)
                )
        );

        assertThat(result)
                .extracting(FinanceCalendarMovement::transactionId)
                .contains(base, selectedSimulation)
                .doesNotContain(excludedSimulation);

        assertThat(result)
                .extracting(FinanceCalendarMovement::recurringTransactionId)
                .contains(selectedRecurringSimulation)
                .doesNotContain(excludedRecurringSimulation);
    }

    @Test
    @DisplayName("Credit card persisted transactions should generate technical charge in next month charge date")
    void creditCardPersistedTransactionsShouldGenerateTechnicalChargeInNextMonthChargeDate() {
        givenUserGroup(groupId, "Owner group");
        givenUser(ownerId, groupId, "OWNER", "owner@example.com");

        givenCategory(categoryId, groupId, ownerId, "Casa");
        givenAccount(accountAId, groupId, "Conto A");
        givenCreditCard(creditCardId, accountAId, groupId, "Carta principale", 15);

        UUID cardTransactionId = givenCreditCardTransaction(
                accountAId,
                groupId,
                categoryId,
                creditCardId,
                LocalDate.of(2026, 1, 10),
                BigDecimal.valueOf(-42),
                "Supermercato"
        );

        List<FinanceCalendarMovement> result = financeCalendarService.getCalendarMovements(
                ownerId,
                new FinanceCalendarSearchRequest(
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 2, 28),
                        List.of(accountAId),
                        null
                )
        );

        assertThat(result)
                .singleElement()
                .satisfies(movement -> {
                    assertThat(movement.type())
                            .isEqualTo(FinanceCalendarMovementType.TECHNICAL_CREDIT_CARD_CHARGE_FROM_PERSISTED_TRANSACTION);
                    assertThat(movement.transactionId()).isEqualTo(cardTransactionId);
                    assertThat(movement.recurringTransactionId()).isNull();
                    assertThat(movement.logicalDate()).isEqualTo(LocalDate.of(2026, 1, 10));
                    assertThat(movement.chargeDate()).isEqualTo(LocalDate.of(2026, 2, 15));
                    assertThat(movement.amount()).isEqualByComparingTo("-42.00");
                    assertThat(movement.affectsAccountBalance()).isTrue();
                    assertThat(movement.affectsSerenityline()).isFalse();
                    assertThat(movement.categoryId()).isEqualTo(categoryId);
                    assertThat(movement.accountId()).isEqualTo(accountAId);
                    assertThat(movement.creditCardId()).isEqualTo(creditCardId);
                    assertThat(movement.confirmed()).isFalse();
                    assertThat(movement.userEntered()).isFalse();
                });
    }

    @Test
    @DisplayName("Credit card charge day 31 should use last day when next month is shorter")
    void creditCardChargeDay31ShouldUseLastDayWhenNextMonthIsShorter() {
        givenUserGroup(groupId, "Owner group");
        givenUser(ownerId, groupId, "OWNER", "owner@example.com");

        givenCategory(categoryId, groupId, ownerId, "Casa");
        givenAccount(accountAId, groupId, "Conto A");
        givenCreditCard(creditCardId, accountAId, groupId, "Carta principale", 31);

        UUID cardTransactionId = givenCreditCardTransaction(
                accountAId,
                groupId,
                categoryId,
                creditCardId,
                LocalDate.of(2026, 1, 10),
                BigDecimal.valueOf(-42),
                "Supermercato"
        );

        List<FinanceCalendarMovement> result = financeCalendarService.getCalendarMovements(
                ownerId,
                new FinanceCalendarSearchRequest(
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 2, 28),
                        List.of(accountAId),
                        null
                )
        );

        assertThat(result)
                .singleElement()
                .satisfies(movement -> {
                    assertThat(movement.type())
                            .isEqualTo(FinanceCalendarMovementType.TECHNICAL_CREDIT_CARD_CHARGE_FROM_PERSISTED_TRANSACTION);
                    assertThat(movement.transactionId()).isEqualTo(cardTransactionId);
                    assertThat(movement.logicalDate()).isEqualTo(LocalDate.of(2026, 1, 10));
                    assertThat(movement.chargeDate()).isEqualTo(LocalDate.of(2026, 2, 28));
                    assertThat(movement.amount()).isEqualByComparingTo("-42.00");
                    assertThat(movement.affectsAccountBalance()).isTrue();
                    assertThat(movement.affectsSerenityline()).isFalse();
                    assertThat(movement.creditCardId()).isEqualTo(creditCardId);
                });
    }

    @Test
    @DisplayName("Credit card projected recurring transactions should generate technical charge in next month charge date")
    void creditCardProjectedRecurringTransactionsShouldGenerateTechnicalChargeInNextMonthChargeDate() {
        givenUserGroup(groupId, "Owner group");
        givenUser(ownerId, groupId, "OWNER", "owner@example.com");

        givenCategory(categoryId, groupId, ownerId, "Casa");
        givenAccount(accountAId, groupId, "Conto A");
        givenCreditCard(creditCardId, accountAId, groupId, "Carta principale", 15);

        UUID recurringTransactionId = givenCreditCardRecurringTransaction(
                groupId,
                accountAId,
                categoryId,
                creditCardId,
                "Abbonamento carta",
                LocalDate.of(2026, 1, 10),
                BigDecimal.valueOf(-29)
        );

        List<FinanceCalendarMovement> result = financeCalendarService.getCalendarMovements(
                ownerId,
                new FinanceCalendarSearchRequest(
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 2, 28),
                        List.of(accountAId),
                        null
                )
        );

        assertThat(result).hasSize(2);

        assertThat(result)
                .filteredOn(movement -> movement.type()
                        == FinanceCalendarMovementType.PROJECTED_RECURRING_TRANSACTION)
                .singleElement()
                .satisfies(movement -> {
                    assertThat(movement.transactionId()).isNull();
                    assertThat(movement.recurringTransactionId()).isEqualTo(recurringTransactionId);
                    assertThat(movement.logicalDate()).isEqualTo(LocalDate.of(2026, 2, 10));
                    assertThat(movement.chargeDate()).isEqualTo(LocalDate.of(2026, 2, 10));
                    assertThat(movement.amount()).isEqualByComparingTo("-29.00");
                    assertThat(movement.affectsAccountBalance()).isFalse();
                    assertThat(movement.affectsSerenityline()).isTrue();
                    assertThat(movement.categoryId()).isEqualTo(categoryId);
                    assertThat(movement.financialPriorityId()).isEqualTo(financialPriorityId);
                    assertThat(movement.accountId()).isEqualTo(accountAId);
                    assertThat(movement.creditCardId()).isEqualTo(creditCardId);
                });

        assertThat(result)
                .filteredOn(movement -> movement.type()
                        == FinanceCalendarMovementType.TECHNICAL_CREDIT_CARD_CHARGE_FROM_PROJECTED_RECURRING_TRANSACTION)
                .singleElement()
                .satisfies(movement -> {
                    assertThat(movement.transactionId()).isNull();
                    assertThat(movement.recurringTransactionId()).isEqualTo(recurringTransactionId);
                    assertThat(movement.logicalDate()).isEqualTo(LocalDate.of(2026, 1, 10));
                    assertThat(movement.chargeDate()).isEqualTo(LocalDate.of(2026, 2, 15));
                    assertThat(movement.amount()).isEqualByComparingTo("-29.00");
                    assertThat(movement.affectsAccountBalance()).isTrue();
                    assertThat(movement.affectsSerenityline()).isFalse();
                    assertThat(movement.categoryId()).isEqualTo(categoryId);
                    assertThat(movement.financialPriorityId()).isEqualTo(financialPriorityId);
                    assertThat(movement.accountId()).isEqualTo(accountAId);
                    assertThat(movement.creditCardId()).isEqualTo(creditCardId);
                    assertThat(movement.confirmed()).isFalse();
                    assertThat(movement.userEntered()).isFalse();
                });
    }

    @Test
    @DisplayName("Confirmed credit card recurring occurrence should not duplicate projected technical charge")
    void confirmedCreditCardRecurringOccurrenceShouldNotDuplicateProjectedTechnicalCharge() {
        givenUserGroup(groupId, "Owner group");
        givenUser(ownerId, groupId, "OWNER", "owner@example.com");

        givenCategory(categoryId, groupId, ownerId, "Casa");
        givenAccount(accountAId, groupId, "Conto A");
        givenCreditCard(creditCardId, accountAId, groupId, "Carta principale", 15);

        UUID recurringTransactionId = givenCreditCardRecurringTransaction(
                groupId,
                accountAId,
                categoryId,
                creditCardId,
                "Abbonamento carta",
                LocalDate.of(2026, 1, 10),
                BigDecimal.valueOf(-29)
        );

        UUID confirmedTransactionId = givenConfirmedCreditCardRecurringTransaction(
                accountAId,
                groupId,
                categoryId,
                creditCardId,
                recurringTransactionId,
                LocalDate.of(2026, 1, 10),
                BigDecimal.valueOf(-29),
                "Abbonamento carta"
        );

        List<FinanceCalendarMovement> result = financeCalendarService.getCalendarMovements(
                ownerId,
                new FinanceCalendarSearchRequest(
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 2, 28),
                        List.of(accountAId),
                        null
                )
        );

        assertThat(result)
                .filteredOn(movement -> movement.type()
                        == FinanceCalendarMovementType.TECHNICAL_CREDIT_CARD_CHARGE_FROM_PROJECTED_RECURRING_TRANSACTION)
                .isEmpty();

        assertThat(result)
                .filteredOn(movement -> movement.type()
                        == FinanceCalendarMovementType.TECHNICAL_CREDIT_CARD_CHARGE_FROM_PERSISTED_TRANSACTION)
                .singleElement()
                .satisfies(movement -> {
                    assertThat(movement.transactionId()).isEqualTo(confirmedTransactionId);
                    assertThat(movement.recurringTransactionId()).isEqualTo(recurringTransactionId);
                    assertThat(movement.logicalDate()).isEqualTo(LocalDate.of(2026, 1, 10));
                    assertThat(movement.chargeDate()).isEqualTo(LocalDate.of(2026, 2, 15));
                    assertThat(movement.amount()).isEqualByComparingTo("-29.00");
                    assertThat(movement.affectsAccountBalance()).isTrue();
                    assertThat(movement.affectsSerenityline()).isFalse();
                    assertThat(movement.creditCardId()).isEqualTo(creditCardId);
                });
    }

    @Test
    @DisplayName("Credit card technical charge should not be returned when settlement date is outside requested range")
    void creditCardTechnicalChargeShouldNotBeReturnedWhenSettlementDateIsOutsideRequestedRange() {
        givenUserGroup(groupId, "Owner group");
        givenUser(ownerId, groupId, "OWNER", "owner@example.com");

        givenCategory(categoryId, groupId, ownerId, "Casa");
        givenAccount(accountAId, groupId, "Conto A");
        givenCreditCard(creditCardId, accountAId, groupId, "Carta principale", 15);

        givenCreditCardTransaction(
                accountAId,
                groupId,
                categoryId,
                creditCardId,
                LocalDate.of(2026, 1, 10),
                BigDecimal.valueOf(-42),
                "Supermercato"
        );

        List<FinanceCalendarMovement> result = financeCalendarService.getCalendarMovements(
                ownerId,
                new FinanceCalendarSearchRequest(
                        LocalDate.of(2026, 2, 16),
                        LocalDate.of(2026, 2, 28),
                        List.of(accountAId),
                        null
                )
        );

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Credit card source month should return original persisted transaction but not next month technical charge")
    void creditCardSourceMonthShouldReturnOriginalPersistedTransactionButNotNextMonthTechnicalCharge() {
        givenUserGroup(groupId, "Owner group");
        givenUser(ownerId, groupId, "OWNER", "owner@example.com");

        givenCategory(categoryId, groupId, ownerId, "Casa");
        givenAccount(accountAId, groupId, "Conto A");
        givenCreditCard(creditCardId, accountAId, groupId, "Carta principale", 15);

        UUID transactionId = givenCreditCardTransaction(
                accountAId,
                groupId,
                categoryId,
                creditCardId,
                LocalDate.of(2026, 1, 10),
                BigDecimal.valueOf(-42),
                "Supermercato"
        );

        List<FinanceCalendarMovement> result = financeCalendarService.getCalendarMovements(
                ownerId,
                new FinanceCalendarSearchRequest(
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 31),
                        List.of(accountAId),
                        null
                )
        );

        assertThat(result)
                .singleElement()
                .satisfies(movement -> {
                    assertThat(movement.type()).isEqualTo(FinanceCalendarMovementType.PERSISTED_TRANSACTION);
                    assertThat(movement.transactionId()).isEqualTo(transactionId);
                    assertThat(movement.chargeDate()).isEqualTo(LocalDate.of(2026, 1, 10));
                    assertThat(movement.affectsAccountBalance()).isFalse();
                    assertThat(movement.affectsSerenityline()).isTrue();
                });
    }

    @Test
    @DisplayName("Credit card persisted transactions should generate one technical charge per original transaction")
    void creditCardPersistedTransactionsShouldGenerateOneTechnicalChargePerOriginalTransaction() {
        givenUserGroup(groupId, "Owner group");
        givenUser(ownerId, groupId, "OWNER", "owner@example.com");

        givenCategory(categoryId, groupId, ownerId, "Casa");
        givenAccount(accountAId, groupId, "Conto A");
        givenCreditCard(creditCardId, accountAId, groupId, "Carta principale", 15);

        UUID firstTransactionId = givenCreditCardTransaction(
                accountAId,
                groupId,
                categoryId,
                creditCardId,
                LocalDate.of(2026, 1, 5),
                BigDecimal.valueOf(-10),
                "Spesa 1"
        );

        UUID secondTransactionId = givenCreditCardTransaction(
                accountAId,
                groupId,
                categoryId,
                creditCardId,
                LocalDate.of(2026, 1, 20),
                BigDecimal.valueOf(-20),
                "Spesa 2"
        );

        List<FinanceCalendarMovement> result = financeCalendarService.getCalendarMovements(
                ownerId,
                new FinanceCalendarSearchRequest(
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 2, 28),
                        List.of(accountAId),
                        null
                )
        );

        assertThat(result)
                .filteredOn(movement -> movement.type()
                        == FinanceCalendarMovementType.TECHNICAL_CREDIT_CARD_CHARGE_FROM_PERSISTED_TRANSACTION)
                .hasSize(2)
                .extracting(FinanceCalendarMovement::transactionId)
                .containsExactlyInAnyOrder(firstTransactionId, secondTransactionId);

        assertThat(result)
                .filteredOn(movement -> movement.type()
                        == FinanceCalendarMovementType.TECHNICAL_CREDIT_CARD_CHARGE_FROM_PERSISTED_TRANSACTION)
                .extracting(FinanceCalendarMovement::amount)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactlyInAnyOrder(
                        BigDecimal.valueOf(-10),
                        BigDecimal.valueOf(-20)
                );
    }

    @Test
    @DisplayName("Non credit card persisted transaction should not generate technical credit card charge")
    void nonCreditCardPersistedTransactionShouldNotGenerateTechnicalCreditCardCharge() {
        givenUserGroup(groupId, "Owner group");
        givenUser(ownerId, groupId, "OWNER", "owner@example.com");

        givenCategory(categoryId, groupId, ownerId, "Casa");
        givenAccount(accountAId, groupId, "Conto A");

        givenTransaction(
                accountAId,
                groupId,
                categoryId,
                LocalDate.of(2026, 1, 10),
                false,
                null,
                "Bonifico"
        );

        List<FinanceCalendarMovement> result = financeCalendarService.getCalendarMovements(
                ownerId,
                new FinanceCalendarSearchRequest(
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 2, 28),
                        List.of(accountAId),
                        null
                )
        );

        assertThat(result)
                .noneMatch(movement -> movement.type()
                        == FinanceCalendarMovementType.TECHNICAL_CREDIT_CARD_CHARGE_FROM_PERSISTED_TRANSACTION);
    }

    @Test
    @DisplayName("Credit card transaction already affecting account balance should not generate technical charge")
    void creditCardTransactionAlreadyAffectingAccountBalanceShouldNotGenerateTechnicalCharge() {
        givenUserGroup(groupId, "Owner group");
        givenUser(ownerId, groupId, "OWNER", "owner@example.com");

        givenCategory(categoryId, groupId, ownerId, "Casa");
        givenAccount(accountAId, groupId, "Conto A");
        givenCreditCard(creditCardId, accountAId, groupId, "Carta principale", 15);

        givenCreditCardTransactionWithFlags(
                accountAId,
                groupId,
                categoryId,
                creditCardId,
                LocalDate.of(2026, 1, 10),
                BigDecimal.valueOf(-42),
                "Movimento già a saldo",
                true,
                false
        );

        List<FinanceCalendarMovement> result = financeCalendarService.getCalendarMovements(
                ownerId,
                new FinanceCalendarSearchRequest(
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 2, 28),
                        List.of(accountAId),
                        null
                )
        );

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Credit card refund should generate positive technical charge")
    void creditCardRefundShouldGeneratePositiveTechnicalCharge() {
        givenUserGroup(groupId, "Owner group");
        givenUser(ownerId, groupId, "OWNER", "owner@example.com");

        givenCategory(categoryId, groupId, ownerId, "Casa");
        givenAccount(accountAId, groupId, "Conto A");
        givenCreditCard(creditCardId, accountAId, groupId, "Carta principale", 15);

        UUID refundTransactionId = givenCreditCardTransaction(
                accountAId,
                groupId,
                categoryId,
                creditCardId,
                LocalDate.of(2026, 1, 10),
                BigDecimal.valueOf(25),
                "Rimborso carta"
        );

        List<FinanceCalendarMovement> result = financeCalendarService.getCalendarMovements(
                ownerId,
                new FinanceCalendarSearchRequest(
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 2, 28),
                        List.of(accountAId),
                        null
                )
        );

        assertThat(result)
                .singleElement()
                .satisfies(movement -> {
                    assertThat(movement.type())
                            .isEqualTo(FinanceCalendarMovementType.TECHNICAL_CREDIT_CARD_CHARGE_FROM_PERSISTED_TRANSACTION);
                    assertThat(movement.transactionId()).isEqualTo(refundTransactionId);
                    assertThat(movement.amount()).isEqualByComparingTo("25.00");
                    assertThat(movement.chargeDate()).isEqualTo(LocalDate.of(2026, 2, 15));
                    assertThat(movement.affectsAccountBalance()).isTrue();
                    assertThat(movement.affectsSerenityline()).isFalse();
                });
    }

    @Test
    @DisplayName("Different credit cards should use their own charge day")
    void differentCreditCardsShouldUseTheirOwnChargeDay() {
        UUID secondCreditCardId = UUID.randomUUID();

        givenUserGroup(groupId, "Owner group");
        givenUser(ownerId, groupId, "OWNER", "owner@example.com");

        givenCategory(categoryId, groupId, ownerId, "Casa");
        givenAccount(accountAId, groupId, "Conto A");
        givenCreditCard(creditCardId, accountAId, groupId, "Carta 15", 15);
        givenCreditCard(secondCreditCardId, accountAId, groupId, "Carta 25", 25);

        givenCreditCardTransaction(
                accountAId,
                groupId,
                categoryId,
                creditCardId,
                LocalDate.of(2026, 1, 10),
                BigDecimal.valueOf(-10),
                "Spesa carta 15"
        );

        givenCreditCardTransaction(
                accountAId,
                groupId,
                categoryId,
                secondCreditCardId,
                LocalDate.of(2026, 1, 10),
                BigDecimal.valueOf(-20),
                "Spesa carta 25"
        );

        List<FinanceCalendarMovement> result = financeCalendarService.getCalendarMovements(
                ownerId,
                new FinanceCalendarSearchRequest(
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 2, 28),
                        List.of(accountAId),
                        null
                )
        );

        assertThat(result)
                .filteredOn(movement -> movement.creditCardId().equals(creditCardId))
                .singleElement()
                .satisfies(movement -> assertThat(movement.chargeDate())
                        .isEqualTo(LocalDate.of(2026, 2, 15)));

        assertThat(result)
                .filteredOn(movement -> movement.creditCardId().equals(secondCreditCardId))
                .singleElement()
                .satisfies(movement -> assertThat(movement.chargeDate())
                        .isEqualTo(LocalDate.of(2026, 2, 25)));
    }

    @Test
    @DisplayName("Credit card projected recurring charge day 31 should use last day when next month is shorter")
    void creditCardProjectedRecurringChargeDay31ShouldUseLastDayWhenNextMonthIsShorter() {
        givenUserGroup(groupId, "Owner group");
        givenUser(ownerId, groupId, "OWNER", "owner@example.com");

        givenCategory(categoryId, groupId, ownerId, "Casa");
        givenAccount(accountAId, groupId, "Conto A");
        givenCreditCard(creditCardId, accountAId, groupId, "Carta principale", 31);

        UUID recurringTransactionId = givenCreditCardRecurringTransaction(
                groupId,
                accountAId,
                categoryId,
                creditCardId,
                "Abbonamento carta",
                LocalDate.of(2026, 1, 10),
                BigDecimal.valueOf(-29)
        );

        List<FinanceCalendarMovement> result = financeCalendarService.getCalendarMovements(
                ownerId,
                new FinanceCalendarSearchRequest(
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 2, 28),
                        List.of(accountAId),
                        null
                )
        );

        assertThat(result)
                .filteredOn(movement -> movement.type()
                        == FinanceCalendarMovementType.TECHNICAL_CREDIT_CARD_CHARGE_FROM_PROJECTED_RECURRING_TRANSACTION)
                .singleElement()
                .satisfies(movement -> {
                    assertThat(movement.recurringTransactionId()).isEqualTo(recurringTransactionId);
                    assertThat(movement.logicalDate()).isEqualTo(LocalDate.of(2026, 1, 10));
                    assertThat(movement.chargeDate()).isEqualTo(LocalDate.of(2026, 2, 28));
                    assertThat(movement.amount()).isEqualByComparingTo("-29.00");
                });
    }

    @Test
    @DisplayName("Non credit card projected recurring transaction should not generate technical credit card charge")
    void nonCreditCardProjectedRecurringTransactionShouldNotGenerateTechnicalCreditCardCharge() {
        givenUserGroup(groupId, "Owner group");
        givenUser(ownerId, groupId, "OWNER", "owner@example.com");

        givenCategory(categoryId, groupId, ownerId, "Casa");
        givenAccount(accountAId, groupId, "Conto A");

        UUID recurringTransactionId = givenRecurringTransaction(
                groupId,
                accountAId,
                false,
                null,
                "Ricorrente normale"
        );

        List<FinanceCalendarMovement> result = financeCalendarService.getCalendarMovements(
                ownerId,
                new FinanceCalendarSearchRequest(
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 2, 28),
                        List.of(accountAId),
                        null
                )
        );

        assertThat(result)
                .noneMatch(movement -> movement.type()
                        == FinanceCalendarMovementType.TECHNICAL_CREDIT_CARD_CHARGE_FROM_PROJECTED_RECURRING_TRANSACTION);

        assertThat(result)
                .filteredOn(movement -> movement.type()
                        == FinanceCalendarMovementType.PROJECTED_RECURRING_TRANSACTION)
                .extracting(FinanceCalendarMovement::recurringTransactionId)
                .contains(recurringTransactionId);
    }

    @Test
    @DisplayName("Daily balances should apply credit card expense to serenityline first and account balance on charge date")
    void dailyBalancesShouldApplyCreditCardExpenseToSerenitylineFirstAndAccountBalanceOnChargeDate() {
        givenUserGroup(groupId, "Owner group");
        givenUser(ownerId, groupId, "OWNER", "owner@example.com");

        givenCategory(categoryId, groupId, ownerId, "Casa");

        givenAccountWithOpeningBalance(
                accountAId,
                groupId,
                "Conto A",
                LocalDate.of(2026, 1, 1),
                BigDecimal.valueOf(1000)
        );

        givenCreditCard(creditCardId, accountAId, groupId, "Carta principale", 15);

        givenCreditCardTransaction(
                accountAId,
                groupId,
                categoryId,
                creditCardId,
                LocalDate.of(2026, 1, 10),
                BigDecimal.valueOf(-100),
                "Supermercato"
        );

        List<FinanceCalendarDailyBalance> result = financeCalendarService.getDailyBalances(
                ownerId,
                new FinanceCalendarSearchRequest(
                        LocalDate.of(2026, 1, 9),
                        LocalDate.of(2026, 2, 16),
                        List.of(accountAId),
                        null
                )
        );

        FinanceCalendarAccountDailyBalance beforeCardExpense =
                accountBalanceOn(result, LocalDate.of(2026, 1, 9), accountAId);

        assertThat(beforeCardExpense.endOfDayAccountBalance()).isEqualByComparingTo("1000.00");
        assertThat(beforeCardExpense.endOfDaySerenityline()).isEqualByComparingTo("1000.00");

        FinanceCalendarAccountDailyBalance afterCardExpense =
                accountBalanceOn(result, LocalDate.of(2026, 1, 10), accountAId);

        assertThat(afterCardExpense.endOfDayAccountBalance()).isEqualByComparingTo("1000.00");
        assertThat(afterCardExpense.endOfDaySerenityline()).isEqualByComparingTo("900.00");

        FinanceCalendarAccountDailyBalance beforeCharge =
                accountBalanceOn(result, LocalDate.of(2026, 2, 14), accountAId);

        assertThat(beforeCharge.endOfDayAccountBalance()).isEqualByComparingTo("1000.00");
        assertThat(beforeCharge.endOfDaySerenityline()).isEqualByComparingTo("900.00");

        FinanceCalendarAccountDailyBalance afterCharge =
                accountBalanceOn(result, LocalDate.of(2026, 2, 15), accountAId);

        assertThat(afterCharge.endOfDayAccountBalance()).isEqualByComparingTo("900.00");
        assertThat(afterCharge.endOfDaySerenityline()).isEqualByComparingTo("900.00");
    }

    @Test
    @DisplayName("Daily balances should apply projected credit card recurring expense and technical charge")
    void dailyBalancesShouldApplyProjectedCreditCardRecurringExpenseAndTechnicalCharge() {
        givenUserGroup(groupId, "Owner group");
        givenUser(ownerId, groupId, "OWNER", "owner@example.com");

        givenCategory(categoryId, groupId, ownerId, "Casa");

        givenAccountWithOpeningBalance(
                accountAId,
                groupId,
                "Conto A",
                LocalDate.of(2026, 1, 1),
                BigDecimal.valueOf(1000)
        );

        givenCreditCard(creditCardId, accountAId, groupId, "Carta principale", 15);

        givenCreditCardRecurringTransaction(
                groupId,
                accountAId,
                categoryId,
                creditCardId,
                "Abbonamento carta",
                LocalDate.of(2026, 1, 10),
                BigDecimal.valueOf(-100)
        );

        List<FinanceCalendarDailyBalance> result = financeCalendarService.getDailyBalances(
                ownerId,
                new FinanceCalendarSearchRequest(
                        LocalDate.of(2026, 1, 9),
                        LocalDate.of(2026, 2, 16),
                        List.of(accountAId),
                        null
                )
        );

        FinanceCalendarAccountDailyBalance afterJanuaryOccurrence =
                accountBalanceOn(result, LocalDate.of(2026, 1, 10), accountAId);

        assertThat(afterJanuaryOccurrence.endOfDayAccountBalance()).isEqualByComparingTo("1000.00");
        assertThat(afterJanuaryOccurrence.endOfDaySerenityline()).isEqualByComparingTo("900.00");

        FinanceCalendarAccountDailyBalance afterFebruaryOccurrence =
                accountBalanceOn(result, LocalDate.of(2026, 2, 10), accountAId);

        assertThat(afterFebruaryOccurrence.endOfDayAccountBalance()).isEqualByComparingTo("1000.00");
        assertThat(afterFebruaryOccurrence.endOfDaySerenityline()).isEqualByComparingTo("800.00");

        FinanceCalendarAccountDailyBalance afterFebruaryCharge =
                accountBalanceOn(result, LocalDate.of(2026, 2, 15), accountAId);

        assertThat(afterFebruaryCharge.endOfDayAccountBalance()).isEqualByComparingTo("900.00");
        assertThat(afterFebruaryCharge.endOfDaySerenityline()).isEqualByComparingTo("800.00");
    }

    @Test
    @DisplayName("Technical credit card charge description should be localized in Italian")
    void technicalCreditCardChargeDescriptionShouldBeLocalizedInItalian() {
        Locale previousLocale = LocaleContextHolder.getLocale();

        try {
            LocaleContextHolder.setLocale(Locale.ITALIAN);

            givenUserGroup(groupId, "Owner group");
            givenUser(ownerId, groupId, "OWNER", "owner@example.com");

            givenCategory(categoryId, groupId, ownerId, "Casa");
            givenAccount(accountAId, groupId, "Conto A");
            givenCreditCard(creditCardId, accountAId, groupId, "Carta principale", 15);

            givenCreditCardTransaction(
                    accountAId,
                    groupId,
                    categoryId,
                    creditCardId,
                    LocalDate.of(2026, 1, 10),
                    BigDecimal.valueOf(-42),
                    "Supermercato"
            );

            List<FinanceCalendarMovement> result = financeCalendarService.getCalendarMovements(
                    ownerId,
                    new FinanceCalendarSearchRequest(
                            LocalDate.of(2026, 2, 1),
                            LocalDate.of(2026, 2, 28),
                            List.of(accountAId),
                            null
                    )
            );

            assertThat(result)
                    .singleElement()
                    .satisfies(movement -> assertThat(movement.description())
                            .isEqualTo("Addebito carta di credito - Supermercato"));
        } finally {
            LocaleContextHolder.setLocale(previousLocale);
        }
    }

    @Test
    @DisplayName("Technical credit card charge description should be localized in English")
    void technicalCreditCardChargeDescriptionShouldBeLocalizedInEnglish() {
        Locale previousLocale = LocaleContextHolder.getLocale();

        try {
            LocaleContextHolder.setLocale(Locale.ENGLISH);

            givenUserGroup(groupId, "Owner group");
            givenUser(ownerId, groupId, "OWNER", "owner@example.com");

            givenCategory(categoryId, groupId, ownerId, "Casa");
            givenAccount(accountAId, groupId, "Conto A");
            givenCreditCard(creditCardId, accountAId, groupId, "Carta principale", 15);

            givenCreditCardTransaction(
                    accountAId,
                    groupId,
                    categoryId,
                    creditCardId,
                    LocalDate.of(2026, 1, 10),
                    BigDecimal.valueOf(-42),
                    "Groceries"
            );

            List<FinanceCalendarMovement> result = financeCalendarService.getCalendarMovements(
                    ownerId,
                    new FinanceCalendarSearchRequest(
                            LocalDate.of(2026, 2, 1),
                            LocalDate.of(2026, 2, 28),
                            List.of(accountAId),
                            null
                    )
            );

            assertThat(result)
                    .singleElement()
                    .satisfies(movement -> assertThat(movement.description())
                            .isEqualTo("Credit card charge - Groceries"));
        } finally {
            LocaleContextHolder.setLocale(previousLocale);
        }
    }

    @Test
    @DisplayName("Technical credit card charge should require category")
    void technicalCreditCardChargeShouldRequireCategory() {
        assertThatThrownBy(() -> new FinanceCalendarMovement(
                FinanceCalendarMovementType.TECHNICAL_CREDIT_CARD_CHARGE_FROM_PERSISTED_TRANSACTION,
                UUID.randomUUID(),
                null,
                LocalDate.of(2026, 1, 10),
                LocalDate.of(2026, 2, 15),
                "Addebito carta",
                BigDecimal.valueOf(-42),
                true,
                false,
                null,
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                false,
                false,
                null,
                false,
                false
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Technical credit card charge should require credit card")
    void technicalCreditCardChargeShouldRequireCreditCard() {
        assertThatThrownBy(() -> new FinanceCalendarMovement(
                FinanceCalendarMovementType.TECHNICAL_CREDIT_CARD_CHARGE_FROM_PROJECTED_RECURRING_TRANSACTION,
                null,
                UUID.randomUUID(),
                LocalDate.of(2026, 1, 10),
                LocalDate.of(2026, 2, 15),
                "Addebito carta",
                BigDecimal.valueOf(-42),
                true,
                false,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                false,
                false,
                null,
                false,
                false
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finance.calendar.creditCardIdRequired");
    }


    private FinanceCalendarAccountDailyBalance accountBalanceOn(
            List<FinanceCalendarDailyBalance> balances,
            LocalDate date,
            UUID accountId
    ) {
        return balances.stream()
                .filter(balance -> balance.date().equals(date))
                .findFirst()
                .orElseThrow()
                .accounts()
                .stream()
                .filter(account -> account.accountId().equals(accountId))
                .findFirst()
                .orElseThrow();
    }

    private UUID givenConfirmedCreditCardRecurringTransaction(
            UUID accountId,
            UUID userGroupId,
            UUID categoryId,
            UUID creditCardId,
            UUID recurringTransactionId,
            LocalDate chargeDate,
            BigDecimal amount,
            String description
    ) {
        UUID transactionId = UUID.randomUUID();

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
                            recurring_transaction_id,
                            recurring_transaction_logical_date,
                            recurring_transaction_confirmed_at,
                            transaction_is_simulated,
                            simulation_group_id,
                            transaction_is_user_entered,
                            transaction_reminder_enabled,
                            transaction_reminder_days_before,
                            user_group_id
                        )
                        VALUES (?, ?, ?, false, true, ?, ?, true, ?, ?, ?, ?, CURRENT_TIMESTAMP, false, NULL, false, true, 7, ?)
                        """,
                transactionId,
                description,
                amount,
                categoryId,
                chargeDate,
                accountId,
                creditCardId,
                recurringTransactionId,
                chargeDate,
                userGroupId
        );

        return transactionId;
    }

    private UUID givenCreditCardRecurringTransaction(
            UUID userGroupId,
            UUID accountId,
            UUID categoryId,
            UUID creditCardId,
            String description,
            LocalDate firstPaymentDate,
            BigDecimal amount
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
                        VALUES (?, true, ?, false, NULL, true, 7, ?)
                        """,
                recurringTransactionId,
                firstPaymentDate,
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
                firstPaymentDate.getDayOfMonth(),
                amount
        );

        jdbcTemplate.update("""
                        INSERT INTO recurring_transaction_details_history (
                            recurring_transaction_id,
                            recurring_transaction_description,
                            category_id,
                            financial_priority_id,
                            linked_account_id,
                            linked_credit_card_id,
                            recurring_transaction_affects_account_balance,
                            recurring_transaction_affects_serenityline,
                            recurring_transaction_details_effective_from,
                            user_group_id
                        )
                        VALUES (?, ?, ?, ?, ?, ?, false, true, ?, ?)
                        """,
                recurringTransactionId,
                description,
                categoryId,
                financialPriorityId,
                accountId,
                creditCardId,
                firstPaymentDate.withDayOfMonth(1),
                userGroupId
        );

        return recurringTransactionId;
    }

    private void givenCreditCard(
            UUID creditCardId,
            UUID accountId,
            UUID userGroupId,
            String name,
            int chargeDay
    ) {
        jdbcTemplate.update("""
                        INSERT INTO credit_cards (
                            credit_card_id,
                            credit_card_name,
                            credit_card_description,
                            credit_card_charge_day,
                            account_id,
                            user_group_id
                        )
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                creditCardId,
                name,
                "Test credit card",
                chargeDay,
                accountId,
                userGroupId
        );
    }

    private UUID givenCreditCardTransaction(
            UUID accountId,
            UUID userGroupId,
            UUID categoryId,
            UUID creditCardId,
            LocalDate chargeDate,
            BigDecimal amount,
            String description
    ) {
        UUID transactionId = UUID.randomUUID();

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
                            transaction_is_simulated,
                            simulation_group_id,
                            transaction_is_user_entered,
                            transaction_reminder_enabled,
                            transaction_reminder_days_before,
                            user_group_id
                        )
                        VALUES (?, ?, ?, false, true, ?, ?, true, ?, ?, false, NULL, true, true, 7, ?)
                        """,
                transactionId,
                description,
                amount,
                categoryId,
                chargeDate,
                accountId,
                creditCardId,
                userGroupId
        );

        return transactionId;
    }

    private void givenUserGroup(UUID userGroupId, String name) {
        jdbcTemplate.update("""
                INSERT INTO user_groups (
                    user_group_id,
                    user_group_name
                )
                VALUES (?, ?)
                """, userGroupId, name);
    }

    private void givenUser(UUID userId, UUID userGroupId, String role, String email) {
        jdbcTemplate.update("""
                        INSERT INTO users (
                            user_id,
                            user_group_id,
                            user_name,
                            email,
                            user_role,
                            user_platform_role,
                            preferred_locale,
                            preferred_theme,
                            wants_invoice,
                            user_password_hash,
                            user_is_enabled,
                            email_2fa_enabled,
                            payment_email_reminders_enabled
                        )
                        VALUES (?, ?, ?, ?, ?, 'USER', 'it-IT', 'DEFAULT', false, ?, true, false, true)
                        """,
                userId,
                userGroupId,
                "Test User",
                email,
                role,
                "{bcrypt}hash"
        );
    }

    private void givenCategory(UUID categoryId, UUID userGroupId, UUID createdByUserId, String name) {
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
                VALUES (?, true)
                """, categoryId);

        jdbcTemplate.update("""
                INSERT INTO category_details_history (
                    category_id,
                    category_name,
                    category_description
                )
                VALUES (?, ?, ?)
                """, categoryId, name, "Test category");
    }

    private void givenAccount(UUID accountId, UUID userGroupId, String name) {
        jdbcTemplate.update("""
                INSERT INTO accounts (
                    account_id,
                    user_group_id,
                    account_name,
                    currency,
                    opening_balance,
                    opening_balance_date
                )
                VALUES (?, ?, ?, 'EUR', 0, ?)
                """, accountId, userGroupId, name, LocalDate.of(2026, 1, 1));
    }

    private void givenAccountUser(UUID accountId, UUID userId, UUID userGroupId) {
        jdbcTemplate.update("""
                INSERT INTO accounts_users (
                    account_id,
                    user_id,
                    user_group_id
                )
                VALUES (?, ?, ?)
                """, accountId, userId, userGroupId);
    }

    private void givenSimulationGroup(UUID simulationGroupId, UUID userGroupId, String name) {
        jdbcTemplate.update("""
                INSERT INTO simulation_groups (
                    simulation_group_id,
                    user_group_id,
                    simulation_group_name
                )
                VALUES (?, ?, ?)
                """, simulationGroupId, userGroupId, name);
    }

    private UUID givenTransaction(
            UUID accountId,
            UUID userGroupId,
            UUID categoryId,
            LocalDate chargeDate,
            boolean simulated,
            UUID simulationGroupId,
            String description
    ) {
        UUID transactionId = UUID.randomUUID();

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
                            transaction_is_simulated,
                            simulation_group_id,
                            transaction_is_user_entered,
                            transaction_reminder_enabled,
                            transaction_reminder_days_before,
                            user_group_id
                        )
                        VALUES (?, ?, ?, true, true, ?, ?, true, ?, ?, ?, true, true, 7, ?)
                        """,
                transactionId,
                description,
                BigDecimal.valueOf(-10),
                categoryId,
                chargeDate,
                accountId,
                simulated,
                simulationGroupId,
                userGroupId
        );

        return transactionId;
    }

    private UUID givenCreditCardTransactionWithFlags(
            UUID accountId,
            UUID userGroupId,
            UUID categoryId,
            UUID creditCardId,
            LocalDate chargeDate,
            BigDecimal amount,
            String description,
            boolean affectsAccountBalance,
            boolean affectsSerenityline
    ) {
        UUID transactionId = UUID.randomUUID();

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
                            transaction_is_simulated,
                            simulation_group_id,
                            transaction_is_user_entered,
                            transaction_reminder_enabled,
                            transaction_reminder_days_before,
                            user_group_id
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, true, ?, ?, false, NULL, true, true, 7, ?)
                        """,
                transactionId,
                description,
                amount,
                affectsAccountBalance,
                affectsSerenityline,
                categoryId,
                chargeDate,
                accountId,
                creditCardId,
                userGroupId
        );

        return transactionId;
    }

    private void givenAccountWithOpeningBalance(
            UUID accountId,
            UUID userGroupId,
            String name,
            LocalDate openingBalanceDate,
            BigDecimal openingBalance
    ) {
        jdbcTemplate.update("""
                        INSERT INTO accounts (
                            account_id,
                            user_group_id,
                            account_name,
                            currency,
                            opening_balance,
                            opening_balance_date
                        )
                        VALUES (?, ?, ?, 'EUR', ?, ?)
                        """,
                accountId,
                userGroupId,
                name,
                openingBalance,
                openingBalanceDate
        );
    }

    private UUID givenRecurringTransaction(
            UUID userGroupId,
            UUID accountId,
            boolean simulated,
            UUID simulationGroupId,
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
                        VALUES (?, true, ?, ?, ?, true, 7, ?)
                        """,
                recurringTransactionId,
                LocalDate.of(2026, 1, 10),
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
                        VALUES (?, ?, NULL, 10, 1, 'MONTH', 'NONE', ?)
                        """,
                recurringTransactionId,
                LocalDate.of(2026, 1, 1),
                BigDecimal.valueOf(-100)
        );

        jdbcTemplate.update("""
                        INSERT INTO recurring_transaction_details_history (
                            recurring_transaction_id,
                            recurring_transaction_description,
                            category_id,
                            financial_priority_id,
                            linked_account_id,
                            recurring_transaction_affects_account_balance,
                            recurring_transaction_affects_serenityline,
                            recurring_transaction_details_effective_from,
                            user_group_id
                        )
                        VALUES (?, ?, ?, ?, ?, true, true, ?, ?)
                        """,
                recurringTransactionId,
                description,
                categoryId,
                financialPriorityId,
                accountId,
                LocalDate.of(2026, 1, 1),
                userGroupId
        );

        return recurringTransactionId;
    }
}