package me.serenityline.api.finance.financialpriority.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialPriorityResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void financialPriorityResponseShouldSerializeExpectedPublicContractOnly() throws Exception {
        UUID id = UUID.randomUUID();

        FinancialPriorityResponse response = new FinancialPriorityResponse(
                id,
                "CRITICAL",
                "Prioritario",
                "Spese non evitabili o entrate critiche.",
                (short) 80
        );

        String json = objectMapper.writeValueAsString(response);
        JsonNode root = objectMapper.readTree(json);

        assertThat(root).hasSize(5);

        assertThat(root.get("financialPriorityId").asText()).isEqualTo(id.toString());
        assertThat(root.get("financialPriorityCode").asText()).isEqualTo("CRITICAL");
        assertThat(root.get("financialPriorityDisplayName").asText()).isEqualTo("Prioritario");
        assertThat(root.get("financialPriorityDescription").asText()).isEqualTo("Spese non evitabili o entrate critiche.");
        assertThat(root.get("financialPriorityRanking").asInt()).isEqualTo(80);

        assertThat(root.get("financialPriorityRanking").isNumber()).isTrue();

        assertThat(root.has("financialPriorityName")).isFalse();
        assertThat(root.has("userGroupId")).isFalse();
        assertThat(root.has("createdAt")).isFalse();
        assertThat(root.has("updatedAt")).isFalse();
    }
}