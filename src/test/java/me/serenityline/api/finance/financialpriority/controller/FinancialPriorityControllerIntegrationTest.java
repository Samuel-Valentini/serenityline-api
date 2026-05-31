package me.serenityline.api.finance.financialpriority.controller;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import me.serenityline.api.finance.financialpriority.dto.FinancialPriorityResponse;
import me.serenityline.api.finance.financialpriority.service.FinancialPriorityService;
import me.serenityline.api.security.auth.JwtAuthenticationFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FinancialPriorityController.class)
class FinancialPriorityControllerIntegrationTest {

    private static final String URL = "/api/finance/financial-priorities";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FinancialPriorityService financialPriorityService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void bypassJwtAuthenticationFilter() throws Exception {
        doAnswer(invocation -> {
            ServletRequest request = invocation.getArgument(0);
            ServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);

            chain.doFilter(request, response);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(
                any(ServletRequest.class),
                any(ServletResponse.class),
                any(FilterChain.class)
        );
    }

    @Test
    void findFinancialPrioritiesShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(financialPriorityService);
    }

    @Test
    @WithMockUser
    void findFinancialPrioritiesShouldReturnFinancialPriorities() throws Exception {
        UUID criticalId = UUID.randomUUID();
        UUID essentialId = UUID.randomUUID();

        given(financialPriorityService.findFinancialPriorities(any(Locale.class)))
                .willReturn(List.of(
                        new FinancialPriorityResponse(
                                criticalId,
                                "CRITICAL",
                                "Prioritario",
                                "Spese non evitabili o entrate critiche.",
                                (short) 80
                        ),
                        new FinancialPriorityResponse(
                                essentialId,
                                "ESSENTIAL",
                                "Essenziale",
                                "Spese o entrate indispensabili.",
                                (short) 60
                        )
                ));

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "it-IT"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))

                .andExpect(jsonPath("$[0].financialPriorityId").value(criticalId.toString()))
                .andExpect(jsonPath("$[0].financialPriorityCode").value("CRITICAL"))
                .andExpect(jsonPath("$[0].financialPriorityDisplayName").value("Prioritario"))
                .andExpect(jsonPath("$[0].financialPriorityDescription").value("Spese non evitabili o entrate critiche."))
                .andExpect(jsonPath("$[0].financialPriorityRanking").value(80))

                .andExpect(jsonPath("$[1].financialPriorityId").value(essentialId.toString()))
                .andExpect(jsonPath("$[1].financialPriorityCode").value("ESSENTIAL"))
                .andExpect(jsonPath("$[1].financialPriorityDisplayName").value("Essenziale"))
                .andExpect(jsonPath("$[1].financialPriorityDescription").value("Spese o entrate indispensabili."))
                .andExpect(jsonPath("$[1].financialPriorityRanking").value(60));
    }

    @Test
    @WithMockUser
    void findFinancialPrioritiesShouldPassAcceptLanguageLocaleToService() throws Exception {
        given(financialPriorityService.findFinancialPriorities(any(Locale.class)))
                .willReturn(List.of());

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                .andExpect(status().isOk());

        ArgumentCaptor<Locale> localeCaptor = ArgumentCaptor.forClass(Locale.class);

        verify(financialPriorityService).findFinancialPriorities(localeCaptor.capture());

        assertThat(localeCaptor.getValue()).isEqualTo(Locale.forLanguageTag("en-US"));
    }

    @Test
    @WithMockUser
    void findFinancialPrioritiesShouldReturnEmptyArrayWhenNoPrioritiesAreReturned() throws Exception {
        given(financialPriorityService.findFinancialPriorities(any(Locale.class)))
                .willReturn(List.of());

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "it-IT"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @WithMockUser
    void findFinancialPrioritiesShouldNotExposeInternalOrLegacyFields() throws Exception {
        UUID criticalId = UUID.randomUUID();

        given(financialPriorityService.findFinancialPriorities(any(Locale.class)))
                .willReturn(List.of(
                        new FinancialPriorityResponse(
                                criticalId,
                                "CRITICAL",
                                "Prioritario",
                                "Descrizione",
                                (short) 80
                        )
                ));

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "it-IT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].financialPriorityName").doesNotExist())
                .andExpect(jsonPath("$[0].financialPriorityDescriptionRaw").doesNotExist())
                .andExpect(jsonPath("$[0].userGroupId").doesNotExist());
    }
}