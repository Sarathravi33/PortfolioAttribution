package com.portfolio.attribution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.attribution.model.request.AttributionRequest;
import com.portfolio.attribution.model.request.GroupInput;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AttributionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String URL = "/api/performance/attribution";

    private GroupInput group(String name, double weight, Double ret, Double fallback) {
        GroupInput g = new GroupInput();
        g.setGroupName(name);
        g.setWeightPct(BigDecimal.valueOf(weight));
        g.setReturnPct(ret != null ? BigDecimal.valueOf(ret) : null);
        g.setFallbackReturnPct(fallback != null ? BigDecimal.valueOf(fallback) : null);
        return g;
    }

    private AttributionRequest base(String requestId, List<GroupInput> groups) {
        AttributionRequest req = new AttributionRequest();
        req.setRequestId(requestId);
        req.setPortfolioId("PF-TEST");
        req.setValuationDate(LocalDate.of(2026, 6, 14));
        req.setGroups(groups);
        req.setCurrency("USD");
        req.setRequestedBy("tester");
        return req;
    }

    // T1 — All groups have primary return, weights sum to 100
    @Test
    void t1_validAllPrimary() throws Exception {
        AttributionRequest req = base("ATTR-T1", List.of(
                group("Equity",      60, 1.5,  null),
                group("FixedIncome", 30, 0.4,  null),
                group("Cash",        10, 0.1,  null)
        ));

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.degraded").value(false))
                .andExpect(jsonPath("$.groupContributions[0].pricingMode").value("PRIMARY"))
                .andExpect(jsonPath("$.groupContributions[1].pricingMode").value("PRIMARY"))
                .andExpect(jsonPath("$.groupContributions[2].pricingMode").value("PRIMARY"))
                .andExpect(jsonPath("$.warnings").isEmpty());
    }

    // T2 — One group uses fallback, result still VALID
    @Test
    void t2_validWithFallback() throws Exception {
        AttributionRequest req = base("ATTR-T2", List.of(
                group("Equity",      60, 1.5,  null),
                group("FixedIncome", 30, 0.4,  null),
                group("Cash",        10, null, 0.05)
        ));

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.degraded").value(false))
                .andExpect(jsonPath("$.groupContributions[2].pricingMode").value("FALLBACK_USED"))
                .andExpect(jsonPath("$.warnings[0]").value("Fallback pricing used for Cash"));
    }

    // T3 — One group missing return and no fallback → DEGRADED
    @Test
    void t3_degradedOneMissing() throws Exception {
        AttributionRequest req = base("ATTR-T3", List.of(
                group("Equity",      60, 1.5,  null),
                group("FixedIncome", 30, 0.4,  null),
                group("Cash",        10, null, null)
        ));

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEGRADED"))
                .andExpect(jsonPath("$.degraded").value(true))
                .andExpect(jsonPath("$.warnings[0]").value("Return unavailable for Cash, group excluded from total"));
    }

    // T4 — Two groups missing return, no fallback → REVIEW_REQUIRED
    @Test
    void t4_reviewRequiredTwoMissing() throws Exception {
        AttributionRequest req = base("ATTR-T4", List.of(
                group("Equity",      80, 1.5,  null),
                group("FixedIncome", 10, null, null),
                group("Cash",        10, null, null)
        ));

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.degraded").value(true));
    }

    // T5 — Weight sum outside [99, 101] → HTTP 400, INVALID_INPUT
    @Test
    void t5_invalidWeightTooLow() throws Exception {
        AttributionRequest req = base("ATTR-T5", List.of(
                group("Equity",      50, 1.5,  null),
                group("FixedIncome", 30, 0.4,  null)
                // total = 80 — invalid
        ));

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.warnings[0]").value(
                        org.hamcrest.Matchers.containsString("outside the valid range")));
    }

    // T6 — Repeat same requestId returns identical response (idempotency)
    @Test
    void t6_idempotentRepeatRequest() throws Exception {
        AttributionRequest req = base("ATTR-T6", List.of(
                group("Equity",      60, 2.0, null),
                group("FixedIncome", 40, 0.5, null)
        ));

        String body = objectMapper.writeValueAsString(req);

        MvcResult first = mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult second = mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        String firstProcessedAt  = objectMapper.readTree(first.getResponse().getContentAsString()).get("processedAt").asText();
        String secondProcessedAt = objectMapper.readTree(second.getResponse().getContentAsString()).get("processedAt").asText();

        org.junit.jupiter.api.Assertions.assertEquals(firstProcessedAt, secondProcessedAt,
                "Idempotent requests must return the same processedAt timestamp");
    }
}
