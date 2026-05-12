package me.serenityline.api.auth.service;

import me.serenityline.api.auth.dto.LoginResponse;
import me.serenityline.api.auth.dto.RestoreAccountRequest;
import me.serenityline.api.auth.dto.RestoreAccountResult;
import me.serenityline.api.auth.dto.RestoreAccountVerificationRequiredResponse;
import me.serenityline.api.auth.entity.AuthActionToken;
import me.serenityline.api.auth.entity.AuthActionTokenType;
import me.serenityline.api.auth.repository.AuthActionTokenRepository;
import me.serenityline.api.security.token.TokenHashingService;
import me.serenityline.api.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class RestoreAccountService {

    private final AuthActionTokenRepository authActionTokenRepository;
    private final TokenHashingService tokenHashingService;
    private final EmailVerificationService emailVerificationService;

    public RestoreAccountService(
            AuthActionTokenRepository authActionTokenRepository,
            TokenHashingService tokenHashingService,
            EmailVerificationService emailVerificationService
    ) {
        this.authActionTokenRepository = Objects.requireNonNull(authActionTokenRepository, "authActionTokenRepository");
        this.tokenHashingService = Objects.requireNonNull(tokenHashingService, "tokenHashingService");
        this.emailVerificationService = Objects.requireNonNull(emailVerificationService, "emailVerificationService");
    }

    private static IllegalArgumentException invalidRestoreToken() {
        return new IllegalArgumentException("auth.restoreAccount.invalidOrExpired");
    }

    @Transactional
    public RestoreAccountResult restoreAccount(RestoreAccountRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("auth.restoreAccount.request.required");
        }

        String restoreToken = request.restoreToken();

        if (restoreToken == null || restoreToken.isBlank()) {
            throw invalidRestoreToken();
        }

        String tokenHash = tokenHashingService.hash(restoreToken.trim());

        AuthActionToken actionToken = authActionTokenRepository
                .findByAuthActionTokenHashForUpdate(tokenHash)
                .orElseThrow(RestoreAccountService::invalidRestoreToken);

        if (actionToken.getAuthActionTokenType() != AuthActionTokenType.RESTORE_ACCOUNT) {
            throw invalidRestoreToken();
        }

        if (!actionToken.isPending()) {
            throw invalidRestoreToken();
        }

        User user = actionToken.getUser();

        if (!user.isPendingDeletion()) {
            throw invalidRestoreToken();
        }

        if (user.isHardDeletionDue()) {
            throw invalidRestoreToken();
        }

        try {
            actionToken.markUsed(AuthActionTokenType.RESTORE_ACCOUNT);
            user.restoreFromSoftDelete();

            if (user.isUserIsEnabled()) {
                user.markSuccessfulLogin();

                return RestoreAccountResult.authenticated(
                        LoginResponse.from(user)
                );
            }

            emailVerificationService.createEmailVerification(user);

            return RestoreAccountResult.verificationRequired(
                    RestoreAccountVerificationRequiredResponse.from(user)
            );

        } catch (IllegalStateException ex) {
            throw invalidRestoreToken();
        }
    }
}