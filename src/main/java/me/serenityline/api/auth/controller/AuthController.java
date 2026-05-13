package me.serenityline.api.auth.controller;

import jakarta.validation.Valid;
import me.serenityline.api.auth.dto.*;
import me.serenityline.api.auth.service.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegisterService registerService;
    private final EmailVerificationService emailVerificationService;
    private final LoginService loginService;
    private final RestoreAccountService restoreAccountService;
    private final ResendEmailVerificationService resendEmailVerificationService;

    public AuthController(RegisterService registerService, EmailVerificationService emailVerificationService, LoginService loginService, RestoreAccountService restoreAccountService, ResendEmailVerificationService resendEmailVerificationService) {
        this.registerService = registerService;
        this.emailVerificationService = emailVerificationService;
        this.loginService = loginService;
        this.restoreAccountService = restoreAccountService;
        this.resendEmailVerificationService = resendEmailVerificationService;
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
            @Valid @RequestBody LoginRequest request
    ) {
        LoginResult result = loginService.login(request);

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

        return ResponseEntity.ok(result.loginResponse());
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
}