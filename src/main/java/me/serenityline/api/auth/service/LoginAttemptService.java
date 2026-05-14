package me.serenityline.api.auth.service;

import jakarta.annotation.PostConstruct;
import me.serenityline.api.auth.entity.AuthLoginAttempt;
import me.serenityline.api.auth.entity.AuthLoginFailureReason;
import me.serenityline.api.auth.repository.AuthLoginAttemptRepository;
import me.serenityline.api.user.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class LoginAttemptService {

    private final AuthLoginAttemptRepository authLoginAttemptRepository;

    private final Duration loginAttemptWindow;
    private final int maxFailedByEmailIp;
    private final int maxFailedByEmail;
    private final int maxFailedByIp;

    public LoginAttemptService(
            AuthLoginAttemptRepository authLoginAttemptRepository,
            @Value("${serenityline.auth.login-attempt.window}") Duration loginAttemptWindow,
            @Value("${serenityline.auth.login-attempt.max-failed-by-email-ip}") int maxFailedByEmailIp,
            @Value("${serenityline.auth.login-attempt.max-failed-by-email}") int maxFailedByEmail,
            @Value("${serenityline.auth.login-attempt.max-failed-by-ip}") int maxFailedByIp
    ) {
        this.authLoginAttemptRepository = authLoginAttemptRepository;
        this.loginAttemptWindow = loginAttemptWindow;
        this.maxFailedByEmailIp = maxFailedByEmailIp;
        this.maxFailedByEmail = maxFailedByEmail;
        this.maxFailedByIp = maxFailedByIp;
    }

    @PostConstruct
    void validateConfig() {
        if (loginAttemptWindow == null || loginAttemptWindow.isZero() || loginAttemptWindow.isNegative()) {
            throw new IllegalStateException("auth.loginAttempt.window.invalid");
        }

        if (maxFailedByEmailIp < 1) {
            throw new IllegalStateException("auth.loginAttempt.maxFailedByEmailIp.invalid");
        }

        if (maxFailedByEmail < 1) {
            throw new IllegalStateException("auth.loginAttempt.maxFailedByEmail.invalid");
        }

        if (maxFailedByIp < 1) {
            throw new IllegalStateException("auth.loginAttempt.maxFailedByIp.invalid");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCredentialAccepted(User user, String emailHash, String ipAddressHash) {
        authLoginAttemptRepository.save(
                AuthLoginAttempt.successful(user, emailHash, ipAddressHash)
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordInvalidCredentials(User user, String emailHash, String ipAddressHash) {
        authLoginAttemptRepository.save(
                AuthLoginAttempt.failed(
                        user,
                        emailHash,
                        ipAddressHash,
                        AuthLoginFailureReason.INVALID_CREDENTIALS
                )
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRateLimited(String emailHash, String ipAddressHash) {
        authLoginAttemptRepository.save(
                AuthLoginAttempt.failed(
                        null,
                        emailHash,
                        ipAddressHash,
                        AuthLoginFailureReason.RATE_LIMITED
                )
        );
    }

    @Transactional(readOnly = true)
    public boolean isOverLimit(String emailHash, String ipAddressHash) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime windowStart = now.minus(loginAttemptWindow);

        OffsetDateTime emailIpCutoff = effectiveCutoff(
                windowStart,
                authLoginAttemptRepository.findLastSuccessfulAtByEmailHashAndIpAddressHash(
                        emailHash,
                        ipAddressHash
                )
        );

        OffsetDateTime emailCutoff = effectiveCutoff(
                windowStart,
                authLoginAttemptRepository.findLastSuccessfulAtByEmailHash(emailHash)
        );

        long failedByEmailIp = authLoginAttemptRepository.countFailuresByEmailHashAndIpAddressHashAfter(
                emailHash,
                ipAddressHash,
                AuthLoginFailureReason.INVALID_CREDENTIALS,
                emailIpCutoff
        );

        long failedByEmail = authLoginAttemptRepository.countFailuresByEmailHashAfter(
                emailHash,
                AuthLoginFailureReason.INVALID_CREDENTIALS,
                emailCutoff
        );

        long failedByIp = authLoginAttemptRepository.countFailuresByIpAddressHashAfter(
                ipAddressHash,
                AuthLoginFailureReason.INVALID_CREDENTIALS,
                windowStart
        );

        return failedByEmailIp >= maxFailedByEmailIp
                || failedByEmail >= maxFailedByEmail
                || failedByIp >= maxFailedByIp;
    }

    private OffsetDateTime effectiveCutoff(
            OffsetDateTime windowStart,
            Optional<OffsetDateTime> lastSuccessfulAt
    ) {
        return lastSuccessfulAt
                .filter(successAt -> successAt.isAfter(windowStart))
                .orElse(windowStart);
    }
}