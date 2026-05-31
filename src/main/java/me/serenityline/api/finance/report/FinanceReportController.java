package me.serenityline.api.finance.report;

import me.serenityline.api.security.auth.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance/reports")
public class FinanceReportController {

    private final FinanceReportService financeReportService;

    public FinanceReportController(FinanceReportService financeReportService) {
        this.financeReportService = Objects.requireNonNull(
                financeReportService,
                "financeReportService"
        );
    }

    @GetMapping("/summary")
    public ResponseEntity<FinanceReportSummaryResponse> getReportSummary(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false)
            List<UUID> accountIds,
            @RequestParam(required = false)
            List<UUID> simulationGroupIds
    ) {
        FinanceReportSummary summary = financeReportService.getReportSummary(
                authenticatedUser.userId(),
                new FinanceReportSummaryRequest(
                        accountIds,
                        simulationGroupIds
                )
        );

        return ResponseEntity.ok(
                FinanceReportSummaryResponse.from(summary)
        );
    }
}