package com.biffis.tracker.controller;

import com.biffis.tracker.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class StatsControllerTest extends AbstractIntegrationTest {

    private static final String SEED_PASSWORD = "password";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    private String token(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + SEED_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private void logWater(String token, int glasses) throws Exception {
        mockMvc.perform(post("/api/logged-events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventTypeSlug\":\"water\",\"options\":[{\"propertyName\":\"Glasses\",\"value\":" + glasses + "}]}"))
                .andExpect(status().isCreated());
    }

    @Test
    void stats_unauthenticated_401() throws Exception {
        mockMvc.perform(get("/api/stats")).andExpect(status().isUnauthorized());
    }

    @Test
    void stats_reflectLoggedEntries() throws Exception {
        String carley = token("carley401@gmail.com");
        logWater(carley, 3);
        logWater(carley, 5);

        String body = mockMvc.perform(get("/api/stats?days=30").header("Authorization", "Bearer " + carley))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEntries").isNumber())
                .andExpect(jsonPath("$.currentStreakDays").isNumber())
                .andExpect(jsonPath("$.daily").isArray())
                .andReturn().getResponse().getContentAsString();
        JsonNode j = objectMapper.readTree(body);
        assertThat(j.get("totalEntries").asLong()).isGreaterThanOrEqualTo(2);
        assertThat(j.get("currentStreakDays").asInt()).isGreaterThanOrEqualTo(1);
        boolean hasWater = false;
        for (JsonNode t : j.get("perTracker")) {
            if ("water".equals(t.get("eventTypeSlug").asText())) hasWater = true;
        }
        assertThat(hasWater).isTrue();
    }

    @Test
    void stats_tzParam_validZoneAndInvalidFallbackBothReturn200() throws Exception {
        String jeremy = token("jeremy@biffis.com");
        logWater(jeremy, 4); // ensure the daily array isn't empty

        // Valid IANA zone — daily date strings are still well-formed YYYY-MM-DD.
        String body = mockMvc.perform(get("/api/stats?days=14&tz=America/Toronto")
                        .header("Authorization", "Bearer " + jeremy))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode j = objectMapper.readTree(body);
        assertThat(j.get("daily").isArray()).isTrue();
        if (j.get("daily").size() > 0) {
            assertThat(j.get("daily").get(0).get("date").asText()).matches("\\d{4}-\\d{2}-\\d{2}");
        }

        // Invalid zone → falls back to UTC, still 200 (tz is advisory).
        mockMvc.perform(get("/api/stats?days=14&tz=Not/A_Real_Zone")
                        .header("Authorization", "Bearer " + jeremy))
                .andExpect(status().isOk());

        // No tz at all → back-compat, defaults to UTC.
        mockMvc.perform(get("/api/stats?days=14")
                        .header("Authorization", "Bearer " + jeremy))
                .andExpect(status().isOk());
    }
}
