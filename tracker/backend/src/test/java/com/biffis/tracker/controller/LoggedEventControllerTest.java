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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Logged-events integration tests. The privacy-critical ones (cross-user
 * isolation) are the reason this epic exists — see CLAUDE.md / TEST_CASES TC-4.x.
 */
@AutoConfigureMockMvc
class LoggedEventControllerTest extends AbstractIntegrationTest {

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

    private String logWater(String token, int glasses) throws Exception {
        String body = mockMvc.perform(post("/api/logged-events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventTypeSlug":"water","note":"test",
                                 "options":[{"propertyName":"Glasses","value":%d}]}
                                """.formatted(glasses)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    @Test
    void unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/logged-events")).andExpect(status().isUnauthorized());
    }

    @Test
    void create_andReadBack() throws Exception {
        String carley = token("carley");
        String id = logWater(carley, 5);

        mockMvc.perform(get("/api/logged-events/" + id).header("Authorization", "Bearer " + carley))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventType.slug").value("water"))
                .andExpect(jsonPath("$.note").value("test"))
                .andExpect(jsonPath("$.options[0].property").value("Glasses"))
                .andExpect(jsonPath("$.options[0].value").value(5));
    }

    @Test
    void create_unknownEventType_404() throws Exception {
        mockMvc.perform(post("/api/logged-events")
                        .header("Authorization", "Bearer " + token("carley"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventTypeSlug\":\"does-not-exist\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void crossUser_cannotReadOthersEventById_404() throws Exception {
        String carleyId = logWater(token("carley"), 3);
        // Jeremy must not be able to read Carley's event — 404 (not 403).
        mockMvc.perform(get("/api/logged-events/" + carleyId)
                        .header("Authorization", "Bearer " + token("jeremy")))
                .andExpect(status().isNotFound());
    }

    @Test
    void crossUser_listExcludesOthersEvents() throws Exception {
        String carleyId = logWater(token("carley"), 2);

        String jeremyList = mockMvc.perform(get("/api/logged-events?limit=200")
                        .header("Authorization", "Bearer " + token("jeremy")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode events = objectMapper.readTree(jeremyList).get("events");
        for (JsonNode e : events) {
            assertThat(e.get("id").asText()).isNotEqualTo(carleyId);
        }
    }

    @Test
    void crossUser_cannotDeleteOthersEvent_404() throws Exception {
        String carleyId = logWater(token("carley"), 1);
        mockMvc.perform(delete("/api/logged-events/" + carleyId)
                        .header("Authorization", "Bearer " + token("jeremy")))
                .andExpect(status().isNotFound());
        // still readable by the owner afterwards
        mockMvc.perform(get("/api/logged-events/" + carleyId)
                        .header("Authorization", "Bearer " + token("carley")))
                .andExpect(status().isOk());
    }

    @Test
    void update_own_replacesValues() throws Exception {
        String carley = token("carley");
        String id = logWater(carley, 3);
        mockMvc.perform(put("/api/logged-events/" + id)
                        .header("Authorization", "Bearer " + carley)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventTypeSlug":"water","note":"edited",
                                 "options":[{"propertyName":"Glasses","value":8}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.note").value("edited"))
                .andExpect(jsonPath("$.options[0].value").value(8));
        // persisted
        mockMvc.perform(get("/api/logged-events/" + id).header("Authorization", "Bearer " + carley))
                .andExpect(jsonPath("$.options[0].value").value(8));
    }

    @Test
    void crossUser_cannotUpdateOthersEvent_404() throws Exception {
        String carleyId = logWater(token("carley"), 2);
        mockMvc.perform(put("/api/logged-events/" + carleyId)
                        .header("Authorization", "Bearer " + token("jeremy"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventTypeSlug":"water","options":[{"propertyName":"Glasses","value":9}]}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_own_thenGone() throws Exception {
        String carley = token("carley");
        String id = logWater(carley, 4);
        mockMvc.perform(delete("/api/logged-events/" + id).header("Authorization", "Bearer " + carley))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/logged-events/" + id).header("Authorization", "Bearer " + carley))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_thenRestore_bringsItBack() throws Exception {
        String carley = token("carley");
        String id = logWater(carley, 4);
        mockMvc.perform(delete("/api/logged-events/" + id).header("Authorization", "Bearer " + carley))
                .andExpect(status().isNoContent());
        // soft-deleted → gone from reads
        mockMvc.perform(get("/api/logged-events/" + id).header("Authorization", "Bearer " + carley))
                .andExpect(status().isNotFound());
        // restore → back, same id, original value
        mockMvc.perform(post("/api/logged-events/" + id + "/restore").header("Authorization", "Bearer " + carley))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.options[0].value").value(4));
        mockMvc.perform(get("/api/logged-events/" + id).header("Authorization", "Bearer " + carley))
                .andExpect(status().isOk());
    }

    @Test
    void crossUser_cannotRestoreOthersEvent_404() throws Exception {
        String carley = token("carley");
        String id = logWater(carley, 2);
        mockMvc.perform(delete("/api/logged-events/" + id).header("Authorization", "Bearer " + carley))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/logged-events/" + id + "/restore")
                        .header("Authorization", "Bearer " + token("jeremy")))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_filterByEventType() throws Exception {
        String carley = token("carley");
        logWater(carley, 6);
        String body = mockMvc.perform(get("/api/logged-events?eventTypeSlug=water&limit=200")
                        .header("Authorization", "Bearer " + carley))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode events = objectMapper.readTree(body).get("events");
        assertThat(events.size()).isGreaterThan(0);
        for (JsonNode e : events) {
            assertThat(e.get("eventType").get("slug").asText()).isEqualTo("water");
        }
    }

    @Test
    void list_cursorPagination_noOverlap() throws Exception {
        String carley = token("carley");
        for (int i = 1; i <= 5; i++) {
            logWater(carley, i);
        }

        String p1body = mockMvc.perform(get("/api/logged-events?limit=2")
                        .header("Authorization", "Bearer " + carley))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode p1 = objectMapper.readTree(p1body);
        assertThat(p1.get("events").size()).isEqualTo(2);
        String cursor = p1.get("nextCursor").asText();
        assertThat(cursor).isNotBlank();

        String enc = java.net.URLEncoder.encode(cursor, java.nio.charset.StandardCharsets.UTF_8);
        String p2body = mockMvc.perform(get("/api/logged-events?limit=2&cursor=" + enc)
                        .header("Authorization", "Bearer " + carley))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode p2 = objectMapper.readTree(p2body);

        java.util.Set<String> page1Ids = new java.util.HashSet<>();
        for (JsonNode e : p1.get("events")) page1Ids.add(e.get("id").asText());
        for (JsonNode e : p2.get("events")) {
            assertThat(page1Ids).doesNotContain(e.get("id").asText());
        }
    }

    @Test
    void list_dateWindowExcludesOld() throws Exception {
        String carley = token("carley");
        // an event 10 days ago
        mockMvc.perform(post("/api/logged-events")
                        .header("Authorization", "Bearer " + carley)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventTypeSlug":"water","occurredAt":"2000-01-01T00:00:00Z",
                                 "options":[{"propertyName":"Glasses","value":1}]}
                                """))
                .andExpect(status().isCreated());
        // default window (last 24h) must not include the year-2000 event
        String body = mockMvc.perform(get("/api/logged-events?limit=200")
                        .header("Authorization", "Bearer " + carley))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode events = objectMapper.readTree(body).get("events");
        for (JsonNode e : events) {
            assertThat(e.get("occurredAt").asText()).doesNotStartWith("2000");
        }
    }

    @Test
    void home_today_shape() throws Exception {
        String carley = token("carley");
        logWater(carley, 7);
        mockMvc.perform(get("/api/home/today").header("Authorization", "Bearer " + carley))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void home_hero_shape() throws Exception {
        mockMvc.perform(get("/api/home/hero").header("Authorization", "Bearer " + token("carley")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].eventTypeSlug").exists())
                .andExpect(jsonPath("$[0].progress").exists())
                .andExpect(jsonPath("$[0].valueText").exists());
    }
}
