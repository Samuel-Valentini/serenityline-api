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
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Test
    void getRecurringTransactionShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH + "/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerShouldGetRecurringTransactionDetail() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring get owner");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria get owner");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID recurringTransactionId = createRecurringTransactionThroughApi(
                owner,
                minimalRecurringTransactionRequest(account, categoryId, financialPriorityId)
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH + "/" + recurringTransactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recurringTransactionId").value(recurringTransactionId.toString()))
                .andExpect(jsonPath("$.recurringTransactionAmountIsAdjustable").value(false))
                .andExpect(jsonPath("$.recurringTransactionFirstPaymentDate").value("2026-06-01"))
                .andExpect(jsonPath("$.recurringTransactionIsSimulated").value(false))
                .andExpect(jsonPath("$.simulationGroupId").doesNotExist())
                .andExpect(jsonPath("$.recurringTransactionReminderEnabled").value(true))
                .andExpect(jsonPath("$.recurringTransactionReminderDaysBefore").value(7))
                .andExpect(jsonPath("$.effectiveFrom").value("2026-06-01"))
                .andExpect(jsonPath("$.effectiveTo").doesNotExist())
                .andExpect(jsonPath("$.dayOfUnit").value(1))
                .andExpect(jsonPath("$.recurrenceInterval").value(1))
                .andExpect(jsonPath("$.recurrenceUnit").value("MONTH"))
                .andExpect(jsonPath("$.paymentDateAdjustmentPolicy").value("PREVIOUS_BUSINESS_DAY"))
                .andExpect(jsonPath("$.paymentAmount").value(-800.0))
                .andExpect(jsonPath("$.recurringTransactionDescription").value("Affitto casa"))
                .andExpect(jsonPath("$.categoryId").value(categoryId.toString()))
                .andExpect(jsonPath("$.financialPriorityId").value(financialPriorityId.toString()))
                .andExpect(jsonPath("$.linkedAccountId").value(account.accountId().toString()))
                .andExpect(jsonPath("$.recurringTransactionAffectsAccountBalance").value(true))
                .andExpect(jsonPath("$.recurringTransactionAffectsLiquidity").value(true));
    }

    @Test
    void getRecurringTransactionShouldReturnCurrentHistoryAndCurrentDetails() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring current state");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria current old");
        UUID updatedCategoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria current new");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");
        UUID updatedFinancialPriorityId = financialPriorityId("OPTIONAL");

        UUID recurringTransactionId = createRecurringTransactionThroughApi(
                owner,
                minimalRecurringTransactionRequest(account, categoryId, financialPriorityId)
        );

        closeCurrentRecurringTransactionHistory(
                recurringTransactionId,
                LocalDate.of(2026, 7, 1)
        );

        insertRecurringTransactionHistory(
                recurringTransactionId,
                LocalDate.of(2026, 7, 1),
                null,
                1,
                1,
                "MONTH",
                "NEXT_BUSINESS_DAY",
                new BigDecimal("-900.00"),
                null,
                new BigDecimal("2400.00")
        );

        insertRecurringTransactionDetailsHistory(
                recurringTransactionId,
                "Affitto aggiornato",
                updatedCategoryId,
                updatedFinancialPriorityId,
                account,
                LocalDate.of(2026, 7, 1),
                owner.userGroupId()
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH + "/" + recurringTransactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recurringTransactionId").value(recurringTransactionId.toString()))
                .andExpect(jsonPath("$.effectiveFrom").value("2026-07-01"))
                .andExpect(jsonPath("$.effectiveTo").doesNotExist())
                .andExpect(jsonPath("$.paymentDateAdjustmentPolicy").value("NEXT_BUSINESS_DAY"))
                .andExpect(jsonPath("$.paymentAmount").value(-900.0))
                .andExpect(jsonPath("$.finalPaymentAmount").value(2400.0))
                .andExpect(jsonPath("$.recurringTransactionDescription").value("Affitto aggiornato"))
                .andExpect(jsonPath("$.categoryId").value(updatedCategoryId.toString()))
                .andExpect(jsonPath("$.financialPriorityId").value(updatedFinancialPriorityId.toString()))
                .andExpect(jsonPath("$.linkedAccountId").value(account.accountId().toString()));
    }

    @Test
    void superCollaboratorShouldGetRecurringTransactionFromGroupAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef superCollaborator = createUser(owner.userGroupId(), "SUPER_COLLABORATOR");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring get super");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria get super");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID recurringTransactionId = createRecurringTransactionThroughApi(
                owner,
                minimalRecurringTransactionRequest(account, categoryId, financialPriorityId)
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH + "/" + recurringTransactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(superCollaborator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recurringTransactionId").value(recurringTransactionId.toString()))
                .andExpect(jsonPath("$.linkedAccountId").value(account.accountId().toString()));
    }

    @Test
    void viewerCollaboratorShouldGetRecurringTransactionEvenWhenAccountIsNotOperable() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef viewer = createUser(owner.userGroupId(), "VIEWER_COLLABORATOR");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring get viewer hidden");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria get viewer hidden");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID recurringTransactionId = createRecurringTransactionThroughApi(
                owner,
                minimalRecurringTransactionRequest(account, categoryId, financialPriorityId)
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH + "/" + recurringTransactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(viewer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recurringTransactionId").value(recurringTransactionId.toString()))
                .andExpect(jsonPath("$.linkedAccountId").value(account.accountId().toString()));
    }

    @Test
    void collaboratorShouldGetRecurringTransactionOnLinkedAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring get collaborator linked");
        grantAccountAccess(account, collaborator);

        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria get collaborator linked");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID recurringTransactionId = createRecurringTransactionThroughApi(
                owner,
                minimalRecurringTransactionRequest(account, categoryId, financialPriorityId)
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH + "/" + recurringTransactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recurringTransactionId").value(recurringTransactionId.toString()))
                .andExpect(jsonPath("$.linkedAccountId").value(account.accountId().toString()));
    }

    @Test
    void collaboratorShouldReceiveNotFoundWhenGettingRecurringTransactionOnHiddenAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef hiddenAccount = createAccount(owner.userGroupId(), "Conto recurring get collaborator hidden");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria get collaborator hidden");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID recurringTransactionId = createRecurringTransactionThroughApi(
                owner,
                minimalRecurringTransactionRequest(hiddenAccount, categoryId, financialPriorityId)
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH + "/" + recurringTransactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.recurringTransaction.notFound"));
    }

    @Test
    void collaboratorShouldReceiveNotFoundWhenCurrentDetailsMoveToHiddenAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef linkedAccount = createAccount(owner.userGroupId(), "Conto recurring get old linked");
        AccountRef hiddenAccount = createAccount(owner.userGroupId(), "Conto recurring get new hidden");

        grantAccountAccess(linkedAccount, collaborator);

        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria get move hidden");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID recurringTransactionId = createRecurringTransactionThroughApi(
                owner,
                minimalRecurringTransactionRequest(linkedAccount, categoryId, financialPriorityId)
        );

        insertRecurringTransactionDetailsHistory(
                recurringTransactionId,
                "Ricorrente spostata su conto nascosto",
                categoryId,
                financialPriorityId,
                hiddenAccount,
                LocalDate.of(2026, 7, 1),
                owner.userGroupId()
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH + "/" + recurringTransactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.recurringTransaction.notFound"));
    }

    @Test
    void ownerShouldReceiveNotFoundWhenGettingRecurringTransactionFromAnotherGroup() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef otherOwner = createUserWithNewGroup("OWNER");

        AccountRef otherAccount = createAccount(otherOwner.userGroupId(), "Conto recurring get other group");
        UUID otherCategoryId = createActiveCategory(
                otherOwner.userGroupId(),
                otherOwner.userId(),
                "Categoria get other group"
        );
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID otherRecurringTransactionId = createRecurringTransactionThroughApi(
                otherOwner,
                minimalRecurringTransactionRequest(otherAccount, otherCategoryId, financialPriorityId)
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH + "/" + otherRecurringTransactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.recurringTransaction.notFound"));
    }

    @Test
    void getRecurringTransactionShouldReturnNotFoundWhenRecurringTransactionDoesNotExist() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH + "/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.recurringTransaction.notFound"));
    }

    @Test
    void getRecurringTransactionShouldReturnBadRequestWhenRecurringTransactionIdIsInvalid() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH + "/not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getRecurringTransactionShouldReturnNotFoundWhenCurrentHistoryIsMissing() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring missing history");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria missing history");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID recurringTransactionId = createRecurringTransactionThroughApi(
                owner,
                minimalRecurringTransactionRequest(account, categoryId, financialPriorityId)
        );

        jdbcTemplate.update("""
                        DELETE FROM recurring_transaction_history
                        WHERE recurring_transaction_id = ?
                        """,
                recurringTransactionId
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH + "/" + recurringTransactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.recurringTransaction.historyNotFound"));
    }

    @Test
    void getRecurringTransactionShouldReturnNotFoundWhenCurrentDetailsAreMissing() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring missing details");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria missing details");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID recurringTransactionId = createRecurringTransactionThroughApi(
                owner,
                minimalRecurringTransactionRequest(account, categoryId, financialPriorityId)
        );

        jdbcTemplate.update("""
                        DELETE FROM recurring_transaction_details_history
                        WHERE recurring_transaction_id = ?
                        """,
                recurringTransactionId
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH + "/" + recurringTransactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.recurringTransaction.detailsNotFound"));
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

    @Test
    void ownerShouldGetRecurringTransactionDetailWithAllOptionalFields() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring get full");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria get full");
        UUID financialPriorityId = financialPriorityId("CRITICAL");

        CreditCardRef creditCard = createCreditCard(owner.userGroupId(), account, "Carta get full");

        BucketRef bucket = createOpenBucket(owner.userGroupId(), "Bucket get full");
        linkBucketToAccount(bucket, account, owner.userGroupId());

        UUID simulationGroupId = createSimulationGroup(owner.userGroupId(), "Simulation get full");
        linkSimulationGroupToAccount(simulationGroupId, account);

        Map<String, Object> body = validRecurringTransactionRequest(account, categoryId, financialPriorityId);
        body.put("recurringTransactionDescription", "  Ricorrente completa GET  ");
        body.put("paymentAmount", new BigDecimal("-123.45"));
        body.put("recurringTransactionAmountIsAdjustable", true);
        body.put("recurringTransactionFirstPaymentDate", "2026-06-03");
        body.put("recurrenceInterval", 2);
        body.put("recurrenceUnit", "WEEK");
        body.put("paymentDateAdjustmentPolicy", "NEXT_BUSINESS_DAY");
        body.put("recurringTransactionEndDate", "2027-06-03");
        body.put("finalPaymentAmount", new BigDecimal("2400.00"));
        body.put("linkedCreditCardId", creditCard.creditCardId());
        body.put("linkedBucketId", bucket.bucketId());
        body.put("recurringTransactionAffectsAccountBalance", false);
        body.put("recurringTransactionAffectsLiquidity", true);
        body.put("recurringTransactionIsSimulated", true);
        body.put("simulationGroupId", simulationGroupId);
        body.put("recurringTransactionReminderEnabled", false);
        body.put("recurringTransactionReminderDaysBefore", 30);

        UUID recurringTransactionId = createRecurringTransactionThroughApi(owner, body);

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH + "/" + recurringTransactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recurringTransactionId").value(recurringTransactionId.toString()))
                .andExpect(jsonPath("$.recurringTransactionDescription").value("Ricorrente completa GET"))
                .andExpect(jsonPath("$.paymentAmount").value(-123.45))
                .andExpect(jsonPath("$.recurringTransactionAmountIsAdjustable").value(true))
                .andExpect(jsonPath("$.recurringTransactionFirstPaymentDate").value("2026-06-03"))
                .andExpect(jsonPath("$.dayOfUnit").value(3))
                .andExpect(jsonPath("$.recurrenceInterval").value(2))
                .andExpect(jsonPath("$.recurrenceUnit").value("WEEK"))
                .andExpect(jsonPath("$.paymentDateAdjustmentPolicy").value("NEXT_BUSINESS_DAY"))
                .andExpect(jsonPath("$.recurringTransactionEndDate").value("2027-06-03"))
                .andExpect(jsonPath("$.finalPaymentAmount").value(2400.0))
                .andExpect(jsonPath("$.categoryId").value(categoryId.toString()))
                .andExpect(jsonPath("$.financialPriorityId").value(financialPriorityId.toString()))
                .andExpect(jsonPath("$.linkedAccountId").value(account.accountId().toString()))
                .andExpect(jsonPath("$.linkedCreditCardId").value(creditCard.creditCardId().toString()))
                .andExpect(jsonPath("$.linkedBucketId").value(bucket.bucketId().toString()))
                .andExpect(jsonPath("$.recurringTransactionAffectsAccountBalance").value(false))
                .andExpect(jsonPath("$.recurringTransactionAffectsLiquidity").value(true))
                .andExpect(jsonPath("$.recurringTransactionIsSimulated").value(true))
                .andExpect(jsonPath("$.simulationGroupId").value(simulationGroupId.toString()))
                .andExpect(jsonPath("$.recurringTransactionReminderEnabled").value(false))
                .andExpect(jsonPath("$.recurringTransactionReminderDaysBefore").value(30));
    }

    @Test
    void collaboratorShouldGetRecurringTransactionWhenCurrentDetailsMoveToLinkedAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef hiddenAccount = createAccount(owner.userGroupId(), "Conto recurring old hidden");
        AccountRef linkedAccount = createAccount(owner.userGroupId(), "Conto recurring new linked");

        grantAccountAccess(linkedAccount, collaborator);

        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria move linked");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID recurringTransactionId = createRecurringTransactionThroughApi(
                owner,
                minimalRecurringTransactionRequest(hiddenAccount, categoryId, financialPriorityId)
        );

        insertRecurringTransactionDetailsHistory(
                recurringTransactionId,
                "Ricorrente spostata su conto collegato",
                categoryId,
                financialPriorityId,
                linkedAccount,
                LocalDate.of(2026, 7, 1),
                owner.userGroupId()
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH + "/" + recurringTransactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recurringTransactionId").value(recurringTransactionId.toString()))
                .andExpect(jsonPath("$.recurringTransactionDescription").value("Ricorrente spostata su conto collegato"))
                .andExpect(jsonPath("$.linkedAccountId").value(linkedAccount.accountId().toString()));
    }

    @Test
    void getRecurringTransactionShouldReturnNotFoundWhenNoOpenCurrentHistoryExists() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring no open history");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria no open history");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID recurringTransactionId = createRecurringTransactionThroughApi(
                owner,
                minimalRecurringTransactionRequest(account, categoryId, financialPriorityId)
        );

        closeCurrentRecurringTransactionHistory(
                recurringTransactionId,
                LocalDate.of(2026, 7, 1)
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH + "/" + recurringTransactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.recurringTransaction.historyNotFound"));
    }

    @Test
    void listRecurringTransactionsShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerShouldListOnlyBaseRecurringTransactionsWhenNoSimulationGroupsAreProvided() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring list base");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria list base");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID simulationGroupId = createSimulationGroup(owner.userGroupId(), "Simulation list base");
        linkSimulationGroupToAccount(simulationGroupId, account);

        UUID baseOneId = createBaseRecurringTransaction(
                owner,
                account,
                categoryId,
                financialPriorityId,
                "Base uno",
                LocalDate.of(2026, 6, 1),
                new BigDecimal("-800.00")
        );

        UUID baseTwoId = createBaseRecurringTransaction(
                owner,
                account,
                categoryId,
                financialPriorityId,
                "Base due",
                LocalDate.of(2026, 7, 1),
                new BigDecimal("-100.00")
        );

        createSimulatedRecurringTransaction(
                owner,
                account,
                categoryId,
                financialPriorityId,
                simulationGroupId,
                "Simulata esclusa",
                LocalDate.of(2026, 8, 1),
                new BigDecimal("-300.00")
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].recurringTransactionId").value(baseOneId.toString()))
                .andExpect(jsonPath("$[0].recurringTransactionDescription").value("Base uno"))
                .andExpect(jsonPath("$[1].recurringTransactionId").value(baseTwoId.toString()))
                .andExpect(jsonPath("$[1].recurringTransactionDescription").value("Base due"));
    }

    @Test
    void ownerShouldListBaseAndSelectedSimulationGroups() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring list simulations");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria list simulations");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID simulationGroupOneId = createSimulationGroup(owner.userGroupId(), "Simulation list one");
        UUID simulationGroupTwoId = createSimulationGroup(owner.userGroupId(), "Simulation list two");
        UUID simulationGroupThreeId = createSimulationGroup(owner.userGroupId(), "Simulation list three");

        linkSimulationGroupToAccount(simulationGroupOneId, account);
        linkSimulationGroupToAccount(simulationGroupTwoId, account);
        linkSimulationGroupToAccount(simulationGroupThreeId, account);

        UUID baseId = createBaseRecurringTransaction(
                owner,
                account,
                categoryId,
                financialPriorityId,
                "Base inclusa",
                LocalDate.of(2026, 6, 1),
                new BigDecimal("-800.00")
        );

        UUID simulationOneTransactionId = createSimulatedRecurringTransaction(
                owner,
                account,
                categoryId,
                financialPriorityId,
                simulationGroupOneId,
                "Simulata uno inclusa",
                LocalDate.of(2026, 7, 1),
                new BigDecimal("-100.00")
        );

        UUID simulationTwoTransactionId = createSimulatedRecurringTransaction(
                owner,
                account,
                categoryId,
                financialPriorityId,
                simulationGroupTwoId,
                "Simulata due inclusa",
                LocalDate.of(2026, 8, 1),
                new BigDecimal("-200.00")
        );

        createSimulatedRecurringTransaction(
                owner,
                account,
                categoryId,
                financialPriorityId,
                simulationGroupThreeId,
                "Simulata tre esclusa",
                LocalDate.of(2026, 9, 1),
                new BigDecimal("-300.00")
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH)
                        .param("simulationGroupIds", simulationGroupOneId.toString(), simulationGroupTwoId.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].recurringTransactionId").value(baseId.toString()))
                .andExpect(jsonPath("$[1].recurringTransactionId").value(simulationOneTransactionId.toString()))
                .andExpect(jsonPath("$[2].recurringTransactionId").value(simulationTwoTransactionId.toString()));
    }

    @Test
    void ownerShouldNotDuplicateResultsWhenSimulationGroupIdsContainDuplicates() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring duplicate simulations");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria duplicate simulations");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID simulationGroupId = createSimulationGroup(owner.userGroupId(), "Simulation duplicate");
        linkSimulationGroupToAccount(simulationGroupId, account);

        UUID baseId = createBaseRecurringTransaction(
                owner,
                account,
                categoryId,
                financialPriorityId,
                "Base duplicate",
                LocalDate.of(2026, 6, 1),
                new BigDecimal("-800.00")
        );

        UUID simulatedId = createSimulatedRecurringTransaction(
                owner,
                account,
                categoryId,
                financialPriorityId,
                simulationGroupId,
                "Simulata duplicate",
                LocalDate.of(2026, 7, 1),
                new BigDecimal("-100.00")
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH)
                        .param("simulationGroupIds", simulationGroupId.toString(), simulationGroupId.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].recurringTransactionId").value(baseId.toString()))
                .andExpect(jsonPath("$[1].recurringTransactionId").value(simulatedId.toString()));
    }

    @Test
    void ownerShouldFilterRecurringTransactionsByCurrentAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef accountOne = createAccount(owner.userGroupId(), "Conto recurring list account one");
        AccountRef accountTwo = createAccount(owner.userGroupId(), "Conto recurring list account two");

        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria list account");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID accountOneTransactionId = createBaseRecurringTransaction(
                owner,
                accountOne,
                categoryId,
                financialPriorityId,
                "Ricorrente conto uno",
                LocalDate.of(2026, 6, 1),
                new BigDecimal("-800.00")
        );

        createBaseRecurringTransaction(
                owner,
                accountTwo,
                categoryId,
                financialPriorityId,
                "Ricorrente conto due",
                LocalDate.of(2026, 7, 1),
                new BigDecimal("-100.00")
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH)
                        .param("accountId", accountOne.accountId().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].recurringTransactionId").value(accountOneTransactionId.toString()))
                .andExpect(jsonPath("$[0].linkedAccountId").value(accountOne.accountId().toString()));
    }

    @Test
    void ownerShouldFilterByCurrentDetailsAccountNotOriginalAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef oldAccount = createAccount(owner.userGroupId(), "Conto recurring old account");
        AccountRef currentAccount = createAccount(owner.userGroupId(), "Conto recurring current account");

        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria current account");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID recurringTransactionId = createBaseRecurringTransaction(
                owner,
                oldAccount,
                categoryId,
                financialPriorityId,
                "Ricorrente spostata",
                LocalDate.of(2026, 6, 1),
                new BigDecimal("-800.00")
        );

        insertRecurringTransactionDetailsHistory(
                recurringTransactionId,
                "Ricorrente spostata su conto corrente",
                categoryId,
                financialPriorityId,
                currentAccount,
                LocalDate.of(2026, 7, 1),
                owner.userGroupId()
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH)
                        .param("accountId", oldAccount.accountId().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH)
                        .param("accountId", currentAccount.accountId().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].recurringTransactionId").value(recurringTransactionId.toString()))
                .andExpect(jsonPath("$[0].linkedAccountId").value(currentAccount.accountId().toString()));
    }

    @Test
    void ownerShouldReceiveNotFoundWhenFilteringByAccountFromAnotherGroup() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef otherOwner = createUserWithNewGroup("OWNER");

        AccountRef otherAccount = createAccount(otherOwner.userGroupId(), "Conto recurring other group filter");

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH)
                        .param("accountId", otherAccount.accountId().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.account.notFound"));
    }

    @Test
    void listRecurringTransactionsShouldReturnBadRequestWhenAccountIdIsInvalid() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH)
                        .param("accountId", "not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listRecurringTransactionsShouldReturnBadRequestWhenSimulationGroupIdIsInvalid() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH)
                        .param("simulationGroupIds", "not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ownerShouldReceiveNotFoundWhenSimulationGroupDoesNotExist() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH)
                        .param("simulationGroupIds", UUID.randomUUID().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.simulationGroup.notFound"));
    }

    @Test
    void ownerShouldReceiveNotFoundWhenSimulationGroupBelongsToAnotherGroup() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef otherOwner = createUserWithNewGroup("OWNER");

        UUID otherSimulationGroupId = createSimulationGroup(otherOwner.userGroupId(), "Simulation other group");

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH)
                        .param("simulationGroupIds", otherSimulationGroupId.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.simulationGroup.notFound"));
    }

    @Test
    void ownerShouldReceiveNotFoundWhenSimulationGroupIsArchived() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        UUID archivedSimulationGroupId = createArchivedSimulationGroup(owner.userGroupId(), "Simulation archived list");

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH)
                        .param("simulationGroupIds", archivedSimulationGroupId.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.simulationGroup.notFound"));
    }

    @Test
    void listRecurringTransactionsShouldRejectTooManySimulationGroups() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        String[] simulationGroupIdParams = IntStream.range(0, 51)
                .mapToObj(index -> UUID.randomUUID().toString())
                .toArray(String[]::new);

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH)
                        .param("simulationGroupIds", simulationGroupIdParams)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.recurringTransaction.tooManySimulationGroups"));
    }

    @Test
    void listRecurringTransactionsShouldReturnEmptyArrayWhenNoRecurringTransactionsExist() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void listRecurringTransactionsShouldExcludeRowsWithoutOpenCurrentHistory() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring closed history list");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria closed history list");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID recurringTransactionId = createBaseRecurringTransaction(
                owner,
                account,
                categoryId,
                financialPriorityId,
                "Ricorrente senza history corrente",
                LocalDate.of(2026, 6, 1),
                new BigDecimal("-800.00")
        );

        closeCurrentRecurringTransactionHistory(
                recurringTransactionId,
                LocalDate.of(2026, 7, 1)
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void superCollaboratorShouldListGroupRecurringTransactions() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef superCollaborator = createUser(owner.userGroupId(), "SUPER_COLLABORATOR");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring list super");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria list super");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID recurringTransactionId = createBaseRecurringTransaction(
                owner,
                account,
                categoryId,
                financialPriorityId,
                "Ricorrente super",
                LocalDate.of(2026, 6, 1),
                new BigDecimal("-800.00")
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(superCollaborator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].recurringTransactionId").value(recurringTransactionId.toString()));
    }

    @Test
    void viewerCollaboratorShouldListGroupRecurringTransactionsEvenWithoutAccountLink() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef viewer = createUser(owner.userGroupId(), "VIEWER_COLLABORATOR");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring list viewer");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria list viewer");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID recurringTransactionId = createBaseRecurringTransaction(
                owner,
                account,
                categoryId,
                financialPriorityId,
                "Ricorrente viewer",
                LocalDate.of(2026, 6, 1),
                new BigDecimal("-800.00")
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(viewer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].recurringTransactionId").value(recurringTransactionId.toString()));
    }

    @Test
    void collaboratorShouldListOnlyRecurringTransactionsOnLinkedAccounts() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef linkedAccount = createAccount(owner.userGroupId(), "Conto recurring list collaborator linked");
        AccountRef hiddenAccount = createAccount(owner.userGroupId(), "Conto recurring list collaborator hidden");

        grantAccountAccess(linkedAccount, collaborator);

        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria list collaborator");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID linkedRecurringTransactionId = createBaseRecurringTransaction(
                owner,
                linkedAccount,
                categoryId,
                financialPriorityId,
                "Ricorrente collaborator linked",
                LocalDate.of(2026, 6, 1),
                new BigDecimal("-800.00")
        );

        createBaseRecurringTransaction(
                owner,
                hiddenAccount,
                categoryId,
                financialPriorityId,
                "Ricorrente collaborator hidden",
                LocalDate.of(2026, 7, 1),
                new BigDecimal("-100.00")
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].recurringTransactionId").value(linkedRecurringTransactionId.toString()))
                .andExpect(jsonPath("$[0].linkedAccountId").value(linkedAccount.accountId().toString()));
    }

    @Test
    void collaboratorShouldFilterListByLinkedAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef linkedAccountOne = createAccount(owner.userGroupId(), "Conto recurring collaborator filter one");
        AccountRef linkedAccountTwo = createAccount(owner.userGroupId(), "Conto recurring collaborator filter two");

        grantAccountAccess(linkedAccountOne, collaborator);
        grantAccountAccess(linkedAccountTwo, collaborator);

        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria collaborator filter");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID recurringTransactionId = createBaseRecurringTransaction(
                owner,
                linkedAccountOne,
                categoryId,
                financialPriorityId,
                "Ricorrente collaborator filter one",
                LocalDate.of(2026, 6, 1),
                new BigDecimal("-800.00")
        );

        createBaseRecurringTransaction(
                owner,
                linkedAccountTwo,
                categoryId,
                financialPriorityId,
                "Ricorrente collaborator filter two",
                LocalDate.of(2026, 7, 1),
                new BigDecimal("-100.00")
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH)
                        .param("accountId", linkedAccountOne.accountId().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].recurringTransactionId").value(recurringTransactionId.toString()))
                .andExpect(jsonPath("$[0].linkedAccountId").value(linkedAccountOne.accountId().toString()));
    }

    @Test
    void collaboratorShouldReceiveNotFoundWhenFilteringByHiddenAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef hiddenAccount = createAccount(owner.userGroupId(), "Conto recurring collaborator hidden filter");

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH)
                        .param("accountId", hiddenAccount.accountId().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.account.notFound"));
    }

    @Test
    void collaboratorShouldListBaseAndReadableSimulationGroups() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef linkedAccount = createAccount(owner.userGroupId(), "Conto recurring collaborator simulation linked");
        grantAccountAccess(linkedAccount, collaborator);

        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria collaborator simulation");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID simulationGroupId = createSimulationGroup(owner.userGroupId(), "Simulation collaborator readable");
        linkSimulationGroupToAccount(simulationGroupId, linkedAccount);

        UUID baseId = createBaseRecurringTransaction(
                owner,
                linkedAccount,
                categoryId,
                financialPriorityId,
                "Base collaborator simulation",
                LocalDate.of(2026, 6, 1),
                new BigDecimal("-800.00")
        );

        UUID simulatedId = createSimulatedRecurringTransaction(
                owner,
                linkedAccount,
                categoryId,
                financialPriorityId,
                simulationGroupId,
                "Simulata collaborator readable",
                LocalDate.of(2026, 7, 1),
                new BigDecimal("-100.00")
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH)
                        .param("simulationGroupIds", simulationGroupId.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].recurringTransactionId").value(baseId.toString()))
                .andExpect(jsonPath("$[1].recurringTransactionId").value(simulatedId.toString()));
    }

    @Test
    void collaboratorShouldReceiveNotFoundWhenSimulationGroupIsNotReadable() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef hiddenAccount = createAccount(owner.userGroupId(), "Conto recurring collaborator simulation hidden");

        UUID simulationGroupId = createSimulationGroup(owner.userGroupId(), "Simulation collaborator hidden");
        linkSimulationGroupToAccount(simulationGroupId, hiddenAccount);

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH)
                        .param("simulationGroupIds", simulationGroupId.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.simulationGroup.notFound"));
    }

    @Test
    void ownerShouldNotSeeRecurringTransactionsFromAnotherGroupInList() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef otherOwner = createUserWithNewGroup("OWNER");

        AccountRef otherAccount = createAccount(otherOwner.userGroupId(), "Conto recurring list other group");
        UUID otherCategoryId = createActiveCategory(
                otherOwner.userGroupId(),
                otherOwner.userId(),
                "Categoria list other group"
        );
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        createBaseRecurringTransaction(
                otherOwner,
                otherAccount,
                otherCategoryId,
                financialPriorityId,
                "Ricorrente altro gruppo",
                LocalDate.of(2026, 6, 1),
                new BigDecimal("-800.00")
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void ownerShouldListBaseAndSelectedSimulationGroupsFilteredByAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef accountOne = createAccount(owner.userGroupId(), "Conto recurring account simulation one");
        AccountRef accountTwo = createAccount(owner.userGroupId(), "Conto recurring account simulation two");

        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria account simulation");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID simulationGroupOneId = createSimulationGroup(owner.userGroupId(), "Simulation account one");
        UUID simulationGroupTwoId = createSimulationGroup(owner.userGroupId(), "Simulation account two");

        linkSimulationGroupToAccount(simulationGroupOneId, accountOne);
        linkSimulationGroupToAccount(simulationGroupTwoId, accountTwo);

        UUID baseAccountOneId = createBaseRecurringTransaction(
                owner,
                accountOne,
                categoryId,
                financialPriorityId,
                "Base conto uno",
                LocalDate.of(2026, 6, 1),
                new BigDecimal("-800.00")
        );

        createBaseRecurringTransaction(
                owner,
                accountTwo,
                categoryId,
                financialPriorityId,
                "Base conto due esclusa",
                LocalDate.of(2026, 6, 15),
                new BigDecimal("-500.00")
        );

        UUID simulatedAccountOneId = createSimulatedRecurringTransaction(
                owner,
                accountOne,
                categoryId,
                financialPriorityId,
                simulationGroupOneId,
                "Simulata conto uno",
                LocalDate.of(2026, 7, 1),
                new BigDecimal("-100.00")
        );

        createSimulatedRecurringTransaction(
                owner,
                accountTwo,
                categoryId,
                financialPriorityId,
                simulationGroupTwoId,
                "Simulata conto due esclusa",
                LocalDate.of(2026, 7, 15),
                new BigDecimal("-200.00")
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH)
                        .param("accountId", accountOne.accountId().toString())
                        .param("simulationGroupIds", simulationGroupOneId.toString(), simulationGroupTwoId.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].recurringTransactionId").value(baseAccountOneId.toString()))
                .andExpect(jsonPath("$[0].linkedAccountId").value(accountOne.accountId().toString()))
                .andExpect(jsonPath("$[1].recurringTransactionId").value(simulatedAccountOneId.toString()))
                .andExpect(jsonPath("$[1].linkedAccountId").value(accountOne.accountId().toString()));
    }

    @Test
    void listRecurringTransactionsShouldReturnCurrentHistoryAndCurrentDetails() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring list current state");
        UUID oldCategoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria list old");
        UUID newCategoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria list new");
        UUID oldFinancialPriorityId = financialPriorityId("ESSENTIAL");
        UUID newFinancialPriorityId = financialPriorityId("OPTIONAL");

        UUID recurringTransactionId = createBaseRecurringTransaction(
                owner,
                account,
                oldCategoryId,
                oldFinancialPriorityId,
                "Ricorrente vecchia",
                LocalDate.of(2026, 6, 1),
                new BigDecimal("-800.00")
        );

        closeCurrentRecurringTransactionHistory(
                recurringTransactionId,
                LocalDate.of(2026, 7, 1)
        );

        insertRecurringTransactionHistory(
                recurringTransactionId,
                LocalDate.of(2026, 7, 1),
                null,
                1,
                1,
                "MONTH",
                "NEXT_BUSINESS_DAY",
                new BigDecimal("-900.00"),
                null,
                new BigDecimal("2400.00")
        );

        insertRecurringTransactionDetailsHistory(
                recurringTransactionId,
                "Ricorrente aggiornata in lista",
                newCategoryId,
                newFinancialPriorityId,
                account,
                LocalDate.of(2026, 7, 1),
                owner.userGroupId()
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].recurringTransactionId").value(recurringTransactionId.toString()))
                .andExpect(jsonPath("$[0].recurringTransactionDescription").value("Ricorrente aggiornata in lista"))
                .andExpect(jsonPath("$[0].effectiveFrom").value("2026-07-01"))
                .andExpect(jsonPath("$[0].paymentDateAdjustmentPolicy").value("NEXT_BUSINESS_DAY"))
                .andExpect(jsonPath("$[0].paymentAmount").value(-900.0))
                .andExpect(jsonPath("$[0].finalPaymentAmount").value(2400.0))
                .andExpect(jsonPath("$[0].categoryId").value(newCategoryId.toString()))
                .andExpect(jsonPath("$[0].financialPriorityId").value(newFinancialPriorityId.toString()));
    }

    @Test
    void listRecurringTransactionsShouldRejectPartiallyInvalidSimulationGroups() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        UUID validSimulationGroupId = createSimulationGroup(owner.userGroupId(), "Simulation valid partial");
        UUID invalidSimulationGroupId = UUID.randomUUID();

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH)
                        .param("simulationGroupIds", validSimulationGroupId.toString(), invalidSimulationGroupId.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.simulationGroup.notFound"));
    }

    @Test
    void collaboratorShouldNotGetDuplicateRowsWhenSimulationGroupIsLinkedToMultipleAccessibleAccounts() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef accountOne = createAccount(owner.userGroupId(), "Conto recurring duplicate account one");
        AccountRef accountTwo = createAccount(owner.userGroupId(), "Conto recurring duplicate account two");

        grantAccountAccess(accountOne, collaborator);
        grantAccountAccess(accountTwo, collaborator);

        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria duplicate account");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID simulationGroupId = createSimulationGroup(owner.userGroupId(), "Simulation linked to multiple accounts");
        linkSimulationGroupToAccount(simulationGroupId, accountOne);
        linkSimulationGroupToAccount(simulationGroupId, accountTwo);

        UUID simulatedRecurringTransactionId = createSimulatedRecurringTransaction(
                owner,
                accountOne,
                categoryId,
                financialPriorityId,
                simulationGroupId,
                "Simulata senza duplicati",
                LocalDate.of(2026, 7, 1),
                new BigDecimal("-100.00")
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH)
                        .param("simulationGroupIds", simulationGroupId.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].recurringTransactionId").value(simulatedRecurringTransactionId.toString()));
    }

    @Test
    void collaboratorShouldExcludeSimulatedRecurringTransactionsOnHiddenAccountsEvenWhenSimulationGroupIsReadable() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef linkedAccount = createAccount(owner.userGroupId(), "Conto recurring readable simulation linked");
        AccountRef hiddenAccount = createAccount(owner.userGroupId(), "Conto recurring readable simulation hidden");

        grantAccountAccess(linkedAccount, collaborator);

        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria readable simulation hidden");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID simulationGroupId = createSimulationGroup(owner.userGroupId(), "Simulation readable with hidden tx");
        linkSimulationGroupToAccount(simulationGroupId, linkedAccount);
        linkSimulationGroupToAccount(simulationGroupId, hiddenAccount);

        UUID baseLinkedId = createBaseRecurringTransaction(
                owner,
                linkedAccount,
                categoryId,
                financialPriorityId,
                "Base visibile collaborator",
                LocalDate.of(2026, 6, 1),
                new BigDecimal("-800.00")
        );

        createSimulatedRecurringTransaction(
                owner,
                hiddenAccount,
                categoryId,
                financialPriorityId,
                simulationGroupId,
                "Simulata nascosta collaborator",
                LocalDate.of(2026, 7, 1),
                new BigDecimal("-100.00")
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH)
                        .param("simulationGroupIds", simulationGroupId.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].recurringTransactionId").value(baseLinkedId.toString()))
                .andExpect(jsonPath("$[0].linkedAccountId").value(linkedAccount.accountId().toString()));
    }

    @Test
    void getRecurringTransactionHistoryShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH + "/" + UUID.randomUUID() + "/history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerShouldGetRecurringTransactionHistory() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring history owner");
        UUID oldCategoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria history old");
        UUID newCategoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria history new");
        UUID oldFinancialPriorityId = financialPriorityId("ESSENTIAL");
        UUID newFinancialPriorityId = financialPriorityId("OPTIONAL");

        UUID recurringTransactionId = createBaseRecurringTransaction(
                owner,
                account,
                oldCategoryId,
                oldFinancialPriorityId,
                "Ricorrente history iniziale",
                LocalDate.of(2026, 6, 1),
                new BigDecimal("-800.00")
        );

        closeCurrentRecurringTransactionHistory(
                recurringTransactionId,
                LocalDate.of(2026, 7, 1)
        );

        insertRecurringTransactionHistory(
                recurringTransactionId,
                LocalDate.of(2026, 7, 1),
                null,
                1,
                1,
                "MONTH",
                "NEXT_BUSINESS_DAY",
                new BigDecimal("-900.00"),
                LocalDate.of(2027, 6, 1),
                new BigDecimal("2400.00")
        );

        insertRecurringTransactionDetailsHistory(
                recurringTransactionId,
                "Ricorrente history aggiornata",
                newCategoryId,
                newFinancialPriorityId,
                account,
                LocalDate.of(2026, 7, 1),
                owner.userGroupId()
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH + "/" + recurringTransactionId + "/history")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recurringTransactionId").value(recurringTransactionId.toString()))
                .andExpect(jsonPath("$.ruleHistory", hasSize(2)))
                .andExpect(jsonPath("$.ruleHistory[0].effectiveFrom").value("2026-06-01"))
                .andExpect(jsonPath("$.ruleHistory[0].effectiveTo").value("2026-07-01"))
                .andExpect(jsonPath("$.ruleHistory[0].dayOfUnit").value(1))
                .andExpect(jsonPath("$.ruleHistory[0].recurrenceInterval").value(1))
                .andExpect(jsonPath("$.ruleHistory[0].recurrenceUnit").value("MONTH"))
                .andExpect(jsonPath("$.ruleHistory[0].paymentDateAdjustmentPolicy").value("PREVIOUS_BUSINESS_DAY"))
                .andExpect(jsonPath("$.ruleHistory[0].paymentAmount").value(-800.0))
                .andExpect(jsonPath("$.ruleHistory[1].effectiveFrom").value("2026-07-01"))
                .andExpect(jsonPath("$.ruleHistory[1].effectiveTo").doesNotExist())
                .andExpect(jsonPath("$.ruleHistory[1].paymentDateAdjustmentPolicy").value("NEXT_BUSINESS_DAY"))
                .andExpect(jsonPath("$.ruleHistory[1].paymentAmount").value(-900.0))
                .andExpect(jsonPath("$.ruleHistory[1].recurringTransactionEndDate").value("2027-06-01"))
                .andExpect(jsonPath("$.ruleHistory[1].finalPaymentAmount").value(2400.0))
                .andExpect(jsonPath("$.detailsHistory", hasSize(2)))
                .andExpect(jsonPath("$.detailsHistory[0].effectiveFrom").value("2026-06-01"))
                .andExpect(jsonPath("$.detailsHistory[0].recurringTransactionDescription").value("Ricorrente history iniziale"))
                .andExpect(jsonPath("$.detailsHistory[0].categoryId").value(oldCategoryId.toString()))
                .andExpect(jsonPath("$.detailsHistory[0].financialPriorityId").value(oldFinancialPriorityId.toString()))
                .andExpect(jsonPath("$.detailsHistory[0].linkedAccountId").value(account.accountId().toString()))
                .andExpect(jsonPath("$.detailsHistory[0].recurringTransactionAffectsAccountBalance").value(true))
                .andExpect(jsonPath("$.detailsHistory[0].recurringTransactionAffectsLiquidity").value(true))
                .andExpect(jsonPath("$.detailsHistory[1].effectiveFrom").value("2026-07-01"))
                .andExpect(jsonPath("$.detailsHistory[1].recurringTransactionDescription").value("Ricorrente history aggiornata"))
                .andExpect(jsonPath("$.detailsHistory[1].categoryId").value(newCategoryId.toString()))
                .andExpect(jsonPath("$.detailsHistory[1].financialPriorityId").value(newFinancialPriorityId.toString()))
                .andExpect(jsonPath("$.detailsHistory[1].linkedAccountId").value(account.accountId().toString()));
    }

    @Test
    void ownerShouldGetRecurringTransactionHistoryWithOptionalDetailsFields() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring history optional");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria history optional");
        UUID financialPriorityId = financialPriorityId("CRITICAL");

        CreditCardRef creditCard = createCreditCard(owner.userGroupId(), account, "Carta history optional");

        BucketRef bucket = createOpenBucket(owner.userGroupId(), "Bucket history optional");
        linkBucketToAccount(bucket, account, owner.userGroupId());

        Map<String, Object> body = minimalRecurringTransactionRequest(account, categoryId, financialPriorityId);
        body.put("recurringTransactionDescription", "Ricorrente history optional");
        body.put("linkedCreditCardId", creditCard.creditCardId());
        body.put("linkedBucketId", bucket.bucketId());
        body.put("recurringTransactionAffectsAccountBalance", false);
        body.put("recurringTransactionAffectsLiquidity", true);

        UUID recurringTransactionId = createRecurringTransactionThroughApi(owner, body);

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH + "/" + recurringTransactionId + "/history")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detailsHistory", hasSize(1)))
                .andExpect(jsonPath("$.detailsHistory[0].linkedCreditCardId").value(creditCard.creditCardId().toString()))
                .andExpect(jsonPath("$.detailsHistory[0].linkedBucketId").value(bucket.bucketId().toString()))
                .andExpect(jsonPath("$.detailsHistory[0].recurringTransactionAffectsAccountBalance").value(false))
                .andExpect(jsonPath("$.detailsHistory[0].recurringTransactionAffectsLiquidity").value(true));
    }

    @Test
    void superCollaboratorShouldGetRecurringTransactionHistory() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef superCollaborator = createUser(owner.userGroupId(), "SUPER_COLLABORATOR");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring history super");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria history super");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID recurringTransactionId = createBaseRecurringTransaction(
                owner,
                account,
                categoryId,
                financialPriorityId,
                "Ricorrente history super",
                LocalDate.of(2026, 6, 1),
                new BigDecimal("-800.00")
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH + "/" + recurringTransactionId + "/history")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(superCollaborator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recurringTransactionId").value(recurringTransactionId.toString()))
                .andExpect(jsonPath("$.ruleHistory", hasSize(1)))
                .andExpect(jsonPath("$.detailsHistory", hasSize(1)));
    }

    @Test
    void viewerCollaboratorShouldGetRecurringTransactionHistoryEvenWithoutAccountLink() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef viewer = createUser(owner.userGroupId(), "VIEWER_COLLABORATOR");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring history viewer");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria history viewer");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID recurringTransactionId = createBaseRecurringTransaction(
                owner,
                account,
                categoryId,
                financialPriorityId,
                "Ricorrente history viewer",
                LocalDate.of(2026, 6, 1),
                new BigDecimal("-800.00")
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH + "/" + recurringTransactionId + "/history")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(viewer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recurringTransactionId").value(recurringTransactionId.toString()))
                .andExpect(jsonPath("$.ruleHistory", hasSize(1)))
                .andExpect(jsonPath("$.detailsHistory", hasSize(1)));
    }

    @Test
    void collaboratorShouldReceiveForbiddenWhenGettingRecurringTransactionHistoryOnLinkedAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring history collaborator linked");
        grantAccountAccess(account, collaborator);

        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria history collaborator linked");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID recurringTransactionId = createBaseRecurringTransaction(
                owner,
                account,
                categoryId,
                financialPriorityId,
                "Ricorrente history collaborator linked",
                LocalDate.of(2026, 6, 1),
                new BigDecimal("-800.00")
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH + "/" + recurringTransactionId + "/history")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("finance.recurringTransaction.historyOperationNotAllowed"));
    }

    @Test
    void collaboratorShouldReceiveNotFoundWhenGettingRecurringTransactionHistoryOnHiddenAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef hiddenAccount = createAccount(owner.userGroupId(), "Conto recurring history collaborator hidden");

        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria history collaborator hidden");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID recurringTransactionId = createBaseRecurringTransaction(
                owner,
                hiddenAccount,
                categoryId,
                financialPriorityId,
                "Ricorrente history collaborator hidden",
                LocalDate.of(2026, 6, 1),
                new BigDecimal("-800.00")
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH + "/" + recurringTransactionId + "/history")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.recurringTransaction.notFound"));
    }

    @Test
    void ownerShouldReceiveNotFoundWhenGettingRecurringTransactionHistoryFromAnotherGroup() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef otherOwner = createUserWithNewGroup("OWNER");

        AccountRef otherAccount = createAccount(otherOwner.userGroupId(), "Conto recurring history other group");
        UUID otherCategoryId = createActiveCategory(
                otherOwner.userGroupId(),
                otherOwner.userId(),
                "Categoria history other group"
        );
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID otherRecurringTransactionId = createBaseRecurringTransaction(
                otherOwner,
                otherAccount,
                otherCategoryId,
                financialPriorityId,
                "Ricorrente history altro gruppo",
                LocalDate.of(2026, 6, 1),
                new BigDecimal("-800.00")
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH + "/" + otherRecurringTransactionId + "/history")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.recurringTransaction.notFound"));
    }

    @Test
    void getRecurringTransactionHistoryShouldReturnNotFoundWhenRecurringTransactionDoesNotExist() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH + "/" + UUID.randomUUID() + "/history")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.recurringTransaction.notFound"));
    }

    @Test
    void getRecurringTransactionHistoryShouldReturnBadRequestWhenRecurringTransactionIdIsInvalid() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH + "/not-a-uuid/history")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getRecurringTransactionHistoryShouldReturnNotFoundWhenRuleHistoryIsMissing() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring history missing rule");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria history missing rule");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID recurringTransactionId = createBaseRecurringTransaction(
                owner,
                account,
                categoryId,
                financialPriorityId,
                "Ricorrente history missing rule",
                LocalDate.of(2026, 6, 1),
                new BigDecimal("-800.00")
        );

        jdbcTemplate.update("""
                        DELETE FROM recurring_transaction_history
                        WHERE recurring_transaction_id = ?
                        """,
                recurringTransactionId
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH + "/" + recurringTransactionId + "/history")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.recurringTransaction.historyNotFound"));
    }

    @Test
    void getRecurringTransactionHistoryShouldReturnNotFoundWhenDetailsHistoryIsMissing() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring history missing details");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria history missing details");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID recurringTransactionId = createBaseRecurringTransaction(
                owner,
                account,
                categoryId,
                financialPriorityId,
                "Ricorrente history missing details",
                LocalDate.of(2026, 6, 1),
                new BigDecimal("-800.00")
        );

        jdbcTemplate.update("""
                        DELETE FROM recurring_transaction_details_history
                        WHERE recurring_transaction_id = ?
                        """,
                recurringTransactionId
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH + "/" + recurringTransactionId + "/history")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.recurringTransaction.detailsNotFound"));
    }

    @Test
    void getRecurringTransactionHistoryShouldWorkWhenNoOpenCurrentHistoryExists() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring history closed only");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria history closed only");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID recurringTransactionId = createBaseRecurringTransaction(
                owner,
                account,
                categoryId,
                financialPriorityId,
                "Ricorrente history solo chiusa",
                LocalDate.of(2026, 6, 1),
                new BigDecimal("-800.00")
        );

        closeCurrentRecurringTransactionHistory(
                recurringTransactionId,
                LocalDate.of(2026, 7, 1)
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH + "/" + recurringTransactionId + "/history")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recurringTransactionId").value(recurringTransactionId.toString()))
                .andExpect(jsonPath("$.ruleHistory", hasSize(1)))
                .andExpect(jsonPath("$.ruleHistory[0].effectiveFrom").value("2026-06-01"))
                .andExpect(jsonPath("$.ruleHistory[0].effectiveTo").value("2026-07-01"))
                .andExpect(jsonPath("$.detailsHistory", hasSize(1)));
    }

    @Test
    void ownerShouldGetHistoryForSimulatedRecurringTransaction() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto recurring history simulated");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria history simulated");
        UUID financialPriorityId = financialPriorityId("ESSENTIAL");

        UUID simulationGroupId = createSimulationGroup(owner.userGroupId(), "Simulation history");
        linkSimulationGroupToAccount(simulationGroupId, account);

        UUID recurringTransactionId = createSimulatedRecurringTransaction(
                owner,
                account,
                categoryId,
                financialPriorityId,
                simulationGroupId,
                "Ricorrente simulata history",
                LocalDate.of(2026, 6, 1),
                new BigDecimal("-500.00")
        );

        mockMvc.perform(get(RECURRING_TRANSACTIONS_PATH + "/" + recurringTransactionId + "/history")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recurringTransactionId").value(recurringTransactionId.toString()))
                .andExpect(jsonPath("$.ruleHistory", hasSize(1)))
                .andExpect(jsonPath("$.ruleHistory[0].paymentAmount").value(-500.0))
                .andExpect(jsonPath("$.detailsHistory", hasSize(1)))
                .andExpect(jsonPath("$.detailsHistory[0].recurringTransactionDescription")
                        .value("Ricorrente simulata history"));
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

    private UUID createRecurringTransactionThroughApi(
            UserRef authenticatedUser,
            Map<String, Object> body
    ) throws Exception {
        MvcResult result = mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(authenticatedUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();

        return recurringTransactionIdFrom(result);
    }

    private void closeCurrentRecurringTransactionHistory(
            UUID recurringTransactionId,
            LocalDate effectiveTo
    ) {
        jdbcTemplate.update("""
                        UPDATE recurring_transaction_history
                        SET effective_to = ?
                        WHERE recurring_transaction_id = ?
                          AND effective_to IS NULL
                        """,
                effectiveTo,
                recurringTransactionId
        );
    }

    private void insertRecurringTransactionHistory(
            UUID recurringTransactionId,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            int dayOfUnit,
            int recurrenceInterval,
            String recurrenceUnit,
            String paymentDateAdjustmentPolicy,
            BigDecimal paymentAmount,
            LocalDate recurringTransactionEndDate,
            BigDecimal finalPaymentAmount
    ) {
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
                            recurring_transaction_end_date,
                            final_payment_amount
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                recurringTransactionId,
                effectiveFrom,
                effectiveTo,
                dayOfUnit,
                recurrenceInterval,
                recurrenceUnit,
                paymentDateAdjustmentPolicy,
                paymentAmount,
                recurringTransactionEndDate,
                finalPaymentAmount
        );
    }

    private void insertRecurringTransactionDetailsHistory(
            UUID recurringTransactionId,
            String description,
            UUID categoryId,
            UUID financialPriorityId,
            AccountRef linkedAccount,
            LocalDate effectiveFrom,
            UUID userGroupId
    ) {
        jdbcTemplate.update("""
                        INSERT INTO recurring_transaction_details_history (
                            recurring_transaction_id,
                            recurring_transaction_description,
                            category_id,
                            financial_priority_id,
                            linked_account_id,
                            recurring_transaction_affects_account_balance,
                            recurring_transaction_affects_liquidity,
                            recurring_transaction_details_effective_from,
                            user_group_id
                        )
                        VALUES (?, ?, ?, ?, ?, TRUE, TRUE, ?, ?)
                        """,
                recurringTransactionId,
                description,
                categoryId,
                financialPriorityId,
                linkedAccount.accountId(),
                effectiveFrom,
                userGroupId
        );
    }

    private UUID createBaseRecurringTransaction(
            UserRef authenticatedUser,
            AccountRef account,
            UUID categoryId,
            UUID financialPriorityId,
            String description,
            LocalDate firstPaymentDate,
            BigDecimal paymentAmount
    ) throws Exception {
        Map<String, Object> body = minimalRecurringTransactionRequest(
                account,
                categoryId,
                financialPriorityId
        );

        body.put("recurringTransactionDescription", description);
        body.put("recurringTransactionFirstPaymentDate", firstPaymentDate.toString());
        body.put("paymentAmount", paymentAmount);

        return createRecurringTransactionThroughApi(authenticatedUser, body);
    }

    private UUID createSimulatedRecurringTransaction(
            UserRef authenticatedUser,
            AccountRef account,
            UUID categoryId,
            UUID financialPriorityId,
            UUID simulationGroupId,
            String description,
            LocalDate firstPaymentDate,
            BigDecimal paymentAmount
    ) throws Exception {
        Map<String, Object> body = minimalRecurringTransactionRequest(
                account,
                categoryId,
                financialPriorityId
        );

        body.put("recurringTransactionDescription", description);
        body.put("recurringTransactionFirstPaymentDate", firstPaymentDate.toString());
        body.put("paymentAmount", paymentAmount);
        body.put("recurringTransactionIsSimulated", true);
        body.put("simulationGroupId", simulationGroupId);

        return createRecurringTransactionThroughApi(authenticatedUser, body);
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