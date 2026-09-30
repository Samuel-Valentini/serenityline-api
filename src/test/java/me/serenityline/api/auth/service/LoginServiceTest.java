package me.serenityline.api.auth.service;

import me.serenityline.api.auth.dto.LoginClientMetadata;
import me.serenityline.api.auth.dto.LoginRequest;
import me.serenityline.api.auth.exception.TooManyLoginAttemptsException;
import me.serenityline.api.auth.repository.AuthActionTokenRepository;
import me.serenityline.api.security.crypto.SensitiveHashService;
import me.serenityline.api.security.jwt.JwtTokenService;
import me.serenityline.api.security.token.SecureTokenGenerator;
import me.serenityline.api.security.token.TokenHashingService;
import me.serenityline.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class LoginServiceTest {

    @Test
    void rateLimitedLoginShouldNotLookUpUserOrCheckPassword() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        LoginAttemptService loginAttemptService = mock(LoginAttemptService.class);
        SensitiveHashService sensitiveHashService = mock(SensitiveHashService.class);

        LoginService loginService = new LoginService(
                userRepository,
                passwordEncoder,
                mock(SecureTokenGenerator.class),
                mock(TokenHashingService.class),
                mock(AuthActionTokenRepository.class),
                Duration.ofMinutes(15),
                mock(EmailVerificationResendChallengeService.class),
                mock(JwtTokenService.class),
                mock(RefreshTokenService.class),
                loginAttemptService,
                sensitiveHashService,
                mock(Login2faService.class)
        );

        String email = "samuel@example.com";
        String ip = "203.0.113.10";
        String emailHash = "email-hash";
        String ipHash = "ip-hash";

        when(sensitiveHashService.hash(email)).thenReturn(emailHash);
        when(sensitiveHashService.hash(ip)).thenReturn(ipHash);
        when(loginAttemptService.isOverLimit(emailHash, ipHash))
                .thenReturn(true);

        LoginRequest request = new LoginRequest(
                email,
                "VeryStrongPassword-2026-SerenityLine!",
                null
        );

        LoginClientMetadata metadata = new LoginClientMetadata(
                ip,
                "JUnit",
                null
        );

        assertThatThrownBy(() -> loginService.login(request, metadata))
                .isInstanceOf(TooManyLoginAttemptsException.class)
                .hasMessage("auth.login.tooManyAttempts");

        verify(loginAttemptService).isOverLimit(emailHash, ipHash);
        verify(loginAttemptService).recordRateLimited(emailHash, ipHash);
        verifyNoMoreInteractions(loginAttemptService);

        verifyNoInteractions(userRepository, passwordEncoder);
    }
}