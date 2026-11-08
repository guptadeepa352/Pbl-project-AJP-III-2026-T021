package com.mca.securecloudstorage.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mca.securecloudstorage.IntegrationTestSupport;
import com.mca.securecloudstorage.security.CustomUserDetailsService;
import com.mca.securecloudstorage.security.JwtService;
import com.mca.securecloudstorage.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LoginControllerTest extends IntegrationTestSupport {
    private static final String PASSWORD = "SecureLoginPassword1!";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Test
    void loginReturnsBearerJwtWhichAuthenticatesProtectedRequests() throws Exception {
        String email = createUser();

        ResponseEntity<String> loginResponse = login(email, PASSWORD);

        assertEquals(HttpStatus.OK, loginResponse.getStatusCode());
        JsonNode body = objectMapper.readTree(loginResponse.getBody());
        String token = body.path("accessToken").asText();
        assertFalse(token.isBlank());
        assertEquals("Bearer", body.path("tokenType").asText());
        assertEquals(60, body.path("expiresInSeconds").asLong());
        assertFalse(body.has("password"));
        assertFalse(body.has("passwordHash"));
        assertFalse(loginResponse.getBody().contains(PASSWORD));
        assertEquals(email, jwtService.extractUsername(token));
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        assertTrue(jwtService.isTokenValid(token, userDetails));

        HttpHeaders profileHeaders = new HttpHeaders();
        profileHeaders.setBearerAuth(token);
        ResponseEntity<String> profileResponse = restTemplate.exchange(
                "/api/auth/me",
                HttpMethod.GET,
                new HttpEntity<>(profileHeaders),
                String.class);
        assertEquals(HttpStatus.OK, profileResponse.getStatusCode());
        assertEquals(email, objectMapper.readTree(profileResponse.getBody()).path("email").asText());

        ResponseEntity<String> protectedWithoutToken =
                restTemplate.getForEntity("/api/test/protected", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, protectedWithoutToken.getStatusCode());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<String> protectedWithToken = restTemplate.exchange(
                "/api/test/protected",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
        assertEquals(HttpStatus.OK, protectedWithToken.getStatusCode());
        assertEquals("authenticated", protectedWithToken.getBody());
    }

    @Test
    void corsAllowsLocalFrontendOriginAndAuthorizationHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("http://localhost:5500");
        headers.set("Access-Control-Request-Method", "GET");
        headers.set("Access-Control-Request-Headers", "authorization");

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/auth/me",
                HttpMethod.OPTIONS,
                new HttpEntity<>(headers),
                String.class);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals("http://localhost:5500",
                response.getHeaders().getFirst("Access-Control-Allow-Origin"));
        assertTrue(response.getHeaders().getFirst("Access-Control-Allow-Headers")
                .toLowerCase(java.util.Locale.ROOT).contains("authorization"));
    }

    @Test
    void loginRejectsIncorrectPasswordWithoutRevealingCredentials() throws Exception {
        String email = createUser();

        ResponseEntity<String> response = login(email, "IncorrectLoginPassword1!");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Invalid email or password", body.path("message").asText());
        assertFalse(response.getBody().contains("IncorrectLoginPassword1!"));
    }

    @Test
    void loginRejectsUnknownEmailWithSameGenericError() throws Exception {
        ResponseEntity<String> response = login(uniqueEmail(), PASSWORD);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Invalid email or password", body.path("message").asText());
    }

    @Test
    void loginRejectsMalformedBearerTokenOnProtectedEndpoint() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("not-a-valid-jwt");

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/test/protected",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void loginRejectsTamperedJwtOnProtectedEndpoint() throws Exception {
        String email = createUser();
        JsonNode loginBody = objectMapper.readTree(login(email, PASSWORD).getBody());
        String token = loginBody.path("accessToken").asText();
        String[] tokenParts = token.split("\\.", -1);
        String signature = tokenParts[2];
        String tamperedSignature =
                (signature.charAt(0) == 'a' ? "b" : "a") + signature.substring(1);
        String tamperedToken = tokenParts[0] + "." + tokenParts[1] + "." + tamperedSignature;
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tamperedToken);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/test/protected",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void loginRejectsExpiredJwtOnProtectedEndpoint() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(expiredToken(uniqueEmail()));

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/test/protected",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void loginValidatesEmailAndRequiredPassword() throws Exception {
        String requestJson = objectMapper.writeValueAsString(Map.of(
                "email", "invalid-email",
                "password", " "));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/login",
                new HttpEntity<>(requestJson, headers),
                String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        JsonNode body = objectMapper.readTree(response.getBody());
        assertTrue(body.path("fieldErrors").has("email"));
        assertTrue(body.path("fieldErrors").has("password"));
    }

    private String createUser() {
        String email = uniqueEmail();
        userService.createUser("Login Test User", email, PASSWORD);
        return email;
    }

    private ResponseEntity<String> login(String email, String password) throws Exception {
        String requestJson = objectMapper.writeValueAsString(Map.of(
                "email", email,
                "password", password));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(
                "/api/auth/login",
                new HttpEntity<>(requestJson, headers),
                String.class);
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@example.test";
    }
}
