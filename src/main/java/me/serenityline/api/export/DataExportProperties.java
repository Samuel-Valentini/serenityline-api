package me.serenityline.api.export;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "serenityline.export")
public record DataExportProperties(
        int jdbcFetchSize,
        int maxRowsPerCsvFile,
        int maxConcurrentExports
) {

    private static final int MIN_JDBC_FETCH_SIZE = 1;
    private static final int MAX_JDBC_FETCH_SIZE = 10_000;

    private static final int MIN_ROWS_PER_CSV_FILE = 1;
    private static final int MAX_ROWS_PER_CSV_FILE = 1_000_000;

    private static final int MIN_CONCURRENT_EXPORTS = 1;
    private static final int MAX_CONCURRENT_EXPORTS = 20;

    public DataExportProperties {
        if (jdbcFetchSize < MIN_JDBC_FETCH_SIZE || jdbcFetchSize > MAX_JDBC_FETCH_SIZE) {
            throw new IllegalStateException("export.jdbcFetchSize.invalid");
        }

        if (maxRowsPerCsvFile < MIN_ROWS_PER_CSV_FILE || maxRowsPerCsvFile > MAX_ROWS_PER_CSV_FILE) {
            throw new IllegalStateException("export.maxRowsPerCsvFile.invalid");
        }

        if (maxConcurrentExports < MIN_CONCURRENT_EXPORTS || maxConcurrentExports > MAX_CONCURRENT_EXPORTS) {
            throw new IllegalStateException("export.maxConcurrentExports.invalid");
        }
    }
}