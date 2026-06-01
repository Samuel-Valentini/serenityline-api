package me.serenityline.api.export;

import java.util.Objects;

record DataExportTable(
        String directory,
        String sql,
        DataExportStatementBinder binder
) {

    DataExportTable {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(binder, "binder");

        if (directory.isBlank()) {
            throw new IllegalArgumentException("export.table.directory.required");
        }

        if (sql.isBlank()) {
            throw new IllegalArgumentException("export.table.sql.required");
        }

        if (directory.startsWith("/") || directory.contains("..")) {
            throw new IllegalArgumentException("export.table.directory.invalid");
        }
    }
}