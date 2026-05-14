package me.serenityline.api.user.controller;

import me.serenityline.api.auth.service.AuthCookieService;
import me.serenityline.api.security.auth.AuthenticatedUser;
import me.serenityline.api.user.dto.CurrentUserResponse;
import me.serenityline.api.user.service.AccountDeletionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
public class MeController {

    private final AccountDeletionService accountDeletionService;
    private final AuthCookieService authCookieService;

    public MeController(
            AccountDeletionService accountDeletionService,
            AuthCookieService authCookieService
    ) {
        this.accountDeletionService = Objects.requireNonNull(accountDeletionService, "accountDeletionService");
        this.authCookieService = Objects.requireNonNull(authCookieService, "authCookieService");
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
}