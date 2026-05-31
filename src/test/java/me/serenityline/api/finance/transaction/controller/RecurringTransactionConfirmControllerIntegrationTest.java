package me.serenityline.api.finance.transaction.controller;

import me.serenityline.api.common.error.ResourceNotFoundException;
import me.serenityline.api.finance.transaction.dto.RecurringTransactionOccurrenceConfirmRequest;
import me.serenityline.api.finance.transaction.dto.TransactionResponse;
import me.serenityline.api.finance.transaction.service.RecurringTransactionOccurrenceConfirmationService;
import me.serenityline.api.security.jwt.JwtTokenService;
import me.serenityline.api.support.IntegrationTestSupport;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecurringTransactionConfirmControllerIntegrationTest extends IntegrationTestSupport {

    private static final String RECURRING_TRANSACTIONS_PATH = "/api/finance/recurring-transactions";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private RecurringTransactionOccurrenceConfirmationService recurringTransactionOccurrenceConfirmationService;

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
    void confirmRecurringTransactionOccurrenceShouldRequireAuthentication() throws Exception {
        UUID recurringTransactionId = UUID.randomUUID();

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH + "/" + recurringTransactionId + "/occurrences/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "logicalDate": "2026-06-01"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(recurringTransactionOccurrenceConfirmationService);
    }

    @Test
    void ownerShouldConfirmRecurringTransactionOccurrence() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        UUID recurringTransactionId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        TransactionResponse response = new TransactionResponse(
                transactionId,
                "Affitto confermato",
                new BigDecimal("-100.00"),
                true,
                true,
                categoryId,
                LocalDate.of(2026, 6, 3),
                true,
                accountId,
                null,
                null,
                false,
                null,
                false,
                recurringTransactionId,
                LocalDate.of(2026, 6, 1),
                OffsetDateTime.parse("2026-06-01T10:15:30+02:00"),
                true,
                (short) 7,
                OffsetDateTime.parse("2026-06-01T10:15:30+02:00"),
                OffsetDateTime.parse("2026-06-01T10:15:30+02:00")
        );

        when(recurringTransactionOccurrenceConfirmationService.confirmOccurrence(
                eq(owner.userId()),
                eq(recurringTransactionId),
                any(RecurringTransactionOccurrenceConfirmRequest.class)
        )).thenReturn(response);

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH + "/" + recurringTransactionId + "/occurrences/confirm")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "logicalDate": "2026-06-01",
                                  "transactionAmount": -100.00,
                                  "transactionChargeDate": "2026-06-03"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId").value(transactionId.toString()))
                .andExpect(jsonPath("$.transactionDescription").value("Affitto confermato"))
                .andExpect(jsonPath("$.transactionAmount").value(-100.00))
                .andExpect(jsonPath("$.transactionAffectsAccountBalance").value(true))
                .andExpect(jsonPath("$.transactionAffectsSerenityline").value(true))
                .andExpect(jsonPath("$.categoryId").value(categoryId.toString()))
                .andExpect(jsonPath("$.transactionChargeDate").value("2026-06-03"))
                .andExpect(jsonPath("$.transactionIsConfirmed").value(true))
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.creditCardId").doesNotExist())
                .andExpect(jsonPath("$.bucketId").doesNotExist())
                .andExpect(jsonPath("$.transactionIsSimulated").value(false))
                .andExpect(jsonPath("$.simulationGroupId").doesNotExist())
                .andExpect(jsonPath("$.transactionIsUserEntered").value(false))
                .andExpect(jsonPath("$.recurringTransactionId").value(recurringTransactionId.toString()))
                .andExpect(jsonPath("$.recurringTransactionLogicalDate").value("2026-06-01"))
                .andExpect(jsonPath("$.recurringTransactionConfirmedAt").exists())
                .andExpect(jsonPath("$.transactionReminderEnabled").value(true))
                .andExpect(jsonPath("$.transactionReminderDaysBefore").value(7));

        ArgumentCaptor<RecurringTransactionOccurrenceConfirmRequest> requestCaptor =
                ArgumentCaptor.forClass(RecurringTransactionOccurrenceConfirmRequest.class);

        verify(recurringTransactionOccurrenceConfirmationService).confirmOccurrence(
                eq(owner.userId()),
                eq(recurringTransactionId),
                requestCaptor.capture()
        );

        RecurringTransactionOccurrenceConfirmRequest capturedRequest = requestCaptor.getValue();

        assertThat(capturedRequest.logicalDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(capturedRequest.transactionAmount()).isEqualByComparingTo("-100.00");
        assertThat(capturedRequest.transactionChargeDate()).isEqualTo(LocalDate.of(2026, 6, 3));
    }

    @Test
    void confirmRecurringTransactionOccurrenceShouldPassNullOverridesWhenMissing() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        UUID recurringTransactionId = UUID.randomUUID();

        when(recurringTransactionOccurrenceConfirmationService.confirmOccurrence(
                eq(owner.userId()),
                eq(recurringTransactionId),
                any(RecurringTransactionOccurrenceConfirmRequest.class)
        )).thenReturn(emptyConfirmedResponse(recurringTransactionId));

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH + "/" + recurringTransactionId + "/occurrences/confirm")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "logicalDate": "2026-06-01"
                                }
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<RecurringTransactionOccurrenceConfirmRequest> requestCaptor =
                ArgumentCaptor.forClass(RecurringTransactionOccurrenceConfirmRequest.class);

        verify(recurringTransactionOccurrenceConfirmationService).confirmOccurrence(
                eq(owner.userId()),
                eq(recurringTransactionId),
                requestCaptor.capture()
        );

        assertThat(requestCaptor.getValue().logicalDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(requestCaptor.getValue().transactionAmount()).isNull();
        assertThat(requestCaptor.getValue().transactionChargeDate()).isNull();
    }

    @Test
    void confirmRecurringTransactionOccurrenceShouldRejectMissingLogicalDate() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UUID recurringTransactionId = UUID.randomUUID();

        when(recurringTransactionOccurrenceConfirmationService.confirmOccurrence(
                eq(owner.userId()),
                eq(recurringTransactionId),
                any(RecurringTransactionOccurrenceConfirmRequest.class)
        )).thenThrow(new IllegalArgumentException(
                "finance.recurringTransaction.occurrenceLogicalDateRequired"
        ));

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH + "/" + recurringTransactionId + "/occurrences/confirm")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "transactionAmount": -100.00
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.recurringTransaction.occurrenceLogicalDateRequired"));

        ArgumentCaptor<RecurringTransactionOccurrenceConfirmRequest> requestCaptor =
                ArgumentCaptor.forClass(RecurringTransactionOccurrenceConfirmRequest.class);

        verify(recurringTransactionOccurrenceConfirmationService).confirmOccurrence(
                eq(owner.userId()),
                eq(recurringTransactionId),
                requestCaptor.capture()
        );

        assertThat(requestCaptor.getValue().logicalDate()).isNull();
        assertThat(requestCaptor.getValue().transactionAmount()).isEqualByComparingTo("-100.00");
        assertThat(requestCaptor.getValue().transactionChargeDate()).isNull();
    }

    @Test
    void confirmRecurringTransactionOccurrenceShouldRejectInvalidRecurringTransactionIdBeforeCallingService() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH + "/not-a-uuid/occurrences/confirm")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "logicalDate": "2026-06-01"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(recurringTransactionOccurrenceConfirmationService);
    }

    @Test
    void confirmRecurringTransactionOccurrenceShouldRejectInvalidLogicalDateBeforeCallingService() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UUID recurringTransactionId = UUID.randomUUID();

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH + "/" + recurringTransactionId + "/occurrences/confirm")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "logicalDate": "2026-99-99"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(recurringTransactionOccurrenceConfirmationService);
    }

    @Test
    void confirmRecurringTransactionOccurrenceShouldReturnBadRequestWhenServiceRejectsAlreadyConfirmedOccurrence() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UUID recurringTransactionId = UUID.randomUUID();

        when(recurringTransactionOccurrenceConfirmationService.confirmOccurrence(
                eq(owner.userId()),
                eq(recurringTransactionId),
                any(RecurringTransactionOccurrenceConfirmRequest.class)
        )).thenThrow(new IllegalArgumentException("finance.recurringTransaction.occurrenceAlreadyConfirmed"));

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH + "/" + recurringTransactionId + "/occurrences/confirm")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "logicalDate": "2026-06-01"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.recurringTransaction.occurrenceAlreadyConfirmed"));
    }

    @Test
    void confirmRecurringTransactionOccurrenceShouldReturnNotFoundWhenServiceRejectsMissingOccurrence() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UUID recurringTransactionId = UUID.randomUUID();

        when(recurringTransactionOccurrenceConfirmationService.confirmOccurrence(
                eq(owner.userId()),
                eq(recurringTransactionId),
                any(RecurringTransactionOccurrenceConfirmRequest.class)
        )).thenThrow(new ResourceNotFoundException("finance.recurringTransaction.occurrenceNotFound"));

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH + "/" + recurringTransactionId + "/occurrences/confirm")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "logicalDate": "2026-06-01"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.recurringTransaction.occurrenceNotFound"));
    }

    @Test
    void confirmRecurringTransactionOccurrenceShouldUseAuthenticatedUserId() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UUID recurringTransactionId = UUID.randomUUID();

        when(recurringTransactionOccurrenceConfirmationService.confirmOccurrence(
                eq(owner.userId()),
                eq(recurringTransactionId),
                any(RecurringTransactionOccurrenceConfirmRequest.class)
        )).thenReturn(emptyConfirmedResponse(recurringTransactionId));

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(post(RECURRING_TRANSACTIONS_PATH + "/" + recurringTransactionId + "/occurrences/confirm")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "logicalDate": "2026-06-01"
                                }
                                """))
                .andExpect(status().isCreated());

        verify(recurringTransactionOccurrenceConfirmationService).confirmOccurrence(
                eq(owner.userId()),
                eq(recurringTransactionId),
                any(RecurringTransactionOccurrenceConfirmRequest.class)
        );
    }

    private TransactionResponse emptyConfirmedResponse(UUID recurringTransactionId) {
        return new TransactionResponse(
                UUID.randomUUID(),
                "Occorrenza confermata",
                new BigDecimal("-100.00"),
                true,
                true,
                UUID.randomUUID(),
                LocalDate.of(2026, 6, 1),
                true,
                UUID.randomUUID(),
                null,
                null,
                false,
                null,
                false,
                recurringTransactionId,
                LocalDate.of(2026, 6, 1),
                OffsetDateTime.parse("2026-06-01T10:15:30+02:00"),
                true,
                (short) 7,
                OffsetDateTime.parse("2026-06-01T10:15:30+02:00"),
                OffsetDateTime.parse("2026-06-01T10:15:30+02:00")
        );
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
                unique("Recurring transaction controller test group")
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
                "Recurring Transaction Controller Test User",
                uniqueEmail("recurring-controller"),
                userGroupId,
                role,
                "test-password-hash"
        );

        return new UserRef(userId, userGroupId, role);
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
}