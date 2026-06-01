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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("stress")
@Disabled("Manual stress test: remove @Disabled and run explicitly when validating data export scalability")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "serenityline.export.jdbc-fetch-size=500",
                "serenityline.export.max-rows-per-csv-file=10000",
                "serenityline.export.max-concurrent-exports=1"
        }
)
class DataExportStressIntegrationTest extends IntegrationTestSupport {

    private static final String DEFAULT_PASSWORD = "VeryStrongPassword-2026-SerenityLine!";
    private static final String DEFAULT_LOCALE = "en-US";
    private static final String DEFAULT_USER_AGENT = "JUnit Stress Browser";

    private static final int STRESS_CATEGORY_ROWS = 100_000;
    private static final int INSERT_BATCH_SIZE = 5_000;

    @LocalServerPort
    private int port;

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

    @Test
    void ownerExportShouldHandleOneHundredThousandCategoriesWithoutMockMvcResponseBuffering() throws Exception {
        AuthenticatedStressUser owner = registerVerifyAndLoginUser("export-stress-100k");

        long categoryRowsBeforeInsert = countCategories(owner.userGroupId());

        insertManyCategories(
                owner.userGroupId(),
                owner.userId(),
                STRESS_CATEGORY_ROWS
        );

        long expectedCategoryRows = categoryRowsBeforeInsert + STRESS_CATEGORY_ROWS;

        Path targetZip = Files.createTempFile("serenityline-export-stress-100k-", ".zip");

        try {
            long startedAt = System.nanoTime();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:%d/api/me/export".formatted(port)))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                    .header(HttpHeaders.ACCEPT_LANGUAGE, DEFAULT_LOCALE)
                    .GET()
                    .build();

            HttpResponse<Path> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofFile(targetZip));

            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            long zipSizeBytes = Files.size(targetZip);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue(HttpHeaders.CONTENT_TYPE))
                    .contains("application/zip");
            assertThat(zipSizeBytes).isGreaterThan(0);

            ZipStats stats = inspectZip(targetZip);

            assertThat(stats.entries()).contains("manifest.csv");
            assertThat(stats.entries()).contains("README.txt");
            assertThat(stats.entries()).contains("finance/categories/part-00001.csv");
            assertThat(stats.entries()).contains("finance/categories/part-00010.csv");

            assertThat(stats.rowsByDirectory().get("finance/categories"))
                    .isEqualTo(expectedCategoryRows);

            System.out.printf(
                    "Data export stress test completed: insertedCategories=%d, exportedCategories=%d, zipSizeBytes=%d, elapsedMillis=%d%n",
                    STRESS_CATEGORY_ROWS,
                    expectedCategoryRows,
                    zipSizeBytes,
                    elapsedMillis
            );
        } finally {
            Files.deleteIfExists(targetZip);
        }
    }

    private long countCategories(UUID userGroupId) {
        Long count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from categories
                        where user_group_id = ?
                        """,
                Long.class,
                userGroupId
        );

        return count == null ? 0L : count;
    }

    private void insertManyCategories(
            UUID userGroupId,
            UUID userId,
            int rowsToInsert
    ) {
        String sql = """
                insert into categories (
                    user_group_id,
                    category_created_by_user_id,
                    category_current_name
                )
                values (?, ?, ?)
                """;

        for (int offset = 0; offset < rowsToInsert; offset += INSERT_BATCH_SIZE) {
            int currentBatchSize = Math.min(
                    INSERT_BATCH_SIZE,
                    rowsToInsert - offset
            );

            int currentOffset = offset;

            jdbcTemplate.batchUpdate(
                    sql,
                    new BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(
                                PreparedStatement preparedStatement,
                                int index
                        ) throws SQLException {
                            int rowNumber = currentOffset + index;

                            preparedStatement.setObject(1, userGroupId);
                            preparedStatement.setObject(2, userId);
                            preparedStatement.setString(
                                    3,
                                    "Stress Category %06d".formatted(rowNumber)
                            );
                        }

                        @Override
                        public int getBatchSize() {
                            return currentBatchSize;
                        }
                    }
            );
        }
    }

    private ZipStats inspectZip(Path zipPath) throws Exception {
        Set<String> entries = new HashSet<>();
        Map<String, Long> rowsByDirectory = new HashMap<>();

        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;

            while ((entry = zipInputStream.getNextEntry()) != null) {
                entries.add(entry.getName());

                if ("manifest.csv".equals(entry.getName())) {
                    String manifest = readCurrentEntryAsString(zipInputStream);
                    rowsByDirectory.putAll(parseManifest(manifest));
                } else {
                    skipCurrentEntry(zipInputStream);
                }

                zipInputStream.closeEntry();
            }
        }

        return new ZipStats(
                entries,
                rowsByDirectory
        );
    }

    private String readCurrentEntryAsString(ZipInputStream zipInputStream) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        zipInputStream.transferTo(outputStream);
        return outputStream.toString(StandardCharsets.UTF_8);
    }

    private void skipCurrentEntry(ZipInputStream zipInputStream) throws Exception {
        zipInputStream.transferTo(OutputStream.nullOutputStream());
    }

    private Map<String, Long> parseManifest(String manifest) {
        Map<String, Long> rowsByDirectory = new HashMap<>();

        String[] lines = manifest.split("\\R");

        for (int index = 1; index < lines.length; index++) {
            String line = lines[index];

            if (line == null || line.isBlank()) {
                continue;
            }

            String normalizedLine = line.replace("\"", "");
            String[] parts = normalizedLine.split(",", -1);

            if (parts.length != 2) {
                continue;
            }

            rowsByDirectory.put(
                    parts[0],
                    Long.parseLong(parts[1])
            );
        }

        return rowsByDirectory;
    }

    private AuthenticatedStressUser registerVerifyAndLoginUser(String prefix) throws Exception {
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

        var loginResult = mockMvc.perform(post("/api/auth/login")
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
                                "Stress Device " + prefix
                        )))
                .andExpect(status().isOk())
                .andReturn();

        return new AuthenticatedStressUser(
                email,
                user.getUserId(),
                user.getUserGroup().getUserGroupId(),
                extractAccessToken(loginResult)
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

    private String extractAccessToken(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.accessToken"
        );
    }

    private String uniqueEmail(String prefix) {
        return "%s.%s@example.com".formatted(
                prefix,
                UUID.randomUUID().toString().replace("-", "")
        );
    }

    private record AuthenticatedStressUser(
            String email,
            UUID userId,
            UUID userGroupId,
            String accessToken
    ) {
    }

    private record ZipStats(
            Set<String> entries,
            Map<String, Long> rowsByDirectory
    ) {
    }
}