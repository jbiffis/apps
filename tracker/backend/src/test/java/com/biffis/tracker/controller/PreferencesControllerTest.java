package com.biffis.tracker.controller;

import com.biffis.tracker.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit preferences: metric defaults out of the box, round-trip on update,
 * partial updates leave other fields untouched, and validation/auth guards.
 * Each test isolates by user so the shared seed DB has no write contention
 * (only this class touches {@code /me/preferences}).
 */
@AutoConfigureMockMvc
class PreferencesControllerTest extends AbstractIntegrationTest {

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

    @Test
    void preferences_unauthenticated_401() throws Exception {
        mockMvc.perform(get("/api/me/preferences")).andExpect(status().isUnauthorized());
    }

    @Test
    void defaultsAreMetric_thenUpdate_roundTrips() throws Exception {
        // jeremy is only mutated by this test, so the first GET sees the V9 defaults.
        String jeremy = token("jeremy@biffis.com");

        mockMvc.perform(get("/api/me/preferences").header("Authorization", "Bearer " + jeremy))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weightUnit", is("kg")))
                .andExpect(jsonPath("$.heightUnit", is("cm")))
                .andExpect(jsonPath("$.temperatureUnit", is("c")));

        mockMvc.perform(put("/api/me/preferences")
                        .header("Authorization", "Bearer " + jeremy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightUnit\":\"lb\",\"heightUnit\":\"ftin\",\"temperatureUnit\":\"f\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weightUnit", is("lb")))
                .andExpect(jsonPath("$.heightUnit", is("ftin")))
                .andExpect(jsonPath("$.temperatureUnit", is("f")));

        mockMvc.perform(get("/api/me/preferences").header("Authorization", "Bearer " + jeremy))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weightUnit", is("lb")))
                .andExpect(jsonPath("$.heightUnit", is("ftin")))
                .andExpect(jsonPath("$.temperatureUnit", is("f")));
    }

    @Test
    void partialUpdate_leavesOtherFieldsUnchanged() throws Exception {
        // carley is only mutated here, and only her weightUnit.
        String carley = token("carley401@gmail.com");

        mockMvc.perform(put("/api/me/preferences")
                        .header("Authorization", "Bearer " + carley)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightUnit\":\"lb\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weightUnit", is("lb")))
                .andExpect(jsonPath("$.heightUnit", is("cm")))
                .andExpect(jsonPath("$.temperatureUnit", is("c")));
    }

    @Test
    void invalidUnit_422() throws Exception {
        mockMvc.perform(put("/api/me/preferences")
                        .header("Authorization", "Bearer " + token("carley401@gmail.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightUnit\":\"stone\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", is("validation_failed")));
    }
}
