package me.serenityline.api.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.serenityline.api.user.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-13T10:00:00Z");
    private static final String SECRET = "test-jwt-secret-string-of-32-bytes-minimum";

    @Test
    void createAccessTokenShouldGenerateValidToken() {
        JwtTokenService jwtTokenService = jwtTokenServiceAt(NOW);
        User user = verifiedUser();

        JwtAccessToken accessToken = jwtTokenService.createAccessToken(user);

        assertThat(accessToken.token()).isNotBlank();
        assertThat(accessToken.expiresAt()).isEqualTo(NOW.atOffset(ZoneOffset.UTC).plusMinutes(15));
        ;

        Optional<JwtTokenClaims> claims = jwtTokenService.parseAndValidate(accessToken.token());

        assertThat(claims).isPresent();
        assertThat(claims.get().userId()).isEqualTo(user.getUserId());
        assertThat(claims.get().tokenVersion()).isEqualTo(user.getTokenVersion());
        assertThat(claims.get().expiresAt()).isEqualTo(accessToken.expiresAt());
    }

    @Test
    void parseAndValidateShouldRejectTamperedToken() {
        JwtTokenService jwtTokenService = jwtTokenServiceAt(NOW);
        User user = verifiedUser();

        JwtAccessToken accessToken = jwtTokenService.createAccessToken(user);

        String tamperedToken = accessToken.token().substring(0, accessToken.token().length() - 1) + "x";

        assertThat(jwtTokenService.parseAndValidate(tamperedToken)).isEmpty();
    }

    @Test
    void parseAndValidateShouldRejectExpiredToken() {
        JwtTokenService issuingService = jwtTokenServiceAt(NOW);
        JwtAccessToken accessToken = issuingService.createAccessToken(verifiedUser());

        JwtTokenService validatingService = jwtTokenServiceAt(NOW.plus(Duration.ofMinutes(16)));

        assertThat(validatingService.parseAndValidate(accessToken.token())).isEmpty();
    }

    @Test
    void parseAndValidateShouldRejectBlankToken() {
        JwtTokenService jwtTokenService = jwtTokenServiceAt(NOW);

        assertThat(jwtTokenService.parseAndValidate(" ")).isEmpty();
    }

    @Test
    void jwtPropertiesShouldRejectShortSecret() {
        assertThatThrownBy(() -> new JwtProperties(
                "serenityline-api",
                Duration.ofMinutes(15),
                "short"
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("security.jwt.secret.tooShort");
    }

    @Test
    void parseAndValidateShouldRejectMalformedToken() {
        JwtTokenService jwtTokenService = jwtTokenServiceAt(NOW);

        assertThat(jwtTokenService.parseAndValidate("not-a-jwt")).isEmpty();
        assertThat(jwtTokenService.parseAndValidate("a.b")).isEmpty();
        assertThat(jwtTokenService.parseAndValidate("a.b.c.d")).isEmpty();
        assertThat(jwtTokenService.parseAndValidate("..")).isEmpty();
    }

    @Test
    void parseAndValidateShouldRejectTokenSignedWithDifferentSecret() {
        JwtTokenService issuingService = jwtTokenServiceAt(NOW);

        JwtTokenService validatingService = new JwtTokenService(
                new JwtProperties(
                        "serenityline-api",
                        Duration.ofMinutes(15),
                        "different-jwt-secret-string-of-32-bytes-minimum"
                ),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new ObjectMapper()
        );

        JwtAccessToken accessToken = issuingService.createAccessToken(verifiedUser());

        assertThat(validatingService.parseAndValidate(accessToken.token())).isEmpty();
    }

    @Test
    void parseAndValidateShouldRejectWrongIssuer() {
        JwtTokenService issuingService = jwtTokenServiceAt(NOW);

        JwtTokenService validatingService = new JwtTokenService(
                new JwtProperties(
                        "another-issuer",
                        Duration.ofMinutes(15),
                        SECRET
                ),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new ObjectMapper()
        );

        JwtAccessToken accessToken = issuingService.createAccessToken(verifiedUser());

        assertThat(validatingService.parseAndValidate(accessToken.token())).isEmpty();
    }

    @Test
    void createAccessTokenShouldRejectNullUser() {
        JwtTokenService jwtTokenService = jwtTokenServiceAt(NOW);

        assertThatThrownBy(() -> jwtTokenService.createAccessToken(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("auth.jwt.user.required");
    }

    @Test
    void createAccessTokenShouldRejectUserWithoutId() {
        JwtTokenService jwtTokenService = jwtTokenServiceAt(NOW);

        User user = verifiedUser();
        ReflectionTestUtils.setField(user, "userId", null);

        assertThatThrownBy(() -> jwtTokenService.createAccessToken(user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("auth.jwt.userId.required");
    }

    @Test
    void jwtPropertiesShouldRejectBlankIssuer() {
        assertThatThrownBy(() -> new JwtProperties(
                " ",
                Duration.ofMinutes(15),
                SECRET
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("security.jwt.issuer.required");
    }

    @Test
    void jwtPropertiesShouldRejectInvalidAccessTokenTtl() {
        assertThatThrownBy(() -> new JwtProperties(
                "serenityline-api",
                Duration.ZERO,
                SECRET
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("security.jwt.accessTokenTtl.invalid");
    }

    private JwtTokenService jwtTokenServiceAt(Instant instant) {
        return new JwtTokenService(
                new JwtProperties(
                        "serenityline-api",
                        Duration.ofMinutes(15),
                        SECRET
                ),
                Clock.fixed(instant, ZoneOffset.UTC),
                new ObjectMapper()
        );
    }

    private User verifiedUser() {
        UserGroup userGroup = new UserGroup("Samuel's group");
        ReflectionTestUtils.setField(userGroup, "userGroupId", UUID.randomUUID());

        User user = new User(
                "Samuel",
                "samuel@example.com",
                userGroup,
                UserRole.OWNER,
                UserPlatformRole.USER,
                "en-US",
                PreferredTheme.DEFAULT,
                false,
                false,
                "$2a$12$fakehashfakehashfakehashfakehashfakehashfakehashfakehash",
                true,
                0L
        );

        ReflectionTestUtils.setField(user, "userId", UUID.randomUUID());

        return user;
    }
}