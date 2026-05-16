package me.serenityline.api.auth.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import me.serenityline.api.auth.dto.*;
import me.serenityline.api.auth.service.*;
import me.serenityline.api.security.auth.AuthenticatedUser;
import me.serenityline.api.user.dto.ConfirmEmailChangeRequest;
import me.serenityline.api.user.service.EmailChangeService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegisterService registerService;
    private final EmailVerificationService emailVerificationService;
    private final LoginService loginService;
    private final RestoreAccountService restoreAccountService;
    private final ResendEmailVerificationService resendEmailVerificationService;
    private final AuthCookieService authCookieService;
    private final RefreshTokenService refreshTokenService;
    private final LogoutService logoutService;
    private final PasswordResetService passwordResetService;
    private final Login2faService login2faService;
    private final EmailChangeService emailChangeService;

    public AuthController(RegisterService registerService, EmailVerificationService emailVerificationService, LoginService loginService, RestoreAccountService restoreAccountService, ResendEmailVerificationService resendEmailVerificationService, AuthCookieService authCookieService, RefreshTokenService refreshTokenService, LogoutService logoutService, PasswordResetService passwordResetService, Login2faService login2faService, EmailChangeService emailChangeService) {
        this.registerService = registerService;
        this.emailVerificationService = emailVerificationService;
        this.loginService = loginService;
        this.restoreAccountService = restoreAccountService;
        this.resendEmailVerificationService = resendEmailVerificationService;
        this.authCookieService = authCookieService;
        this.refreshTokenService = refreshTokenService;
        this.logoutService = logoutService;
        this.passwordResetService = passwordResetService;
        this.login2faService = login2faService;
        this.emailChangeService = emailChangeService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        RegisterResponse response = registerService.register(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/verify-email")
    public ResponseEntity<VerifyEmailResponse> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request
    ) {
        VerifyEmailResponse response = emailVerificationService.verifyEmail(request.token());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpServletRequest
    ) {

        LoginClientMetadata metadata = new LoginClientMetadata(
                httpServletRequest.getRemoteAddr(),
                httpServletRequest.getHeader(HttpHeaders.USER_AGENT),
                request.deviceLabel()
        );

        LoginResult result = loginService.login(request, metadata);

        if (result.isRestoreRequired()) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(result.restoreAccountChallenge());
        }

        if (result.isEmailVerificationRequired()) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(result.emailVerificationRequiredResponse());
        }

        if (result.isLogin2faRequired()) {
            return ResponseEntity
                    .status(HttpStatus.ACCEPTED)
                    .body(result.login2faRequiredResponse());
        }

        ResponseCookie refreshCookie = authCookieService.createRefreshTokenCookie(
                result.authenticatedLogin().refreshToken().token()
        );

        AuthenticatedResponse response = AuthenticatedResponse.of(
                result.authenticatedLogin().accessToken(),
                result.authenticatedLogin().user()
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(response);
    }

    @PostMapping("/login/2fa/verify")
    public ResponseEntity<AuthenticatedResponse> verifyLogin2fa(
            @Valid @RequestBody VerifyLogin2faRequest request,
            HttpServletRequest httpServletRequest
    ) {
        LoginClientMetadata metadata = new LoginClientMetadata(
                httpServletRequest.getRemoteAddr(),
                httpServletRequest.getHeader(HttpHeaders.USER_AGENT),
                request.deviceLabel()
        );

        AuthenticatedLoginResult result = login2faService.verify(request, metadata);

        ResponseCookie refreshCookie = authCookieService.createRefreshTokenCookie(
                result.refreshToken().token()
        );

        AuthenticatedResponse response = AuthenticatedResponse.of(
                result.accessToken(),
                result.user()
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest httpServletRequest
    ) {
        Optional<String> refreshToken = refreshCookieValueFrom(httpServletRequest);

        refreshToken.ifPresent(logoutService::logout);

        ResponseCookie clearCookie = authCookieService.clearRefreshTokenCookie();

        return ResponseEntity
                .noContent()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        logoutService.logoutAllDevices(authenticatedUser.userId());

        ResponseCookie clearCookie = authCookieService.clearRefreshTokenCookie();

        return ResponseEntity
                .noContent()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .build();
    }

    @PostMapping("/restore-account")
    public ResponseEntity<?> restoreAccount(
            @Valid @RequestBody RestoreAccountRequest request
    ) {
        RestoreAccountResult result = restoreAccountService.restoreAccount(request);

        if (result.isEmailVerificationRequired()) {
            return ResponseEntity.ok(result.emailVerificationRequiredResponse());
        }

        return ResponseEntity.ok(result.loginResponse());
    }

    @PostMapping("/resend-email-verification")
    public ResponseEntity<EmailVerificationRequiredResponse> resendEmailVerification(
            @Valid @RequestBody ResendEmailVerificationRequest request
    ) {
        EmailVerificationRequiredResponse response = resendEmailVerificationService.resend(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            HttpServletRequest httpServletRequest
    ) {
        Optional<String> refreshToken = refreshCookieValueFrom(httpServletRequest);

        if (refreshToken.isEmpty() || refreshToken.get().isBlank()) {
            return unauthorizedRefreshResponse();
        }

        Optional<AuthenticatedLoginResult> result = refreshTokenService.refresh(refreshToken.get());

        if (result.isEmpty()) {
            return unauthorizedRefreshResponse();
        }

        AuthenticatedLoginResult authenticatedLogin = result.get();

        ResponseCookie refreshCookie = authCookieService.createRefreshTokenCookie(
                authenticatedLogin.refreshToken().token()
        );

        AuthenticatedResponse response = AuthenticatedResponse.of(
                authenticatedLogin.accessToken(),
                authenticatedLogin.user()
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(response);
    }

    private Optional<String> refreshCookieValueFrom(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return Optional.empty();
        }

        return Arrays.stream(cookies)
                .filter(cookie -> authCookieService.refreshCookieName().equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }

    private ResponseEntity<Void> unauthorizedRefreshResponse() {
        ResponseCookie clearCookie = authCookieService.clearRefreshTokenCookie();

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        passwordResetService.requestPasswordReset(request);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        passwordResetService.resetPassword(request);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/email-change/confirm")
    public ResponseEntity<Void> confirmEmailChange(
            @Valid @RequestBody ConfirmEmailChangeRequest request
    ) {
        emailChangeService.confirmEmailChange(request.token());

        ResponseCookie clearCookie = authCookieService.clearRefreshTokenCookie();

        return ResponseEntity
                .noContent()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .build();
    }

}