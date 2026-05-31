package me.serenityline.api.finance.report;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FinanceReportControllerIntegrationTest extends IntegrationTestSupport {

    private static final String REPORT_SUMMARY_PATH = "/api/finance/reports/summary";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private FinanceReportService financeReportService;

    private static FinanceReportSummary reportSummary() {
        return new FinanceReportSummary(
                LocalDate.of(2026, 6, 15),
                FinanceReportProjectionMode.PROJECTED_PLANNING,
                new FinanceReportRange(
                        LocalDate.of(2023, 6, 15),
                        LocalDate.of(2029, 6, 15)
                ),
                10,
                List.of(new FinanceRecurringReportSummary(
                        "EUR",
                        new BigDecimal("1200.00"),
                        new BigDecimal("600.00"),
                        new BigDecimal("600.00"),
                        new BigDecimal("100.00"),
                        new BigDecimal("50.00"),
                        new BigDecimal("50.00")
                )),
                List.of(new FinanceReportExtremesByCurrency(
                        "EUR",
                        LocalDate.of(2026, 6, 15),
                        LocalDate.of(2023, 6, 15),
                        LocalDate.of(2029, 6, 15),
                        new FinanceReportPoint(
                                LocalDate.of(2026, 6, 15),
                                new BigDecimal("300.00"),
                                FinanceReportTemporalPosition.TODAY,
                                FinanceReportExtremeClassification.IN_RANGE_EXTREME,
                                new FinanceReportTrend(
                                        FinanceReportTrendDirection.DOWN,
                                        LocalDate.of(2026, 3, 15),
                                        LocalDate.of(2026, 6, 15),
                                        false
                                )
                        ),
                        new FinanceReportPoint(
                                LocalDate.of(2029, 6, 15),
                                new BigDecimal("900.00"),
                                FinanceReportTemporalPosition.FUTURE,
                                FinanceReportExtremeClassification.RANGE_END_BOUNDARY,
                                null
                        ),
                        new FinanceReportPoint(
                                LocalDate.of(2023, 6, 15),
                                new BigDecimal("500.00"),
                                FinanceReportTemporalPosition.PAST,
                                FinanceReportExtremeClassification.RANGE_START_BOUNDARY,
                                null
                        ),
                        new FinanceReportPoint(
                                LocalDate.of(2029, 6, 15),
                                new BigDecimal("1000.00"),
                                FinanceReportTemporalPosition.FUTURE,
                                FinanceReportExtremeClassification.MONOTONIC_TREND_WITHIN_HORIZON,
                                null
                        )
                )),
                List.of(
                        new FinanceYearEndForecast(
                                2026,
                                LocalDate.of(2026, 12, 31),
                                List.of(new FinanceYearEndForecastByCurrency(
                                        "EUR",
                                        new BigDecimal("1100.00"),
                                        new BigDecimal("950.00")
                                ))
                        ),
                        new FinanceYearEndForecast(
                                2027,
                                LocalDate.of(2027, 12, 31),
                                List.of(new FinanceYearEndForecastByCurrency(
                                        "EUR",
                                        new BigDecimal("1200.00"),
                                        new BigDecimal("850.00")
                                ))
                        )
                )
        );
    }

    private static FinanceReportSummary emptyReportSummary() {
        return new FinanceReportSummary(
                LocalDate.of(2026, 6, 15),
                FinanceReportProjectionMode.PROJECTED_PLANNING,
                new FinanceReportRange(
                        LocalDate.of(2023, 6, 15),
                        LocalDate.of(2029, 6, 15)
                ),
                10,
                List.of(),
                List.of(),
                List.of()
        );
    }

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
    void getReportSummaryShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get(REPORT_SUMMARY_PATH))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(financeReportService);
    }

    @Test
    void ownerShouldGetReportSummary() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        UUID firstAccountId = UUID.randomUUID();
        UUID secondAccountId = UUID.randomUUID();
        UUID simulationGroupId = UUID.randomUUID();

        FinanceReportSummary summary = reportSummary();

        when(financeReportService.getReportSummary(
                eq(owner.userId()),
                any(FinanceReportSummaryRequest.class)
        )).thenReturn(summary);

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(REPORT_SUMMARY_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("accountIds", firstAccountId.toString(), secondAccountId.toString())
                        .param("simulationGroupIds", simulationGroupId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asOfDate").value("2026-06-15"))
                .andExpect(jsonPath("$.projectionMode").value("PROJECTED_PLANNING"))
                .andExpect(jsonPath("$.extremesRange.from").value("2023-06-15"))
                .andExpect(jsonPath("$.extremesRange.to").value("2029-06-15"))
                .andExpect(jsonPath("$.yearEndForecastYears").value(10))

                .andExpect(jsonPath("$.recurringByCurrency[0].currency").value("EUR"))
                .andExpect(jsonPath("$.recurringByCurrency[0].annualIncome").value(1200.00))
                .andExpect(jsonPath("$.recurringByCurrency[0].annualExpenses").value(600.00))
                .andExpect(jsonPath("$.recurringByCurrency[0].annualNetBalance").value(600.00))
                .andExpect(jsonPath("$.recurringByCurrency[0].averageMonthlyIncome").value(100.00))
                .andExpect(jsonPath("$.recurringByCurrency[0].averageMonthlyExpenses").value(50.00))
                .andExpect(jsonPath("$.recurringByCurrency[0].averageMonthlyNetBalance").value(50.00))

                .andExpect(jsonPath("$.extremesByCurrency[0].currency").value("EUR"))
                .andExpect(jsonPath("$.extremesByCurrency[0].asOfDate").value("2026-06-15"))
                .andExpect(jsonPath("$.extremesByCurrency[0].rangeFrom").value("2023-06-15"))
                .andExpect(jsonPath("$.extremesByCurrency[0].rangeTo").value("2029-06-15"))

                .andExpect(jsonPath("$.extremesByCurrency[0].minSerenityline.date").value("2026-06-15"))
                .andExpect(jsonPath("$.extremesByCurrency[0].minSerenityline.value").value(300.00))
                .andExpect(jsonPath("$.extremesByCurrency[0].minSerenityline.temporalPosition").value("TODAY"))
                .andExpect(jsonPath("$.extremesByCurrency[0].minSerenityline.classification").value("IN_RANGE_EXTREME"))
                .andExpect(jsonPath("$.extremesByCurrency[0].minSerenityline.trend.direction").value("DOWN"))
                .andExpect(jsonPath("$.extremesByCurrency[0].minSerenityline.trend.startedAt").value("2026-03-15"))
                .andExpect(jsonPath("$.extremesByCurrency[0].minSerenityline.trend.observedUntil").value("2026-06-15"))
                .andExpect(jsonPath("$.extremesByCurrency[0].minSerenityline.trend.monotonicUntilRangeEnd").value(false))

                .andExpect(jsonPath("$.extremesByCurrency[0].maxSerenityline.date").value("2029-06-15"))
                .andExpect(jsonPath("$.extremesByCurrency[0].maxSerenityline.value").value(900.00))
                .andExpect(jsonPath("$.extremesByCurrency[0].maxSerenityline.temporalPosition").value("FUTURE"))
                .andExpect(jsonPath("$.extremesByCurrency[0].maxSerenityline.classification").value("RANGE_END_BOUNDARY"))

                .andExpect(jsonPath("$.extremesByCurrency[0].minAccountBalance.date").value("2023-06-15"))
                .andExpect(jsonPath("$.extremesByCurrency[0].minAccountBalance.value").value(500.00))
                .andExpect(jsonPath("$.extremesByCurrency[0].minAccountBalance.temporalPosition").value("PAST"))
                .andExpect(jsonPath("$.extremesByCurrency[0].minAccountBalance.classification").value("RANGE_START_BOUNDARY"))

                .andExpect(jsonPath("$.extremesByCurrency[0].maxAccountBalance.date").value("2029-06-15"))
                .andExpect(jsonPath("$.extremesByCurrency[0].maxAccountBalance.value").value(1000.00))
                .andExpect(jsonPath("$.extremesByCurrency[0].maxAccountBalance.temporalPosition").value("FUTURE"))
                .andExpect(jsonPath("$.extremesByCurrency[0].maxAccountBalance.classification").value("MONOTONIC_TREND_WITHIN_HORIZON"))

                .andExpect(jsonPath("$.yearEndForecasts[0].year").value(2026))
                .andExpect(jsonPath("$.yearEndForecasts[0].date").value("2026-12-31"))
                .andExpect(jsonPath("$.yearEndForecasts[0].balancesByCurrency[0].currency").value("EUR"))
                .andExpect(jsonPath("$.yearEndForecasts[0].balancesByCurrency[0].endOfYearAccountBalance").value(1100.00))
                .andExpect(jsonPath("$.yearEndForecasts[0].balancesByCurrency[0].endOfYearSerenityline").value(950.00))

                .andExpect(jsonPath("$.yearEndForecasts[1].year").value(2027))
                .andExpect(jsonPath("$.yearEndForecasts[1].date").value("2027-12-31"))
                .andExpect(jsonPath("$.yearEndForecasts[1].balancesByCurrency[0].currency").value("EUR"))
                .andExpect(jsonPath("$.yearEndForecasts[1].balancesByCurrency[0].endOfYearAccountBalance").value(1200.00))
                .andExpect(jsonPath("$.yearEndForecasts[1].balancesByCurrency[0].endOfYearSerenityline").value(850.00));

        ArgumentCaptor<FinanceReportSummaryRequest> requestCaptor =
                ArgumentCaptor.forClass(FinanceReportSummaryRequest.class);

        verify(financeReportService).getReportSummary(
                eq(owner.userId()),
                requestCaptor.capture()
        );

        FinanceReportSummaryRequest capturedRequest = requestCaptor.getValue();

        assertThat(capturedRequest.accountIds())
                .containsExactly(firstAccountId, secondAccountId);

        assertThat(capturedRequest.simulationGroupIds())
                .containsExactly(simulationGroupId);
    }

    @Test
    void getReportSummaryShouldPassNullFiltersWhenOptionalParamsAreMissing() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        when(financeReportService.getReportSummary(
                eq(owner.userId()),
                any(FinanceReportSummaryRequest.class)
        )).thenReturn(emptyReportSummary());

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(REPORT_SUMMARY_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asOfDate").value("2026-06-15"))
                .andExpect(jsonPath("$.projectionMode").value("PROJECTED_PLANNING"))
                .andExpect(jsonPath("$.recurringByCurrency").isArray())
                .andExpect(jsonPath("$.recurringByCurrency").isEmpty())
                .andExpect(jsonPath("$.extremesByCurrency").isArray())
                .andExpect(jsonPath("$.extremesByCurrency").isEmpty())
                .andExpect(jsonPath("$.yearEndForecasts").isArray())
                .andExpect(jsonPath("$.yearEndForecasts").isEmpty());

        ArgumentCaptor<FinanceReportSummaryRequest> requestCaptor =
                ArgumentCaptor.forClass(FinanceReportSummaryRequest.class);

        verify(financeReportService).getReportSummary(
                eq(owner.userId()),
                requestCaptor.capture()
        );

        FinanceReportSummaryRequest capturedRequest = requestCaptor.getValue();

        assertThat(capturedRequest.accountIds()).isNull();
        assertThat(capturedRequest.simulationGroupIds()).isNull();
    }

    @Test
    void getReportSummaryShouldBindMultipleAccountIdsAndSimulationGroupIds() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        UUID firstAccountId = UUID.randomUUID();
        UUID secondAccountId = UUID.randomUUID();
        UUID firstSimulationGroupId = UUID.randomUUID();
        UUID secondSimulationGroupId = UUID.randomUUID();

        when(financeReportService.getReportSummary(
                eq(owner.userId()),
                any(FinanceReportSummaryRequest.class)
        )).thenReturn(emptyReportSummary());

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(REPORT_SUMMARY_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("accountIds", firstAccountId.toString(), secondAccountId.toString())
                        .param("simulationGroupIds", firstSimulationGroupId.toString(), secondSimulationGroupId.toString()))
                .andExpect(status().isOk());

        ArgumentCaptor<FinanceReportSummaryRequest> requestCaptor =
                ArgumentCaptor.forClass(FinanceReportSummaryRequest.class);

        verify(financeReportService).getReportSummary(
                eq(owner.userId()),
                requestCaptor.capture()
        );

        assertThat(requestCaptor.getValue().accountIds())
                .containsExactly(firstAccountId, secondAccountId);

        assertThat(requestCaptor.getValue().simulationGroupIds())
                .containsExactly(firstSimulationGroupId, secondSimulationGroupId);
    }

    @Test
    void getReportSummaryShouldUseAuthenticatedUserId() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        when(financeReportService.getReportSummary(
                eq(owner.userId()),
                any(FinanceReportSummaryRequest.class)
        )).thenReturn(emptyReportSummary());

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(REPORT_SUMMARY_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk());

        verify(financeReportService).getReportSummary(
                eq(owner.userId()),
                any(FinanceReportSummaryRequest.class)
        );
    }

    @Test
    void getReportSummaryShouldReturnBadRequestWhenServiceRejectsTooManyAccountIds() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        when(financeReportService.getReportSummary(
                eq(owner.userId()),
                any(FinanceReportSummaryRequest.class)
        )).thenThrow(new IllegalArgumentException("finance.calendar.accountIdsTooMany"));

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(REPORT_SUMMARY_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("accountIds", UUID.randomUUID().toString(), UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.calendar.accountIdsTooMany"));
    }

    @Test
    void getReportSummaryShouldReturnBadRequestWhenServiceRejectsTooManySimulationGroupIds() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        when(financeReportService.getReportSummary(
                eq(owner.userId()),
                any(FinanceReportSummaryRequest.class)
        )).thenThrow(new IllegalArgumentException("finance.simulationGroup.idsTooMany"));

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(REPORT_SUMMARY_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("simulationGroupIds", UUID.randomUUID().toString(), UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("finance.simulationGroup.idsTooMany"));
    }

    @Test
    void getReportSummaryShouldReturnNotFoundWhenServiceRejectsAccount() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        when(financeReportService.getReportSummary(
                eq(owner.userId()),
                any(FinanceReportSummaryRequest.class)
        )).thenThrow(new ResourceNotFoundException("finance.account.notFound"));

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(REPORT_SUMMARY_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("accountIds", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.account.notFound"));
    }

    @Test
    void getReportSummaryShouldReturnNotFoundWhenServiceRejectsSimulationGroup() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        when(financeReportService.getReportSummary(
                eq(owner.userId()),
                any(FinanceReportSummaryRequest.class)
        )).thenThrow(new ResourceNotFoundException("finance.simulationGroup.notFound"));

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(REPORT_SUMMARY_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("simulationGroupIds", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("finance.simulationGroup.notFound"));
    }

    @Test
    void getReportSummaryShouldReturnNotFoundWhenServiceRejectsMissingUser() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        when(financeReportService.getReportSummary(
                eq(owner.userId()),
                any(FinanceReportSummaryRequest.class)
        )).thenThrow(new ResourceNotFoundException("user.notFound"));

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(REPORT_SUMMARY_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("error.notFound"));
    }

    @Test
    void getReportSummaryShouldRejectInvalidAccountIdBeforeCallingService() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(REPORT_SUMMARY_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("accountIds", "not-a-uuid"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(financeReportService);
    }

    @Test
    void getReportSummaryShouldRejectInvalidSimulationGroupIdBeforeCallingService() throws Exception {
        UserRef owner = createUserWithNewGroup("OWNER");

        String accessToken = accessTokenFor(owner);

        mockMvc.perform(get(REPORT_SUMMARY_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("simulationGroupIds", "not-a-uuid"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(financeReportService);
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
                unique("Finance report controller test group")
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
                "Finance Report Controller Test User",
                uniqueEmail("finance-report-controller"),
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