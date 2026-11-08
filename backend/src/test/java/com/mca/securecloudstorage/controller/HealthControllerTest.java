package com.mca.securecloudstorage.controller;

import com.mca.securecloudstorage.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HealthControllerTest extends IntegrationTestSupport {
    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void healthEndpointReturnsUpStatusWithoutAuthentication() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/api/health", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(
                response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON));
        assertEquals("{\"status\":\"UP\"}", response.getBody());
    }

    @Test
    void protectedEndpointsRequireAuthentication() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/api/not-implemented", String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
