package me.serenityline.api.finance.transaction.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import me.serenityline.api.security.jwt.JwtTokenService;
import me.serenityline.api.support.IntegrationTestSupport;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecurringTransactionControllerIntegrationTest extends IntegrationTestSupport {

    private static final String RECURRING_TRANSACTIONS_PATH = "/api/finance/recurring-transactions";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    private static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private static String unique(String prefix) {
        return prefix + " " + UUID.randomUUID();
    }

    private static String uniqueEmail(String label) {
        return label + "-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    void createRecurringTransactionShouldRequireAuthentication() throws Exception {
        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerShouldCreateMinimalRecurringTransactionWithDefaults() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring default");
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria recurring default"
        );
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        Map<String, Object> body = minimalRecurringTransactionRequest(
                account,
                categoryId,
                financialPriorityId
        );

        MvcResult result = mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recurringTransactionId").isString())
                .andExpect(jsonPath("$.recurringTransactionAmountIsAdjustable").value(false))
                .andExpect(jsonPath("$.recurringTransactionFirstPaymentDate").value("2026-06-01"))
                .andExpect(jsonPath("$.recurringTransactionIsSimulated").value(false))
                .andExpect(jsonPath("$.simulationGroupId").doesNotExist())
                .andExpect(jsonPath("$.recurringTransactionReminderEnabled").value(true))
                .andExpect(jsonPath("$.recurringTransactionReminderDaysBefore").value(7))
                .andExpect(jsonPath("$.recurringTransactionCreatedAt").exists())
                .andExpect(jsonPath("$.recurringTransactionUpdatedAt").exists())
                .andExpect(jsonPath("$.recurringTransactionHistoryId").isString())
                .andExpect(jsonPath("$.effectiveFrom").value("2026-06-01"))
                .andExpect(jsonPath("$.effectiveTo").doesNotExist())
                .andExpect(jsonPath("$.dayOfUnit").value(1))
                .andExpect(jsonPath("$.recurrenceInterval").value(1))
                .andExpect(jsonPath("$.recurrenceUnit").value("MONTH"))
                .andExpect(jsonPath("$.paymentDateAdjustmentPolicy").value("PREVIOUS_BUSINESS_DAY"))
                .andExpect(jsonPath("$.paymentAmount").value(-800.0))
                .andExpect(jsonPath("$.recurringTransactionEndDate").doesNotExist())
                .andExpect(jsonPath("$.finalPaymentAmount").doesNotExist())
                .andExpect(jsonPath("$.recurringTransactionDetailsHistoryId").isString())
                .andExpect(jsonPath("$.recurringTransactionDescription").value("Affitto casa"))
                .andExpect(jsonPath("$.categoryId").value(categoryId.toString()))
                .andExpect(jsonPath("$.financialPriorityId").value(financialPriorityId.toString()))
                .andExpect(jsonPath("$.linkedAccountId").value(account.accountId().toString()))
                .andExpect(jsonPath("$.linkedCreditCardId").doesNotExist())
                .andExpect(jsonPath("$.linkedBucketId").doesNotExist())
                .andExpect(jsonPath("$.recurringTransactionAffectsAccountBalance").value(true))
                .andExpect(jsonPath("$.recurringTransactionAffectsLiquidity").value(true))
                .andExpect(jsonPath("$.recurringTransactionDetailsEffectiveFrom").value("2026-06-01"))
                .andReturn();

        UUID recurringTransactionId = recurringTransactionIdFrom(result);

        Map<String, Object> recurringRow = findRecurringTransaction(recurringTransactionId);
        Map<String, Object> historyRow = findRecurringTransactionHistory(recurringTransactionId);
        Map<String, Object> detailsRow = findRecurringTransactionDetailsHistory(recurringTransactionId);

        assertThat(recurringRow.get("user_group_id")).isEqualTo(owner.userGroupId());
        assertThat(recurringRow.get("recurring_transaction_amount_is_adjustable")).isEqualTo(false);
        assertThat(asLocalDate(recurringRow.get("recurring_transaction_first_payment_date")))
                .isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(recurringRow.get("recurring_transaction_is_simulated")).isEqualTo(false);
        assertThat(recurringRow.get("simulation_group_id")).isNull();
        assertThat(recurringRow.get("recurring_transaction_reminder_enabled")).isEqualTo(true);
        assertThat(((Number) recurringRow.get("recurring_transaction_reminder_days_before")).intValue())
                .isEqualTo(7);

        assertThat(asLocalDate(historyRow.get("effective_from"))).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(historyRow.get("effective_to")).isNull();
        assertThat(((Number) historyRow.get("day_of_unit")).intValue()).isEqualTo(1);
        assertThat(((Number) historyRow.get("recurrence_interval")).intValue()).isEqualTo(1);
        assertThat(historyRow.get("recurrence_unit")).isEqualTo("MONTH");
        assertThat(historyRow.get("payment_date_adjustment_policy")).isEqualTo("PREVIOUS_BUSINESS_DAY");
        assertThat((BigDecimal) historyRow.get("payment_amount")).isEqualByComparingTo("-800.00");
        assertThat(historyRow.get("recurring_transaction_end_date")).isNull();
        assertThat(historyRow.get("final_payment_amount")).isNull();

        assertThat(detailsRow.get("recurring_transaction_description")).isEqualTo("Affitto casa");
        assertThat(detailsRow.get("category_id")).isEqualTo(categoryId);
        assertThat(detailsRow.get("financial_priority_id")).isEqualTo(financialPriorityId);
        assertThat(detailsRow.get("linked_account_id")).isEqualTo(account.accountId());
        assertThat(detailsRow.get("linked_credit_card_id")).isNull();
        assertThat(detailsRow.get("linked_bucket_id")).isNull();
        assertThat(detailsRow.get("recurring_transaction_affects_account_balance")).isEqualTo(true);
        assertThat(detailsRow.get("recurring_transaction_affects_liquidity")).isEqualTo(true);
        assertThat(asLocalDate(detailsRow.get("recurring_transaction_details_effective_from")))
                .isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(detailsRow.get("user_group_id")).isEqualTo(owner.userGroupId());

        assertThat(countRecurringTransactionUsers(
                recurringTransactionId,
                owner.userId(),
                owner.userGroupId()
        )).isEqualTo(1L);

        assertThat(countTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void ownerShouldCreateRecurringTransactionWithAllOptionalFields() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring full");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria recurring full");
        UUID financialPriorityId = financialPriorityId("CRITICAL");

        CreditCardRef creditCard = createCreditCard(owner.userGroupId(), account, "Carta recurring full");

        BucketRef bucket = createOpenBucket(owner.userGroupId(), "Bucket recurring full");
        linkBucketToAccount(bucket, account, owner.userGroupId());

        UUID simulationGroupId = createSimulationGroup(owner.userGroupId(), "Simulation recurring full");
        linkSimulationGroupToAccount(simulationGroupId, account);

        Map<String, Object> body = validRecurringTransactionRequest(
                account,
                categoryId,
                financialPriorityId
        );
        body.put("recurringTransactionDescription", "  Abbonamento premium  ");
        body.put("paymentAmount", new BigDecimal("-49.90"));
        body.put("recurringTransactionAmountIsAdjustable", true);
        body.put("recurringTransactionFirstPaymentDate", "2026-06-03");
        body.put("recurrenceInterval", 2);
        body.put("recurrenceUnit", "WEEK");
        body.put("paymentDateAdjustmentPolicy", "NEXT_BUSINESS_DAY");
        body.put("recurringTransactionEndDate", "2027-06-03");
        body.put("finalPaymentAmount", new BigDecimal("-19.90"));
        body.put("linkedCreditCardId", creditCard.creditCardId());
        body.put("linkedBucketId", bucket.bucketId());
        body.put("recurringTransactionAffectsAccountBalance", false);
        body.put("recurringTransactionAffectsLiquidity", true);
        body.put("recurringTransactionIsSimulated", true);
        body.put("simulationGroupId", simulationGroupId);
        body.put("recurringTransactionReminderEnabled", false);
        body.put("recurringTransactionReminderDaysBefore", 0);

        MvcResult result = mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recurringTransactionDescription").value("Abbonamento premium"))
                .andExpect(jsonPath("$.paymentAmount").value(-49.9))
                .andExpect(jsonPath("$.recurringTransactionAmountIsAdjustable").value(true))
                .andExpect(jsonPath("$.recurringTransactionFirstPaymentDate").value("2026-06-03"))
                .andExpect(jsonPath("$.recurringTransactionIsSimulated").value(true))
                .andExpect(jsonPath("$.simulationGroupId").value(simulationGroupId.toString()))
                .andExpect(jsonPath("$.recurringTransactionReminderEnabled").value(false))
                .andExpect(jsonPath("$.recurringTransactionReminderDaysBefore").value(0))
                .andExpect(jsonPath("$.dayOfUnit").value(3))
                .andExpect(jsonPath("$.recurrenceInterval").value(2))
                .andExpect(jsonPath("$.recurrenceUnit").value("WEEK"))
                .andExpect(jsonPath("$.paymentDateAdjustmentPolicy").value("NEXT_BUSINESS_DAY"))
                .andExpect(jsonPath("$.recurringTransactionEndDate").value("2027-06-03"))
                .andExpect(jsonPath("$.finalPaymentAmount").value(-19.9))
                .andExpect(jsonPath("$.categoryId").value(categoryId.toString()))
                .andExpect(jsonPath("$.financialPriorityId").value(financialPriorityId.toString()))
                .andExpect(jsonPath("$.linkedAccountId").value(account.accountId().toString()))
                .andExpect(jsonPath("$.linkedCreditCardId").value(creditCard.creditCardId().toString()))
                .andExpect(jsonPath("$.linkedBucketId").value(bucket.bucketId().toString()))
                .andExpect(jsonPath("$.recurringTransactionAffectsAccountBalance").value(false))
                .andExpect(jsonPath("$.recurringTransactionAffectsLiquidity").value(true))
                .andReturn();

        UUID recurringTransactionId = recurringTransactionIdFrom(result);

        Map<String, Object> recurringRow = findRecurringTransaction(recurringTransactionId);
        Map<String, Object> historyRow = findRecurringTransactionHistory(recurringTransactionId);
        Map<String, Object> detailsRow = findRecurringTransactionDetailsHistory(recurringTransactionId);

        assertThat(recurringRow.get("simulation_group_id")).isEqualTo(simulationGroupId);
        assertThat(recurringRow.get("recurring_transaction_reminder_enabled")).isEqualTo(false);
        assertThat(((Number) recurringRow.get("recurring_transaction_reminder_days_before")).intValue()).isZero();

        assertThat(((Number) historyRow.get("day_of_unit")).intValue()).isEqualTo(3);
        assertThat(((Number) historyRow.get("recurrence_interval")).intValue()).isEqualTo(2);
        assertThat(historyRow.get("recurrence_unit")).isEqualTo("WEEK");
        assertThat(historyRow.get("payment_date_adjustment_policy")).isEqualTo("NEXT_BUSINESS_DAY");
        assertThat((BigDecimal) historyRow.get("payment_amount")).isEqualByComparingTo("-49.90");
        assertThat(asLocalDate(historyRow.get("recurring_transaction_end_date")))
                .isEqualTo(LocalDate.of(2027, 6, 3));
        assertThat((BigDecimal) historyRow.get("final_payment_amount")).isEqualByComparingTo("-19.90");

        assertThat(detailsRow.get("linked_credit_card_id")).isEqualTo(creditCard.creditCardId());
        assertThat(detailsRow.get("linked_bucket_id")).isEqualTo(bucket.bucketId());
        assertThat(detailsRow.get("recurring_transaction_affects_account_balance")).isEqualTo(false);
        assertThat(detailsRow.get("recurring_transaction_affects_liquidity")).isEqualTo(true);
    }

    @Test
    void ownerShouldCreateRecurringTransactionWithFinalPaymentAmountWithoutEndDate() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring final no end");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria final no end");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        Map<String, Object> body = validRecurringTransactionRequest(
                account,
                categoryId,
                financialPriorityId
        );
        body.put("paymentAmount", new BigDecimal("-800.00"));
        body.put("recurringTransactionEndDate", null);
        body.put("finalPaymentAmount", new BigDecimal("2400.00"));

        MvcResult result = mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentAmount").value(-800.0))
                .andExpect(jsonPath("$.recurringTransactionEndDate").doesNotExist())
                .andExpect(jsonPath("$.finalPaymentAmount").value(2400.0))
                .andReturn();

        UUID recurringTransactionId = recurringTransactionIdFrom(result);
        Map<String, Object> historyRow = findRecurringTransactionHistory(recurringTransactionId);

        assertThat(historyRow.get("recurring_transaction_end_date")).isNull();
        assertThat((BigDecimal) historyRow.get("final_payment_amount")).isEqualByComparingTo("2400.00");
    }

    @Test
    void createRecurringTransactionShouldDeriveDayOfUnitFromFirstPaymentDate() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring day of unit");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria day of unit");
        UUID financialPriorityId = financialPriorityId("OPTIONAL");

        assertRecurringDayOfUnit(owner, account, categoryId, financialPriorityId, "DAY", "2026-06-17", 1);
        assertRecurringDayOfUnit(owner, account, categoryId, financialPriorityId, "WEEK", "2026-06-17", 3);
        assertRecurringDayOfUnit(owner, account, categoryId, financialPriorityId, "MONTH", "2026-06-17", 17);
        assertRecurringDayOfUnit(owner, account, categoryId, financialPriorityId, "YEAR", "2026-12-31", 365);
    }

    @Test
    void superCollaboratorShouldCreateRecurringTransactionOnGroupAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef superCollaborator = createUser(owner.userGroupId(), "SUPER_COLLABORATOR");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring super");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria recurring super");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(superCollaborator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(minimalRecurringTransactionRequest(account, categoryId, financialPriorityId))))
                .andExpect(status().isCreated());

        assertThat(countRecurringTransactionsForUserGroup(owner.userGroupId())).isEqualTo(1L);
    }

    @Test
    void viewerCollaboratorShouldCreateRecurringTransactionOnOperableAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef viewer = createUser(owner.userGroupId(), "VIEWER_COLLABORATOR");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring viewer linked");
        grantAccountAccess(account, viewer);

        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria viewer linked");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(viewer)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(minimalRecurringTransactionRequest(account, categoryId, financialPriorityId))))
                .andExpect(status().isCreated());

        assertThat(countRecurringTransactionsForUserGroup(owner.userGroupId())).isEqualTo(1L);
    }

    @Test
    void viewerCollaboratorShouldReceiveForbiddenWhenCreatingRecurringTransactionOnNonOperableAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef viewer = createUser(owner.userGroupId(), "VIEWER_COLLABORATOR");

        AccountRef hiddenAccount = createAccount(owner.userGroupId(), "Conto recurring viewer hidden");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria viewer hidden");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(viewer)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(minimalRecurringTransactionRequest(hiddenAccount, categoryId, financialPriorityId))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("finance.account.operationNotAllowed"));

        assertThat(countRecurringTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void collaboratorShouldCreateRecurringTransactionOnLinkedAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring collaborator linked");
        grantAccountAccess(account, collaborator);

        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria collaborator linked");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(minimalRecurringTransactionRequest(account, categoryId, financialPriorityId))))
                .andExpect(status().isCreated());

        assertThat(countRecurringTransactionsForUserGroup(owner.userGroupId())).isEqualTo(1L);
    }

    @Test
    void collaboratorShouldReceiveNotFoundWhenCreatingRecurringTransactionOnHiddenAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef hiddenAccount = createAccount(owner.userGroupId(), "Conto recurring collaborator hidden");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria collaborator hidden");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(minimalRecurringTransactionRequest(hiddenAccount, categoryId, financialPriorityId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.account.notFound"));

        assertThat(countRecurringTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void ownerShouldRejectRecurringTransactionWithAccountFromAnotherGroup() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef otherOwner = createUserWithNewGroup("OWNER");

        AccountRef otherAccount = createAccount(otherOwner.userGroupId(), "Conto recurring other group");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria own");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(minimalRecurringTransactionRequest(otherAccount, categoryId, financialPriorityId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.account.notFound"));

        assertThat(countRecurringTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void ownerShouldRejectRecurringTransactionWithCategoryFromAnotherGroup() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef otherOwner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring category other group");
        UUID otherCategoryId = createActiveCategory(otherOwner.userGroupId(), otherOwner.userId(), "Categoria other");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(minimalRecurringTransactionRequest(account, otherCategoryId, financialPriorityId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.category.notFound"));

        assertThat(countRecurringTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void ownerShouldRejectRecurringTransactionWithInactiveCategory() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring inactive category");
        UUID inactiveCategoryId = createInactiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria inactive recurring"
        );
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(minimalRecurringTransactionRequest(account, inactiveCategoryId, financialPriorityId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.category.notFound"));

        assertThat(countRecurringTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void ownerShouldRejectRecurringTransactionWithUnknownFinancialPriority() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring unknown priority");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria unknown priority");

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(minimalRecurringTransactionRequest(account, categoryId, UUID.randomUUID()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.financialPriority.notFound"));

        assertThat(countRecurringTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void ownerShouldRejectRecurringTransactionWithCreditCardLinkedToDifferentAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring card target");
        AccountRef otherAccount = createAccount(owner.userGroupId(), "Conto recurring card other");

        CreditCardRef otherCreditCard = createCreditCard(owner.userGroupId(), otherAccount, "Carta other account");

        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria card mismatch");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        Map<String, Object> body = validRecurringTransactionRequest(account, categoryId, financialPriorityId);
        body.put("linkedCreditCardId", otherCreditCard.creditCardId());

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.creditCard.notFound"));

        assertThat(countRecurringTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void ownerShouldRejectRecurringTransactionWithClosedBucket() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring closed bucket");
        BucketRef closedBucket = createClosedBucket(owner.userGroupId(), "Bucket recurring closed");
        linkBucketToAccount(closedBucket, account, owner.userGroupId());

        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria closed bucket");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        Map<String, Object> body = validRecurringTransactionRequest(account, categoryId, financialPriorityId);
        body.put("linkedBucketId", closedBucket.bucketId());

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"));

        assertThat(countRecurringTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void ownerShouldRejectRecurringTransactionWithBucketNotLinkedToAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring bucket target");
        BucketRef bucket = createOpenBucket(owner.userGroupId(), "Bucket recurring not linked");

        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria bucket not linked");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        Map<String, Object> body = validRecurringTransactionRequest(account, categoryId, financialPriorityId);
        body.put("linkedBucketId", bucket.bucketId());

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"));

        assertThat(countRecurringTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void createRecurringTransactionShouldRequireSimulationGroupWhenSimulated() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring simulation required");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria simulation required");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        Map<String, Object> body = validRecurringTransactionRequest(account, categoryId, financialPriorityId);
        body.put("recurringTransactionIsSimulated", true);
        body.put("simulationGroupId", null);

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.recurringTransaction.simulationGroupRequired"));

        assertThat(countRecurringTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void createRecurringTransactionShouldRejectSimulationGroupWhenNotSimulated() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring simulation not allowed");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria simulation not allowed");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID simulationGroupId = createSimulationGroup(owner.userGroupId(), "Simulation not allowed");
        linkSimulationGroupToAccount(simulationGroupId, account);

        Map<String, Object> body = validRecurringTransactionRequest(account, categoryId, financialPriorityId);
        body.put("recurringTransactionIsSimulated", false);
        body.put("simulationGroupId", simulationGroupId);

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.recurringTransaction.simulationGroupNotAllowed"));

        assertThat(countRecurringTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void ownerShouldRejectSimulatedRecurringTransactionWithArchivedSimulationGroup() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring archived simulation");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria archived simulation");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID archivedSimulationGroupId = createArchivedSimulationGroup(
                owner.userGroupId(),
                "Simulation archived recurring"
        );
        linkSimulationGroupToAccount(archivedSimulationGroupId, account);

        Map<String, Object> body = validRecurringTransactionRequest(account, categoryId, financialPriorityId);
        body.put("recurringTransactionIsSimulated", true);
        body.put("simulationGroupId", archivedSimulationGroupId);

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.simulationGroup.notFound"));

        assertThat(countRecurringTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void ownerShouldRejectSimulatedRecurringTransactionWithSimulationGroupNotLinkedToAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring simulation not linked");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria simulation not linked");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID simulationGroupId = createSimulationGroup(owner.userGroupId(), "Simulation not linked recurring");

        Map<String, Object> body = validRecurringTransactionRequest(account, categoryId, financialPriorityId);
        body.put("recurringTransactionIsSimulated", true);
        body.put("simulationGroupId", simulationGroupId);

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.simulationGroup.notFound"));

        assertThat(countRecurringTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void ownerShouldRejectSimulatedRecurringTransactionWithSimulationGroupFromAnotherGroup() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef otherOwner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring simulation other group");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria simulation other group");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID otherSimulationGroupId = createSimulationGroup(
                otherOwner.userGroupId(),
                "Simulation recurring other group"
        );

        Map<String, Object> body = validRecurringTransactionRequest(account, categoryId, financialPriorityId);
        body.put("recurringTransactionIsSimulated", true);
        body.put("simulationGroupId", otherSimulationGroupId);

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.simulationGroup.notFound"));

        assertThat(countRecurringTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void createRecurringTransactionShouldRejectBlankDescription() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring blank description");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria blank description");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        Map<String, Object> body = validRecurringTransactionRequest(account, categoryId, financialPriorityId);
        body.put("recurringTransactionDescription", "   ");

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));

        assertThat(countRecurringTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void createRecurringTransactionShouldRejectDescriptionLongerThan500() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring long description");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria long description");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        Map<String, Object> body = validRecurringTransactionRequest(account, categoryId, financialPriorityId);
        body.put("recurringTransactionDescription", "x".repeat(501));

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));

        assertThat(countRecurringTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void createRecurringTransactionShouldRejectMissingPaymentAmount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring missing amount");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria missing amount");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        Map<String, Object> body = validRecurringTransactionRequest(account, categoryId, financialPriorityId);
        body.remove("paymentAmount");

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));

        assertThat(countRecurringTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void createRecurringTransactionShouldRejectZeroPaymentAmount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring zero amount");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria zero amount");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        Map<String, Object> body = validRecurringTransactionRequest(account, categoryId, financialPriorityId);
        body.put("paymentAmount", new BigDecimal("0.00"));

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.recurringTransaction.paymentAmountNotZero"));

        assertThat(countAllRecurringRowsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void createRecurringTransactionShouldRejectPaymentAmountWithMoreThanTwoDecimals() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring invalid decimals");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria invalid decimals");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        Map<String, Object> body = validRecurringTransactionRequest(account, categoryId, financialPriorityId);
        body.put("paymentAmount", new BigDecimal("-10.123"));

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));

        assertThat(countRecurringTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void createRecurringTransactionShouldRejectMissingFirstPaymentDate() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring missing first date");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria missing first date");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        Map<String, Object> body = validRecurringTransactionRequest(account, categoryId, financialPriorityId);
        body.remove("recurringTransactionFirstPaymentDate");

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));

        assertThat(countRecurringTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void createRecurringTransactionShouldRejectMissingRecurrenceInterval() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring missing interval");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria missing interval");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        Map<String, Object> body = validRecurringTransactionRequest(account, categoryId, financialPriorityId);
        body.remove("recurrenceInterval");

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));

        assertThat(countRecurringTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void createRecurringTransactionShouldRejectInvalidRecurrenceInterval() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring invalid interval");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria invalid interval");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        Map<String, Object> zeroInterval = validRecurringTransactionRequest(account, categoryId, financialPriorityId);
        zeroInterval.put("recurrenceInterval", 0);

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(zeroInterval)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));

        Map<String, Object> tooLargeInterval = validRecurringTransactionRequest(account, categoryId, financialPriorityId);
        tooLargeInterval.put("recurrenceInterval", 32768);

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(tooLargeInterval)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));

        assertThat(countRecurringTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void createRecurringTransactionShouldRejectMissingRecurrenceUnit() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring missing unit");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria missing unit");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        Map<String, Object> body = validRecurringTransactionRequest(account, categoryId, financialPriorityId);
        body.remove("recurrenceUnit");

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));

        assertThat(countRecurringTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void createRecurringTransactionShouldRejectInvalidEnumValues() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring invalid enum");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria invalid enum");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        Map<String, Object> invalidRecurrenceUnit = validRecurringTransactionRequest(
                account,
                categoryId,
                financialPriorityId
        );
        invalidRecurrenceUnit.put("recurrenceUnit", "INVALID_UNIT");

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(invalidRecurrenceUnit)))
                .andExpect(status().isBadRequest());

        Map<String, Object> invalidAdjustmentPolicy = validRecurringTransactionRequest(
                account,
                categoryId,
                financialPriorityId
        );
        invalidAdjustmentPolicy.put("paymentDateAdjustmentPolicy", "INVALID_POLICY");

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(invalidAdjustmentPolicy)))
                .andExpect(status().isBadRequest());

        assertThat(countRecurringTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void createRecurringTransactionShouldRejectEndDateBeforeFirstPaymentDate() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring invalid end");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria invalid end");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        Map<String, Object> body = validRecurringTransactionRequest(account, categoryId, financialPriorityId);
        body.put("recurringTransactionFirstPaymentDate", "2026-06-10");
        body.put("recurringTransactionEndDate", "2026-06-09");

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.recurringTransaction.endDateInvalid"));

        assertThat(countAllRecurringRowsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void createRecurringTransactionShouldRejectZeroFinalPaymentAmount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring zero final");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria zero final");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        Map<String, Object> body = validRecurringTransactionRequest(account, categoryId, financialPriorityId);
        body.put("finalPaymentAmount", new BigDecimal("0.00"));

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.recurringTransaction.finalPaymentAmountNotZero"));

        assertThat(countAllRecurringRowsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void createRecurringTransactionShouldRejectFinalPaymentAmountWithMoreThanTwoDecimals() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring invalid final decimals");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria invalid final decimals");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        Map<String, Object> body = validRecurringTransactionRequest(account, categoryId, financialPriorityId);
        body.put("finalPaymentAmount", new BigDecimal("10.123"));

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));

        assertThat(countRecurringTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void createRecurringTransactionShouldRejectFalseFalseAffectsFlags() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring false false");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria false false");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        Map<String, Object> body = validRecurringTransactionRequest(account, categoryId, financialPriorityId);
        body.put("recurringTransactionAffectsAccountBalance", false);
        body.put("recurringTransactionAffectsLiquidity", false);

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.recurringTransaction.affectsSomethingRequired"));

        assertThat(countAllRecurringRowsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void createRecurringTransactionShouldRejectInvalidReminderDays() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring invalid reminder");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria invalid reminder");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        Map<String, Object> negativeReminder = validRecurringTransactionRequest(
                account,
                categoryId,
                financialPriorityId
        );
        negativeReminder.put("recurringTransactionReminderDaysBefore", -1);

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(negativeReminder)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));

        Map<String, Object> tooLargeReminder = validRecurringTransactionRequest(
                account,
                categoryId,
                financialPriorityId
        );
        tooLargeReminder.put("recurringTransactionReminderDaysBefore", 367);

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(tooLargeReminder)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));

        assertThat(countRecurringTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void createRecurringTransactionShouldRejectMissingCategory() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring missing category");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        Map<String, Object> body = validRecurringTransactionRequest(account, UUID.randomUUID(), financialPriorityId);
        body.remove("categoryId");

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));

        assertThat(countAllRecurringRowsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void createRecurringTransactionShouldRejectMissingFinancialPriority() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring missing priority");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria missing priority");

        Map<String, Object> body = validRecurringTransactionRequest(account, categoryId, UUID.randomUUID());
        body.remove("financialPriorityId");

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));

        assertThat(countAllRecurringRowsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void createRecurringTransactionShouldRejectMissingLinkedAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring missing account");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria missing account");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        Map<String, Object> body = validRecurringTransactionRequest(account, categoryId, financialPriorityId);
        body.remove("linkedAccountId");

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));

        assertThat(countAllRecurringRowsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void ownerShouldCreateRecurringTransactionWithMaxValidReminderDaysAndRecurrenceInterval() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring max values");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria max values");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        Map<String, Object> body = validRecurringTransactionRequest(account, categoryId, financialPriorityId);
        body.put("recurrenceInterval", 32767);
        body.put("recurringTransactionReminderDaysBefore", 366);

        MvcResult result = mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recurrenceInterval").value(32767))
                .andExpect(jsonPath("$.recurringTransactionReminderDaysBefore").value(366))
                .andReturn();

        UUID recurringTransactionId = recurringTransactionIdFrom(result);
        Map<String, Object> recurringRow = findRecurringTransaction(recurringTransactionId);
        Map<String, Object> historyRow = findRecurringTransactionHistory(recurringTransactionId);

        assertThat(((Number) recurringRow.get("recurring_transaction_reminder_days_before")).intValue())
                .isEqualTo(366);
        assertThat(((Number) historyRow.get("recurrence_interval")).intValue())
                .isEqualTo(32767);
    }

    @Test
    void ownerShouldCreateRecurringTransactionWithEndDateEqualToFirstPaymentDate() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring same end date");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria same end date");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        Map<String, Object> body = validRecurringTransactionRequest(account, categoryId, financialPriorityId);
        body.put("recurringTransactionFirstPaymentDate", "2026-06-10");
        body.put("recurringTransactionEndDate", "2026-06-10");

        MvcResult result = mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recurringTransactionFirstPaymentDate").value("2026-06-10"))
                .andExpect(jsonPath("$.recurringTransactionEndDate").value("2026-06-10"))
                .andReturn();

        UUID recurringTransactionId = recurringTransactionIdFrom(result);
        Map<String, Object> historyRow = findRecurringTransactionHistory(recurringTransactionId);

        assertThat(asLocalDate(historyRow.get("recurring_transaction_end_date")))
                .isEqualTo(LocalDate.of(2026, 6, 10));
    }

    @Test
    void createRecurringTransactionShouldRollbackAllRowsWhenDetailsValidationFailsAfterHistorySave() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring rollback details");
        BucketRef bucket = createOpenBucket(owner.userGroupId(), "Bucket rollback not linked");

        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria rollback details");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        Map<String, Object> body = validRecurringTransactionRequest(account, categoryId, financialPriorityId);
        body.put("linkedBucketId", bucket.bucketId());

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"));

        assertThat(countAllRecurringRowsForUserGroup(owner.userGroupId())).isZero();
    }

    private Map<String, Object> minimalRecurringTransactionRequest(
            AccountRef account,
            UUID categoryId,
            UUID financialPriorityId
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recurringTransactionDescription", "  Affitto casa  ");
        body.put("paymentAmount", new BigDecimal("-800.00"));
        body.put("recurringTransactionFirstPaymentDate", "2026-06-01");
        body.put("recurrenceInterval", 1);
        body.put("recurrenceUnit", "MONTH");
        body.put("categoryId", categoryId);
        body.put("financialPriorityId", financialPriorityId);
        body.put("linkedAccountId", account.accountId());
        return body;
    }

    private Map<String, Object> validRecurringTransactionRequest(
            AccountRef account,
            UUID categoryId,
            UUID financialPriorityId
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recurringTransactionDescription", "  Affitto casa  ");
        body.put("paymentAmount", new BigDecimal("-800.00"));
        body.put("recurringTransactionAmountIsAdjustable", false);
        body.put("recurringTransactionFirstPaymentDate", "2026-06-01");
        body.put("recurrenceInterval", 1);
        body.put("recurrenceUnit", "MONTH");
        body.put("paymentDateAdjustmentPolicy", "PREVIOUS_BUSINESS_DAY");
        body.put("recurringTransactionEndDate", null);
        body.put("finalPaymentAmount", null);
        body.put("categoryId", categoryId);
        body.put("financialPriorityId", financialPriorityId);
        body.put("linkedAccountId", account.accountId());
        body.put("linkedCreditCardId", null);
        body.put("linkedBucketId", null);
        body.put("recurringTransactionAffectsAccountBalance", true);
        body.put("recurringTransactionAffectsLiquidity", true);
        body.put("recurringTransactionIsSimulated", false);
        body.put("simulationGroupId", null);
        body.put("recurringTransactionReminderEnabled", true);
        body.put("recurringTransactionReminderDaysBefore", 7);
        return body;
    }

    private void assertRecurringDayOfUnit(
            UserRef owner,
            AccountRef account,
            UUID categoryId,
            UUID financialPriorityId,
            String recurrenceUnit,
            String firstPaymentDate,
            int expectedDayOfUnit
    ) throws Exception {
        Map<String, Object> body = validRecurringTransactionRequest(
                account,
                categoryId,
                financialPriorityId
        );
        body.put("recurrenceUnit", recurrenceUnit);
        body.put("recurringTransactionFirstPaymentDate", firstPaymentDate);

        MvcResult result = mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recurrenceUnit").value(recurrenceUnit))
                .andExpect(jsonPath("$.dayOfUnit").value(expectedDayOfUnit))
                .andReturn();

        UUID recurringTransactionId = recurringTransactionIdFrom(result);
        Map<String, Object> historyRow = findRecurringTransactionHistory(recurringTransactionId);

        assertThat(((Number) historyRow.get("day_of_unit")).intValue()).isEqualTo(expectedDayOfUnit);
    }

    private UserRef createUserWithNewGroup(String role) {
        UUID userGroupId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        jdbcTemplate.update("""
                        INSERT INTO user_groups (
                            user_group_id,
                            user_group_name
                        )
                        VALUES (?, ?)
                        """,
                userGroupId,
                unique("Recurring transaction test group")
        );

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
                "Recurring Transaction Test User",
                uniqueEmail("recurring-owner"),
                userGroupId,
                role,
                "test-password-hash"
        );

        return new UserRef(userId, userGroupId, role);
    }

    private UserRef createUser(
            UUID userGroupId,
            String role
    ) {
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
                "Recurring Transaction Test User",
                uniqueEmail("recurring-user"),
                userGroupId,
                role,
                "test-password-hash"
        );

        return new UserRef(userId, userGroupId, role);
    }

    private AccountRef createAccount(
            UUID userGroupId,
            String name
    ) {
        UUID accountId = UUID.randomUUID();

        jdbcTemplate.update("""
                        INSERT INTO accounts (
                            account_id,
                            account_name,
                            account_description,
                            currency,
                            issuing_institution,
                            opening_balance,
                            opening_balance_date,
                            user_group_id
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                accountId,
                unique(name),
                "Conto test",
                "EUR",
                "Banca test",
                new BigDecimal("1000.00"),
                LocalDate.of(2026, 1, 1),
                userGroupId
        );

        return new AccountRef(accountId);
    }

    private void grantAccountAccess(
            AccountRef account,
            UserRef user
    ) {
        jdbcTemplate.update("""
                        INSERT INTO accounts_users (
                            account_id,
                            user_id,
                            user_group_id
                        )
                        VALUES (?, ?, ?)
                        ON CONFLICT (account_id, user_id) DO NOTHING
                        """,
                account.accountId(),
                user.userId(),
                user.userGroupId()
        );
    }

    private UUID createActiveCategory(
            UUID userGroupId,
            UUID createdByUserId,
            String name
    ) {
        return createCategory(
                userGroupId,
                createdByUserId,
                name,
                true
        );
    }

    private UUID createInactiveCategory(
            UUID userGroupId,
            UUID createdByUserId,
            String name
    ) {
        return createCategory(
                userGroupId,
                createdByUserId,
                name,
                false
        );
    }

    private UUID createCategory(
            UUID userGroupId,
            UUID createdByUserId,
            String name,
            boolean active
    ) {
        UUID categoryId = UUID.randomUUID();
        String uniqueName = unique(name);

        jdbcTemplate.update("""
                        INSERT INTO categories (
                            category_id,
                            user_group_id,
                            category_created_by_user_id,
                            category_current_name
                        )
                        VALUES (?, ?, ?, ?)
                        """,
                categoryId,
                userGroupId,
                createdByUserId,
                uniqueName
        );

        jdbcTemplate.update("""
                        INSERT INTO category_details_history (
                            category_id,
                            category_name,
                            category_description
                        )
                        VALUES (?, ?, ?)
                        """,
                categoryId,
                uniqueName,
                "Categoria test"
        );

        jdbcTemplate.update("""
                        INSERT INTO category_status_history (
                            category_id,
                            category_is_active
                        )
                        VALUES (?, ?)
                        """,
                categoryId,
                active
        );

        return categoryId;
    }

    private CreditCardRef createCreditCard(
            UUID userGroupId,
            AccountRef account,
            String name
    ) {
        UUID creditCardId = UUID.randomUUID();

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
                unique(name),
                "Carta test",
                15,
                account.accountId(),
                userGroupId
        );

        return new CreditCardRef(creditCardId);
    }

    private BucketRef createOpenBucket(
            UUID userGroupId,
            String name
    ) {
        UUID bucketId = UUID.randomUUID();

        jdbcTemplate.update("""
                        INSERT INTO buckets (
                            bucket_id,
                            bucket_name,
                            bucket_description,
                            user_group_id
                        )
                        VALUES (?, ?, ?, ?)
                        """,
                bucketId,
                unique(name),
                "Bucket test",
                userGroupId
        );

        return new BucketRef(bucketId);
    }

    private BucketRef createClosedBucket(
            UUID userGroupId,
            String name
    ) {
        UUID bucketId = UUID.randomUUID();

        jdbcTemplate.update("""
                        INSERT INTO buckets (
                            bucket_id,
                            bucket_name,
                            bucket_description,
                            bucket_closed_at,
                            user_group_id
                        )
                        VALUES (?, ?, ?, now(), ?)
                        """,
                bucketId,
                unique(name),
                "Bucket chiuso test",
                userGroupId
        );

        return new BucketRef(bucketId);
    }

    private void linkBucketToAccount(
            BucketRef bucket,
            AccountRef account,
            UUID userGroupId
    ) {
        jdbcTemplate.update("""
                        INSERT INTO buckets_accounts (
                            bucket_id,
                            account_id,
                            user_group_id
                        )
                        VALUES (?, ?, ?)
                        ON CONFLICT (bucket_id, account_id, user_group_id) DO NOTHING
                        """,
                bucket.bucketId(),
                account.accountId(),
                userGroupId
        );
    }

    private UUID createSimulationGroup(
            UUID userGroupId,
            String name
    ) {
        UUID simulationGroupId = UUID.randomUUID();

        jdbcTemplate.update("""
                        INSERT INTO simulation_groups (
                            simulation_group_id,
                            user_group_id,
                            simulation_group_name,
                            simulation_group_description
                        )
                        VALUES (?, ?, ?, ?)
                        """,
                simulationGroupId,
                userGroupId,
                unique(name),
                "Simulation group test"
        );

        return simulationGroupId;
    }

    private UUID createArchivedSimulationGroup(
            UUID userGroupId,
            String name
    ) {
        UUID simulationGroupId = UUID.randomUUID();

        jdbcTemplate.update("""
                        INSERT INTO simulation_groups (
                            simulation_group_id,
                            user_group_id,
                            simulation_group_name,
                            simulation_group_description,
                            simulation_group_archived_at
                        )
                        VALUES (?, ?, ?, ?, now())
                        """,
                simulationGroupId,
                userGroupId,
                unique(name),
                "Simulation group archiviata test"
        );

        return simulationGroupId;
    }

    private void linkSimulationGroupToAccount(
            UUID simulationGroupId,
            AccountRef account
    ) {
        UUID userGroupId = jdbcTemplate.queryForObject("""
                        SELECT user_group_id
                        FROM simulation_groups
                        WHERE simulation_group_id = ?
                        """,
                UUID.class,
                simulationGroupId
        );

        jdbcTemplate.update("""
                        INSERT INTO simulation_groups_accounts (
                            simulation_group_id,
                            account_id,
                            user_group_id
                        )
                        VALUES (?, ?, ?)
                        ON CONFLICT (simulation_group_id, account_id, user_group_id) DO NOTHING
                        """,
                simulationGroupId,
                account.accountId(),
                userGroupId
        );
    }

    private UUID financialPriorityId(String financialPriorityName) {
        return jdbcTemplate.queryForObject("""
                        SELECT financial_priority_id
                        FROM financial_priorities
                        WHERE financial_priority_name = ?
                        """,
                UUID.class,
                financialPriorityName
        );
    }

    private UUID recurringTransactionIdFrom(MvcResult result) throws Exception {
        String response = result.getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.recurringTransactionId"));
    }

    private Map<String, Object> findRecurringTransaction(UUID recurringTransactionId) {
        return jdbcTemplate.queryForMap("""
                        SELECT *
                        FROM recurring_transactions
                        WHERE recurring_transaction_id = ?
                        """,
                recurringTransactionId
        );
    }

    private Map<String, Object> findRecurringTransactionHistory(UUID recurringTransactionId) {
        return jdbcTemplate.queryForMap("""
                        SELECT *
                        FROM recurring_transaction_history
                        WHERE recurring_transaction_id = ?
                        ORDER BY
                            effective_from DESC,
                            recurring_transaction_history_created_at DESC
                        LIMIT 1
                        """,
                recurringTransactionId
        );
    }

    private Map<String, Object> findRecurringTransactionDetailsHistory(UUID recurringTransactionId) {
        return jdbcTemplate.queryForMap("""
                        SELECT *
                        FROM recurring_transaction_details_history
                        WHERE recurring_transaction_id = ?
                        ORDER BY
                            recurring_transaction_details_effective_from DESC,
                            recurring_transaction_details_history_created_at DESC
                        LIMIT 1
                        """,
                recurringTransactionId
        );
    }

    private long countRecurringTransactionUsers(
            UUID recurringTransactionId,
            UUID userId,
            UUID userGroupId
    ) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT count(*)
                        FROM recurring_transactions_users
                        WHERE recurring_transaction_id = ?
                          AND user_id = ?
                          AND user_group_id = ?
                        """,
                Long.class,
                recurringTransactionId,
                userId,
                userGroupId
        );

        return count == null ? 0L : count;
    }

    private long countRecurringTransactionsForUserGroup(UUID userGroupId) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT count(*)
                        FROM recurring_transactions
                        WHERE user_group_id = ?
                        """,
                Long.class,
                userGroupId
        );

        return count == null ? 0L : count;
    }

    private long countRecurringTransactionHistoryForUserGroup(UUID userGroupId) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT count(*)
                        FROM recurring_transaction_history rth
                        JOIN recurring_transactions rt
                            ON rt.recurring_transaction_id = rth.recurring_transaction_id
                        WHERE rt.user_group_id = ?
                        """,
                Long.class,
                userGroupId
        );

        return count == null ? 0L : count;
    }

    private long countRecurringTransactionDetailsHistoryForUserGroup(UUID userGroupId) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT count(*)
                        FROM recurring_transaction_details_history
                        WHERE user_group_id = ?
                        """,
                Long.class,
                userGroupId
        );

        return count == null ? 0L : count;
    }

    private long countRecurringTransactionUsersForUserGroup(UUID userGroupId) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT count(*)
                        FROM recurring_transactions_users
                        WHERE user_group_id = ?
                        """,
                Long.class,
                userGroupId
        );

        return count == null ? 0L : count;
    }

    private long countAllRecurringRowsForUserGroup(UUID userGroupId) {
        return countRecurringTransactionsForUserGroup(userGroupId)
                + countRecurringTransactionHistoryForUserGroup(userGroupId)
                + countRecurringTransactionDetailsHistoryForUserGroup(userGroupId)
                + countRecurringTransactionUsersForUserGroup(userGroupId);
    }

    private long countTransactionsForUserGroup(UUID userGroupId) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT count(*)
                        FROM transactions
                        WHERE user_group_id = ?
                        """,
                Long.class,
                userGroupId
        );

        return count == null ? 0L : count;
    }

    private LocalDate asLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }

        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }

        throw new IllegalArgumentException("Unsupported date value: " + value);
    }

    private String json(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private String accessTokenFor(UserRef userRef) {
        User user = userRepository.findById(userRef.userId())
                .orElseThrow();

        return jwtTokenService.createAccessToken(user)
                .token();
    }

    private record UserRef(
            UUID userId,
            UUID userGroupId,
            String role
    ) {
    }

    private record AccountRef(UUID accountId) {
    }

    private record CreditCardRef(UUID creditCardId) {
    }

    private record BucketRef(UUID bucketId) {
    }
}