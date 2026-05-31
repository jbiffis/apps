package com.biffis.tracker.controller;

import com.biffis.tracker.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.Period;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Biometrics: stored facts round-trip, derived fields (age from DOB, latest
 * weight/height, BMI) computed at read time, and validation/auth guards.
 */
@AutoConfigureMockMvc
class BiometricsControllerTest extends AbstractIntegrationTest {

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
    void biometrics_unauthenticated_401() throws Exception {
        mockMvc.perform(get("/api/me/biometrics")).andExpect(status().isUnauthorized());
    }

    @Test
    void putThenGet_storesFacts_andDerivesAge() throws Exception {
        String jeremy = token("jeremy@biffis.com");
        String dob = "1990-05-15";
        int expectedAge = Period.between(LocalDate.parse(dob), LocalDate.now()).getYears();

        mockMvc.perform(put("/api/me/biometrics")
                        .header("Authorization", "Bearer " + jeremy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dateOfBirth":"%s","biologicalSex":"male","bloodType":"O+",
                                 "activityLevel":"moderate","weightGoal":"maintain",
                                 "drugAllergies":"penicillin","chronicConditions":"none"}
                                """.formatted(dob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.biologicalSex", is("male")))
                .andExpect(jsonPath("$.bloodType", is("O+")))
                .andExpect(jsonPath("$.age", is(expectedAge)));

        mockMvc.perform(get("/api/me/biometrics").header("Authorization", "Bearer " + jeremy))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateOfBirth", is(dob)))
                .andExpect(jsonPath("$.drugAllergies", is("penicillin")))
                .andExpect(jsonPath("$.age", is(expectedAge)));
    }

    @Test
    void derivesLatestWeightHeight_andBmi_fromLog() throws Exception {
        String carley = token("carley401@gmail.com");

        logMeasurement(carley, "weight", 68.0);
        logMeasurement(carley, "height", 165.0);
        // A second, newer weight must win (derive = most recent, never stored).
        logMeasurement(carley, "weight", 70.5);

        // BMI = 70.5 / 1.65^2 = 25.895… → 25.9 (HALF_UP, 1 dp).
        mockMvc.perform(get("/api/me/biometrics").header("Authorization", "Bearer " + carley))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestWeightKg").value(closeTo(70.5, 0.001)))
                .andExpect(jsonPath("$.latestHeightCm").value(closeTo(165.0, 0.001)))
                .andExpect(jsonPath("$.bmi").value(closeTo(25.9, 0.001)));
    }

    @Test
    void unloggedMeasurements_deriveNull() throws Exception {
        // jeremy never logs weight/height in this test → derived stays null.
        // (No other test logs weight/height for jeremy.)
        String jeremy = token("jeremy@biffis.com");
        mockMvc.perform(get("/api/me/biometrics").header("Authorization", "Bearer " + jeremy))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestWeightKg", is(nullValue())))
                .andExpect(jsonPath("$.latestHeightCm", is(nullValue())))
                .andExpect(jsonPath("$.bmi", is(nullValue())));
    }

    @Test
    void invalidBloodType_422() throws Exception {
        mockMvc.perform(put("/api/me/biometrics")
                        .header("Authorization", "Bearer " + token("carley401@gmail.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bloodType\":\"Z-\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", is("validation_failed")));
    }

    @Test
    void futureDateOfBirth_422() throws Exception {
        String future = LocalDate.now().plusYears(1).toString();
        mockMvc.perform(put("/api/me/biometrics")
                        .header("Authorization", "Bearer " + token("carley401@gmail.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dateOfBirth\":\"" + future + "\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    private void logMeasurement(String token, String slug, double value) throws Exception {
        mockMvc.perform(post("/api/logged-events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventTypeSlug":"%s","options":[{"propertyName":"Measurement","value":%s}]}
                                """.formatted(slug, value)))
                .andExpect(status().isCreated());
    }
}
