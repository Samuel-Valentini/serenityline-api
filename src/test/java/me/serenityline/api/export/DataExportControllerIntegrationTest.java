package me.serenityline.api.export;

import com.jayway.jsonpath.JsonPath;
import me.serenityline.api.email.outbox.entity.EmailOutbox;
import me.serenityline.api.email.outbox.entity.EmailOutboxStatus;
import me.serenityline.api.email.outbox.entity.EmailOutboxType;
import me.serenityline.api.email.outbox.repository.EmailOutboxRepository;
import me.serenityline.api.security.crypto.EmailOutboxEncryptionService;
import me.serenityline.api.security.crypto.EncryptedValue;
import me.serenityline.api.support.IntegrationTestSupport;
import me.serenityline.api.user.entity.User;
import me.serenityline.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

@SpringBootTest(properties = {
        "serenityline.export.jdbc-fetch-size=2",
        "serenityline.export.max-rows-per-csv-file=2",
        "serenityline.export.max-concurrent-exports=1"
})
class DataExportControllerIntegrationTest extends IntegrationTestSupport {

    private static final String DEFAULT_PASSWORD = "VeryStrongPassword-2026-SerenityLine!";
    private static final String DEFAULT_LOCALE = "en-US";
    private static final String DEFAULT_USER_AGENT = "JUnit Browser";
    private static final String REFRESH_COOKIE_NAME = "serenityline_refresh";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

    @Autowired
    private EmailOutboxEncryptionService emailOutboxEncryptionService;

    @Autowired
    private DataExportConcurrencyGuard concurrencyGuard;

