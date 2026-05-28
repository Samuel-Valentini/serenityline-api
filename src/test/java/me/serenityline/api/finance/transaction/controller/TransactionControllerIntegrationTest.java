package me.serenityline.api.finance.transaction.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import me.serenityline.api.security.jwt.JwtTokenService;
import me.serenityline.api.support.IntegrationTestSupport;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
        assertThat(row.get("recurring_transaction_logical_date")).isNull();
        assertThat(row.get("recurring_transaction_confirmed_at")).isNull();

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
                .andExpect(jsonPath("$.recurringTransactionLogicalDate").doesNotExist())
                .andExpect(jsonPath("$.recurringTransactionConfirmedAt").doesNotExist())
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
                .andExpect(jsonPath("$.recurringTransactionLogicalDate").doesNotExist())
                .andExpect(jsonPath("$.recurringTransactionConfirmedAt").doesNotExist())
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
                .andExpect(jsonPath("$.recurringTransactionLogicalDate").value("2026-07-15"))
                .andExpect(jsonPath("$.recurringTransactionConfirmedAt").exists())
                .andExpect(jsonPath("$.transactionReminderEnabled").value(true))
                .andExpect(jsonPath("$.transactionReminderDaysBefore").value(7));
    }

    @Test
    void listTransactionsShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get(TRANSACTIONS_PATH)
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listTransactionsShouldRequireFromDate() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        mockMvc.perform(get(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .param("to", "2026-06-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.transaction.fromRequired"));
    }

    @Test
    void listTransactionsShouldRequireToDate() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        mockMvc.perform(get(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .param("from", "2026-06-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.transaction.toRequired"));
    }

    @Test
    void listTransactionsShouldRejectInvalidDateRange() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        mockMvc.perform(get(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .param("from", "2026-07-01")
                        .param("to", "2026-06-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.transaction.invalidDateRange"));
    }

    @Test
    void listTransactionsShouldRejectDateRangeGreaterThanFiveYears() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        mockMvc.perform(get(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .param("from", "2026-01-01")
                        .param("to", "2032-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.transaction.dateRangeTooLarge"));
    }

    @Test
    void ownerShouldListOwnGroupTransactionsInDateRangeOrderedByChargeDate() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef otherOwner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto list owner range");
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria list owner range"
        );

        AccountRef otherAccount = createAccount(otherOwner.userGroupId(), "Conto list other range");
        UUID otherCategoryId = createActiveCategory(
                otherOwner.userGroupId(),
                otherOwner.userId(),
                "Categoria list other range"
        );

        createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Fuori range prima", "-10.00", LocalDate.of(2026, 5, 31))
        );

        UUID toDateTransactionId = createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Movimento finale", "-30.00", LocalDate.of(2026, 6, 30))
        );

        UUID middleTransactionId = createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Movimento centrale", "-20.00", LocalDate.of(2026, 6, 15))
        );

        UUID fromDateTransactionId = createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Movimento iniziale", "-15.00", LocalDate.of(2026, 6, 1))
        );

        createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Fuori range dopo", "-40.00", LocalDate.of(2026, 7, 1))
        );

        createTransactionViaApi(
                otherOwner,
                transactionRequest(otherAccount, otherCategoryId, "Altro gruppo", "-99.00", LocalDate.of(2026, 6, 15))
        );

        mockMvc.perform(get(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].transactionId").value(fromDateTransactionId.toString()))
                .andExpect(jsonPath("$[0].transactionChargeDate").value("2026-06-01"))
                .andExpect(jsonPath("$[1].transactionId").value(middleTransactionId.toString()))
                .andExpect(jsonPath("$[1].transactionChargeDate").value("2026-06-15"))
                .andExpect(jsonPath("$[2].transactionId").value(toDateTransactionId.toString()))
                .andExpect(jsonPath("$[2].transactionChargeDate").value("2026-06-30"));
    }

    @Test
    void ownerShouldListTransactionsFilteredByAccountId() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef firstAccount = createAccount(owner.userGroupId(), "Conto list account first");
        AccountRef secondAccount = createAccount(owner.userGroupId(), "Conto list account second");

        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria list account"
        );

        UUID firstTransactionId = createTransactionViaApi(
                owner,
                transactionRequest(firstAccount, categoryId, "Primo conto uno", "-10.00", LocalDate.of(2026, 6, 10))
        );

        createTransactionViaApi(
                owner,
                transactionRequest(secondAccount, categoryId, "Secondo conto", "-20.00", LocalDate.of(2026, 6, 11))
        );

        UUID secondTransactionId = createTransactionViaApi(
                owner,
                transactionRequest(firstAccount, categoryId, "Primo conto due", "-30.00", LocalDate.of(2026, 6, 12))
        );

        mockMvc.perform(get(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30")
                        .param("accountId", firstAccount.accountId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].transactionId").value(firstTransactionId.toString()))
                .andExpect(jsonPath("$[0].accountId").value(firstAccount.accountId().toString()))
                .andExpect(jsonPath("$[1].transactionId").value(secondTransactionId.toString()))
                .andExpect(jsonPath("$[1].accountId").value(firstAccount.accountId().toString()));
    }

    @Test
    void ownerShouldReceiveEmptyListWhenFilteringByAccountFromAnotherGroup() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef otherOwner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto list account own");
        AccountRef otherAccount = createAccount(otherOwner.userGroupId(), "Conto list account other");

        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria list account other group"
        );

        createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Movimento proprio", "-10.00", LocalDate.of(2026, 6, 10))
        );

        mockMvc.perform(get(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30")
                        .param("accountId", otherAccount.accountId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void ownerShouldListTransactionsFilteredBySimulationGroupId() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto list simulation");

        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria list simulation"
        );

        UUID firstSimulationGroupId = createSimulationGroup(
                owner.userGroupId(),
                "Simulation list first"
        );
        linkSimulationGroupToAccount(firstSimulationGroupId, account);

        UUID secondSimulationGroupId = createSimulationGroup(
                owner.userGroupId(),
                "Simulation list second"
        );
        linkSimulationGroupToAccount(secondSimulationGroupId, account);

        UUID firstSimulationTransactionId = createTransactionViaApi(
                owner,
                simulatedTransactionRequest(
                        account,
                        categoryId,
                        firstSimulationGroupId,
                        "Simulata uno",
                        "-10.00",
                        LocalDate.of(2026, 6, 10)
                )
        );

        createTransactionViaApi(
                owner,
                simulatedTransactionRequest(
                        account,
                        categoryId,
                        secondSimulationGroupId,
                        "Simulata due",
                        "-20.00",
                        LocalDate.of(2026, 6, 11)
                )
        );

        createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Non simulata", "-30.00", LocalDate.of(2026, 6, 12))
        );

        mockMvc.perform(get(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30")
                        .param("simulationGroupId", firstSimulationGroupId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].transactionId").value(firstSimulationTransactionId.toString()))
                .andExpect(jsonPath("$[0].transactionIsSimulated").value(true))
                .andExpect(jsonPath("$[0].simulationGroupId").value(firstSimulationGroupId.toString()));
    }

    @Test
    void ownerShouldReceiveEmptyListWhenFilteringBySimulationGroupFromAnotherGroup() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef otherOwner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto list simulation own");

        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria list simulation other group"
        );

        UUID otherSimulationGroupId = createSimulationGroup(
                otherOwner.userGroupId(),
                "Simulation list other group"
        );

        createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Movimento proprio", "-10.00", LocalDate.of(2026, 6, 10))
        );

        mockMvc.perform(get(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30")
                        .param("simulationGroupId", otherSimulationGroupId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void superCollaboratorShouldListGroupTransactions() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef superCollaborator = createUser(owner.userGroupId(), "SUPER_COLLABORATOR");

        AccountRef account = createAccount(owner.userGroupId(), "Conto list super");
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria list super"
        );

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Movimento super", "-10.00", LocalDate.of(2026, 6, 10))
        );

        mockMvc.perform(get(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(superCollaborator)))
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].transactionId").value(transactionId.toString()));
    }

    @Test
    void viewerCollaboratorShouldListGroupTransactionsEvenWithoutAccountAccess() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef viewer = createUser(owner.userGroupId(), "VIEWER_COLLABORATOR");

        AccountRef account = createAccount(owner.userGroupId(), "Conto list viewer");
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria list viewer"
        );

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Movimento viewer", "-10.00", LocalDate.of(2026, 6, 10))
        );

        mockMvc.perform(get(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(viewer)))
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].transactionId").value(transactionId.toString()));
    }

    @Test
    void collaboratorShouldListOnlyTransactionsOnLinkedAccounts() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef linkedAccount = createAccount(owner.userGroupId(), "Conto list collaborator linked");
        AccountRef hiddenAccount = createAccount(owner.userGroupId(), "Conto list collaborator hidden");

        grantAccountAccess(linkedAccount, collaborator);

        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria list collaborator"
        );

        UUID linkedTransactionId = createTransactionViaApi(
                owner,
                transactionRequest(linkedAccount, categoryId, "Movimento linked", "-10.00", LocalDate.of(2026, 6, 10))
        );

        createTransactionViaApi(
                owner,
                transactionRequest(hiddenAccount, categoryId, "Movimento hidden", "-20.00", LocalDate.of(2026, 6, 11))
        );

        mockMvc.perform(get(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator)))
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].transactionId").value(linkedTransactionId.toString()))
                .andExpect(jsonPath("$[0].accountId").value(linkedAccount.accountId().toString()));
    }

    @Test
    void collaboratorShouldReceiveEmptyListWhenFilteringByUnlinkedAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef linkedAccount = createAccount(owner.userGroupId(), "Conto list collaborator linked filter");
        AccountRef hiddenAccount = createAccount(owner.userGroupId(), "Conto list collaborator hidden filter");

        grantAccountAccess(linkedAccount, collaborator);

        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria list collaborator hidden filter"
        );

        createTransactionViaApi(
                owner,
                transactionRequest(linkedAccount, categoryId, "Movimento linked", "-10.00", LocalDate.of(2026, 6, 10))
        );

        createTransactionViaApi(
                owner,
                transactionRequest(hiddenAccount, categoryId, "Movimento hidden", "-20.00", LocalDate.of(2026, 6, 11))
        );

        mockMvc.perform(get(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator)))
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30")
                        .param("accountId", hiddenAccount.accountId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listTransactionsShouldIncludeConfirmedRecurringOccurrences() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto list recurring occurrence");
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria list recurring occurrence"
        );

        UUID recurringTransactionId = createRecurringTransaction(
                owner.userGroupId(),
                LocalDate.of(2026, 6, 1)
        );

        UUID transactionId = createConfirmedRecurringOccurrence(
                owner.userGroupId(),
                account,
                categoryId,
                recurringTransactionId,
                LocalDate.of(2026, 6, 15)
        );

        mockMvc.perform(get(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].transactionId").value(transactionId.toString()))
                .andExpect(jsonPath("$[0].transactionIsUserEntered").value(false))
                .andExpect(jsonPath("$[0].transactionIsConfirmed").value(true))
                .andExpect(jsonPath("$[0].recurringTransactionId").value(recurringTransactionId.toString()));
    }

    @Test
    void listTransactionsShouldReturnBadRequestWhenAccountIdIsInvalid() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        mockMvc.perform(get(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30")
                        .param("accountId", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ownerShouldListTransactionsFilteredByAccountIdAndSimulationGroupId() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef firstAccount = createAccount(owner.userGroupId(), "Conto combined filter first");
        AccountRef secondAccount = createAccount(owner.userGroupId(), "Conto combined filter second");

        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria combined filter"
        );

        UUID simulationGroupId = createSimulationGroup(
                owner.userGroupId(),
                "Simulation combined filter"
        );
        linkSimulationGroupToAccount(simulationGroupId, firstAccount);
        linkSimulationGroupToAccount(simulationGroupId, secondAccount);

        UUID expectedTransactionId = createTransactionViaApi(
                owner,
                simulatedTransactionRequest(
                        firstAccount,
                        categoryId,
                        simulationGroupId,
                        "Transazione conto e simulazione corretta",
                        "-10.00",
                        LocalDate.of(2026, 6, 10)
                )
        );

        createTransactionViaApi(
                owner,
                simulatedTransactionRequest(
                        secondAccount,
                        categoryId,
                        simulationGroupId,
                        "Transazione stessa simulazione altro conto",
                        "-20.00",
                        LocalDate.of(2026, 6, 11)
                )
        );

        createTransactionViaApi(
                owner,
                transactionRequest(
                        firstAccount,
                        categoryId,
                        "Transazione stesso conto non simulata",
                        "-30.00",
                        LocalDate.of(2026, 6, 12)
                )
        );

        mockMvc.perform(get(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30")
                        .param("accountId", firstAccount.accountId().toString())
                        .param("simulationGroupId", simulationGroupId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].transactionId").value(expectedTransactionId.toString()))
                .andExpect(jsonPath("$[0].accountId").value(firstAccount.accountId().toString()))
                .andExpect(jsonPath("$[0].simulationGroupId").value(simulationGroupId.toString()));
    }

    @Test
    void collaboratorShouldListOnlyLinkedAccountTransactionsWhenFilteringBySimulationGroupId() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef linkedAccount = createAccount(
                owner.userGroupId(),
                "Conto collaborator simulation linked"
        );
        AccountRef hiddenAccount = createAccount(
                owner.userGroupId(),
                "Conto collaborator simulation hidden"
        );

        grantAccountAccess(linkedAccount, collaborator);

        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria collaborator simulation filter"
        );

        UUID simulationGroupId = createSimulationGroup(
                owner.userGroupId(),
                "Simulation collaborator filter"
        );
        linkSimulationGroupToAccount(simulationGroupId, linkedAccount);
        linkSimulationGroupToAccount(simulationGroupId, hiddenAccount);

        UUID visibleTransactionId = createTransactionViaApi(
                owner,
                simulatedTransactionRequest(
                        linkedAccount,
                        categoryId,
                        simulationGroupId,
                        "Simulata visibile",
                        "-10.00",
                        LocalDate.of(2026, 6, 10)
                )
        );

        createTransactionViaApi(
                owner,
                simulatedTransactionRequest(
                        hiddenAccount,
                        categoryId,
                        simulationGroupId,
                        "Simulata nascosta",
                        "-20.00",
                        LocalDate.of(2026, 6, 11)
                )
        );

        mockMvc.perform(get(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator)))
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30")
                        .param("simulationGroupId", simulationGroupId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].transactionId").value(visibleTransactionId.toString()))
                .andExpect(jsonPath("$[0].accountId").value(linkedAccount.accountId().toString()));
    }

    @Test
    void listTransactionsShouldAllowDateRangeUpToFiveYears() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto five years range");
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria five years range"
        );

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(
                        account,
                        categoryId,
                        "Movimento nel range cinque anni",
                        "-10.00",
                        LocalDate.of(2030, 12, 31)
                )
        );

        mockMvc.perform(get(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .param("from", "2026-01-01")
                        .param("to", "2030-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].transactionId").value(transactionId.toString()));
    }

    @Test
    void listTransactionsShouldReturnBadRequestWhenSimulationGroupIdIsInvalid() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        mockMvc.perform(get(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30")
                        .param("simulationGroupId", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ownerShouldUpdateTransaction() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef oldAccount = createAccount(owner.userGroupId(), "Conto update old");
        AccountRef newAccount = createAccount(owner.userGroupId(), "Conto update new");

        UUID oldCategoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria update old");
        UUID newCategoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria update new");

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(oldAccount, oldCategoryId, "Descrizione vecchia", "-42.50", LocalDate.of(2026, 6, 10))
        );

        Map<String, Object> body = validTransactionUpdateRequest(
                newAccount,
                newCategoryId,
                "Descrizione aggiornata",
                "123.45",
                LocalDate.of(2026, 7, 20)
        );
        body.put("transactionAffectsAccountBalance", true);
        body.put("transactionAffectsLiquidity", false);
        body.put("transactionIsConfirmed", true);
        body.put("transactionReminderEnabled", false);
        body.put("transactionReminderDaysBefore", 2);

        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(transactionId.toString()))
                .andExpect(jsonPath("$.transactionDescription").value("Descrizione aggiornata"))
                .andExpect(jsonPath("$.transactionAmount").value(123.45))
                .andExpect(jsonPath("$.transactionAffectsAccountBalance").value(true))
                .andExpect(jsonPath("$.transactionAffectsLiquidity").value(false))
                .andExpect(jsonPath("$.categoryId").value(newCategoryId.toString()))
                .andExpect(jsonPath("$.transactionChargeDate").value("2026-07-20"))
                .andExpect(jsonPath("$.transactionIsConfirmed").value(true))
                .andExpect(jsonPath("$.accountId").value(newAccount.accountId().toString()))
                .andExpect(jsonPath("$.creditCardId").doesNotExist())
                .andExpect(jsonPath("$.bucketId").doesNotExist())
                .andExpect(jsonPath("$.transactionIsSimulated").value(false))
                .andExpect(jsonPath("$.simulationGroupId").doesNotExist())
                .andExpect(jsonPath("$.transactionIsUserEntered").value(true))
                .andExpect(jsonPath("$.recurringTransactionId").doesNotExist())
                .andExpect(jsonPath("$.recurringTransactionLogicalDate").doesNotExist())
                .andExpect(jsonPath("$.recurringTransactionConfirmedAt").doesNotExist())
                .andExpect(jsonPath("$.transactionReminderEnabled").value(false))
                .andExpect(jsonPath("$.transactionReminderDaysBefore").value(2))
                .andExpect(jsonPath("$.transactionCreatedAt").exists())
                .andExpect(jsonPath("$.transactionUpdatedAt").exists());

        Map<String, Object> row = findTransaction(transactionId);

        assertThat(row.get("transaction_description")).isEqualTo("Descrizione aggiornata");
        assertThat((BigDecimal) row.get("transaction_amount")).isEqualByComparingTo(new BigDecimal("123.45"));
        assertThat(row.get("transaction_affects_account_balance")).isEqualTo(true);
        assertThat(row.get("transaction_affects_liquidity")).isEqualTo(false);
        assertThat(row.get("category_id")).isEqualTo(newCategoryId);
        assertThat(((java.sql.Date) row.get("transaction_charge_date")).toLocalDate()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(row.get("transaction_is_confirmed")).isEqualTo(true);
        assertThat(row.get("account_id")).isEqualTo(newAccount.accountId());
        assertThat(row.get("transaction_is_user_entered")).isEqualTo(true);
        assertThat(row.get("recurring_transaction_id")).isNull();
        assertThat(row.get("user_group_id")).isEqualTo(owner.userGroupId());
    }

    @Test
    void ownerShouldUpdateConfirmedRecurringOccurrence() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto update recurring");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria update recurring");

        UUID recurringTransactionId = createRecurringTransaction(
                owner.userGroupId(),
                LocalDate.of(2026, 6, 1)
        );

        UUID transactionId = createConfirmedRecurringOccurrence(
                owner.userGroupId(),
                account,
                categoryId,
                recurringTransactionId,
                LocalDate.of(2026, 6, 15)
        );

        Map<String, Object> before = findTransaction(transactionId);

        Map<String, Object> body = validTransactionUpdateRequest(
                account,
                categoryId,
                "Importo ricorrente effettivo",
                "-83.47",
                LocalDate.of(2026, 6, 16)
        );
        body.put("transactionIsConfirmed", true);

        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(transactionId.toString()))
                .andExpect(jsonPath("$.transactionDescription").value("Importo ricorrente effettivo"))
                .andExpect(jsonPath("$.transactionAmount").value(-83.47))
                .andExpect(jsonPath("$.transactionChargeDate").value("2026-06-16"))
                .andExpect(jsonPath("$.transactionIsConfirmed").value(true))
                .andExpect(jsonPath("$.transactionIsUserEntered").value(false))
                .andExpect(jsonPath("$.recurringTransactionId").value(recurringTransactionId.toString()))
                .andExpect(jsonPath("$.recurringTransactionLogicalDate").value("2026-06-15"))
                .andExpect(jsonPath("$.recurringTransactionConfirmedAt").exists());

        Map<String, Object> after = findTransaction(transactionId);

        assertThat(after.get("transaction_is_user_entered")).isEqualTo(false);
        assertThat(after.get("recurring_transaction_id")).isEqualTo(recurringTransactionId);
        assertThat(after.get("transaction_is_confirmed")).isEqualTo(true);

        assertThat(((java.sql.Date) after.get("transaction_charge_date")).toLocalDate())
                .isEqualTo(LocalDate.of(2026, 6, 16));

        assertThat(after.get("recurring_transaction_logical_date"))
                .isEqualTo(before.get("recurring_transaction_logical_date"));

        assertThat(after.get("recurring_transaction_confirmed_at"))
                .isEqualTo(before.get("recurring_transaction_confirmed_at"));
    }

    @Test
    void updateTransactionShouldRejectUnconfirmingRecurringOccurrence() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto update recurring unconfirm");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria update recurring unconfirm");

        UUID recurringTransactionId = createRecurringTransaction(
                owner.userGroupId(),
                LocalDate.of(2026, 6, 1)
        );

        UUID transactionId = createConfirmedRecurringOccurrence(
                owner.userGroupId(),
                account,
                categoryId,
                recurringTransactionId,
                LocalDate.of(2026, 6, 15)
        );

        Map<String, Object> body = validTransactionUpdateRequest(
                account,
                categoryId,
                "Tentativo non confermata",
                "-83.47",
                LocalDate.of(2026, 6, 16)
        );
        body.put("transactionIsConfirmed", false);

        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.transaction.recurringTransactionMustBeConfirmed"));

        Map<String, Object> row = findTransaction(transactionId);

        assertThat(row.get("transaction_description")).isEqualTo("Occorrenza ricorrente confermata");
        assertThat(row.get("transaction_is_confirmed")).isEqualTo(true);
        assertThat(row.get("recurring_transaction_id")).isEqualTo(recurringTransactionId);
    }

    @Test
    void superCollaboratorShouldUpdateGroupTransaction() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef superCollaborator = createUser(owner.userGroupId(), "SUPER_COLLABORATOR");

        AccountRef account = createAccount(owner.userGroupId(), "Conto update super");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria update super");

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Vecchia super", "-10.00", LocalDate.of(2026, 6, 10))
        );

        Map<String, Object> body = validTransactionUpdateRequest(
                account,
                categoryId,
                "Aggiornata super",
                "-20.00",
                LocalDate.of(2026, 6, 11)
        );

        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(superCollaborator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionDescription").value("Aggiornata super"))
                .andExpect(jsonPath("$.transactionAmount").value(-20.0));
    }

    @Test
    void viewerCollaboratorShouldUpdateTransactionOnOperableAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef viewer = createUser(owner.userGroupId(), "VIEWER_COLLABORATOR");

        AccountRef account = createAccount(owner.userGroupId(), "Conto update viewer");
        grantAccountAccess(account, viewer);

        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria update viewer");

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Vecchia viewer", "-10.00", LocalDate.of(2026, 6, 10))
        );

        Map<String, Object> body = validTransactionUpdateRequest(
                account,
                categoryId,
                "Aggiornata viewer",
                "-20.00",
                LocalDate.of(2026, 6, 11)
        );

        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(viewer)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionDescription").value("Aggiornata viewer"));
    }

    @Test
    void viewerCollaboratorShouldReceiveForbiddenWhenUpdatingTransactionOnNonOperableAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef viewer = createUser(owner.userGroupId(), "VIEWER_COLLABORATOR");

        AccountRef hiddenAccount = createAccount(owner.userGroupId(), "Conto update viewer hidden");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria update viewer hidden");

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(hiddenAccount, categoryId, "Vecchia viewer hidden", "-10.00", LocalDate.of(2026, 6, 10))
        );

        Map<String, Object> body = validTransactionUpdateRequest(
                hiddenAccount,
                categoryId,
                "Aggiornata viewer hidden",
                "-20.00",
                LocalDate.of(2026, 6, 11)
        );

        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(viewer)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("finance.account.operationNotAllowed"));
    }

    @Test
    void collaboratorShouldUpdateTransactionOnLinkedAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef account = createAccount(owner.userGroupId(), "Conto update collaborator");
        grantAccountAccess(account, collaborator);

        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria update collaborator");

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Vecchia collaborator", "-10.00", LocalDate.of(2026, 6, 10))
        );

        Map<String, Object> body = validTransactionUpdateRequest(
                account,
                categoryId,
                "Aggiornata collaborator",
                "-20.00",
                LocalDate.of(2026, 6, 11)
        );

        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionDescription").value("Aggiornata collaborator"));
    }

    @Test
    void collaboratorShouldReceiveNotFoundWhenUpdatingTransactionOnHiddenAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef hiddenAccount = createAccount(owner.userGroupId(), "Conto update collaborator hidden");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria update collaborator hidden");

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(hiddenAccount, categoryId, "Vecchia hidden", "-10.00", LocalDate.of(2026, 6, 10))
        );

        Map<String, Object> body = validTransactionUpdateRequest(
                hiddenAccount,
                categoryId,
                "Aggiornata hidden",
                "-20.00",
                LocalDate.of(2026, 6, 11)
        );

        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.transaction.notFound"));
    }

    @Test
    void ownerShouldReceiveNotFoundWhenUpdatingTransactionFromAnotherGroup() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef otherOwner = createUserWithNewGroup("OWNER");

        AccountRef otherAccount = createAccount(otherOwner.userGroupId(), "Conto update other group");
        UUID otherCategoryId = createActiveCategory(otherOwner.userGroupId(), otherOwner.userId(), "Categoria update other group");

        UUID otherTransactionId = createTransactionViaApi(
                otherOwner,
                transactionRequest(otherAccount, otherCategoryId, "Altro gruppo", "-10.00", LocalDate.of(2026, 6, 10))
        );

        AccountRef account = createAccount(owner.userGroupId(), "Conto update own body");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria update own body");

        Map<String, Object> body = validTransactionUpdateRequest(
                account,
                categoryId,
                "Tentativo cross group",
                "-20.00",
                LocalDate.of(2026, 6, 11)
        );

        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + otherTransactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.transaction.notFound"));
    }

    @Test
    void collaboratorShouldUpdateTransactionMovingItToAnotherLinkedAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef oldAccount = createAccount(owner.userGroupId(), "Conto update collaborator old linked");
        AccountRef newAccount = createAccount(owner.userGroupId(), "Conto update collaborator new linked");

        grantAccountAccess(oldAccount, collaborator);
        grantAccountAccess(newAccount, collaborator);

        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria update collaborator move");

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(oldAccount, categoryId, "Vecchio conto", "-10.00", LocalDate.of(2026, 6, 10))
        );

        Map<String, Object> body = validTransactionUpdateRequest(
                newAccount,
                categoryId,
                "Nuovo conto",
                "-20.00",
                LocalDate.of(2026, 6, 11)
        );

        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(newAccount.accountId().toString()));

        assertThat(findTransaction(transactionId).get("account_id")).isEqualTo(newAccount.accountId());
    }

    @Test
    void viewerCollaboratorShouldReceiveForbiddenWhenMovingTransactionToNonOperableAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef viewer = createUser(owner.userGroupId(), "VIEWER_COLLABORATOR");

        AccountRef oldAccount = createAccount(owner.userGroupId(), "Conto update viewer old linked");
        AccountRef newHiddenAccount = createAccount(owner.userGroupId(), "Conto update viewer new hidden");

        grantAccountAccess(oldAccount, viewer);

        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria update viewer move forbidden");

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(oldAccount, categoryId, "Vecchio conto viewer", "-10.00", LocalDate.of(2026, 6, 10))
        );

        Map<String, Object> body = validTransactionUpdateRequest(
                newHiddenAccount,
                categoryId,
                "Nuovo conto viewer hidden",
                "-20.00",
                LocalDate.of(2026, 6, 11)
        );

        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(viewer)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("finance.account.operationNotAllowed"));
    }

    @Test
    void collaboratorShouldReceiveNotFoundWhenMovingTransactionToHiddenAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef oldAccount = createAccount(owner.userGroupId(), "Conto update collaborator old");
        AccountRef hiddenAccount = createAccount(owner.userGroupId(), "Conto update collaborator new hidden");

        grantAccountAccess(oldAccount, collaborator);

        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria update collaborator move hidden");

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(oldAccount, categoryId, "Vecchio conto collaborator", "-10.00", LocalDate.of(2026, 6, 10))
        );

        Map<String, Object> body = validTransactionUpdateRequest(
                hiddenAccount,
                categoryId,
                "Nuovo conto collaborator hidden",
                "-20.00",
                LocalDate.of(2026, 6, 11)
        );

        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.account.notFound"));
    }

    @Test
    void ownerShouldAddAndRemoveCreditCardOnTransactionUpdate() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto update card");
        CreditCardRef creditCard = createCreditCard(owner.userGroupId(), account, "Carta update card");

        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria update card");

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Senza carta", "-10.00", LocalDate.of(2026, 6, 10))
        );

        Map<String, Object> addBody = validTransactionUpdateRequest(
                account,
                categoryId,
                "Con carta",
                "-20.00",
                LocalDate.of(2026, 6, 11)
        );
        addBody.put("creditCardId", creditCard.creditCardId());

        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(addBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditCardId").value(creditCard.creditCardId().toString()));

        Map<String, Object> removeBody = validTransactionUpdateRequest(
                account,
                categoryId,
                "Di nuovo senza carta",
                "-30.00",
                LocalDate.of(2026, 6, 12)
        );
        removeBody.put("creditCardId", null);

        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(removeBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditCardId").doesNotExist());

        assertThat(findTransaction(transactionId).get("credit_card_id")).isNull();
    }

    @Test
    void ownerShouldRejectCreditCardLinkedToDifferentAccountOnUpdate() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef selectedAccount = createAccount(owner.userGroupId(), "Conto update card selected");
        AccountRef cardAccount = createAccount(owner.userGroupId(), "Conto update card other");

        CreditCardRef creditCard = createCreditCard(owner.userGroupId(), cardAccount, "Carta update card mismatch");

        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria update card mismatch");

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(selectedAccount, categoryId, "Card mismatch old", "-10.00", LocalDate.of(2026, 6, 10))
        );

        Map<String, Object> body = validTransactionUpdateRequest(
                selectedAccount,
                categoryId,
                "Card mismatch new",
                "-20.00",
                LocalDate.of(2026, 6, 11)
        );
        body.put("creditCardId", creditCard.creditCardId());

        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.creditCard.notFound"));
    }

    @Test
    void ownerShouldAddAndRemoveBucketOnTransactionUpdate() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto update bucket");
        BucketRef bucket = createOpenBucket(owner.userGroupId(), "Bucket update");
        linkBucketToAccount(bucket, account, owner.userGroupId());

        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria update bucket");

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Senza bucket", "-10.00", LocalDate.of(2026, 6, 10))
        );

        Map<String, Object> addBody = validTransactionUpdateRequest(
                account,
                categoryId,
                "Con bucket",
                "-20.00",
                LocalDate.of(2026, 6, 11)
        );
        addBody.put("bucketId", bucket.bucketId());

        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(addBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketId").value(bucket.bucketId().toString()));

        Map<String, Object> removeBody = validTransactionUpdateRequest(
                account,
                categoryId,
                "Di nuovo senza bucket",
                "-30.00",
                LocalDate.of(2026, 6, 12)
        );
        removeBody.put("bucketId", null);

        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(removeBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketId").doesNotExist());

        assertThat(findTransaction(transactionId).get("bucket_id")).isNull();
    }

    @Test
    void ownerShouldRejectClosedBucketOnUpdate() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto update closed bucket");
        BucketRef closedBucket = createClosedBucket(owner.userGroupId(), "Bucket update closed");
        linkBucketToAccount(closedBucket, account, owner.userGroupId());

        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria update closed bucket");

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Senza bucket chiuso", "-10.00", LocalDate.of(2026, 6, 10))
        );

        Map<String, Object> body = validTransactionUpdateRequest(
                account,
                categoryId,
                "Con bucket chiuso",
                "-20.00",
                LocalDate.of(2026, 6, 11)
        );
        body.put("bucketId", closedBucket.bucketId());

        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.bucket.notFound"));
    }

    @Test
    void ownerShouldRejectChangingTransactionToInactiveCategoryOnUpdate() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto update inactive category");
        UUID activeCategoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria update active");
        UUID inactiveCategoryId = createInactiveCategory(owner.userGroupId(), owner.userId(), "Categoria update inactive");

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(account, activeCategoryId, "Categoria attiva", "-10.00", LocalDate.of(2026, 6, 10))
        );

        Map<String, Object> body = validTransactionUpdateRequest(
                account,
                inactiveCategoryId,
                "Categoria inattiva",
                "-20.00",
                LocalDate.of(2026, 6, 11)
        );

        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.category.notFound"));
    }

    @Test
    void ownerShouldUpdateTransactionKeepingSameCategoryAfterItWasDeactivated() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto update same inactive category");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria update then inactive");

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Prima della disattivazione", "-10.00", LocalDate.of(2026, 6, 10))
        );

        deactivateCategory(categoryId);

        Map<String, Object> body = validTransactionUpdateRequest(
                account,
                categoryId,
                "Dopo disattivazione categoria",
                "-20.00",
                LocalDate.of(2026, 6, 11)
        );

        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(categoryId.toString()))
                .andExpect(jsonPath("$.transactionDescription").value("Dopo disattivazione categoria"));
    }

    @Test
    void ownerShouldTurnRegularTransactionIntoSimulatedTransactionOnUpdate() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto update to simulation");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria update to simulation");

        UUID simulationGroupId = createSimulationGroup(owner.userGroupId(), "Simulation update to simulated");
        linkSimulationGroupToAccount(simulationGroupId, account);

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Non simulata", "-10.00", LocalDate.of(2026, 6, 10))
        );

        Map<String, Object> body = validTransactionUpdateRequest(
                account,
                categoryId,
                "Ora simulata",
                "-20.00",
                LocalDate.of(2026, 6, 11)
        );
        body.put("transactionIsSimulated", true);
        body.put("simulationGroupId", simulationGroupId);

        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionIsSimulated").value(true))
                .andExpect(jsonPath("$.simulationGroupId").value(simulationGroupId.toString()));
    }

    @Test
    void ownerShouldTurnSimulatedTransactionIntoRegularTransactionOnUpdate() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto update from simulation");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria update from simulation");

        UUID simulationGroupId = createSimulationGroup(owner.userGroupId(), "Simulation update from simulated");
        linkSimulationGroupToAccount(simulationGroupId, account);

        UUID transactionId = createTransactionViaApi(
                owner,
                simulatedTransactionRequest(
                        account,
                        categoryId,
                        simulationGroupId,
                        "Simulata",
                        "-10.00",
                        LocalDate.of(2026, 6, 10)
                )
        );

        Map<String, Object> body = validTransactionUpdateRequest(
                account,
                categoryId,
                "Ora non simulata",
                "-20.00",
                LocalDate.of(2026, 6, 11)
        );
        body.put("transactionIsSimulated", false);
        body.put("simulationGroupId", null);

        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionIsSimulated").value(false))
                .andExpect(jsonPath("$.simulationGroupId").doesNotExist());

        assertThat(findTransaction(transactionId).get("simulation_group_id")).isNull();
    }

    @Test
    void updateTransactionShouldRequireSimulationGroupWhenSimulated() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto update simulation required");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria update simulation required");

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Non simulata", "-10.00", LocalDate.of(2026, 6, 10))
        );

        Map<String, Object> body = validTransactionUpdateRequest(
                account,
                categoryId,
                "Simulata senza gruppo",
                "-20.00",
                LocalDate.of(2026, 6, 11)
        );
        body.put("transactionIsSimulated", true);
        body.put("simulationGroupId", null);

        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.transaction.simulationGroupRequired"));
    }

    @Test
    void updateTransactionShouldRejectSimulationGroupWhenNotSimulated() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto update simulation not allowed");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria update simulation not allowed");

        UUID simulationGroupId = createSimulationGroup(owner.userGroupId(), "Simulation update not allowed");
        linkSimulationGroupToAccount(simulationGroupId, account);

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Non simulata", "-10.00", LocalDate.of(2026, 6, 10))
        );

        Map<String, Object> body = validTransactionUpdateRequest(
                account,
                categoryId,
                "Non simulata ma con gruppo",
                "-20.00",
                LocalDate.of(2026, 6, 11)
        );
        body.put("transactionIsSimulated", false);
        body.put("simulationGroupId", simulationGroupId);

        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.transaction.simulationGroupNotAllowed"));
    }

    @Test
    void updateTransactionShouldRequireAuthentication() throws Exception {
        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateTransactionShouldReturnNotFoundWhenTransactionDoesNotExist() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto update missing transaction");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria update missing transaction");

        Map<String, Object> body = validTransactionUpdateRequest(
                account,
                categoryId,
                "Missing transaction",
                "-20.00",
                LocalDate.of(2026, 6, 11)
        );

        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.transaction.notFound"));
    }

    @Test
    void updateTransactionShouldRejectBlankDescription() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto update blank");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria update blank");

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Vecchia descrizione", "-10.00", LocalDate.of(2026, 6, 10))
        );

        Map<String, Object> body = validTransactionUpdateRequest(
                account,
                categoryId,
                "   ",
                "-20.00",
                LocalDate.of(2026, 6, 11)
        );

        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));
    }

    @Test
    void updateTransactionShouldRejectZeroAmount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto update zero amount");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria update zero amount");

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Vecchio importo", "-10.00", LocalDate.of(2026, 6, 10))
        );

        Map<String, Object> body = validTransactionUpdateRequest(
                account,
                categoryId,
                "Importo zero",
                "0.00",
                LocalDate.of(2026, 6, 11)
        );

        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.transaction.amountNotZero"));
    }

    @Test
    void updateTransactionShouldRejectAmountWithMoreThanTwoDecimals() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto update invalid decimals");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria update invalid decimals");

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Vecchio importo", "-10.00", LocalDate.of(2026, 6, 10))
        );

        Map<String, Object> body = validTransactionUpdateRequest(
                account,
                categoryId,
                "Importo con troppi decimali",
                "-20.123",
                LocalDate.of(2026, 6, 11)
        );

        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));
    }

    @Test
    void updateTransactionShouldRejectFalseFalseAffectsFlags() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto update false false");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria update false false");

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Vecchie flag", "-10.00", LocalDate.of(2026, 6, 10))
        );

        Map<String, Object> body = validTransactionUpdateRequest(
                account,
                categoryId,
                "Flag false false",
                "-20.00",
                LocalDate.of(2026, 6, 11)
        );
        body.put("transactionAffectsAccountBalance", false);
        body.put("transactionAffectsLiquidity", false);

        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.transaction.affectsSomethingRequired"));
    }

    @Test
    void updateTransactionShouldRejectNegativeReminderDays() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto update negative reminder");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria update negative reminder");

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Vecchio reminder", "-10.00", LocalDate.of(2026, 6, 10))
        );

        Map<String, Object> body = validTransactionUpdateRequest(
                account,
                categoryId,
                "Reminder negativo",
                "-20.00",
                LocalDate.of(2026, 6, 11)
        );
        body.put("transactionReminderDaysBefore", -1);

        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));
    }

    @Test
    void updateTransactionShouldRejectReminderDaysGreaterThan366() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto update reminder too high");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria update reminder too high");

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Vecchio reminder", "-10.00", LocalDate.of(2026, 6, 10))
        );

        Map<String, Object> body = validTransactionUpdateRequest(
                account,
                categoryId,
                "Reminder troppo alto",
                "-20.00",
                LocalDate.of(2026, 6, 11)
        );
        body.put("transactionReminderDaysBefore", 367);

        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));
    }

    @Test
    void updateTransactionShouldRejectMissingRequiredFullUpdateField() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto update missing field");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria update missing field");

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Vecchia", "-10.00", LocalDate.of(2026, 6, 10))
        );

        Map<String, Object> body = validTransactionUpdateRequest(
                account,
                categoryId,
                "Manca campo",
                "-20.00",
                LocalDate.of(2026, 6, 11)
        );
        body.remove("transactionReminderEnabled");

        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));
    }

    @Test
    void collaboratorShouldNotUpdateHiddenTransactionEvenWhenMovingItToLinkedAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef hiddenAccount = createAccount(owner.userGroupId(), "Conto update hidden source");
        AccountRef linkedAccount = createAccount(owner.userGroupId(), "Conto update linked target");
        grantAccountAccess(linkedAccount, collaborator);

        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria hidden source");

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(hiddenAccount, categoryId, "Transazione nascosta", "-10.00", LocalDate.of(2026, 6, 10))
        );

        Map<String, Object> body = validTransactionUpdateRequest(
                linkedAccount,
                categoryId,
                "Tentativo spostamento",
                "-20.00",
                LocalDate.of(2026, 6, 11)
        );

        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.transaction.notFound"));

        assertThat(findTransaction(transactionId).get("account_id")).isEqualTo(hiddenAccount.accountId());
    }

    @Test
    void viewerCollaboratorShouldNotUpdateNonOperableTransactionEvenWhenMovingItToOperableAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef viewer = createUser(owner.userGroupId(), "VIEWER_COLLABORATOR");

        AccountRef hiddenAccount = createAccount(owner.userGroupId(), "Conto update viewer hidden source");
        AccountRef linkedAccount = createAccount(owner.userGroupId(), "Conto update viewer linked target");
        grantAccountAccess(linkedAccount, viewer);

        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria viewer hidden source");

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(hiddenAccount, categoryId, "Transazione viewer nascosta", "-10.00", LocalDate.of(2026, 6, 10))
        );

        Map<String, Object> body = validTransactionUpdateRequest(
                linkedAccount,
                categoryId,
                "Tentativo viewer spostamento",
                "-20.00",
                LocalDate.of(2026, 6, 11)
        );

        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(viewer)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("finance.account.operationNotAllowed"));

        assertThat(findTransaction(transactionId).get("account_id")).isEqualTo(hiddenAccount.accountId());
    }

    @Test
    void updateTransactionShouldPreserveImmutableFields() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto update immutable");
        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria update immutable");

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Prima", "-10.00", LocalDate.of(2026, 6, 10))
        );

        Map<String, Object> before = findTransaction(transactionId);

        Map<String, Object> body = validTransactionUpdateRequest(
                account,
                categoryId,
                "Dopo",
                "-20.00",
                LocalDate.of(2026, 6, 11)
        );

        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk());

        Map<String, Object> after = findTransaction(transactionId);

        assertThat(after.get("transaction_id")).isEqualTo(before.get("transaction_id"));
        assertThat(after.get("transaction_is_user_entered")).isEqualTo(before.get("transaction_is_user_entered"));
        assertThat(after.get("recurring_transaction_id")).isEqualTo(before.get("recurring_transaction_id"));
        assertThat(after.get("recurring_transaction_logical_date")).isEqualTo(before.get("recurring_transaction_logical_date"));
        assertThat(after.get("recurring_transaction_confirmed_at")).isEqualTo(before.get("recurring_transaction_confirmed_at"));
        assertThat(after.get("user_group_id")).isEqualTo(before.get("user_group_id"));
        assertThat(after.get("transaction_created_at")).isEqualTo(before.get("transaction_created_at"));
        assertThat(after.get("transaction_updated_at")).isNotEqualTo(before.get("transaction_updated_at"));

    }

    @Test
    void ownerShouldReceiveNotFoundWhenUpdatingTransactionToAccountFromAnotherGroup() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef otherOwner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto update own account");
        AccountRef otherAccount = createAccount(otherOwner.userGroupId(), "Conto update other group account");

        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria update own account");

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Prima", "-10.00", LocalDate.of(2026, 6, 10))
        );

        Map<String, Object> body = validTransactionUpdateRequest(
                otherAccount,
                categoryId,
                "Account altro gruppo",
                "-20.00",
                LocalDate.of(2026, 6, 11)
        );

        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.account.notFound"));

        assertThat(findTransaction(transactionId).get("account_id")).isEqualTo(account.accountId());
    }

    @Test
    void ownerShouldReceiveNotFoundWhenUpdatingTransactionToCategoryFromAnotherGroup() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef otherOwner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto update category other group");

        UUID categoryId = createActiveCategory(owner.userGroupId(), owner.userId(), "Categoria update own");
        UUID otherCategoryId = createActiveCategory(otherOwner.userGroupId(), otherOwner.userId(), "Categoria update other");

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Prima", "-10.00", LocalDate.of(2026, 6, 10))
        );

        Map<String, Object> body = validTransactionUpdateRequest(
                account,
                otherCategoryId,
                "Categoria altro gruppo",
                "-20.00",
                LocalDate.of(2026, 6, 11)
        );

        mockMvc.perform(put(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.category.notFound"));

        assertThat(findTransaction(transactionId).get("category_id")).isEqualTo(categoryId);
    }

    @Test
    void deleteTransactionShouldRequireAuthentication() throws Exception {
        mockMvc.perform(delete(TRANSACTIONS_PATH + "/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerShouldDeleteOwnGroupTransaction() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto delete owner");
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria delete owner"
        );

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Da eliminare", "-10.00", LocalDate.of(2026, 6, 10))
        );

        assertThat(countTransactionById(transactionId)).isEqualTo(1L);
        assertThat(countTransactionUsers(
                transactionId,
                owner.userId(),
                owner.userGroupId()
        )).isEqualTo(1L);

        mockMvc.perform(delete(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNoContent())
                .andExpect(jsonPath("$").doesNotExist());

        assertThat(countTransactionById(transactionId)).isZero();
        assertThat(countTransactionUsers(
                transactionId,
                owner.userId(),
                owner.userGroupId()
        )).isZero();
    }

    @Test
    void deletedTransactionShouldNotBeReadableAnymore() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto delete then read");
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria delete then read"
        );

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Da eliminare e leggere", "-10.00", LocalDate.of(2026, 6, 10))
        );

        mockMvc.perform(delete(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.transaction.notFound"));
    }

    @Test
    void ownerShouldDeleteConfirmedRecurringOccurrenceWithoutDeletingRecurringTransaction() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto delete recurring occurrence");
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria delete recurring occurrence"
        );

        UUID recurringTransactionId = createRecurringTransaction(
                owner.userGroupId(),
                LocalDate.of(2026, 6, 1)
        );

        UUID transactionId = createConfirmedRecurringOccurrence(
                owner.userGroupId(),
                account,
                categoryId,
                recurringTransactionId,
                LocalDate.of(2026, 6, 15)
        );

        assertThat(countTransactionById(transactionId)).isEqualTo(1L);
        assertThat(countRecurringTransactionById(recurringTransactionId)).isEqualTo(1L);

        mockMvc.perform(delete(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNoContent());

        assertThat(countTransactionById(transactionId)).isZero();
        assertThat(countRecurringTransactionById(recurringTransactionId)).isEqualTo(1L);
    }

    @Test
    void superCollaboratorShouldDeleteGroupTransaction() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef superCollaborator = createUser(owner.userGroupId(), "SUPER_COLLABORATOR");

        AccountRef account = createAccount(owner.userGroupId(), "Conto delete super");
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria delete super"
        );

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Delete super", "-10.00", LocalDate.of(2026, 6, 10))
        );

        mockMvc.perform(delete(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(superCollaborator))))
                .andExpect(status().isNoContent());

        assertThat(countTransactionById(transactionId)).isZero();
    }

    @Test
    void viewerCollaboratorShouldDeleteTransactionOnOperableAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef viewer = createUser(owner.userGroupId(), "VIEWER_COLLABORATOR");

        AccountRef account = createAccount(owner.userGroupId(), "Conto delete viewer linked");
        grantAccountAccess(account, viewer);

        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria delete viewer linked"
        );

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Delete viewer linked", "-10.00", LocalDate.of(2026, 6, 10))
        );

        mockMvc.perform(delete(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(viewer))))
                .andExpect(status().isNoContent());

        assertThat(countTransactionById(transactionId)).isZero();
    }

    @Test
    void viewerCollaboratorShouldReceiveForbiddenWhenDeletingTransactionOnNonOperableAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef viewer = createUser(owner.userGroupId(), "VIEWER_COLLABORATOR");

        AccountRef hiddenAccount = createAccount(owner.userGroupId(), "Conto delete viewer hidden");
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria delete viewer hidden"
        );

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(hiddenAccount, categoryId, "Delete viewer hidden", "-10.00", LocalDate.of(2026, 6, 10))
        );

        mockMvc.perform(delete(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(viewer))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("finance.account.operationNotAllowed"));

        assertThat(countTransactionById(transactionId)).isEqualTo(1L);
    }

    @Test
    void collaboratorShouldDeleteTransactionOnLinkedAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef account = createAccount(owner.userGroupId(), "Conto delete collaborator linked");
        grantAccountAccess(account, collaborator);

        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria delete collaborator linked"
        );

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Delete collaborator linked", "-10.00", LocalDate.of(2026, 6, 10))
        );

        mockMvc.perform(delete(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isNoContent());

        assertThat(countTransactionById(transactionId)).isZero();
    }

    @Test
    void collaboratorShouldReceiveNotFoundWhenDeletingTransactionOnHiddenAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef hiddenAccount = createAccount(owner.userGroupId(), "Conto delete collaborator hidden");
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria delete collaborator hidden"
        );

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(hiddenAccount, categoryId, "Delete collaborator hidden", "-10.00", LocalDate.of(2026, 6, 10))
        );

        mockMvc.perform(delete(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.transaction.notFound"));

        assertThat(countTransactionById(transactionId)).isEqualTo(1L);
    }

    @Test
    void ownerShouldReceiveNotFoundWhenDeletingTransactionFromAnotherGroup() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef otherOwner = createUserWithNewGroup("OWNER");

        AccountRef otherAccount = createAccount(otherOwner.userGroupId(), "Conto delete other group");
        UUID otherCategoryId = createActiveCategory(
                otherOwner.userGroupId(),
                otherOwner.userId(),
                "Categoria delete other group"
        );

        UUID otherTransactionId = createTransactionViaApi(
                otherOwner,
                transactionRequest(otherAccount, otherCategoryId, "Delete other group", "-10.00", LocalDate.of(2026, 6, 10))
        );

        mockMvc.perform(delete(TRANSACTIONS_PATH + "/" + otherTransactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.transaction.notFound"));

        assertThat(countTransactionById(otherTransactionId)).isEqualTo(1L);
    }

    @Test
    void deleteTransactionShouldReturnNotFoundWhenTransactionDoesNotExist() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        mockMvc.perform(delete(TRANSACTIONS_PATH + "/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.transaction.notFound"));
    }

    @Test
    void deleteTransactionShouldReturnBadRequestWhenTransactionIdIsInvalid() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        mockMvc.perform(delete(TRANSACTIONS_PATH + "/not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteTransactionShouldReturnNotFoundWhenDeletingAlreadyDeletedTransaction() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto delete twice");
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria delete twice"
        );

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Delete twice", "-10.00", LocalDate.of(2026, 6, 10))
        );

        mockMvc.perform(delete(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.transaction.notFound"));
    }

    @Test
    void deletedTransactionShouldNotBeListedAnymore() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto delete list");
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria delete list"
        );

        UUID deletedTransactionId = createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Da eliminare dalla lista", "-10.00", LocalDate.of(2026, 6, 10))
        );

        UUID remainingTransactionId = createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Da mantenere nella lista", "-20.00", LocalDate.of(2026, 6, 11))
        );

        mockMvc.perform(delete(TRANSACTIONS_PATH + "/" + deletedTransactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(TRANSACTIONS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner)))
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].transactionId").value(remainingTransactionId.toString()));
    }

    @Test
    void deleteTransactionShouldCascadeAllTransactionUserLinks() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef account = createAccount(owner.userGroupId(), "Conto delete cascade all links");
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria delete cascade all links"
        );

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(account, categoryId, "Delete cascade links", "-10.00", LocalDate.of(2026, 6, 10))
        );

        linkTransactionToUser(
                transactionId,
                collaborator.userId(),
                collaborator.userGroupId()
        );

        assertThat(countTransactionUserLinksByTransactionId(transactionId)).isEqualTo(2L);

        mockMvc.perform(delete(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNoContent());

        assertThat(countTransactionById(transactionId)).isZero();
        assertThat(countTransactionUserLinksByTransactionId(transactionId)).isZero();
    }

    @Test
    void ownerShouldDeleteSimulatedTransactionWithoutDeletingSimulationGroup() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(owner.userGroupId(), "Conto delete simulated");
        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria delete simulated"
        );

        UUID simulationGroupId = createSimulationGroup(
                owner.userGroupId(),
                "Simulation delete transaction"
        );
        linkSimulationGroupToAccount(simulationGroupId, account);

        UUID transactionId = createTransactionViaApi(
                owner,
                simulatedTransactionRequest(
                        account,
                        categoryId,
                        simulationGroupId,
                        "Simulata da eliminare",
                        "-10.00",
                        LocalDate.of(2026, 6, 10)
                )
        );

        assertThat(countTransactionById(transactionId)).isEqualTo(1L);
        assertThat(countSimulationGroupById(simulationGroupId)).isEqualTo(1L);

        mockMvc.perform(delete(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(owner))))
                .andExpect(status().isNoContent());

        assertThat(countTransactionById(transactionId)).isZero();
        assertThat(countSimulationGroupById(simulationGroupId)).isEqualTo(1L);
    }

    @Test
    void collaboratorShouldNotDeleteTransactionOnlyBecauseLinkedInTransactionsUsers() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UserRef collaborator = createUser(owner.userGroupId(), "COLLABORATOR");

        AccountRef hiddenAccount = createAccount(
                owner.userGroupId(),
                "Conto delete collaborator transaction-user-only hidden"
        );

        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria delete collaborator transaction-user-only"
        );

        UUID transactionId = createTransactionViaApi(
                owner,
                transactionRequest(hiddenAccount, categoryId, "Delete transaction-user-only", "-10.00", LocalDate.of(2026, 6, 10))
        );

        linkTransactionToUser(
                transactionId,
                collaborator.userId(),
                collaborator.userGroupId()
        );

        mockMvc.perform(delete(TRANSACTIONS_PATH + "/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(collaborator))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.transaction.notFound"));

        assertThat(countTransactionById(transactionId)).isEqualTo(1L);
    }

    @Test
    void userEnteredTransactionShouldAcceptNullRecurringLogicalDateAndConfirmedAt() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(
                owner.userGroupId(),
                "Conto user entered recurring metadata null"
        );

        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria user entered recurring metadata null"
        );

        UUID transactionId = createTransactionViaApi(
                owner,
                validTransactionRequest(account, categoryId)
        );

        Map<String, Object> row = findTransaction(transactionId);

        assertThat(row.get("transaction_is_user_entered")).isEqualTo(true);
        assertThat(row.get("recurring_transaction_id")).isNull();
        assertThat(row.get("recurring_transaction_logical_date")).isNull();
        assertThat(row.get("recurring_transaction_confirmed_at")).isNull();
    }

    @Test
    void userEnteredTransactionShouldRejectRecurringLogicalDate() {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(
                owner.userGroupId(),
                "Conto user entered rejects logical date"
        );

        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria user entered rejects logical date"
        );

        assertThatThrownBy(() -> insertRawUserEnteredTransactionWithRecurringMetadata(
                owner.userGroupId(),
                account,
                categoryId,
                LocalDate.of(2026, 6, 10),
                null
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(countTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void userEnteredTransactionShouldRejectRecurringConfirmedAt() {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(
                owner.userGroupId(),
                "Conto user entered rejects confirmed at"
        );

        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria user entered rejects confirmed at"
        );

        assertThatThrownBy(() -> insertRawUserEnteredTransactionWithRecurringMetadata(
                owner.userGroupId(),
                account,
                categoryId,
                null,
                OffsetDateTime.parse("2026-06-10T09:00:00Z")
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(countTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void confirmedRecurringOccurrenceShouldRequireRecurringLogicalDate() {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(
                owner.userGroupId(),
                "Conto recurring requires logical date"
        );

        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria recurring requires logical date"
        );

        UUID recurringTransactionId = createRecurringTransaction(
                owner.userGroupId(),
                LocalDate.of(2026, 6, 1)
        );

        assertThatThrownBy(() -> insertRawConfirmedRecurringOccurrence(
                owner.userGroupId(),
                account,
                categoryId,
                recurringTransactionId,
                LocalDate.of(2026, 6, 10),
                null,
                OffsetDateTime.parse("2026-06-10T09:00:00Z")
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(countTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void confirmedRecurringOccurrenceShouldRequireRecurringConfirmedAt() {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(
                owner.userGroupId(),
                "Conto recurring requires confirmed at"
        );

        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria recurring requires confirmed at"
        );

        UUID recurringTransactionId = createRecurringTransaction(
                owner.userGroupId(),
                LocalDate.of(2026, 6, 1)
        );

        assertThatThrownBy(() -> insertRawConfirmedRecurringOccurrence(
                owner.userGroupId(),
                account,
                categoryId,
                recurringTransactionId,
                LocalDate.of(2026, 6, 10),
                LocalDate.of(2026, 6, 10),
                null
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(countTransactionsForUserGroup(owner.userGroupId())).isZero();
    }

    @Test
    void confirmedRecurringOccurrenceShouldAllowChargeDateDifferentFromLogicalDate() {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(
                owner.userGroupId(),
                "Conto recurring moved charge date"
        );

        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria recurring moved charge date"
        );

        UUID recurringTransactionId = createRecurringTransaction(
                owner.userGroupId(),
                LocalDate.of(2026, 6, 1)
        );

        LocalDate logicalDate = LocalDate.of(2026, 6, 10);
        LocalDate chargeDate = LocalDate.of(2026, 6, 12);
        OffsetDateTime confirmedAt = OffsetDateTime.parse("2026-06-10T09:00:00Z");

        UUID transactionId = insertRawConfirmedRecurringOccurrence(
                owner.userGroupId(),
                account,
                categoryId,
                recurringTransactionId,
                chargeDate,
                logicalDate,
                confirmedAt
        );

        Map<String, Object> row = findTransaction(transactionId);

        assertThat(row.get("transaction_is_user_entered")).isEqualTo(false);
        assertThat(row.get("transaction_is_confirmed")).isEqualTo(true);
        assertThat(row.get("recurring_transaction_id")).isEqualTo(recurringTransactionId);
        assertThat(((java.sql.Date) row.get("transaction_charge_date")).toLocalDate())
                .isEqualTo(chargeDate);
        assertThat(((java.sql.Date) row.get("recurring_transaction_logical_date")).toLocalDate())
                .isEqualTo(logicalDate);
        assertThat(row.get("recurring_transaction_confirmed_at")).isNotNull();
    }

    @Test
    void confirmedRecurringOccurrenceShouldRejectDuplicateLogicalOccurrenceEvenWithDifferentChargeDate() {
        UserRef owner = createUserWithNewGroup("OWNER");

        AccountRef account = createAccount(
                owner.userGroupId(),
                "Conto duplicate logical occurrence"
        );

        UUID categoryId = createActiveCategory(
                owner.userGroupId(),
                owner.userId(),
                "Categoria duplicate logical occurrence"
        );

        UUID recurringTransactionId = createRecurringTransaction(
                owner.userGroupId(),
                LocalDate.of(2026, 6, 1)
        );

        LocalDate logicalDate = LocalDate.of(2026, 6, 15);

        insertRawConfirmedRecurringOccurrence(
                owner.userGroupId(),
                account,
                categoryId,
                recurringTransactionId,
                LocalDate.of(2026, 6, 15),
                logicalDate,
                OffsetDateTime.parse("2026-06-15T09:00:00Z")
        );

        assertThatThrownBy(() -> insertRawConfirmedRecurringOccurrence(
                owner.userGroupId(),
                account,
                categoryId,
                recurringTransactionId,
                LocalDate.of(2026, 6, 17),
                logicalDate,
                OffsetDateTime.parse("2026-06-17T09:00:00Z")
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(countTransactionsForUserGroup(owner.userGroupId())).isEqualTo(1L);
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

    private Map<String, Object> transactionRequest(
            AccountRef account,
            UUID categoryId,
            String description,
            String amount,
            LocalDate chargeDate
    ) {
        Map<String, Object> body = validTransactionRequest(account, categoryId);
        body.put("transactionDescription", description);
        body.put("transactionAmount", new BigDecimal(amount));
        body.put("transactionChargeDate", chargeDate.toString());
        return body;
    }

    private Map<String, Object> simulatedTransactionRequest(
            AccountRef account,
            UUID categoryId,
            UUID simulationGroupId,
            String description,
            String amount,
            LocalDate chargeDate
    ) {
        Map<String, Object> body = transactionRequest(
                account,
                categoryId,
                description,
                amount,
                chargeDate
        );

        body.put("transactionIsSimulated", true);
        body.put("simulationGroupId", simulationGroupId);

        return body;
    }

    private UUID createConfirmedRecurringOccurrence(
            UUID userGroupId,
            AccountRef account,
            UUID categoryId,
            UUID recurringTransactionId
    ) {
        return createConfirmedRecurringOccurrence(
                userGroupId,
                account,
                categoryId,
                recurringTransactionId,
                LocalDate.of(2026, 7, 15)
        );
    }

    private UUID createConfirmedRecurringOccurrence(
            UUID userGroupId,
            AccountRef account,
            UUID categoryId,
            UUID recurringTransactionId,
            LocalDate chargeDate
    ) {
        return insertRawConfirmedRecurringOccurrence(
                userGroupId,
                account,
                categoryId,
                recurringTransactionId,
                chargeDate,
                chargeDate,
                OffsetDateTime.parse(chargeDate + "T09:00:00Z")
        );
    }

    private Map<String, Object> validTransactionUpdateRequest(
            AccountRef account,
            UUID categoryId,
            String description,
            String amount,
            LocalDate chargeDate
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("transactionDescription", description);
        body.put("transactionAmount", new BigDecimal(amount));
        body.put("transactionAffectsAccountBalance", true);
        body.put("transactionAffectsLiquidity", true);
        body.put("categoryId", categoryId);
        body.put("transactionChargeDate", chargeDate.toString());
        body.put("transactionIsConfirmed", true);
        body.put("accountId", account.accountId());
        body.put("creditCardId", null);
        body.put("bucketId", null);
        body.put("transactionIsSimulated", false);
        body.put("simulationGroupId", null);
        body.put("transactionReminderEnabled", true);
        body.put("transactionReminderDaysBefore", 7);
        return body;
    }

    private long countTransactionById(UUID transactionId) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT count(*)
                        FROM transactions
                        WHERE transaction_id = ?
                        """,
                Long.class,
                transactionId
        );

        return count == null ? 0L : count;
    }

    private long countRecurringTransactionById(UUID recurringTransactionId) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT count(*)
                        FROM recurring_transactions
                        WHERE recurring_transaction_id = ?
                        """,
                Long.class,
                recurringTransactionId
        );

        return count == null ? 0L : count;
    }

    private long countTransactionUserLinksByTransactionId(UUID transactionId) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT count(*)
                        FROM transactions_users
                        WHERE transaction_id = ?
                        """,
                Long.class,
                transactionId
        );

        return count == null ? 0L : count;
    }

    private long countSimulationGroupById(UUID simulationGroupId) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT count(*)
                        FROM simulation_groups
                        WHERE simulation_group_id = ?
                        """,
                Long.class,
                simulationGroupId
        );

        return count == null ? 0L : count;
    }

    private void insertRawUserEnteredTransactionWithRecurringMetadata(
            UUID userGroupId,
            AccountRef account,
            UUID categoryId,
            LocalDate recurringTransactionLogicalDate,
            OffsetDateTime recurringTransactionConfirmedAt
    ) {
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
                            recurring_transaction_logical_date,
                            recurring_transaction_confirmed_at,
                            transaction_reminder_enabled,
                            transaction_reminder_days_before,
                            user_group_id
                        )
                        VALUES (?, ?, ?, TRUE, TRUE, ?, ?, FALSE, ?, FALSE, TRUE, ?, ?, TRUE, 7, ?)
                        """,
                UUID.randomUUID(),
                "Transazione manuale con metadata recurring non validi",
                new BigDecimal("-10.00"),
                categoryId,
                LocalDate.of(2026, 6, 10),
                account.accountId(),
                recurringTransactionLogicalDate,
                recurringTransactionConfirmedAt,
                userGroupId
        );
    }

    private UUID insertRawConfirmedRecurringOccurrence(
            UUID userGroupId,
            AccountRef account,
            UUID categoryId,
            UUID recurringTransactionId,
            LocalDate chargeDate,
            LocalDate recurringTransactionLogicalDate,
            OffsetDateTime recurringTransactionConfirmedAt
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
                            recurring_transaction_logical_date,
                            recurring_transaction_confirmed_at,
                            transaction_reminder_enabled,
                            transaction_reminder_days_before,
                            user_group_id
                        )
                        VALUES (?, ?, ?, TRUE, TRUE, ?, ?, TRUE, ?, FALSE, FALSE, ?, ?, ?, TRUE, 7, ?)
                        """,
                transactionId,
                "Occorrenza ricorrente confermata",
                new BigDecimal("-99.90"),
                categoryId,
                chargeDate,
                account.accountId(),
                recurringTransactionId,
                recurringTransactionLogicalDate,
                recurringTransactionConfirmedAt,
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