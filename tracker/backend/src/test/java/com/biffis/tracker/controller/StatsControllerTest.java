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

    private static final String SEED_PASSWORD = "changeme-on-first-login";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    private String token(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + SEED_PASSWORD + "\"}"))
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
        String carley = token("carley");
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
}
