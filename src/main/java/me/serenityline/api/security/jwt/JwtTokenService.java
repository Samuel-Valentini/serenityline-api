package me.serenityline.api.security.jwt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.serenityline.api.user.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class JwtTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String JWT_ALGORITHM = "HS256";

    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder()
            .withoutPadding();

    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private final JwtProperties jwtProperties;
    private final SecretKeySpec secretKeySpec;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    @Autowired
    public JwtTokenService(
            JwtProperties jwtProperties,
            Clock clock
    ) {
        this(
                jwtProperties,
                clock,
                new ObjectMapper()
        );
    }

    public JwtTokenService(
            JwtProperties jwtProperties,
            Clock clock,
            ObjectMapper objectMapper
    ) {
        this.jwtProperties = Objects.requireNonNull(jwtProperties, "jwtProperties");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");

        this.secretKeySpec = new SecretKeySpec(
                jwtProperties.secret().getBytes(StandardCharsets.UTF_8),
                HMAC_ALGORITHM
        );
    }

    private static String requiredText(
            Map<String, Object> payload,
            String claimName
    ) {
        Object value = payload.get(claimName);

        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("security.jwt.claim.required");
        }

        return text;
    }

    private static Long requiredLong(
            Map<String, Object> payload,
            String claimName
    ) {
        Object value = payload.get(claimName);

        if (value instanceof Number number) {
            return number.longValue();
        }

        throw new IllegalArgumentException("security.jwt.claim.required");
    }

    private static OffsetDateTime epochSecondToOffsetDateTime(Long epochSecond) {
        return OffsetDateTime.ofInstant(
                Instant.ofEpochSecond(epochSecond),
                ZoneOffset.UTC
        );
    }

    public JwtAccessToken createAccessToken(User user) {
        if (user == null) {
            throw new IllegalArgumentException("auth.jwt.user.required");
        }

        if (user.getUserId() == null) {
            throw new IllegalArgumentException("auth.jwt.userId.required");
        }

        if (user.getTokenVersion() == null) {
            throw new IllegalArgumentException("auth.jwt.tokenVersion.required");
        }

        OffsetDateTime issuedAt = now();
        OffsetDateTime expiresAt = issuedAt.plus(jwtProperties.accessTokenTtl());

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", JWT_ALGORITHM);
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", jwtProperties.issuer());
        payload.put("sub", user.getUserId().toString());
        payload.put("tokenVersion", user.getTokenVersion());
        payload.put("iat", issuedAt.toEpochSecond());
        payload.put("exp", expiresAt.toEpochSecond());

        String encodedHeader = encodeJson(header);
        String encodedPayload = encodeJson(payload);

        String signingInput = encodedHeader + "." + encodedPayload;
        String signature = BASE64_URL_ENCODER.encodeToString(sign(signingInput));

        return new JwtAccessToken(
                signingInput + "." + signature,
                expiresAt
        );
    }

    public Optional<JwtTokenClaims> parseAndValidate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        String[] parts = token.trim().split("\\.", -1);

        if (parts.length != 3
                || parts[0].isBlank()
                || parts[1].isBlank()
                || parts[2].isBlank()) {
            return Optional.empty();
        }

        String signingInput = parts[0] + "." + parts[1];

        if (!isSignatureValid(signingInput, parts[2])) {
            return Optional.empty();
        }

        Map<String, Object> header = decodeJsonMap(parts[0])
                .orElse(null);

        if (header == null) {
            return Optional.empty();
        }

        if (!JWT_ALGORITHM.equals(header.get("alg"))) {
            return Optional.empty();
        }

        Object type = header.get("typ");

        if (type != null && !"JWT".equals(type)) {
            return Optional.empty();
        }

        Map<String, Object> payload = decodeJsonMap(parts[1])
                .orElse(null);

        if (payload == null) {
            return Optional.empty();
        }

        try {
            String issuer = requiredText(payload, "iss");

            if (!jwtProperties.issuer().equals(issuer)) {
                return Optional.empty();
            }

            UUID userId = UUID.fromString(requiredText(payload, "sub"));
            Long tokenVersion = requiredLong(payload, "tokenVersion");

            OffsetDateTime issuedAt = epochSecondToOffsetDateTime(
                    requiredLong(payload, "iat")
            );

            OffsetDateTime expiresAt = epochSecondToOffsetDateTime(
                    requiredLong(payload, "exp")
            );

            if (!expiresAt.isAfter(now())) {
                return Optional.empty();
            }

            if (!expiresAt.isAfter(issuedAt)) {
                return Optional.empty();
            }

            return Optional.of(new JwtTokenClaims(
                    userId,
                    tokenVersion,
                    issuedAt,
                    expiresAt
            ));

        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private OffsetDateTime now() {
        Instant instant = Instant.now(clock)
                .truncatedTo(ChronoUnit.SECONDS);

        return OffsetDateTime.ofInstant(
                instant,
                ZoneOffset.UTC
        );
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(value);

            return BASE64_URL_ENCODER.encodeToString(json);

        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("security.jwt.serialization.failed", ex);
        }
    }

    private Optional<Map<String, Object>> decodeJsonMap(String encodedJson) {
        try {
            byte[] json = BASE64_URL_DECODER.decode(encodedJson);

            Map<String, Object> result = objectMapper.readValue(
                    json,
                    new TypeReference<>() {
                    }
            );

            return Optional.of(result);

        } catch (IllegalArgumentException | IOException ex) {
            return Optional.empty();
        }
    }

    private byte[] sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(secretKeySpec);

            return mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII));

        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("security.jwt.signing.failed", ex);
        }
    }

    private boolean isSignatureValid(String signingInput, String encodedSignature) {
        try {
            byte[] expectedSignature = sign(signingInput);
            byte[] actualSignature = BASE64_URL_DECODER.decode(encodedSignature);

            return MessageDigest.isEqual(
                    expectedSignature,
                    actualSignature
            );

        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}