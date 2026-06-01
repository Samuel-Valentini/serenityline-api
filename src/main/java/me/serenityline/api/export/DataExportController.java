package me.serenityline.api.export;

import me.serenityline.api.security.auth.AuthenticatedUser;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

@RestController
public class DataExportController {

    private static final MediaType ZIP_MEDIA_TYPE =
            MediaType.parseMediaType("application/zip");

    private final DataExportService dataExportService;

    public DataExportController(DataExportService dataExportService) {
        this.dataExportService = Objects.requireNonNull(dataExportService, "dataExportService");
    }

    @GetMapping(
            value = "/api/me/export",
            produces = "application/zip"
    )
    public ResponseEntity<StreamingResponseBody> exportCurrentUserData(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        DataExportFile exportFile = dataExportService.prepareExport(authenticatedUser);

        String contentDisposition = ContentDisposition
                .attachment()
                .filename(exportFile.filename(), StandardCharsets.UTF_8)
                .build()
                .toString();

        return ResponseEntity.ok()
                .contentType(ZIP_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .body(exportFile.body());
    }
}