package me.serenityline.api.finance.transaction.service;

import me.serenityline.api.common.error.BadRequestException;
import me.serenityline.api.finance.transaction.dto.PatchField;
import me.serenityline.api.finance.transaction.dto.RecurringTransactionDeleteCommand;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;

@Component
public class RecurringTransactionDeleteParser {

    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "endDate",
            "finalPaymentAmount"
    );

    public RecurringTransactionDeleteCommand parse(JsonNode body) {
        if (body == null) {
            return new RecurringTransactionDeleteCommand(
                    PatchField.omitted(),
                    PatchField.omitted()
            );
        }

        requireObject(body);
        rejectUnknownFields(body);

        return new RecurringTransactionDeleteCommand(
                requiredDateField(body, "endDate"),
                nullableMoneyField(body, "finalPaymentAmount")
        );
    }

    private void requireObject(JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) {
            throw new BadRequestException("validation.failed");
        }
    }

    private void rejectUnknownFields(JsonNode node) {
        node.properties().forEach(property -> {
            String fieldName = property.getKey();

            if (!ALLOWED_FIELDS.contains(fieldName)) {
                throw new BadRequestException("validation.failed");
            }
        });
    }

    private PatchField<LocalDate> requiredDateField(JsonNode node, String fieldName) {
        if (!node.has(fieldName)) {
            return PatchField.omitted();
        }

        JsonNode value = node.get(fieldName);

        if (isNullOrBlank(value)) {
            throw new BadRequestException("validation.failed");
        }

        return PatchField.of(parseDate(value));
    }

    private PatchField<BigDecimal> nullableMoneyField(JsonNode node, String fieldName) {
        if (!node.has(fieldName)) {
            return PatchField.omitted();
        }

        JsonNode value = node.get(fieldName);

        if (isNullOrBlank(value)) {
            return PatchField.of(null);
        }

        return PatchField.of(parseMoney(value));
    }

    private boolean isNullOrBlank(JsonNode value) {
        return value == null
                || value.isNull()
                || value.isTextual() && value.asText().isBlank();
    }

    private LocalDate parseDate(JsonNode value) {
        if (!value.isTextual()) {
            throw new BadRequestException("validation.failed");
        }

        try {
            return LocalDate.parse(value.asText());
        } catch (DateTimeParseException ex) {
            throw new BadRequestException("validation.failed");
        }
    }

    private BigDecimal parseMoney(JsonNode value) {
        try {
            BigDecimal parsed = value.isNumber()
                    ? value.decimalValue()
                    : new BigDecimal(value.asText());

            if (parsed.scale() > 2) {
                throw new BadRequestException("validation.failed");
            }

            return parsed;
        } catch (BadRequestException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BadRequestException("validation.failed");
        }
    }
}