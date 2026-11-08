package com.mca.securecloudstorage.dto;

import java.time.Instant;

public record FileDownloadResponse(
        Long id,
        String originalFilename,
        long fileSize,
        String mimeType,
        String encryptionAlgorithm,
        String keyDerivationFunction,
        int keyDerivationIterations,
        String iv,
        String salt,
        Instant createdAt,
        String ciphertext) {
}
