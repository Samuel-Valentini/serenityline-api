package me.serenityline.api.export;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public class DataExportCsvWriter {

    private static final String MANIFEST_PATH = "manifest.csv";
    private static final String README_PATH = "README.txt";

    public long writeResultSet(
            ZipOutputStream zipOutputStream,
            String directory,
            ResultSet resultSet,
            int maxRowsPerCsvFile
    ) throws SQLException, IOException {
        Objects.requireNonNull(zipOutputStream, "zipOutputStream");
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(resultSet, "resultSet");

        if (maxRowsPerCsvFile < 1) {
            throw new IllegalArgumentException("export.maxRowsPerCsvFile.invalid");
        }

        ResultSetMetaData metadata = resultSet.getMetaData();
        int columnCount = metadata.getColumnCount();

        long totalRows = 0;
        int partNumber = 1;
        int rowsInCurrentPart = 0;

        openPart(zipOutputStream, directory, partNumber);
        writeHeader(zipOutputStream, metadata, columnCount);

        while (resultSet.next()) {
            if (rowsInCurrentPart >= maxRowsPerCsvFile) {
                zipOutputStream.closeEntry();

                partNumber++;
                rowsInCurrentPart = 0;

                openPart(zipOutputStream, directory, partNumber);
                writeHeader(zipOutputStream, metadata, columnCount);
            }

            writeRow(zipOutputStream, resultSet, columnCount);

            totalRows++;
            rowsInCurrentPart++;
        }

        zipOutputStream.closeEntry();

        return totalRows;
    }

    public void writeManifest(
            ZipOutputStream zipOutputStream,
            List<DataExportManifestEntry> entries
    ) throws IOException {
        Objects.requireNonNull(zipOutputStream, "zipOutputStream");
        Objects.requireNonNull(entries, "entries");

        zipOutputStream.putNextEntry(new ZipEntry(MANIFEST_PATH));

        writeLine(zipOutputStream, csvLine(List.of(
                "directory",
                "rows"
        )));

        for (DataExportManifestEntry entry : entries) {
            writeLine(zipOutputStream, csvLine(List.of(
                    entry.directory(),
                    Long.toString(entry.rows())
            )));
        }

        zipOutputStream.closeEntry();
    }

    public void writeReadme(
            ZipOutputStream zipOutputStream,
            boolean includesFinanceData
    ) throws IOException {
        Objects.requireNonNull(zipOutputStream, "zipOutputStream");

        zipOutputStream.putNextEntry(new ZipEntry(README_PATH));

        String text = """
                SerenityLine data export
                
                This archive contains CSV files generated from persisted SerenityLine data.
                Files are split into part-00001.csv, part-00002.csv, ... when needed.
                
                The export does not include password hashes, refresh token hashes, auth action token hashes,
                encrypted email subject/body payloads, encryption IVs or encryption tags.
                
                Finance data included: %s
                
                Generated at: %s
                """.formatted(
                includesFinanceData ? "yes" : "no",
                OffsetDateTime.now()
        );

        write(zipOutputStream, text);
        zipOutputStream.closeEntry();
    }

    private void openPart(
            ZipOutputStream zipOutputStream,
            String directory,
            int partNumber
    ) throws IOException {
        String path = "%s/part-%05d.csv".formatted(directory, partNumber);
        zipOutputStream.putNextEntry(new ZipEntry(path));
    }

    private void writeHeader(
            ZipOutputStream zipOutputStream,
            ResultSetMetaData metadata,
            int columnCount
    ) throws SQLException, IOException {
        StringBuilder line = new StringBuilder();

        for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++) {
            if (columnIndex > 1) {
                line.append(',');
            }

            line.append(csvCell(metadata.getColumnLabel(columnIndex)));
        }

        writeLine(zipOutputStream, line.toString());
    }

    private void writeRow(
            ZipOutputStream zipOutputStream,
            ResultSet resultSet,
            int columnCount
    ) throws SQLException, IOException {
        StringBuilder line = new StringBuilder();

        for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++) {
            if (columnIndex > 1) {
                line.append(',');
            }

            Object value = resultSet.getObject(columnIndex);

            line.append(csvCell(value == null ? "" : value.toString()));
        }

        writeLine(zipOutputStream, line.toString());
    }

    private String csvLine(List<String> values) {
        StringBuilder line = new StringBuilder();

        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                line.append(',');
            }

            line.append(csvCell(values.get(index)));
        }

        return line.toString();
    }

    private String csvCell(String value) {
        String safeValue = value == null ? "" : value;

        return "\"" + safeValue.replace("\"", "\"\"") + "\"";
    }

    private void writeLine(
            ZipOutputStream zipOutputStream,
            String line
    ) throws IOException {
        write(zipOutputStream, line);
        write(zipOutputStream, "\n");
    }

    private void write(
            ZipOutputStream zipOutputStream,
            String value
    ) throws IOException {
        zipOutputStream.write(value.getBytes(StandardCharsets.UTF_8));
    }
}