    @Test
    void exportShouldRequireAccessToken() throws Exception {
        mockMvc.perform(get("/api/me/export")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void exportShouldReturnZipAttachmentForOwner() throws Exception {
        AuthenticatedExportUser owner = registerVerifyAndLoginUser("export-owner");

        MvcResult result = performExport(owner.accessToken());

        assertThat(result.getResponse().getContentType()).isEqualTo("application/zip");

        String contentDisposition = result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION);

        assertThat(contentDisposition).isNotBlank();
        assertThat(contentDisposition).contains("attachment");
        assertThat(contentDisposition).contains("serenityline-export-");
        assertThat(contentDisposition).contains(".zip");

        Map<String, String> zipEntries = unzipTextEntries(result);

        assertThat(allZipText(zipEntries))
                .doesNotContain(owner.refreshToken())
                .doesNotContain(owner.accessToken());

        assertThat(zipEntries)
                .containsKeys(
                        "README.txt",
                        "manifest.csv",
                        "user/me/part-00001.csv",
                        "user/user_sessions/part-00001.csv",
                        "user/auth_action_tokens_metadata/part-00001.csv",
                        "user/email_outbox_metadata/part-00001.csv",
                        "owner/user_group/part-00001.csv",
                        "owner/group_users/part-00001.csv",
                        "finance/categories/part-00001.csv",
                        "finance/financial_priorities/part-00001.csv"
                );

        assertThat(zipEntries.get("README.txt"))
                .contains("SerenityLine data export")
                .contains("Finance data included: yes");

        assertThat(zipEntries.get("user/me/part-00001.csv"))
                .contains(owner.userId().toString())
                .contains(owner.userGroupId().toString())
                .contains(owner.email());

        assertThat(zipEntries.get("owner/user_group/part-00001.csv"))
                .contains(owner.userGroupId().toString());

        assertThat(zipEntries.get("manifest.csv"))
                .contains("\"user/me\",\"1\"")
                .contains("\"finance/categories\",\"21\"");

        assertSensitiveColumnsAreExcluded(zipEntries);
    }

    @Test
    void exportShouldIncludeFinanceOnlyForOwnerAndSplitLargeCsvFiles() throws Exception {
        AuthenticatedExportUser owner = registerVerifyAndLoginUser("export-owner-split");

        MvcResult result = performExport(owner.accessToken());

        Map<String, String> zipEntries = unzipTextEntries(result);

        assertThat(zipEntries).containsKey("finance/categories/part-00001.csv");
        assertThat(zipEntries).containsKey("finance/categories/part-00011.csv");
        assertThat(zipEntries).doesNotContainKey("finance/categories/part-00012.csv");

        assertThat(zipEntries.get("finance/categories/part-00001.csv"))
                .startsWith("\"category_id\",\"user_group_id\",\"category_created_by_user_id\"");

        assertThat(zipEntries.get("finance/categories/part-00011.csv"))
                .startsWith("\"category_id\",\"user_group_id\",\"category_created_by_user_id\"");

        assertThat(zipEntries.get("manifest.csv"))
                .contains("\"finance/categories\",\"21\"");
    }

    @Test
    void exportShouldNotIncludeFinanceForCollaborator() throws Exception {
        AuthenticatedExportUser collaborator = registerVerifyAndLoginUser("export-collaborator");

        makeUserCollaborator(collaborator.userId());

        MvcResult result = performExport(collaborator.accessToken());

        Map<String, String> zipEntries = unzipTextEntries(result);

        assertThat(zipEntries.get("README.txt"))
                .contains("Finance data included: no");

        assertThat(zipEntries).containsKeys(
                "README.txt",
                "manifest.csv",
                "user/me/part-00001.csv",
                "user/user_sessions/part-00001.csv",
                "user/auth_action_tokens_metadata/part-00001.csv",
                "user/email_outbox_metadata/part-00001.csv"
        );

        assertThat(zipEntries.keySet())
                .noneMatch(path -> path.startsWith("finance/"));

        assertThat(zipEntries.keySet())
                .noneMatch(path -> path.startsWith("owner/"));

        assertThat(zipEntries.get("user/me/part-00001.csv"))
                .contains(collaborator.userId().toString())
                .contains(collaborator.email())
                .contains("\"COLLABORATOR\"");

        assertSensitiveColumnsAreExcluded(zipEntries);
    }

    @Test
    void exportShouldIgnoreMaliciousQueryParametersAndUseOnlyAuthenticatedUser() throws Exception {
        AuthenticatedExportUser owner = registerVerifyAndLoginUser("export-owner-malicious");
        AuthenticatedExportUser victim = registerVerifyAndLoginUser("export-victim-malicious");

        MvcResult result = performExportWithQueryParameters(
                owner.accessToken(),
                victim.userId(),
                victim.userGroupId()
        );

        Map<String, String> zipEntries = unzipTextEntries(result);

        assertThat(allZipText(zipEntries))
                .doesNotContain(victim.accessToken())
                .doesNotContain(victim.refreshToken());

        assertThat(zipEntries.get("user/me/part-00001.csv"))
                .contains(owner.userId().toString())
                .contains(owner.userGroupId().toString())
                .contains(owner.email())
                .doesNotContain(victim.userId().toString())
                .doesNotContain(victim.userGroupId().toString())
                .doesNotContain(victim.email());

        assertThat(allZipText(zipEntries))
                .doesNotContain(victim.email());
    }

    @Test
    void exportShouldNotExposeSensitiveAuthOrEncryptedEmailColumns() throws Exception {
        AuthenticatedExportUser owner = registerVerifyAndLoginUser("export-sensitive-columns");

        MvcResult result = performExport(owner.accessToken());

        Map<String, String> zipEntries = unzipTextEntries(result);

        assertSensitiveColumnsAreExcluded(zipEntries);

        assertThat(allZipText(zipEntries))
                .doesNotContain("user_password_hash")
                .doesNotContain("refresh_token_hash")
                .doesNotContain("auth_action_token_hash")
                .doesNotContain("subject_encrypted")
                .doesNotContain("subject_iv")
                .doesNotContain("subject_tag")
                .doesNotContain("body_text_encrypted")
                .doesNotContain("body_text_iv")
                .doesNotContain("body_text_tag")
                .doesNotContain("body_html_encrypted")
                .doesNotContain("body_html_iv")
                .doesNotContain("body_html_tag")
                .doesNotContain("email_hash")
                .doesNotContain("ip_address_hash");
    }

    @Test
    void exportShouldEscapeCsvValues() throws Exception {
        AuthenticatedExportUser owner = registerVerifyAndLoginUser("export-csv-escaping");

        jdbcTemplate.update(
                """
                        update users
                        set user_name = ?,
                            user_updated_at = now()
                        where user_id = ?
                        """,
                "CSV \"User\", Test",
                owner.userId()
        );

        MvcResult result = performExport(owner.accessToken());

        Map<String, String> zipEntries = unzipTextEntries(result);

        assertThat(zipEntries.get("user/me/part-00001.csv"))
                .contains("\"CSV \"\"User\"\", Test\"");
    }

    @Test
    void concurrencyGuardShouldRejectSecondExportForSameUser() {
        UUID userId = UUID.randomUUID();

        DataExportConcurrencyGuard.Permit firstPermit = concurrencyGuard.acquire(userId);

        try {
            assertThatThrownBy(() -> concurrencyGuard.acquire(userId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("export.alreadyRunning");
        } finally {
            firstPermit.close();
        }

        try (DataExportConcurrencyGuard.Permit ignored = concurrencyGuard.acquire(userId)) {
            assertThat(ignored).isNotNull();
        }
    }

    @Test
    void concurrencyGuardShouldRejectDifferentUserWhenGlobalLimitIsReached() {
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();

        DataExportConcurrencyGuard.Permit firstPermit = concurrencyGuard.acquire(firstUserId);

        try {
            assertThatThrownBy(() -> concurrencyGuard.acquire(secondUserId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("export.temporarilyBusy");
        } finally {
            firstPermit.close();
        }

        try (DataExportConcurrencyGuard.Permit ignored = concurrencyGuard.acquire(secondUserId)) {
            assertThat(ignored).isNotNull();
        }
    }

    @Test
    void dataExportPropertiesShouldRejectInvalidValues() {
        assertThatThrownBy(() -> new DataExportProperties(0, 2, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("export.jdbcFetchSize.invalid");

        assertThatThrownBy(() -> new DataExportProperties(2, 0, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("export.maxRowsPerCsvFile.invalid");

        assertThatThrownBy(() -> new DataExportProperties(2, 2, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("export.maxConcurrentExports.invalid");
    }

    @Test
    void exportShouldRejectTamperedAccessToken() throws Exception {
        AuthenticatedExportUser user = registerVerifyAndLoginUser("export-tampered-token");

        String tamperedAccessToken = user.accessToken().substring(0, user.accessToken().length() - 1) + "A";

        mockMvc.perform(get("/api/me/export")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedAccessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void directErrorPathShouldStillRequireAuthentication() throws Exception {
        mockMvc.perform(get("/error")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void exportShouldRejectSoftDeletedUserAccessToken() throws Exception {
        AuthenticatedExportUser user = registerVerifyAndLoginUser("export-soft-deleted-user");

        jdbcTemplate.update(
                """
                        update users
                        set user_deleted_at = now(),
                            user_updated_at = now()
                        where user_id = ?
                        """,
                user.userId()
        );

        mockMvc.perform(get("/api/me/export")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.accessToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void exportShouldReturnConflictWhenSameUserAlreadyHasRunningExport() throws Exception {
        AuthenticatedExportUser user = registerVerifyAndLoginUser("export-already-running");

        try (DataExportConcurrencyGuard.Permit ignored = concurrencyGuard.acquire(user.userId())) {
            mockMvc.perform(get("/api/me/export")
                            .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.accessToken()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("export.alreadyRunning"))
                    .andExpect(jsonPath("$.path").value("/api/me/export"));
        }
    }

    @Test
    void exportShouldReturnConflictWhenGlobalExportLimitIsReached() throws Exception {
        AuthenticatedExportUser user = registerVerifyAndLoginUser("export-global-limit");

        UUID anotherUserId = UUID.randomUUID();

        try (DataExportConcurrencyGuard.Permit ignored = concurrencyGuard.acquire(anotherUserId)) {
            mockMvc.perform(get("/api/me/export")
                            .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.accessToken()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("export.temporarilyBusy"))
                    .andExpect(jsonPath("$.path").value("/api/me/export"));
        }
    }

    private MvcResult performExport(String accessToken) throws Exception {
        MvcResult asyncResult = mockMvc.perform(get("/api/me/export")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(request().asyncStarted())
                .andReturn();

        return mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/zip"))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        allOf(
                                containsString("attachment"),
                                containsString("serenityline-export-"),
                                containsString(".zip")
                        )
                ))
                .andReturn();
    }

    private MvcResult performExportWithQueryParameters(
            String accessToken,
            UUID maliciousUserId,
            UUID maliciousUserGroupId
    ) throws Exception {
        MvcResult asyncResult = mockMvc.perform(get("/api/me/export")
                        .queryParam("userId", maliciousUserId.toString())
                        .queryParam("userGroupId", maliciousUserGroupId.toString())
                        .queryParam("role", "OWNER")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(request().asyncStarted())
                .andReturn();

        return mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andReturn();
    }

    private AuthenticatedExportUser registerVerifyAndLoginUser(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        String userName = "Test " + prefix;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                        .content("""
                                {
                                  "userName": "%s",
                                  "email": "%s",
                                  "password": "%s",
                                  "preferredLocale": "%s"
                                }
                                """.formatted(
                                userName,
                                email,
                                DEFAULT_PASSWORD,
                                DEFAULT_LOCALE
                        )))
                .andExpect(status().isCreated());

        EmailOutbox verificationEmail = latestPendingEmailOutbox(
                EmailOutboxType.EMAIL_VERIFICATION,
                email
        );

        String verificationToken = extractTokenFromBody(decryptTextBody(verificationEmail));

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                        .content("""
                                {
                                  "token": "%s"
                                }
                                """.formatted(verificationToken)))
                .andExpect(status().isOk());

        User user = userRepository.findByEmailAndUserDeletedAtIsNull(email)
                .orElseThrow();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                        .header(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s",
                                  "deviceLabel": "%s"
                                }
                                """.formatted(
                                email,
                                DEFAULT_PASSWORD,
                                "Device " + prefix
                        )))
                .andExpect(status().isOk())
                .andReturn();

        return new AuthenticatedExportUser(
                email,
                user.getUserId(),
                user.getUserGroup().getUserGroupId(),
                extractAccessToken(loginResult),
                extractRefreshCookie(loginResult)
        );
    }

    private void makeUserCollaborator(UUID userId) {
        jdbcTemplate.update(
                """
                        update users
                        set user_role = 'COLLABORATOR',
                            user_updated_at = now()
                        where user_id = ?
                        """,
                userId
        );
    }

    private String uniqueEmail(String prefix) {
        return "%s.%s@example.com".formatted(
                prefix,
                UUID.randomUUID().toString().replace("-", "")
        );
    }

    private EmailOutbox latestPendingEmailOutbox(
            EmailOutboxType emailType,
            String recipientEmail
    ) {
        return emailOutboxRepository.findAll()
                .stream()
                .filter(email -> email.getEmailType() == emailType)
                .filter(email -> recipientEmail.equals(email.getRecipientEmail()))
                .filter(email -> email.getEmailStatus() == EmailOutboxStatus.PENDING)
                .max(Comparator.comparing(EmailOutbox::getEmailCreatedAt))
                .orElseThrow();
    }

    private String decryptTextBody(EmailOutbox emailOutbox) {
        return emailOutboxEncryptionService.decrypt(
                new EncryptedValue(
                        emailOutbox.getBodyTextEncrypted(),
                        emailOutbox.getBodyTextIv(),
                        emailOutbox.getBodyTextTag()
                )
        );
    }

    private String extractTokenFromBody(String body) {
        String marker = "#token=";

        int tokenStart = body.indexOf(marker);

        assertThat(tokenStart).isGreaterThanOrEqualTo(0);

        tokenStart += marker.length();

        int tokenEnd = body.indexOf('\n', tokenStart);

        if (tokenEnd == -1) {
            tokenEnd = body.length();
        }

        return body.substring(tokenStart, tokenEnd).trim();
    }

    private String extractAccessToken(MvcResult result) throws Exception {
        return JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.accessToken"
        );
    }

    private String extractRefreshCookie(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertThat(setCookie).isNotBlank();

        String prefix = REFRESH_COOKIE_NAME + "=";

        for (String part : setCookie.split(";")) {
            String trimmedPart = part.trim();

            if (trimmedPart.startsWith(prefix)) {
                return trimmedPart.substring(prefix.length());
            }
        }

        throw new AssertionError("Refresh cookie not found");
    }

    private Map<String, String> unzipTextEntries(MvcResult result) throws Exception {
        byte[] content = result.getResponse().getContentAsByteArray();

        assertThat(content).isNotEmpty();

        Map<String, String> entries = new HashMap<>();

        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;

            while ((entry = zipInputStream.getNextEntry()) != null) {
                entries.put(
                        entry.getName(),
                        new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8)
                );

                zipInputStream.closeEntry();
            }
        }

        assertThat(entries).isNotEmpty();

        return entries;
    }

    private String allZipText(Map<String, String> zipEntries) {
        return String.join(
                "\n",
                zipEntries.values()
        );
    }

    private void assertSensitiveColumnsAreExcluded(Map<String, String> zipEntries) {
        zipEntries.entrySet()
                .stream()
                .filter(entry -> entry.getKey().endsWith(".csv"))
                .forEach(entry -> {
                    String csv = entry.getValue();

                    assertThat(csv)
                            .as("Sensitive columns should not be exported in %s", entry.getKey())
                            .doesNotContain("user_password_hash")
                            .doesNotContain("refresh_token_hash")
                            .doesNotContain("auth_action_token_hash")
                            .doesNotContain("subject_encrypted")
                            .doesNotContain("subject_iv")
                            .doesNotContain("subject_tag")
                            .doesNotContain("body_text_encrypted")
                            .doesNotContain("body_text_iv")
                            .doesNotContain("body_text_tag")
                            .doesNotContain("body_html_encrypted")
                            .doesNotContain("body_html_iv")
                            .doesNotContain("body_html_tag")
                            .doesNotContain("email_hash")
                            .doesNotContain("ip_address_hash");
                });
    }

    private record AuthenticatedExportUser(
            String email,
            UUID userId,
            UUID userGroupId,
            String accessToken,
            String refreshToken
    ) {
    }
}