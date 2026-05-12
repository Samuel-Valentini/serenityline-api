package me.serenityline.api.auth.controller;

import me.serenityline.api.support.IntegrationTestSupport;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.entity.UserGroup;
import me.serenityline.api.user.entity.UserRole;
import me.serenityline.api.user.repository.UserGroupRepository;
import me.serenityline.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserGroupRepository userGroupRepository;

    @Test
    void registerShouldCreateDisabledOwnerUserAndGroup() throws Exception {
        String email = uniqueEmail("valid");

        mockMvc.perform(post("/api/auth/register")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "it-IT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userName": "Samuel",
                                  "email": "%s",
                                  "password": "TrenoMareLuna2026!",
                                  "preferredLocale": "it-IT"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").isString())
                .andExpect(jsonPath("$.userName").value("Samuel"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.userGroupId").isString())
                .andExpect(jsonPath("$.userGroupName").value("Gruppo di Samuel"))
                .andExpect(jsonPath("$.userRole").value("OWNER"))
                .andExpect(jsonPath("$.preferredLocale").value("it-IT"))
                .andExpect(jsonPath("$.wantsInvoice").value(false))
                .andExpect(jsonPath("$.emailVerificationRequired").value(true))
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString("userPasswordHash"))));

        User savedUser = userRepository.findByEmailAndUserDeletedAtIsNull(email)
                .orElseThrow();

        assertThat(savedUser.getEmail()).isEqualTo(email);
        assertThat(savedUser.getUserName()).isEqualTo("Samuel");
        assertThat(savedUser.getUserRole()).isEqualTo(UserRole.OWNER);
        assertThat(savedUser.isUserIsEnabled()).isFalse();
        assertThat(savedUser.isWantsInvoice()).isFalse();
        assertThat(savedUser.getUserDeletedAt()).isNull();

        List<UserGroup> groups = userGroupRepository.findAll();

        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().getUserGroupName()).isEqualTo("Gruppo di Samuel");
    }

    @Test
    void registerShouldReturnConflictWhenEmailAlreadyExists() throws Exception {
        String email = uniqueEmail("duplicate");

        registerValidUser(email);

        mockMvc.perform(post("/api/auth/register")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "it-IT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userName": "Samuel Duplicate",
                                  "email": "%s",
                                  "password": "TrenoMareLuna2026!",
                                  "preferredLocale": "it-IT"
                                }
                                """.formatted(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("auth.register.emailAlreadyExists"))
                .andExpect(jsonPath("$.message").value("Esiste già un account con questa email."))
                .andExpect(jsonPath("$.path").value("/api/auth/register"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void registerShouldReturnEnglishFallbackMessageWhenAcceptLanguageIsEnglish() throws Exception {
        String email = uniqueEmail("duplicate-en");

        registerValidUser(email);

        mockMvc.perform(post("/api/auth/register")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userName": "Samuel Duplicate",
                                  "email": "%s",
                                  "password": "TrenoMareLuna2026!",
                                  "preferredLocale": "it-IT"
                                }
                                """.formatted(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("auth.register.emailAlreadyExists"))
                .andExpect(jsonPath("$.message").value("An account with this email already exists."));
    }

    @Test
    void registerShouldRejectWeakPassword() throws Exception {
        String email = uniqueEmail("weak");

        mockMvc.perform(post("/api/auth/register")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "it-IT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userName": "Weak Password User",
                                  "email": "%s",
                                  "password": "password12345",
                                  "preferredLocale": "it-IT"
                                }
                                """.formatted(email)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.password.tooWeak"))
                .andExpect(jsonPath("$.message").value("La password scelta è troppo debole."))
                .andExpect(jsonPath("$.path").value("/api/auth/register"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        assertThat(userRepository.findByEmailAndUserDeletedAtIsNull(email)).isEmpty();
    }

    @Test
    void registerShouldReturnDtoValidationErrors() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "it-IT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userName": "",
                                  "email": "not-an-email",
                                  "password": "short",
                                  "preferredLocale": "fr-FR"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.message").value("Validazione fallita."))
                .andExpect(jsonPath("$.path").value("/api/auth/register"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors[*].code", hasItems(
                        "user.userName.required",
                        "user.email.invalid",
                        "auth.password.invalidLength",
                        "user.preferredLocale.invalid"
                )))
                .andExpect(content().string(not(containsString("exception"))))
                .andExpect(content().string(not(containsString("stacktrace"))))
                .andExpect(content().string(not(containsString("hibernate"))))
                .andExpect(content().string(not(containsString("sql"))));
    }

    @Test
    void registerShouldApplyDefaultsWhenOptionalFieldsAreMissing() throws Exception {
        String email = uniqueEmail("defaults");

        mockMvc.perform(post("/api/auth/register")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "it-IT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userName": "Default User",
                                  "email": "%s",
                                  "password": "MontagnaFiumeStella2026!"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.preferredLocale").value("it-IT"))
                .andExpect(jsonPath("$.wantsInvoice").value(false))
                .andExpect(jsonPath("$.userRole").value("OWNER"))
                .andExpect(jsonPath("$.emailVerificationRequired").value(true));
    }

    @Test
    void registerShouldUseEnglishGroupNameWhenPreferredLocaleIsEnglish() throws Exception {
        String email = uniqueEmail("english");

        mockMvc.perform(post("/api/auth/register")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "it-IT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userName": "John",
                                  "email": "%s",
                                  "password": "RiverMountainCloud2026!",
                                  "preferredLocale": "en-US"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.preferredLocale").value("en-US"))
                .andExpect(jsonPath("$.userGroupName").value("John's group"));
    }

    @Test
    void registerShouldNormalizeEmailToLowercase() throws Exception {
        String uniquePart = UUID.randomUUID().toString().replace("-", "");
        String rawEmail = "UPPER.%s@EXAMPLE.COM".formatted(uniquePart);
        String expectedEmail = "upper.%s@example.com".formatted(uniquePart);

        mockMvc.perform(post("/api/auth/register")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "it-IT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userName": "Upper Email",
                                  "email": "%s",
                                  "password": "SoleVentoMare2026!",
                                  "preferredLocale": "it-IT"
                                }
                                """.formatted(rawEmail)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(expectedEmail));

        assertThat(userRepository.findByEmailAndUserDeletedAtIsNull(expectedEmail)).isPresent();
    }

    @Test
    void corsPreflightShouldAllowConfiguredDevOrigin() throws Exception {
        mockMvc.perform(options("/api/auth/register")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")));
    }

    private void registerValidUser(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "it-IT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userName": "Samuel",
                                  "email": "%s",
                                  "password": "TrenoMareLuna2026!",
                                  "preferredLocale": "it-IT"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated());
    }

    private String uniqueEmail(String prefix) {
        return "%s.%s@example.com".formatted(
                prefix,
                UUID.randomUUID().toString().replace("-", "")
        );
    }
}