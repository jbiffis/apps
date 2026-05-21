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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class MeControllerTest extends AbstractIntegrationTest {

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

    @Test
    void prefs_unauthenticated_401() throws Exception {
        mockMvc.perform(get("/api/me/tracker-prefs")).andExpect(status().isUnauthorized());
    }

    @Test
    void hideTracker_thenListed_andCrossUserIsolated() throws Exception {
        String carley = token("carley");
        mockMvc.perform(put("/api/me/tracker-prefs/water")
                        .header("Authorization", "Bearer " + carley)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hidden\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventTypeSlug").value("water"))
                .andExpect(jsonPath("$.hidden").value(true));

        String list = mockMvc.perform(get("/api/me/tracker-prefs").header("Authorization", "Bearer " + carley))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        boolean waterHidden = false;
        for (JsonNode p : objectMapper.readTree(list)) {
            if ("water".equals(p.get("eventTypeSlug").asText())) waterHidden = p.get("hidden").asBoolean();
        }
        assertThat(waterHidden).isTrue();

        // Jeremy doesn't see Carley's prefs.
        String jeremyList = mockMvc.perform(get("/api/me/tracker-prefs").header("Authorization", "Bearer " + token("jeremy")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode p : objectMapper.readTree(jeremyList)) {
            assertThat(p.get("eventTypeSlug").asText()).isNotEqualTo("water");
        }
    }

    @Test
    void setPref_unknownTracker_404() throws Exception {
        mockMvc.perform(put("/api/me/tracker-prefs/does-not-exist")
                        .header("Authorization", "Bearer " + token("carley"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hidden\":true}"))
                .andExpect(status().isNotFound());
    }
}
