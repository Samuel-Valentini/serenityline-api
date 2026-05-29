package me.serenityline.api.finance.transaction.service;

import me.serenityline.api.common.error.BadRequestException;
import me.serenityline.api.finance.transaction.dto.PatchField;
import me.serenityline.api.finance.transaction.dto.RecurringTransactionDetailsPatchCommand;
import me.serenityline.api.finance.transaction.dto.RecurringTransactionPatchCommand;
import me.serenityline.api.finance.transaction.dto.RecurringTransactionRulePatchCommand;
import me.serenityline.api.finance.transaction.entity.PaymentDateAdjustmentPolicy;
import me.serenityline.api.finance.transaction.entity.RecurrenceUnit;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.UUID;

@Component
public class RecurringTransactionPatchParser {

    private static final Set<String> ROOT_FIELDS = Set.of(
            "recurringTransactionFirstPaymentDate",
            "recurringTransactionAmountIsAdjustable",
            "recurringTransactionIsSimulated",
            "simulationGroupId",
            "recurringTransactionReminderEnabled",
            "recurringTransactionReminderDaysBefore",
            "rule",
            "details"
    );

    private static final Set<String> RULE_FIELDS = Set.of(
            "effectiveFrom",
            "effectiveTo",
            "dayOfUnit",
            "paymentAmount",
            "recurrenceInterval",
            "recurrenceUnit",
            "paymentDateAdjustmentPolicy",
            "recurringTransactionEndDate",
            "finalPaymentAmount"
    );

    private static final Set<String> DETAILS_FIELDS = Set.of(
            "effectiveFrom",
            "recurringTransactionDescription",
            "categoryId",
            "financialPriorityId",
            "linkedAccountId",
            "linkedCreditCardId",
            "linkedBucketId",
            "recurringTransactionAffectsAccountBalance",
            "recurringtransactionAffectsSerenityline"
    );

    public RecurringTransactionPatchCommand parse(JsonNode body) {
        requireObject(body);
        rejectUnknownFields(body, ROOT_FIELDS);

        RecurringTransactionRulePatchCommand rule = body.has("rule")
                ? parseRule(body.get("rule"))
                : null;

        RecurringTransactionDetailsPatchCommand details = body.has("details")
                ? parseDetails(body.get("details"))
                : null;

        RecurringTransactionPatchCommand command = new RecurringTransactionPatchCommand(
                requiredDateField(body, "recurringTransactionFirstPaymentDate"),
                requiredBooleanField(body, "recurringTransactionAmountIsAdjustable"),
                requiredBooleanField(body, "recurringTransactionIsSimulated"),
                nullableUuidField(body, "simulationGroupId"),
                requiredBooleanField(body, "recurringTransactionReminderEnabled"),
                requiredIntegerField(body, "recurringTransactionReminderDaysBefore", 0, 366),
                rule,
                details
        );

        if (!command.hasAnyPatch()) {
            throw new BadRequestException("finance.recurringTransaction.emptyPatch");
        }

        return command;
    }

    private RecurringTransactionRulePatchCommand parseRule(JsonNode node) {
        requireObject(node);
        rejectUnknownFields(node, RULE_FIELDS);

        RecurringTransactionRulePatchCommand command = new RecurringTransactionRulePatchCommand(
                requiredDateField(node, "effectiveFrom"),
                nullableDateField(node, "effectiveTo"),
                requiredIntegerField(node, "dayOfUnit", 1, 366),
                requiredMoneyField(node, "paymentAmount"),
                requiredIntegerField(node, "recurrenceInterval", 1, 32767),
                requiredEnumField(node, "recurrenceUnit", RecurrenceUnit.class),
                requiredEnumField(node, "paymentDateAdjustmentPolicy", PaymentDateAdjustmentPolicy.class),
                nullableDateField(node, "recurringTransactionEndDate"),
                nullableMoneyField(node, "finalPaymentAmount")
        );

        if (!command.hasAnyField()) {
            throw new BadRequestException("finance.recurringTransaction.emptyPatch");
        }

        return command;
    }

    private RecurringTransactionDetailsPatchCommand parseDetails(JsonNode node) {
        requireObject(node);
        rejectUnknownFields(node, DETAILS_FIELDS);

        RecurringTransactionDetailsPatchCommand command = new RecurringTransactionDetailsPatchCommand(
                requiredDateField(node, "effectiveFrom"),
                requiredTextField(node, "recurringTransactionDescription", 500),
                requiredUuidField(node, "categoryId"),
                requiredUuidField(node, "financialPriorityId"),
                requiredUuidField(node, "linkedAccountId"),
                nullableUuidField(node, "linkedCreditCardId"),
                nullableUuidField(node, "linkedBucketId"),
                requiredBooleanField(node, "recurringTransactionAffectsAccountBalance"),
                requiredBooleanField(node, "recurringtransactionAffectsSerenityline")
        );

        if (!command.hasAnyField()) {
            throw new BadRequestException("finance.recurringTransaction.emptyPatch");
        }

        return command;
    }

