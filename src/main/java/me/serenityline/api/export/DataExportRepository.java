package me.serenityline.api.export;

import me.serenityline.api.security.auth.AuthenticatedUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Objects;
import java.util.zip.ZipOutputStream;

@Repository
public class DataExportRepository {

    private final JdbcTemplate jdbcTemplate;
    private final DataExportCsvWriter csvWriter;
    private final DataExportProperties properties;

    public DataExportRepository(
            JdbcTemplate jdbcTemplate,
            DataExportCsvWriter csvWriter,
            DataExportProperties properties
    ) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.csvWriter = Objects.requireNonNull(csvWriter, "csvWriter");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    public long exportTable(
            DataExportTable table,
            AuthenticatedUser authenticatedUser,
            ZipOutputStream zipOutputStream
    ) {
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(authenticatedUser, "authenticatedUser");
        Objects.requireNonNull(zipOutputStream, "zipOutputStream");

        PreparedStatementCreator preparedStatementCreator = connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    table.sql(),
                    ResultSet.TYPE_FORWARD_ONLY,
                    ResultSet.CONCUR_READ_ONLY
            );

            statement.setFetchSize(properties.jdbcFetchSize());
            table.binder().bind(statement, authenticatedUser);

            return statement;
        };

        ResultSetExtractor<Long> resultSetExtractor = resultSet -> {
            try {
                return csvWriter.writeResultSet(
                        zipOutputStream,
                        table.directory(),
                        resultSet,
                        properties.maxRowsPerCsvFile()
                );
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        };

        Long exportedRows = jdbcTemplate.query(
                preparedStatementCreator,
                resultSetExtractor
        );

        return exportedRows == null ? 0L : exportedRows;
    }
}