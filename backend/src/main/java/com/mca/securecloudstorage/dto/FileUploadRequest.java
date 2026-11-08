package com.mca.securecloudstorage.dto;

import com.mca.securecloudstorage.util.FileUploadLimits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FileUploadRequest(
        @NotBlank(message = "Filename is required")
        @Size(max = 255, message = "Filename must be at most 255 characters")
        String originalFilename,
        @NotBlank(message = "MIME type is required")
        @Size(max = 127, message = "MIME type must be at most 127 characters")
        String mimeType,
        @Min(value = 0, message = "File size must not be negative")
        @Max(value = FileUploadLimits.MAX_PLAINTEXT_SIZE_BYTES,
                message = "File must not exceed 10 MiB")
        long fileSize,
        @NotBlank
        @Pattern(regexp = "AES-256-GCM", message = "Unsupported encryption algorithm")
        String encryptionAlgorithm,
        @NotBlank
        @Pattern(regexp = "PBKDF2-SHA-256", message = "Unsupported key derivation function")
        String keyDerivationFunction,
        @Min(value = 600000, message = "Unsupported PBKDF2 iteration count")
        @Max(value = 600000, message = "Unsupported PBKDF2 iteration count")
        int keyDerivationIterations,
        @NotBlank
        @Size(max = 32, message = "IV encoding is too long")
        @Pattern(regexp = "[A-Za-z0-9_-]+", message = "IV must be base64url encoded")
        String iv,
        @NotBlank
        @Size(max = 32, message = "Salt encoding is too long")
        @Pattern(regexp = "[A-Za-z0-9_-]+", message = "Salt must be base64url encoded")
        String salt,
        @NotBlank
        @Size(max = FileUploadLimits.MAX_BASE64URL_CIPHERTEXT_LENGTH,
                message = "Encrypted file exceeds the 10 MiB upload limit")
        @Pattern(regexp = "[A-Za-z0-9_-]+", message = "Ciphertext must be base64url encoded")
        String ciphertext) {
}
