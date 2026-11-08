package com.mca.securecloudstorage.dto;

import java.time.Instant;

public record RegistrationResponse(
        Long id,
        String name,
        String email,
        Instant createdAt) {
}
