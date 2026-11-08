package com.mca.securecloudstorage.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds) {
}
