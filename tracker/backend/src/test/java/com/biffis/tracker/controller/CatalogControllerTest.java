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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CatalogControllerTest extends AbstractIntegrationTest {

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

    private JsonNode tree(String token, String include) throws Exception {
        var req = get("/api/event-types").header("Authorization", "Bearer " + token);
        if (include != null) {
            req = get("/api/event-types?include=" + include).header("Authorization", "Bearer " + token);
        }
        String body = mockMvc.perform(req).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    /** Recursively look for a node with the given slug. */
    private boolean containsSlug(JsonNode node, String slug) {
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (containsSlug(child, slug)) return true;
            }
            return false;
        }
        if (node.has("slug") && slug.equals(node.get("slug").asText())) return true;
        if (node.has("children")) return containsSlug(node.get("children"), slug);
        return false;
    }

    @Test
    void unauthenticated_treeRequest_returns401() throws Exception {
        mockMvc.perform(get("/api/event-types")).andExpect(status().isUnauthorized());
    }

    @Test
    void tree_hasTopLevelCategoriesWithChildren() throws Exception {
        JsonNode root = tree(token("carley401@gmail.com"), null);
        assertThat(root.isArray()).isTrue();
        assertThat(root.size()).isGreaterThan(0);
        // 'health' is a seeded top-level category with children
        JsonNode health = null;
        for (JsonNode n : root) {
            if ("health".equals(n.path("slug").asText())) health = n;
        }
        assertThat(health).isNotNull();
        assertThat(health.path("isCategory").asBoolean()).isTrue();
        assertThat(health.path("children").size()).isGreaterThan(0);
    }

    @Test
    void tree_assemblesAllLevels_noTruncation() throws Exception {
        // Regression: tree() used to convert a root to an immutable view before
        // all its descendants were linked, silently dropping most of the tree.
        // Assert the full multi-level structure survives.
        JsonNode root = tree(token("carley401@gmail.com"), "all");

        // health's direct leaves AND sub-categories are all present
        assertThat(containsSlug(root, "mood")).as("health direct leaf 'mood'").isTrue();
        assertThat(containsSlug(root, "eyes")).as("health sub-category 'eyes'").isTrue();
        // a 3-level-deep leaf (health → eyes → double-vision) is reachable
        assertThat(containsSlug(root, "double-vision")).as("3-level leaf").isTrue();

        // every non-category seed type should surface as a leaf in the tree
        assertThat(countLeaves(root)).isGreaterThanOrEqualTo(40);
    }

    private int countLeaves(JsonNode node) {
        if (node.isArray()) {
            int n = 0;
            for (JsonNode child : node) n += countLeaves(child);
            return n;
        }
        if (node.path("isCategory").asBoolean()) {
            return countLeaves(node.path("children"));
        }
        return 1;
    }

    @Test
    void audienceFilter_femaleCategoryHiddenForMaleByDefault() throws Exception {
        // Carley (female) sees lady-stuff; Jeremy (male) does not.
        assertThat(containsSlug(tree(token("carley401@gmail.com"), null), "lady-stuff")).isTrue();
        assertThat(containsSlug(tree(token("jeremy@biffis.com"), null), "lady-stuff")).isFalse();
    }

    @Test
    void audienceFilter_includeAllBypassesForMale() throws Exception {
        assertThat(containsSlug(tree(token("jeremy@biffis.com"), "all"), "lady-stuff")).isTrue();
    }

    @Test
    void presets_returnsSeededSet() throws Exception {
        String body = mockMvc.perform(get("/api/property-presets")
                        .header("Authorization", "Bearer " + token("carley401@gmail.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode presets = objectMapper.readTree(body);
        assertThat(presets.isArray()).isTrue();
        assertThat(presets.size()).isEqualTo(28); // +weight-kg, +height-cm (V8)
        // each preset has a widget + options
        assertThat(presets.get(0).has("widget")).isTrue();
        assertThat(presets.get(0).has("options")).isTrue();
    }

    @Test
    void leaf_hasHydratedProperties() throws Exception {
        // Find any leaf with at least one property in the full tree and check
        // its preset is hydrated.
        JsonNode root = tree(token("carley401@gmail.com"), "all");
        JsonNode leafWithProps = findLeafWithProperties(root);
        assertThat(leafWithProps).as("expected at least one leaf with properties").isNotNull();
        JsonNode prop = leafWithProps.get("properties").get(0);
        assertThat(prop.path("preset").path("widget").asText()).isNotBlank();
    }

    private JsonNode findLeafWithProperties(JsonNode node) {
        if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode found = findLeafWithProperties(child);
                if (found != null) return found;
            }
            return null;
        }
        if (!node.path("isCategory").asBoolean() && node.path("properties").size() > 0) {
            return node;
        }
        if (node.has("children")) return findLeafWithProperties(node.get("children"));
        return null;
    }

    @Test
    void create_thenDelete_byCreator() throws Exception {
        String carley = token("carley401@gmail.com");
        String created = mockMvc.perform(post("/api/event-types")
                        .header("Authorization", "Bearer " + carley)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Test Tracker ZZZ","icon":"Pill","parentSlug":"medication"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(created);
        String id = node.get("id").asText();
        assertThat(node.path("isSeed").asBoolean()).isFalse();
        assertThat(node.path("slug").asText()).isEqualTo("test-tracker-zzz");

        // visible in the tree
        assertThat(containsSlug(tree(carley, "all"), "test-tracker-zzz")).isTrue();

        // creator can delete
        mockMvc.perform(delete("/api/event-types/" + id)
                        .header("Authorization", "Bearer " + carley))
                .andExpect(status().isNoContent());
    }

    @Test
    void create_topLevelTracker_noParent() throws Exception {
        String carley = token("carley401@gmail.com");
        String created = mockMvc.perform(post("/api/event-types")
                        .header("Authorization", "Bearer " + carley)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Top Level Tracker AAA","icon":"Heart"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(created);
        assertThat(node.path("isCategory").asBoolean()).isFalse();
        assertThat(node.path("parentId").isNull()).isTrue();
        // surfaces as a top-level node in the tree
        boolean topLevel = false;
        for (JsonNode n : tree(carley, "all")) {
            if ("top-level-tracker-aaa".equals(n.path("slug").asText())) topLevel = true;
        }
        assertThat(topLevel).isTrue();
    }

    @Test
    void create_category() throws Exception {
        String carley = token("carley401@gmail.com");
        String created = mockMvc.perform(post("/api/event-types")
                        .header("Authorization", "Bearer " + carley)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"My Category BBB","icon":"Sparkle","isCategory":true}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(created);
        assertThat(node.path("isCategory").asBoolean()).isTrue();
        assertThat(node.path("slug").asText()).isEqualTo("my-category-bbb");
    }

    @Test
    void create_trackerWithProperties_hydratesPreset() throws Exception {
        String carley = token("carley401@gmail.com");
        String presetSlug = firstPresetSlug(carley);
        mockMvc.perform(post("/api/event-types")
                        .header("Authorization", "Bearer " + carley)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"With Fields CCC","icon":"Journal","properties":[
                                  {"name":"How much","presetSlug":"%s","required":true,"sortOrder":0}
                                ]}
                                """.formatted(presetSlug)))
                .andExpect(status().isCreated());

        // GET by slug returns the property with its preset hydrated
        String body = mockMvc.perform(get("/api/event-types/with-fields-ccc")
                        .header("Authorization", "Bearer " + carley))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(body);
        assertThat(node.path("properties").size()).isEqualTo(1);
        JsonNode prop = node.path("properties").get(0);
        assertThat(prop.path("name").asText()).isEqualTo("How much");
        assertThat(prop.path("required").asBoolean()).isTrue();
        assertThat(prop.path("preset").path("widget").asText()).isNotBlank();
    }

    @Test
    void create_duplicateName_conflict() throws Exception {
        String carley = token("carley401@gmail.com");
        String payload = """
                {"name":"Duplicate Name DDD","icon":"Pill"}
                """;
        mockMvc.perform(post("/api/event-types")
                        .header("Authorization", "Bearer " + carley)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/event-types")
                        .header("Authorization", "Bearer " + carley)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isConflict());
    }

    @Test
    void create_missingName_unprocessable() throws Exception {
        mockMvc.perform(post("/api/event-types")
                        .header("Authorization", "Bearer " + token("carley401@gmail.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"icon\":\"Pill\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void create_missingIcon_unprocessable() throws Exception {
        mockMvc.perform(post("/api/event-types")
                        .header("Authorization", "Bearer " + token("carley401@gmail.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"No Icon EEE\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    private String firstPresetSlug(String token) throws Exception {
        String body = mockMvc.perform(get("/api/property-presets")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get(0).get("slug").asText();
    }

    @Test
    void delete_seedType_forbidden() throws Exception {
        String carley = token("carley401@gmail.com");
        // resolve a seed type's id
        String health = mockMvc.perform(get("/api/event-types/health")
                        .header("Authorization", "Bearer " + carley))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(health).get("id").asText();

        mockMvc.perform(delete("/api/event-types/" + id)
                        .header("Authorization", "Bearer " + carley))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_unknownId_notFound() throws Exception {
        mockMvc.perform(delete("/api/event-types/00000000-0000-0000-0000-000000000000")
                        .header("Authorization", "Bearer " + token("carley401@gmail.com")))
                .andExpect(status().isNotFound());
    }
}
