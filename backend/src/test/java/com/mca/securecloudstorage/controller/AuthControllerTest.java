package com.mca.securecloudstorage.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mca.securecloudstorage.IntegrationTestSupport;
import com.mca.securecloudstorage.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthControllerTest extends IntegrationTestSupport {
    private static final String RAW_PASSWORD = "SecurePassword1!";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerCreatesUserAndNeverReturnsPasswordOrHash() throws Exception {
        String email = uniqueEmail();
        String requestJson = objectMapper.writeValueAsString(Map.of(
                "name", "Registration Test",
                "email", email.toUpperCase(Locale.ROOT),
                "password", RAW_PASSWORD));

        ResponseEntity<String> response = postRegistration(requestJson);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        JsonNode body = objectMapper.readTree(response.getBody());
        assertTrue(body.hasNonNull("id"));
        assertEquals("Registration Test", body.path("name").asText());
        assertEquals(email, body.path("email").asText());
        assertTrue(body.hasNonNull("createdAt"));
        assertFalse(body.has("password"));
        assertFalse(body.has("passwordHash"));
        assertFalse(body.has("password_hash"));
        assertFalse(response.getBody().contains(RAW_PASSWORD));

        var user = userRepository.findByEmail(email).orElseThrow();
        assertNotEqualsRawPassword(user.getPasswordHash());
    }

    @Test
    void registerRejectsDuplicateEmailWithConflict() throws Exception {
        String email = uniqueEmail();
        String requestJson = objectMapper.writeValueAsString(Map.of(
                "name", "Registration Test",
                "email", email,
                "password", RAW_PASSWORD));

        ResponseEntity<String> firstResponse = postRegistration(requestJson);
        ResponseEntity<String> duplicateResponse = postRegistration(requestJson);

        assertEquals(HttpStatus.CREATED, firstResponse.getStatusCode());
        assertEquals(HttpStatus.CONFLICT, duplicateResponse.getStatusCode());
        JsonNode body = objectMapper.readTree(duplicateResponse.getBody());
        assertEquals(HttpStatus.CONFLICT.value(), body.path("status").asInt());
        assertTrue(body.path("message").asText().contains("already exists"));
        assertFalse(duplicateResponse.getBody().contains(RAW_PASSWORD));
    }

    @Test
    void registerReturnsFieldErrorsForInvalidEmailAndWeakPassword() throws Exception {
        String requestJson = objectMapper.writeValueAsString(Map.of(
                "name", "Registration Test",
                "email", "not-an-email",
                "password", "weak"));

        ResponseEntity<String> response = postRegistration(requestJson);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(HttpStatus.BAD_REQUEST.value(), body.path("status").asInt());
        assertTrue(body.path("fieldErrors").has("email"));
        assertTrue(body.path("fieldErrors").has("password"));
        assertFalse(response.getBody().contains("weak"));
    }

    @Test
    void registerRejectsPasswordsLongerThanBcryptByteLimit() throws Exception {
        String requestJson = objectMapper.writeValueAsString(Map.of(
                "name", "Registration Test",
                "email", uniqueEmail(),
                "password", "Aa1!" + "\u00e9".repeat(35)));

        ResponseEntity<String> response = postRegistration(requestJson);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        JsonNode body = objectMapper.readTree(response.getBody());
        assertTrue(body.path("fieldErrors").has("password"));
    }

    private ResponseEntity<String> postRegistration(String requestJson) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(
                "/api/auth/register",
                new HttpEntity<>(requestJson, headers),
                String.class);
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@example.test";
    }

    private void assertNotEqualsRawPassword(String passwordHash) {
        assertNotNull(passwordHash);
        assertFalse(RAW_PASSWORD.equals(passwordHash));
        assertTrue(passwordHash.startsWith("$2"));
    }
}
