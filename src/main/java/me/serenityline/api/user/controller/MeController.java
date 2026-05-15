package me.serenityline.api.user.controller;

import jakarta.validation.Valid;
import me.serenityline.api.auth.service.AuthCookieService;
import me.serenityline.api.auth.service.Email2faManagementService;
import me.serenityline.api.security.auth.AuthenticatedUser;
import me.serenityline.api.user.dto.*;
import me.serenityline.api.user.service.AccountDeletionService;
import me.serenityline.api.user.service.ChangePasswordService;
import me.serenityline.api.user.service.PaymentEmailRemindersService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

@RestController
public class MeController {

    private final AccountDeletionService accountDeletionService;
    private final AuthCookieService authCookieService;
    private final ChangePasswordService changePasswordService;
    private final Email2faManagementService email2faManagementService;
    private final PaymentEmailRemindersService paymentEmailRemindersService;

    public MeController(
            AccountDeletionService accountDeletionService,
            AuthCookieService authCookieService,
            ChangePasswordService changePasswordService,
            Email2faManagementService email2faManagementService,
            PaymentEmailRemindersService paymentEmailRemindersService
    ) {
        this.accountDeletionService = Objects.requireNonNull(accountDeletionService, "accountDeletionService");
        this.authCookieService = Objects.requireNonNull(authCookieService, "authCookieService");
        this.changePasswordService = Objects.requireNonNull(changePasswordService, "changePasswordService");
        this.email2faManagementService = Objects.requireNonNull(email2faManagementService, "email2faManagementService");
        this.paymentEmailRemindersService = Objects.requireNonNull(paymentEmailRemindersService, "paymentEmailRemindersService");
    }

    @GetMapping("/api/me")
    public CurrentUserResponse me(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return CurrentUserResponse.from(authenticatedUser);
    }

    @DeleteMapping("/api/me")
    public ResponseEntity<Void> deleteMe(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        accountDeletionService.deleteAccount(authenticatedUser.userId());

        ResponseCookie clearCookie = authCookieService.clearRefreshTokenCookie();

        return ResponseEntity
                .noContent()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .build();
    }

    @PostMapping("/api/me/change-password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        changePasswordService.changePassword(
                authenticatedUser.userId(),
                request
        );

        ResponseCookie clearCookie = authCookieService.clearRefreshTokenCookie();

        return ResponseEntity
                .noContent()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .build();
    }

    @PostMapping("/api/me/email-2fa/enable/request")
    public ResponseEntity<Email2faChallengeResponse> requestEnableEmail2fa(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody RequestEnableEmail2faRequest request
    ) {
        Email2faChallengeResponse response = email2faManagementService.requestEnable(
                authenticatedUser.userId(),
                request.currentPassword()
        );

        return ResponseEntity
                .accepted()
                .body(response);
    }

    @PostMapping("/api/me/email-2fa/enable/confirm")
    public ResponseEntity<Void> confirmEnableEmail2fa(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody ConfirmEnableEmail2faRequest request
    ) {
        email2faManagementService.confirmEnable(
                authenticatedUser.userId(),
                request.challengeId(),
                request.code()
        );

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/me/email-2fa/disable/request")
    public ResponseEntity<Email2faChallengeResponse> requestDisableEmail2fa(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody RequestDisableEmail2faRequest request
    ) {
        Email2faChallengeResponse response = email2faManagementService.requestDisable(
                authenticatedUser.userId(),
                request.currentPassword()
        );

        return ResponseEntity
                .accepted()
                .body(response);
    }

    @PostMapping("/api/me/email-2fa/disable/confirm")
    public ResponseEntity<Void> confirmDisableEmail2fa(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody ConfirmDisableEmail2faRequest request
    ) {
        email2faManagementService.confirmDisable(
                authenticatedUser.userId(),
                request.challengeId(),
                request.code()
        );

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/api/me/payment-email-reminders")
    public ResponseEntity<PaymentEmailRemindersResponse> updatePaymentEmailReminders(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody UpdatePaymentEmailRemindersRequest request
    ) {
        PaymentEmailRemindersResponse response = paymentEmailRemindersService.updatePaymentEmailReminders(
                authenticatedUser.userId(),
                request.enabled()
        );

        return ResponseEntity.ok(response);
    }
}