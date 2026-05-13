package me.serenityline.api.auth.service;

import me.serenityline.api.auth.dto.EmailVerificationRequiredResponse;
import me.serenityline.api.auth.dto.ResendEmailVerificationRequest;
import me.serenityline.api.auth.entity.AuthActionToken;
import me.serenityline.api.auth.entity.AuthActionTokenType;
import me.serenityline.api.auth.repository.AuthActionTokenRepository;
import me.serenityline.api.security.token.TokenHashingService;
import me.serenityline.api.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;

@Service
public class ResendEmailVerificationService {

    private final AuthActionTokenRepository authActionTokenRepository;
    private final TokenHashingService tokenHashingService;
    private final EmailVerificationService emailVerificationService;
    private final EmailVerificationResendChallengeService emailVerificationResendChallengeService;

    public ResendEmailVerificationService(
            AuthActionTokenRepository authActionTokenRepository,
            TokenHashingService tokenHashingService,
            EmailVerificationService emailVerificationService,
            EmailVerificationResendChallengeService emailVerificationResendChallengeService
    ) {
        this.authActionTokenRepository = Objects.requireNonNull(authActionTokenRepository, "authActionTokenRepository");
        this.tokenHashingService = Objects.requireNonNull(tokenHashingService, "tokenHashingService");
        this.emailVerificationService = Objects.requireNonNull(emailVerificationService, "emailVerificationService");
        this.emailVerificationResendChallengeService = Objects.requireNonNull(emailVerificationResendChallengeService, "emailVerificationResendChallengeService");
    }

    private static IllegalArgumentException invalidResendToken() {
        return new IllegalArgumentException("auth.emailVerificationResend.invalidOrExpired");
    }

    @Transactional
    public EmailVerificationRequiredResponse resend(ResendEmailVerificationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("auth.emailVerificationResend.request.required");
        }

        String resendToken = request.emailVerificationResendToken();

        if (resendToken == null || resendToken.isBlank()) {
            throw invalidResendToken();
        }

        String tokenHash = tokenHashingService.hash(resendToken.trim());

        AuthActionToken actionToken = authActionTokenRepository
                .findByAuthActionTokenHashForUpdate(tokenHash)
                .orElseThrow(ResendEmailVerificationService::invalidResendToken);

        if (actionToken.getAuthActionTokenType() != AuthActionTokenType.EMAIL_VERIFICATION_RESEND) {
            throw invalidResendToken();
        }

        if (!actionToken.isPending()) {
            throw invalidResendToken();
        }

        User user = actionToken.getUser();

        if (user.isPendingDeletion()) {
            throw invalidResendToken();
        }

        if (user.isUserIsEnabled()) {
            throw new IllegalStateException("auth.emailVerification.userAlreadyVerified");
        }

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime availableAt = emailVerificationResendChallengeService
                .calculateResendAvailableAt(user, now);

        if (availableAt.isAfter(now)) {
            throw new IllegalStateException("auth.emailVerificationResend.tooSoon");
        }

        try {
            actionToken.markUsed(AuthActionTokenType.EMAIL_VERIFICATION_RESEND);
        } catch (IllegalStateException ex) {
            throw invalidResendToken();
        }

        emailVerificationService.createEmailVerification(user);

        return emailVerificationResendChallengeService.createChallenge(user);
    }
}