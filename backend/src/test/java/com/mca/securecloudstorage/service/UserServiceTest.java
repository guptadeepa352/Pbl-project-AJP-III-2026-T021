package com.mca.securecloudstorage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mca.securecloudstorage.IntegrationTestSupport;
import com.mca.securecloudstorage.entity.User;
import com.mca.securecloudstorage.exception.EmailAlreadyExistsException;
import com.mca.securecloudstorage.repository.UserRepository;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class UserServiceTest extends IntegrationTestSupport {
    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createUserStoresBcryptHashAndTimestamps() throws JsonProcessingException {
        String rawPassword = "SecurePassword1!";
        String email = uniqueEmail();

        User user =
                userService.createUser(" Test User ", email.toUpperCase(Locale.ROOT), rawPassword);

        assertNotNull(user.getId());
        assertEquals("Test User", user.getName());
        assertEquals(email, user.getEmail());
        assertNotEquals(rawPassword, user.getPasswordHash());
        assertTrue(passwordEncoder.matches(rawPassword, user.getPasswordHash()));
        assertNotNull(user.getCreatedAt());
        assertNotNull(user.getUpdatedAt());
        assertTrue(userRepository.existsByEmail(email));
        assertFalse(objectMapper.writeValueAsString(user).contains("passwordHash"));
    }

    @Test
    void createUserRejectsDuplicateEmailIgnoringCase() {
        String email = uniqueEmail();
        userService.createUser("First User", email, "FirstSecurePassword1!");

        assertThrows(
                EmailAlreadyExistsException.class,
                () -> userService.createUser(
                        "Second User",
                        email.toUpperCase(Locale.ROOT),
                        "SecondSecurePassword1!"));
    }

    @Test
    void createUserRejectsInvalidEmail() {
        assertThrows(
                ConstraintViolationException.class,
                () -> userService.createUser("Test User", "not-an-email", "local-test-password"));
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@example.test";
    }
}
