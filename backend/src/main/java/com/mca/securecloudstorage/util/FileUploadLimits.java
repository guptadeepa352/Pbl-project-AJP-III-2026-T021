package com.mca.securecloudstorage.util;

public final class FileUploadLimits {
    public static final long MAX_PLAINTEXT_SIZE_BYTES = 10L * 1024 * 1024;
    public static final int AES_GCM_TAG_SIZE_BYTES = 16;
    public static final int MAX_CIPHERTEXT_SIZE_BYTES =
            (int) MAX_PLAINTEXT_SIZE_BYTES + AES_GCM_TAG_SIZE_BYTES;
    public static final int MAX_BASE64URL_CIPHERTEXT_LENGTH =
            (MAX_CIPHERTEXT_SIZE_BYTES * 4 + 2) / 3;

    private FileUploadLimits() {
    }
}
