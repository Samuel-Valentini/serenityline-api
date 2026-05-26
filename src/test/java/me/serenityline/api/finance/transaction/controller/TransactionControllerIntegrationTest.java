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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransactionControllerIntegrationTest extends IntegrationTestSupport {

    private static final String TRANSACTIONS_PATH = "/api/finance/transactions";

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
    void createTransactionShouldRequireAuthentication() throws Exception {
        mockMvc.perform(post(TRANSACTIONS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerShouldCreateMinimalUserEnteredTransactionWithDefaults() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        AccountRef account = createAccount(owner.userGroupId(), "Conto transaction default");
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria transaction default"
        );

        String accessToken = accessTokenFor(owner);

        MvcResult result = mockMvc.perform(post(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validTransactionRequest(account, categoryId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId").isString())
                .andExpect(jsonPath("$.transactionDescription").value("Spesa supermercato"))
                .andExpect(jsonPath("$.transactionAmount").value(-42.5))
                .andExpect(jsonPath("$.transactionAffectsAccountBalance").value(true))
                .andExpect(jsonPath("$.transactionAffectsLiquidity").value(true))
                .andExpect(jsonPath("$.categoryId").value(categoryId.toString()))
                .andExpect(jsonPath("$.transactionChargeDate").value("2026-06-10"))
                .andExpect(jsonPath("$.transactionIsConfirmed").value(false))
                .andExpect(jsonPath("$.accountId").value(account.accountId().toString()))
                .andExpect(jsonPath("$.creditCardId").doesNotExist())
                .andExpect(jsonPath("$.bucketId").doesNotExist())
                .andExpect(jsonPath("$.transactionIsSimulated").value(false))
                .andExpect(jsonPath("$.simulationGroupId").doesNotExist())
                .andExpect(jsonPath("$.transactionIsUserEntered").value(true))
                .andExpect(jsonPath("$.recurringTransactionId").doesNotExist())
                .andExpect(jsonPath("$.transactionReminderEnabled").value(true))
                .andExpect(jsonPath("$.transactionReminderDaysBefore").value(7))
                .andExpect(jsonPath("$.transactionCreatedAt").exists())
                .andExpect(jsonPath("$.transactionUpdatedAt").exists())
                .andReturn();

        UUID transactionId = transactionIdFrom(result);
        Map<String, Object> row = findTransaction(transactionId);

        assertThat(row.get("transaction_description")).isEqualTo("Spesa supermercato");
        assertThat((BigDecimal) row.get("transaction_amount"))
                .isEqualByComparingTo(new BigDecimal("-42.50"));
        assertThat(row.get("transaction_affects_account_balance")).isEqualTo(true);
        assertThat(row.get("transaction_affects_liquidity")).isEqualTo(true);
        assertThat(row.get("category_id")).isEqualTo(categoryId);
        assertThat(((java.sql.Date) row.get("transaction_charge_date")).toLocalDate()).isEqualTo(LocalDate.of(2026, 6, 10));
        assertThat(row.get("transaction_is_confirmed")).isEqualTo(false);
        assertThat(row.get("account_id")).isEqualTo(account.accountId());
        assertThat(row.get("credit_card_id")).isNull();
        assertThat(row.get("bucket_id")).isNull();
        assertThat(row.get("transaction_is_simulated")).isEqualTo(false);
        assertThat(row.get("simulation_group_id")).isNull();
        assertThat(row.get("transaction_is_user_entered")).isEqualTo(true);
        assertThat(row.get("recurring_transaction_id")).isNull();
        assertThat(row.get("transaction_reminder_enabled")).isEqualTo(true);
        assertThat(((Number) row.get("transaction_reminder_days_before")).intValue()).isEqualTo(7);
        assertThat(row.get("transaction_created_at")).isNotNull();
        assertThat(row.get("transaction_updated_at")).isNotNull();
        assertThat(row.get("user_group_id")).isEqualTo(owner.userGroupId());

        assertThat(countTransactionUsers(
                transactionId,
                owner.userId(),
                owner.userGroupId()
        )).isEqualTo(1L);
    }

    @Test
    void ownerShouldCreateTransactionWithExplicitOptionalValues() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        AccountRef account = createAccount(owner.userGroupId(), "Conto transaction explicit");
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria transaction explicit"
        );

        Map<String, Object> body = validTransactionRequest(account, categoryId);
        body.put("transactionDescription", "Entrata extra");
        body.put("transactionAmount", new BigDecimal("150.00"));
        body.put("transactionAffectsAccountBalance", true);
        body.put("transactionAffectsLiquidity", false);
        body.put("transactionIsConfirmed", true);
        body.put("transactionReminderEnabled", false);
        body.put("transactionReminderDaysBefore", 0);

        String accessToken = accessTokenFor(owner);

        MvcResult result = mockMvc.perform(post(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionDescription").value("Entrata extra"))
                .andExpect(jsonPath("$.transactionAmount").value(150.0))
                .andExpect(jsonPath("$.transactionAffectsAccountBalance").value(true))
                .andExpect(jsonPath("$.transactionAffectsLiquidity").value(false))
                .andExpect(jsonPath("$.transactionIsConfirmed").value(true))
                .andExpect(jsonPath("$.transactionReminderEnabled").value(false))
                .andExpect(jsonPath("$.transactionReminderDaysBefore").value(0))
                .andReturn();

        UUID transactionId = transactionIdFrom(result);
        Map<String, Object> row = findTransaction(transactionId);

        assertThat(row.get("transaction_is_confirmed")).isEqualTo(true);
        assertThat(row.get("transaction_reminder_enabled")).isEqualTo(false);
        assertThat(((Number) row.get("transaction_reminder_days_before")).intValue()).isEqualTo(0);
    }

    @Test
    void ownerShouldCreateLiquidityOnlyTransaction() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        AccountRef account = createAccount(owner.userGroupId(), "Conto transaction liquidity only");
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria transaction liquidity only"
        );

        Map<String, Object> body = validTransactionRequest(account, categoryId);
        body.put("transactionAffectsAccountBalance", false);
        body.put("transactionAffectsLiquidity", true);

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(post(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionAffectsAccountBalance").value(false))
                .andExpect(jsonPath("$.transactionAffectsLiquidity").value(true));
    }

    @Test
    void createTransactionShouldRejectBlankDescription() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        AccountRef account = createAccount(owner.userGroupId(), "Conto blank description");
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria blank description"
        );

        Map<String, Object> body = validTransactionRequest(account, categoryId);
        body.put("transactionDescription", "   ");

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(post(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));

        assertThat(countTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void createTransactionShouldRejectZeroAmount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        AccountRef account = createAccount(owner.userGroupId(), "Conto zero amount");
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria zero amount"
        );

        Map<String, Object> body = validTransactionRequest(account, categoryId);
        body.put("transactionAmount", new BigDecimal("0.00"));

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(post(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.transaction.amountNotZero"));

        assertThat(countTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void createTransactionShouldRejectAmountWithMoreThanTwoDecimals() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        AccountRef account = createAccount(owner.userGroupId(), "Conto invalid amount");
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria invalid amount"
        );

        Map<String, Object> body = validTransactionRequest(account, categoryId);
        body.put("transactionAmount", new BigDecimal("-10.123"));

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(post(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));

        assertThat(countTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void createTransactionShouldRejectFalseFalseAffectsFlags() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        AccountRef account = createAccount(owner.userGroupId(), "Conto false false");
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria false false"
        );

        Map<String, Object> body = validTransactionRequest(account, categoryId);
        body.put("transactionAffectsAccountBalance", false);
        body.put("transactionAffectsLiquidity", false);

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(post(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.transaction.affectsSomethingRequired"));

        assertThat(countTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void createTransactionShouldRejectReminderDaysGreaterThan366() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        AccountRef account = createAccount(owner.userGroupId(), "Conto reminder invalid");
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria reminder invalid"
        );

        Map<String, Object> body = validTransactionRequest(account, categoryId);
        body.put("transactionReminderDaysBefore", 367);

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(post(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));

        assertThat(countTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void createTransactionShouldRejectInactiveCategory() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        AccountRef account = createAccount(owner.userGroupId(), "Conto inactive category");
        UUID inactiveCategoryId = createInactiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria inattiva transaction"
        );

        Map<String, Object> body = validTransactionRequest(account, inactiveCategoryId);

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(post(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.category.notFound"));

        assertThat(countTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void createTransactionShouldRejectCategoryFromAnotherGroup() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef otherOwner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto category other group");
        UUID otherCategoryId = createActiveCategory(
                otherOwner.userGroupId(),
                otherOwner.userId(),
                "Categoria altro gruppo transaction"
        );

        Map<String, Object> body = validTransactionRequest(account, otherCategoryId);

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(post(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.category.notFound"));

        assertThat(countTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void superCollaboratorShouldCreateTransactionOnAnyGroupAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef superCollaborator = createUser(owner.userGroupId(), "SUPER_COLLABORATOR");

        AccountRef account = createAccount(owner.userGroupId(), "Conto super transaction");
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria super transaction"
        );

        String accessToken = accessTokenFor(superCollaborator);

        mockMvc.perform(post(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validTransactionRequest(account, categoryId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(account.accountId().toString()));
    }

    @Test
    void ownerShouldReceiveNotFoundWhenCreatingTransactionOnAccountFromAnotherGroup() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef otherOwner = createUserWithNewGroup("OWNER");

        AccountRef otherAccount = createAccount(
                otherOwner.userGroupId(),
                "Conto altro gruppo transaction"
        );
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria account altro gruppo"
        );

        Map<String, Object> body = validTransactionRequest(otherAccount, categoryId);

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(post(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.account.notFound"));

        assertThat(countTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void viewerCollaboratorShouldCreateTransactionOnOperableAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef viewer = createUser(owner.userGroupId(), "VIEWER_COLLABORATOR");

        AccountRef account = createAccount(owner.userGroupId(), "Conto viewer transaction");
        grantAccountAccess(account, viewer);

        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria viewer transaction"
        );

        String accessToken = accessTokenFor(viewer);

        mockMvc.perform(post(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validTransactionRequest(account, categoryId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(account.accountId().toString()));
    }

    @Test
    void viewerCollaboratorShouldReceiveForbiddenOnNonOperableAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef viewer = createUser(owner.userGroupId(), "VIEWER_COLLABORATOR");

        AccountRef account = createAccount(owner.userGroupId(), "Conto viewer forbidden transaction");
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria viewer forbidden"
        );

        String accessToken = accessTokenFor(viewer);

        mockMvc.perform(post(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validTransactionRequest(account, categoryId))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("finance.account.operationNotAllowed"));

        assertThat(countTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void collaboratorShouldCreateTransactionOnAccessibleAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef account = createAccount(owner.userGroupId(), "Conto collaborator transaction");
        grantAccountAccess(account, collaborator);

        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria collaborator transaction"
        );

        String accessToken = accessTokenFor(collaborator);

        mockMvc.perform(post(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validTransactionRequest(account, categoryId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(account.accountId().toString()));
    }

    @Test
    void collaboratorShouldReceiveNotFoundOnHiddenAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef hiddenAccount = createAccount(
                owner.userGroupId(),
                "Conto collaborator hidden transaction"
        );
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria collaborator hidden"
        );

        String accessToken = accessTokenFor(collaborator);

        mockMvc.perform(post(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validTransactionRequest(hiddenAccount, categoryId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.account.notFound"));

        assertThat(countTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void ownerShouldCreateTransactionWithCreditCardLinkedToSameAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto card ok");
        CreditCardRef creditCard = createCreditCard(
                owner.userGroupId(),
                account,
                "Carta ok transaction"
        );
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria card ok"
        );

        Map<String, Object> body = validTransactionRequest(account, categoryId);
        body.put("creditCardId", creditCard.creditCardId());

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(post(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creditCardId").value(creditCard.creditCardId().toString()));
    }

    @Test
    void ownerShouldRejectCreditCardLinkedToDifferentAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef selectedAccount = createAccount(owner.userGroupId(), "Conto selected card mismatch");
        AccountRef cardAccount = createAccount(owner.userGroupId(), "Conto card mismatch");
        CreditCardRef creditCard = createCreditCard(
                owner.userGroupId(),
                cardAccount,
                "Carta mismatch transaction"
        );
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria card mismatch"
        );

        Map<String, Object> body = validTransactionRequest(selectedAccount, categoryId);
        body.put("creditCardId", creditCard.creditCardId());

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(post(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.creditCard.notFound"));

        assertThat(countTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void ownerShouldCreateTransactionWithOpenBucketLinkedToAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto bucket ok");
        BucketRef bucket = createOpenBucket(owner.userGroupId(), "Bucket ok transaction");
        linkBucketToAccount(bucket, account, owner.userGroupId());

        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria bucket ok"
        );

        Map<String, Object> body = validTransactionRequest(account, categoryId);
        body.put("bucketId", bucket.bucketId());

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(post(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bucketId").value(bucket.bucketId().toString()));
    }

    @Test
    void ownerShouldRejectBucketNotLinkedToAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto bucket unlinked");
        BucketRef bucket = createOpenBucket(owner.userGroupId(), "Bucket unlinked transaction");
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria bucket unlinked"
        );

        Map<String, Object> body = validTransactionRequest(account, categoryId);
        body.put("bucketId", bucket.bucketId());

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(post(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"));

        assertThat(countTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void ownerShouldRejectClosedBucket() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto closed bucket");
        BucketRef bucket = createClosedBucket(owner.userGroupId(), "Bucket closed transaction");
        linkBucketToAccount(bucket, account, owner.userGroupId());

        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria closed bucket"
        );

        Map<String, Object> body = validTransactionRequest(account, categoryId);
        body.put("bucketId", bucket.bucketId());

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(post(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"));

        assertThat(countTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void createTransactionShouldRejectSimulationGroupWhenTransactionIsNotSimulated() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto simulation not allowed");
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria simulation not allowed"
        );
        UUID simulationGroupId = createSimulationGroup(
                owner.userGroupId(),
                "Simulation not allowed"
        );

        Map<String, Object> body = validTransactionRequest(account, categoryId);
        body.put("transactionIsSimulated", false);
        body.put("simulationGroupId", simulationGroupId);

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(post(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.transaction.simulationGroupNotAllowed"));

        assertThat(countTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void createTransactionShouldRequireSimulationGroupWhenTransactionIsSimulated() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto simulation required");
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria simulation required"
        );

        Map<String, Object> body = validTransactionRequest(account, categoryId);
        body.put("transactionIsSimulated", true);

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(post(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.transaction.simulationGroupRequired"));

        assertThat(countTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void ownerShouldCreateSimulatedTransactionWhenAccountIsLinkedToActiveSimulationGroup() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto simulation ok");
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria simulation ok"
        );

        UUID simulationGroupId = createSimulationGroup(
                owner.userGroupId(),
                "Simulation ok transaction"
        );
        linkSimulationGroupToAccount(simulationGroupId, account);

        Map<String, Object> body = validTransactionRequest(account, categoryId);
        body.put("transactionIsSimulated", true);
        body.put("simulationGroupId", simulationGroupId);

        String accessToken = accessTokenFor(owner);

        MvcResult result = mockMvc.perform(post(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionIsSimulated").value(true))
                .andExpect(jsonPath("$.simulationGroupId").value(simulationGroupId.toString()))
                .andReturn();

        UUID transactionId = transactionIdFrom(result);
        Map<String, Object> row = findTransaction(transactionId);

        assertThat(row.get("transaction_is_simulated")).isEqualTo(true);
        assertThat(row.get("simulation_group_id")).isEqualTo(simulationGroupId);
    }

    @Test
    void ownerShouldRejectSimulatedTransactionWhenAccountIsNotLinkedToSimulationGroup() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto simulation not linked");
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria simulation not linked"
        );

        UUID simulationGroupId = createSimulationGroup(
                owner.userGroupId(),
                "Simulation account not linked"
        );

        Map<String, Object> body = validTransactionRequest(account, categoryId);
        body.put("transactionIsSimulated", true);
        body.put("simulationGroupId", simulationGroupId);

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(post(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.simulationGroup.notFound"));

        assertThat(countTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void ownerShouldRejectSimulatedTransactionWithArchivedSimulationGroup() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto archived simulation");
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria archived simulation"
        );

        UUID simulationGroupId = createArchivedSimulationGroup(
                owner.userGroupId(),
                "Simulation archived transaction"
        );
        linkSimulationGroupToAccount(simulationGroupId, account);

        Map<String, Object> body = validTransactionRequest(account, categoryId);
        body.put("transactionIsSimulated", true);
        body.put("simulationGroupId", simulationGroupId);

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(post(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.simulationGroup.notFound"));

        assertThat(countTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void collaboratorShouldNotCreateSimulatedTransactionOnHiddenSimulationGroup() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef accessibleAccount = createAccount(
                owner.userGroupId(),
                "Conto collaborator simulation accessible"
        );
        AccountRef hiddenAccount = createAccount(
                owner.userGroupId(),
                "Conto collaborator simulation hidden"
        );

        grantAccountAccess(accessibleAccount, collaborator);

        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria collaborator simulation hidden"
        );

        UUID simulationGroupId = createSimulationGroup(
                owner.userGroupId(),
                "Simulation hidden collaborator transaction"
        );
        linkSimulationGroupToAccount(simulationGroupId, hiddenAccount);

        Map<String, Object> body = validTransactionRequest(accessibleAccount, categoryId);
        body.put("transactionIsSimulated", true);
        body.put("simulationGroupId", simulationGroupId);

        String accessToken = accessTokenFor(collaborator);

        mockMvc.perform(post(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.simulationGroup.notFound"));

        assertThat(countTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void createTransactionShouldRejectMissingAmount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        AccountRef account = createAccount(owner.userGroupId(), "Conto missing amount");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria missing amount");

        Map<String, Object> body = validTransactionRequest(account, categoryId);
        body.remove("transactionAmount");

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(post(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));

        assertThat(countTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void createTransactionShouldRejectMissingAccountId() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        AccountRef account = createAccount(owner.userGroupId(), "Conto missing account");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria missing account");

        Map<String, Object> body = validTransactionRequest(account, categoryId);
        body.remove("accountId");

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(post(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));

        assertThat(countTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void createTransactionShouldRejectNegativeReminderDays() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        AccountRef account = createAccount(owner.userGroupId(), "Conto reminder negative");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria reminder negative");

        Map<String, Object> body = validTransactionRequest(account, categoryId);
        body.put("transactionReminderDaysBefore", -1);

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(post(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));

        assertThat(countTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void collaboratorShouldCreateSimulatedTransactionOnAccessibleLinkedSimulationGroup() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef account = createAccount(owner.userGroupId(), "Conto collaborator simulation ok");
        grantAccountAccess(account, collaborator);

        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria collaborator simulation ok"
        );

        UUID simulationGroupId = createSimulationGroup(
                owner.userGroupId(),
                "Simulation collaborator ok"
        );
        linkSimulationGroupToAccount(simulationGroupId, account);

        Map<String, Object> body = validTransactionRequest(account, categoryId);
        body.put("transactionIsSimulated", true);
        body.put("simulationGroupId", simulationGroupId);

        String accessToken = accessTokenFor(collaborator);

        mockMvc.perform(post(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionIsSimulated").value(true))
                .andExpect(jsonPath("$.simulationGroupId").value(simulationGroupId.toString()))
                .andExpect(jsonPath("$.accountId").value(account.accountId().toString()));
    }

    @Test
    void ownerShouldRejectSimulatedTransactionWithSimulationGroupFromAnotherGroup() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef otherOwner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto simulation other group");
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria simulation other group"
        );

        UUID otherSimulationGroupId = createSimulationGroup(
                otherOwner.userGroupId(),
                "Simulation other group"
        );

        Map<String, Object> body = validTransactionRequest(account, categoryId);
        body.put("transactionIsSimulated", true);
        body.put("simulationGroupId", otherSimulationGroupId);

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(post(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.simulationGroup.notFound"));

        assertThat(countTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void getTransactionShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get(TRANSACTIONS_PATH + "/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerShouldReadOwnGroupTransactionById() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        AccountRef account = createAccount(owner.userGroupId(), "Conto read owner");
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria read owner"
        );

        UUID transactionId = createTransactionViaApi(
                owner,
                validTransactionRequest(account, categoryId)
        );

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(transactionId.toString()))
                .andExpect(jsonPath("$.transactionDescription").value("Spesa supermercato"))
                .andExpect(jsonPath("$.transactionAmount").value(-42.5))
                .andExpect(jsonPath("$.transactionAffectsAccountBalance").value(true))
                .andExpect(jsonPath("$.transactionAffectsLiquidity").value(true))
                .andExpect(jsonPath("$.categoryId").value(categoryId.toString()))
                .andExpect(jsonPath("$.transactionChargeDate").value("2026-06-10"))
                .andExpect(jsonPath("$.transactionIsConfirmed").value(false))
                .andExpect(jsonPath("$.accountId").value(account.accountId().toString()))
                .andExpect(jsonPath("$.creditCardId").doesNotExist())
                .andExpect(jsonPath("$.bucketId").doesNotExist())
                .andExpect(jsonPath("$.transactionIsSimulated").value(false))
                .andExpect(jsonPath("$.simulationGroupId").doesNotExist())
                .andExpect(jsonPath("$.transactionIsUserEntered").value(true))
                .andExpect(jsonPath("$.recurringTransactionId").doesNotExist())
                .andExpect(jsonPath("$.transactionReminderEnabled").value(true))
                .andExpect(jsonPath("$.transactionReminderDaysBefore").value(7))
                .andExpect(jsonPath("$.transactionCreatedAt").exists())
                .andExpect(jsonPath("$.transactionUpdatedAt").exists());
    }

    @Test
    void ownerShouldReadTransactionWithOptionalReferencesById() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto read optional references");
        CreditCardRef creditCard = createCreditCard(
                owner.userGroupId(),
                account,
                "Carta read optional references"
        );

        BucketRef bucket = createOpenBucket(
                owner.userGroupId(),
                "Bucket read optional references"
        );
        linkBucketToAccount(bucket, account, owner.userGroupId());

        UUID simulationGroupId = createSimulationGroup(
                owner.userGroupId(),
                "Simulation read optional references"
        );
        linkSimulationGroupToAccount(simulationGroupId, account);

        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria read optional references"
        );

        Map<String, Object> body = validTransactionRequest(account, categoryId);
        body.put("creditCardId", creditCard.creditCardId());
        body.put("bucketId", bucket.bucketId());
        body.put("transactionIsSimulated", true);
        body.put("simulationGroupId", simulationGroupId);
        body.put("transactionIsConfirmed", true);
        body.put("transactionReminderEnabled", false);
        body.put("transactionReminderDaysBefore", 3);

        UUID transactionId = createTransactionViaApi(owner, body);

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(transactionId.toString()))
                .andExpect(jsonPath("$.transactionDescription").value("Spesa supermercato"))
                .andExpect(jsonPath("$.transactionAmount").value(-42.5))
                .andExpect(jsonPath("$.categoryId").value(categoryId.toString()))
                .andExpect(jsonPath("$.accountId").value(account.accountId().toString()))
                .andExpect(jsonPath("$.creditCardId").value(creditCard.creditCardId().toString()))
                .andExpect(jsonPath("$.bucketId").value(bucket.bucketId().toString()))
                .andExpect(jsonPath("$.transactionIsSimulated").value(true))
                .andExpect(jsonPath("$.simulationGroupId").value(simulationGroupId.toString()))
                .andExpect(jsonPath("$.transactionIsConfirmed").value(true))
                .andExpect(jsonPath("$.transactionIsUserEntered").value(true))
                .andExpect(jsonPath("$.recurringTransactionId").doesNotExist())
                .andExpect(jsonPath("$.transactionReminderEnabled").value(false))
                .andExpect(jsonPath("$.transactionReminderDaysBefore").value(3))
                .andExpect(jsonPath("$.transactionCreatedAt").exists())
                .andExpect(jsonPath("$.transactionUpdatedAt").exists());
    }

    @Test
    void superCollaboratorShouldReadGroupTransactionById() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef superCollaborator = createUser(owner.userGroupId(), "SUPER_COLLABORATOR");

        AccountRef account = createAccount(owner.userGroupId(), "Conto read super");
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria read super"
        );

        UUID transactionId = createTransactionViaApi(
                owner,
                validTransactionRequest(account, categoryId)
        );

        String accessToken = accessTokenFor(superCollaborator);

        mockMvc.perform(get(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(transactionId.toString()))
                .andExpect(jsonPath("$.accountId").value(account.accountId().toString()))
                .andExpect(jsonPath("$.categoryId").value(categoryId.toString()));
    }

    @Test
    void viewerCollaboratorShouldReadGroupTransactionByIdEvenWithoutAccountAccess() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef viewer = createUser(owner.userGroupId(), "VIEWER_COLLABORATOR");

        AccountRef account = createAccount(owner.userGroupId(), "Conto read viewer");
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria read viewer"
        );

        UUID transactionId = createTransactionViaApi(
                owner,
                validTransactionRequest(account, categoryId)
        );

        String accessToken = accessTokenFor(viewer);

        mockMvc.perform(get(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(transactionId.toString()))
                .andExpect(jsonPath("$.accountId").value(account.accountId().toString()))
                .andExpect(jsonPath("$.categoryId").value(categoryId.toString()));
    }

    @Test
    void collaboratorShouldReadLinkedAccountTransactionById() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef account = createAccount(owner.userGroupId(), "Conto read collaborator linked");
        grantAccountAccess(account, collaborator);

        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria read collaborator linked"
        );

        UUID transactionId = createTransactionViaApi(
                owner,
                validTransactionRequest(account, categoryId)
        );

        String accessToken = accessTokenFor(collaborator);

        mockMvc.perform(get(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(transactionId.toString()))
                .andExpect(jsonPath("$.accountId").value(account.accountId().toString()))
                .andExpect(jsonPath("$.categoryId").value(categoryId.toString()));
    }

    @Test
    void collaboratorShouldReceiveNotFoundWhenReadingUnlinkedAccountTransactionById() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef accessibleAccount = createAccount(
                owner.userGroupId(),
                "Conto read collaborator accessible"
        );
        grantAccountAccess(accessibleAccount, collaborator);

        AccountRef hiddenAccount = createAccount(
                owner.userGroupId(),
                "Conto read collaborator hidden"
        );

        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria read collaborator hidden"
        );

        UUID transactionId = createTransactionViaApi(
                owner,
                validTransactionRequest(hiddenAccount, categoryId)
        );

        String accessToken = accessTokenFor(collaborator);

        mockMvc.perform(get(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.transaction.notFound"));
    }

    @Test
    void ownerShouldReceiveNotFoundWhenReadingTransactionFromAnotherGroup() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef otherOwner = createUserWithNewGroup("OWNER");

        AccountRef otherAccount = createAccount(
                otherOwner.userGroupId(),
                "Conto read other group"
        );
        UUID otherCategoryId = createActiveCategory(
                otherOwner.userGroupId(),
                otherOwner.userId(),
                "Categoria read other group"
        );

        UUID otherTransactionId = createTransactionViaApi(
                otherOwner,
                validTransactionRequest(otherAccount, otherCategoryId)
        );

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(TRANSACTIONS_PATH + "/" + otherTransactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.transaction.notFound"));
    }

    @Test
    void viewerCollaboratorShouldReceiveNotFoundWhenReadingTransactionFromAnotherGroup() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef viewer = createUser(owner.userGroupId(), "VIEWER_COLLABORATOR");
        UserRef otherOwner = createUserWithNewGroup("OWNER");

        AccountRef otherAccount = createAccount(
                otherOwner.userGroupId(),
                "Conto read viewer other group"
        );
        UUID otherCategoryId = createActiveCategory(
                otherOwner.userGroupId(),
                otherOwner.userId(),
                "Categoria read viewer other group"
        );

        UUID otherTransactionId = createTransactionViaApi(
                otherOwner,
                validTransactionRequest(otherAccount, otherCategoryId)
        );

        String accessToken = accessTokenFor(viewer);

        mockMvc.perform(get(TRANSACTIONS_PATH + "/" + otherTransactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.transaction.notFound"));
    }

    @Test
    void collaboratorShouldReceiveNotFoundWhenReadingTransactionFromAnotherGroup() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");
        UserRef otherOwner = createUserWithNewGroup("OWNER");

        AccountRef otherAccount = createAccount(
                otherOwner.userGroupId(),
                "Conto read collaborator other group"
        );
        UUID otherCategoryId = createActiveCategory(
                otherOwner.userGroupId(),
                otherOwner.userId(),
                "Categoria read collaborator other group"
        );

        UUID otherTransactionId = createTransactionViaApi(
                otherOwner,
                validTransactionRequest(otherAccount, otherCategoryId)
        );

        String accessToken = accessTokenFor(collaborator);

        mockMvc.perform(get(TRANSACTIONS_PATH + "/" + otherTransactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.transaction.notFound"));
    }

    @Test
    void getTransactionShouldReturnNotFoundWhenTransactionDoesNotExist() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(TRANSACTIONS_PATH + "/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.transaction.notFound"));
    }

    @Test
    void getTransactionShouldReturnBadRequestWhenTransactionIdIsInvalid() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(TRANSACTIONS_PATH + "/not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void collaboratorShouldNotReadTransactionOnlyBecauseLinkedInTransactionsUsers() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef hiddenAccount = createAccount(
                owner.userGroupId(),
                "Conto read collaborator transaction-user-only hidden"
        );

        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria read collaborator transaction-user-only"
        );

        UUID transactionId = createTransactionViaApi(
                owner,
                validTransactionRequest(hiddenAccount, categoryId)
        );

        linkTransactionToUser(
                transactionId,
                collaborator.userId(),
                collaborator.userGroupId()
        );

        String accessToken = accessTokenFor(collaborator);

        mockMvc.perform(get(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.transaction.notFound"));
    }

    @Test
    void ownerShouldReadTransactionCreatedByCollaboratorById() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef account = createAccount(
                owner.userGroupId(),
                "Conto read owner reads collaborator transaction"
        );
        grantAccountAccess(account, collaborator);

        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria read owner reads collaborator transaction"
        );

        UUID transactionId = createTransactionViaApi(
                collaborator,
                validTransactionRequest(account, categoryId)
        );

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(transactionId.toString()))
                .andExpect(jsonPath("$.accountId").value(account.accountId().toString()))
                .andExpect(jsonPath("$.categoryId").value(categoryId.toString()));
    }

    @Test
    void ownerShouldReadTransactionAfterCategoryWasDeactivated() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        AccountRef account = createAccount(
                owner.userGroupId(),
                "Conto read after category deactivated"
        );

        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria read after deactivation"
        );

        UUID transactionId = createTransactionViaApi(
                owner,
                validTransactionRequest(account, categoryId)
        );

        deactivateCategory(categoryId);

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(transactionId.toString()))
                .andExpect(jsonPath("$.categoryId").value(categoryId.toString()));
    }

    @Test
    void ownerShouldReadConfirmedRecurringOccurrenceById() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(
                owner.userGroupId(),
                "Conto read confirmed recurring occurrence"
        );

        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria read confirmed recurring occurrence"
        );

        UUID recurringTransactionId = createRecurringTransaction(
                owner.userGroupId(),
                LocalDate.of(2026, 7, 15)
        );

        UUID transactionId = createConfirmedRecurringOccurrence(
                owner.userGroupId(),
                account,
                categoryId,
                recurringTransactionId
        );

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(transactionId.toString()))
                .andExpect(jsonPath("$.transactionDescription").value("Occorrenza ricorrente confermata"))
                .andExpect(jsonPath("$.transactionAmount").value(-99.9))
                .andExpect(jsonPath("$.categoryId").value(categoryId.toString()))
                .andExpect(jsonPath("$.transactionChargeDate").value("2026-07-15"))
                .andExpect(jsonPath("$.transactionIsConfirmed").value(true))
                .andExpect(jsonPath("$.accountId").value(account.accountId().toString()))
                .andExpect(jsonPath("$.transactionIsUserEntered").value(false))
                .andExpect(jsonPath("$.recurringTransactionId").value(recurringTransactionId.toString()))
                .andExpect(jsonPath("$.transactionReminderEnabled").value(true))
                .andExpect(jsonPath("$.transactionReminderDaysBefore").value(7));
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
                unique("Transaction test group")
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
                "Transaction Test User",
                uniqueEmail("transaction-owner"),
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
                "Transaction Test User",
                uniqueEmail("transaction-user"),
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

    private Map<String, Object> validTransactionRequest(
            AccountRef account,
            UUID categoryId
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("transactionDescription", "  Spesa supermercato  ");
        body.put("transactionAmount", new BigDecimal("-42.50"));
        body.put("categoryId", categoryId);
        body.put("transactionChargeDate", LocalDate.of(2026, 6, 10).toString());
        body.put("accountId", account.accountId());
        return body;
    }

    private String json(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private UUID transactionIdFrom(MvcResult result) throws Exception {
        String response = result.getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.transactionId"));
    }

    private Map<String, Object> findTransaction(UUID transactionId) {
        return jdbcTemplate.queryForMap("""
                        SELECT *
                        FROM transactions
                        WHERE transaction_id = ?
                        """,
                transactionId
        );
    }

    private long countTransactionUsers(
            UUID transactionId,
            UUID userId,
            UUID userGroupId
    ) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT count(*)
                        FROM transactions_users
                        WHERE transaction_id = ?
                          AND user_id = ?
                          AND user_group_id = ?
                        """,
                Long.class,
                transactionId,
                userId,
                userGroupId
        );

        return count == null ? 0L : count;
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

    private String accessTokenFor(UserRef userRef) {
        User user = userRepository.findById(userRef.userId())
                .orElseThrow();

        return jwtTokenService.createAccessToken(user)
                .token();
    }

    private UUID createTransactionViaApi(
            UserRef actor,
            Map<String, Object> body
    ) throws Exception {
        MvcResult result = mockMvc.perform(post(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(actor)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();

        return transactionIdFrom(result);
    }

    private void linkTransactionToUser(
            UUID transactionId,
            UUID userId,
            UUID userGroupId
    ) {
        jdbcTemplate.update("""
                        INSERT INTO transactions_users (
                            transaction_id,
                            user_id,
                            user_group_id
                        )
                        VALUES (?, ?, ?)
                        ON CONFLICT (transaction_id, user_id) DO NOTHING
                        """,
                transactionId,
                userId,
                userGroupId
        );
    }

    private void deactivateCategory(UUID categoryId) {
        jdbcTemplate.update("""
                        INSERT INTO category_status_history (
                            category_id,
                            category_is_active,
                            category_status_updated_at
                        )
                        VALUES (?, FALSE, now() + interval '1 second')
                        """,
                categoryId
        );
    }

    private UUID createRecurringTransaction(
            UUID userGroupId,
            LocalDate firstPaymentDate
    ) {
        UUID recurringTransactionId = UUID.randomUUID();

        jdbcTemplate.update("""
                        INSERT INTO recurring_transactions (
                            recurring_transaction_id,
                            recurring_transaction_first_payment_date,
                            user_group_id
                        )
                        VALUES (?, ?, ?)
                        """,
                recurringTransactionId,
                firstPaymentDate,
                userGroupId
        );

        return recurringTransactionId;
    }

    private UUID createConfirmedRecurringOccurrence(
            UUID userGroupId,
            AccountRef account,
            UUID categoryId,
            UUID recurringTransactionId
    ) {
        UUID transactionId = UUID.randomUUID();

        jdbcTemplate.update("""
                        INSERT INTO transactions (
                            transaction_id,
                            transaction_description,
                            transaction_amount,
                            transaction_affects_account_balance,
                            transaction_affects_liquidity,
                            category_id,
                            transaction_charge_date,
                            transaction_is_confirmed,
                            account_id,
                            transaction_is_simulated,
                            transaction_is_user_entered,
                            recurring_transaction_id,
                            transaction_reminder_enabled,
                            transaction_reminder_days_before,
                            user_group_id
                        )
                        VALUES (?, ?, ?, TRUE, TRUE, ?, ?, TRUE, ?, FALSE, FALSE, ?, TRUE, 7, ?)
                        """,
                transactionId,
                "Occorrenza ricorrente confermata",
                new BigDecimal("-99.90"),
                categoryId,
                LocalDate.of(2026, 7, 15),
                account.accountId(),
                recurringTransactionId,
                userGroupId
        );

        return transactionId;
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