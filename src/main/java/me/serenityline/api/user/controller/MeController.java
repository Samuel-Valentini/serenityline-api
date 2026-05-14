package me.serenityline.api.user.controller;

import jakarta.validation.Valid;
import me.serenityline.api.auth.service.AuthCookieService;
import me.serenityline.api.security.auth.AuthenticatedUser;
import me.serenityline.api.user.dto.ChangePasswordRequest;
import me.serenityline.api.user.dto.CurrentUserResponse;
import me.serenityline.api.user.service.AccountDeletionService;
import me.serenityline.api.user.service.ChangePasswordService;
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

    public MeController(
            AccountDeletionService accountDeletionService,
            AuthCookieService authCookieService,
            ChangePasswordService changePasswordService
    ) {
        this.accountDeletionService = Objects.requireNonNull(accountDeletionService, "accountDeletionService");
        this.authCookieService = Objects.requireNonNull(authCookieService, "authCookieService");
        this.changePasswordService = Objects.requireNonNull(changePasswordService, "changePasswordService");
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
}