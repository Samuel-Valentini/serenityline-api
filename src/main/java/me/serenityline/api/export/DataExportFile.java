package me.serenityline.api.export;

import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.Objects;

public record DataExportFile(
        String filename,
        StreamingResponseBody body
) {

    public DataExportFile {
        Objects.requireNonNull(filename, "filename");
        Objects.requireNonNull(body, "body");

        if (filename.isBlank()) {
            throw new IllegalArgumentException("export.filename.required");
        }
    }
}