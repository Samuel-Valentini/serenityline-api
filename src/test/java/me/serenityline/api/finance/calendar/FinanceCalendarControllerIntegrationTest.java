package me.serenityline.api.finance.calendar;

import me.serenityline.api.common.error.ResourceNotFoundException;
import me.serenityline.api.security.jwt.JwtTokenService;
import me.serenityline.api.support.IntegrationTestSupport;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FinanceCalendarControllerIntegrationTest extends IntegrationTestSupport {

    private static final String CALENDAR_PATH = "/api/finance/calendar";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private FinanceCalendarService financeCalendarService;

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
    void getCalendarMovementsShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get(CALENDAR_PATH)
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(financeCalendarService);
    }

    @Test
    void ownerShouldGetCalendarMovements() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        UUID firstAccountId = UUID.randomUUID();
        UUID secondAccountId = UUID.randomUUID();
        UUID simulationGroupId = UUID.randomUUID();

        UUID transactionId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID accountId = firstAccountId;

        FinanceCalendarMovement movement = new FinanceCalendarMovement(
                FinanceCalendarMovementType.PERSISTED_TRANSACTION,
                transactionId,
                null,
                LocalDate.of(2026, 6, 10),
                LocalDate.of(2026, 6, 10),
                "Movimento calendario",
                new BigDecimal("-42.50"),
                true,
                true,
                categoryId,
                null,
                accountId,
                null,
                null,
                true,
                false,
                null,
                true,
                false
        );

        when(financeCalendarService.getCalendarMovements(
                eq(owner.userId()),
                any(FinanceCalendarSearchRequest.class)
        )).thenReturn(List.of(movement));

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(CALENDAR_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30")
                        .param("accountIds", firstAccountId.toString(), secondAccountId.toString())
                        .param("simulationGroupIds", simulationGroupId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].movementType").value("PERSISTED_TRANSACTION"))
                .andExpect(jsonPath("$[0].transactionId").value(transactionId.toString()))
                .andExpect(jsonPath("$[0].recurringTransactionId").doesNotExist())
                .andExpect(jsonPath("$[0].logicalDate").value("2026-06-10"))
                .andExpect(jsonPath("$[0].chargeDate").value("2026-06-10"))
                .andExpect(jsonPath("$[0].description").value("Movimento calendario"))
                .andExpect(jsonPath("$[0].amount").value(-42.5))
                .andExpect(jsonPath("$[0].affectsAccountBalance").value(true))
                .andExpect(jsonPath("$[0].affectsLiquidity").value(true))
                .andExpect(jsonPath("$[0].categoryId").value(categoryId.toString()))
                .andExpect(jsonPath("$[0].financialPriorityId").doesNotExist())
                .andExpect(jsonPath("$[0].accountId").value(accountId.toString()))
                .andExpect(jsonPath("$[0].creditCardId").doesNotExist())
                .andExpect(jsonPath("$[0].bucketId").doesNotExist())
                .andExpect(jsonPath("$[0].confirmed").value(true))
                .andExpect(jsonPath("$[0].simulated").value(false))
                .andExpect(jsonPath("$[0].simulationGroupId").doesNotExist())
                .andExpect(jsonPath("$[0].userEntered").value(true))
                .andExpect(jsonPath("$[0].finalOccurrence").value(false));

        ArgumentCaptor<FinanceCalendarSearchRequest> requestCaptor =
                ArgumentCaptor.forClass(FinanceCalendarSearchRequest.class);

        verify(financeCalendarService).getCalendarMovements(
                eq(owner.userId()),
                requestCaptor.capture()
        );

        FinanceCalendarSearchRequest capturedRequest = requestCaptor.getValue();

        assertThat(capturedRequest.from())
                .isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(capturedRequest.to())
                .isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(capturedRequest.accountIds())
                .containsExactly(firstAccountId, secondAccountId);
        assertThat(capturedRequest.simulationGroupIds())
                .containsExactly(simulationGroupId);
    }

    @Test
    void getCalendarMovementsShouldDelegateMissingFromValidationToService() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        when(financeCalendarService.getCalendarMovements(
                eq(owner.userId()),
                any(FinanceCalendarSearchRequest.class)
        )).thenThrow(new IllegalArgumentException("finance.calendar.fromRequired"));

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(CALENDAR_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("to", "2026-06-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.calendar.fromRequired"));
    }

    @Test
    void getCalendarMovementsShouldReturnNotFoundWhenServiceRejectsAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");
        UUID accountId = UUID.randomUUID();

        when(financeCalendarService.getCalendarMovements(
                eq(owner.userId()),
                any(FinanceCalendarSearchRequest.class)
        )).thenThrow(new ResourceNotFoundException("finance.account.notFound"));

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(CALENDAR_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30")
                        .param("accountIds", accountId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.account.notFound"));
    }

    @Test
    void getCalendarMovementsShouldPassNullFiltersWhenOptionalParamsAreMissing() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        when(financeCalendarService.getCalendarMovements(
                eq(owner.userId()),
                any(FinanceCalendarSearchRequest.class)
        )).thenReturn(List.of());

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(CALENDAR_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        ArgumentCaptor<FinanceCalendarSearchRequest> requestCaptor =
                ArgumentCaptor.forClass(FinanceCalendarSearchRequest.class);

        verify(financeCalendarService).getCalendarMovements(
                eq(owner.userId()),
                requestCaptor.capture()
        );

        FinanceCalendarSearchRequest capturedRequest = requestCaptor.getValue();

        assertThat(capturedRequest.from()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(capturedRequest.to()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(capturedRequest.accountIds()).isNull();
        assertThat(capturedRequest.simulationGroupIds()).isNull();
    }

    @Test
    void getCalendarMovementsShouldBindMultipleAccountIdsAndSimulationGroupIds() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        UUID firstAccountId = UUID.randomUUID();
        UUID secondAccountId = UUID.randomUUID();
        UUID firstSimulationGroupId = UUID.randomUUID();
        UUID secondSimulationGroupId = UUID.randomUUID();

        when(financeCalendarService.getCalendarMovements(
                eq(owner.userId()),
                any(FinanceCalendarSearchRequest.class)
        )).thenReturn(List.of());

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(CALENDAR_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30")
                        .param("accountIds", firstAccountId.toString(), secondAccountId.toString())
                        .param("simulationGroupIds", firstSimulationGroupId.toString(), secondSimulationGroupId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        ArgumentCaptor<FinanceCalendarSearchRequest> requestCaptor =
                ArgumentCaptor.forClass(FinanceCalendarSearchRequest.class);

        verify(financeCalendarService).getCalendarMovements(
                eq(owner.userId()),
                requestCaptor.capture()
        );

        FinanceCalendarSearchRequest capturedRequest = requestCaptor.getValue();

        assertThat(capturedRequest.accountIds())
                .containsExactly(firstAccountId, secondAccountId);

        assertThat(capturedRequest.simulationGroupIds())
                .containsExactly(firstSimulationGroupId, secondSimulationGroupId);
    }

    @Test
    void getCalendarMovementsShouldMapProjectedSimulatedMovementResponse() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        UUID recurringTransactionId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID financialPriorityId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID creditCardId = UUID.randomUUID();
        UUID bucketId = UUID.randomUUID();
        UUID simulationGroupId = UUID.randomUUID();

        FinanceCalendarMovement movement = new FinanceCalendarMovement(
                FinanceCalendarMovementType.PROJECTED_RECURRING_TRANSACTION,
                null,
                recurringTransactionId,
                LocalDate.of(2026, 6, 10),
                LocalDate.of(2026, 6, 9),
                "Movimento ricorrente simulato",
                new BigDecimal("-125.75"),
                true,
                false,
                categoryId,
                financialPriorityId,
                accountId,
                creditCardId,
                bucketId,
                false,
                true,
                simulationGroupId,
                false,
                true
        );

        when(financeCalendarService.getCalendarMovements(
                eq(owner.userId()),
                any(FinanceCalendarSearchRequest.class)
        )).thenReturn(List.of(movement));

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(CALENDAR_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].movementType").value("PROJECTED_RECURRING_TRANSACTION"))
                .andExpect(jsonPath("$[0].transactionId").doesNotExist())
                .andExpect(jsonPath("$[0].recurringTransactionId").value(recurringTransactionId.toString()))
                .andExpect(jsonPath("$[0].logicalDate").value("2026-06-10"))
                .andExpect(jsonPath("$[0].chargeDate").value("2026-06-09"))
                .andExpect(jsonPath("$[0].description").value("Movimento ricorrente simulato"))
                .andExpect(jsonPath("$[0].amount").value(-125.75))
                .andExpect(jsonPath("$[0].affectsAccountBalance").value(true))
                .andExpect(jsonPath("$[0].affectsLiquidity").value(false))
                .andExpect(jsonPath("$[0].categoryId").value(categoryId.toString()))
                .andExpect(jsonPath("$[0].financialPriorityId").value(financialPriorityId.toString()))
                .andExpect(jsonPath("$[0].accountId").value(accountId.toString()))
                .andExpect(jsonPath("$[0].creditCardId").value(creditCardId.toString()))
                .andExpect(jsonPath("$[0].bucketId").value(bucketId.toString()))
                .andExpect(jsonPath("$[0].confirmed").value(false))
                .andExpect(jsonPath("$[0].simulated").value(true))
                .andExpect(jsonPath("$[0].simulationGroupId").value(simulationGroupId.toString()))
                .andExpect(jsonPath("$[0].userEntered").value(false))
                .andExpect(jsonPath("$[0].finalOccurrence").value(true));
    }

    @Test
    void getCalendarMovementsShouldReturnEmptyArrayWhenServiceReturnsNoMovements() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        when(financeCalendarService.getCalendarMovements(
                eq(owner.userId()),
                any(FinanceCalendarSearchRequest.class)
        )).thenReturn(List.of());

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(CALENDAR_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void getCalendarMovementsShouldDelegateMissingToValidationToService() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        when(financeCalendarService.getCalendarMovements(
                eq(owner.userId()),
                any(FinanceCalendarSearchRequest.class)
        )).thenThrow(new IllegalArgumentException("finance.calendar.toRequired"));

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(CALENDAR_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("from", "2026-06-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.calendar.toRequired"));
    }

    @Test
    void getCalendarMovementsShouldReturnBadRequestWhenServiceRejectsInvalidDateRange() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        when(financeCalendarService.getCalendarMovements(
                eq(owner.userId()),
                any(FinanceCalendarSearchRequest.class)
        )).thenThrow(new IllegalArgumentException("finance.calendar.dateRangeInvalid"));

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(CALENDAR_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("from", "2026-07-01")
                        .param("to", "2026-06-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.calendar.dateRangeInvalid"));
    }

    @Test
    void getCalendarMovementsShouldReturnBadRequestWhenServiceRejectsTooLargeDateRange() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        when(financeCalendarService.getCalendarMovements(
                eq(owner.userId()),
                any(FinanceCalendarSearchRequest.class)
        )).thenThrow(new IllegalArgumentException("finance.calendar.dateRangeTooLarge"));

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(CALENDAR_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("from", "2026-01-01")
                        .param("to", "2036-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.calendar.dateRangeTooLarge"));
    }

    @Test
    void getCalendarMovementsShouldReturnBadRequestWhenServiceRejectsTooManyAccountIds() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        UUID firstAccountId = UUID.randomUUID();
        UUID secondAccountId = UUID.randomUUID();

        when(financeCalendarService.getCalendarMovements(
                eq(owner.userId()),
                any(FinanceCalendarSearchRequest.class)
        )).thenThrow(new IllegalArgumentException("finance.calendar.accountIdsTooMany"));

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(CALENDAR_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30")
                        .param("accountIds", firstAccountId.toString(), secondAccountId.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.calendar.accountIdsTooMany"));
    }

    @Test
    void getCalendarMovementsShouldReturnNotFoundWhenServiceRejectsSimulationGroup() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        UUID simulationGroupId = UUID.randomUUID();

        when(financeCalendarService.getCalendarMovements(
                eq(owner.userId()),
                any(FinanceCalendarSearchRequest.class)
        )).thenThrow(new ResourceNotFoundException("finance.simulationGroup.notFound"));

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(CALENDAR_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30")
                        .param("simulationGroupIds", simulationGroupId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.simulationGroup.notFound"));
    }

    @Test
    void getCalendarMovementsShouldRejectInvalidAccountIdBeforeCallingService() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(CALENDAR_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30")
                        .param("accountIds", "not-a-uuid"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(financeCalendarService);
    }

    @Test
    void getCalendarMovementsShouldRejectInvalidDateBeforeCallingService() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(CALENDAR_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("from", "2026-99-99")
                        .param("to", "2026-06-30"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(financeCalendarService);
    }

    @Test
    void getCalendarMovementsShouldUseAuthenticatedUserId() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        when(financeCalendarService.getCalendarMovements(
                eq(owner.userId()),
                any(FinanceCalendarSearchRequest.class)
        )).thenReturn(List.of());

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(CALENDAR_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30"))
                .andExpect(status().isOk());

        verify(financeCalendarService).getCalendarMovements(
                eq(owner.userId()),
                any(FinanceCalendarSearchRequest.class)
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
                unique("Calendar test group")
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
                "Calendar Test User",
                uniqueEmail("calendar-owner"),
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