    private void requireObject(JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) {
            throw new BadRequestException("validation.failed");
        }
    }

    private void rejectUnknownFields(JsonNode node, Set<String> allowedFields) {
        node.properties().forEach(property -> {
            String fieldName = property.getKey();

            if (!allowedFields.contains(fieldName)) {
                throw new BadRequestException("validation.failed");
            }
        });
    }

    private PatchField<String> requiredTextField(
            JsonNode node,
            String fieldName,
            int maxLength
    ) {
        if (!node.has(fieldName)) {
            return PatchField.omitted();
        }

        JsonNode value = node.get(fieldName);

        if (value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()) {
            throw new BadRequestException("validation.failed");
        }

        String cleaned = value.asText().trim();

        if (cleaned.length() > maxLength) {
            throw new BadRequestException("validation.failed");
        }

        return PatchField.of(cleaned);
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

    private PatchField<LocalDate> nullableDateField(JsonNode node, String fieldName) {
        if (!node.has(fieldName)) {
            return PatchField.omitted();
        }

        JsonNode value = node.get(fieldName);

        if (isNullOrBlank(value)) {
            return PatchField.of(null);
        }

        return PatchField.of(parseDate(value));
    }

    private PatchField<UUID> requiredUuidField(JsonNode node, String fieldName) {
        if (!node.has(fieldName)) {
            return PatchField.omitted();
        }

        JsonNode value = node.get(fieldName);

        if (isNullOrBlank(value)) {
            throw new BadRequestException("validation.failed");
        }

        return PatchField.of(parseUuid(value));
    }

    private PatchField<UUID> nullableUuidField(JsonNode node, String fieldName) {
        if (!node.has(fieldName)) {
            return PatchField.omitted();
        }

        JsonNode value = node.get(fieldName);

        if (isNullOrBlank(value)) {
            return PatchField.of(null);
        }

        return PatchField.of(parseUuid(value));
    }

    private PatchField<BigDecimal> requiredMoneyField(JsonNode node, String fieldName) {
        if (!node.has(fieldName)) {
            return PatchField.omitted();
        }

        JsonNode value = node.get(fieldName);

        if (isNullOrBlank(value)) {
            throw new BadRequestException("validation.failed");
        }

        return PatchField.of(parseMoney(value));
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

    private PatchField<Integer> requiredIntegerField(
            JsonNode node,
            String fieldName,
            int min,
            int max
    ) {
        if (!node.has(fieldName)) {
            return PatchField.omitted();
        }

        JsonNode value = node.get(fieldName);

        if (isNullOrBlank(value)) {
            throw new BadRequestException("validation.failed");
        }

        int parsed = parseInteger(value);

        if (parsed < min || parsed > max) {
            throw new BadRequestException("validation.failed");
        }

        return PatchField.of(parsed);
    }

    private PatchField<Boolean> requiredBooleanField(JsonNode node, String fieldName) {
        if (!node.has(fieldName)) {
            return PatchField.omitted();
        }

        JsonNode value = node.get(fieldName);

        if (isNullOrBlank(value)) {
            throw new BadRequestException("validation.failed");
        }

        return PatchField.of(parseBoolean(value));
    }

    private <E extends Enum<E>> PatchField<E> requiredEnumField(
            JsonNode node,
            String fieldName,
            Class<E> enumClass
    ) {
        if (!node.has(fieldName)) {
            return PatchField.omitted();
        }

        JsonNode value = node.get(fieldName);

        if (isNullOrBlank(value)) {
            throw new BadRequestException("validation.failed");
        }

        try {
            return PatchField.of(Enum.valueOf(enumClass, value.asText()));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("validation.failed");
        }
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

    private UUID parseUuid(JsonNode value) {
        if (!value.isTextual()) {
            throw new BadRequestException("validation.failed");
        }

        try {
            return UUID.fromString(value.asText());
        } catch (IllegalArgumentException ex) {
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
        } catch (RuntimeException ex) {
            throw new BadRequestException("validation.failed");
        }
    }

    private int parseInteger(JsonNode value) {
        if (value.isInt()) {
            return value.asInt();
        }

        if (!value.isTextual()) {
            throw new BadRequestException("validation.failed");
        }

        try {
            return Integer.parseInt(value.asText());
        } catch (NumberFormatException ex) {
            throw new BadRequestException("validation.failed");
        }
    }

    private boolean parseBoolean(JsonNode value) {
        if (value.isBoolean()) {
            return value.asBoolean();
        }

        if (!value.isTextual()) {
            throw new BadRequestException("validation.failed");
        }

        return switch (value.asText()) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new BadRequestException("validation.failed");
        };
    }
}