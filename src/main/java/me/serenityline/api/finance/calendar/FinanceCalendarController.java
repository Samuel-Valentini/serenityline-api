package me.serenityline.api.finance.calendar;

import me.serenityline.api.security.auth.AuthenticatedUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance/calendar")
public class FinanceCalendarController {

    private final FinanceCalendarService financeCalendarService;

    public FinanceCalendarController(
            FinanceCalendarService financeCalendarService
    ) {
        this.financeCalendarService = Objects.requireNonNull(
                financeCalendarService,
                "financeCalendarService"
        );
    }

    @GetMapping
    public ResponseEntity<List<FinanceCalendarMovementResponse>> getCalendarMovements(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,

            @RequestParam(required = false)
            List<UUID> accountIds,

            @RequestParam(required = false)
            List<UUID> simulationGroupIds
    ) {
        FinanceCalendarSearchRequest request = new FinanceCalendarSearchRequest(
                from,
                to,
                accountIds,
                simulationGroupIds
        );

        List<FinanceCalendarMovementResponse> response =
                financeCalendarService.getCalendarMovements(
                                authenticatedUser.userId(),
                                request
                        )
                        .stream()
                        .map(FinanceCalendarMovementResponse::from)
                        .toList();

        return ResponseEntity.ok(response);
    }
}