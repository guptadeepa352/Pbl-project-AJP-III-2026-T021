package com.mca.securecloudstorage.exception;

public class FileStorageException extends RuntimeException {
    public FileStorageException(Throwable cause) {
        super("Encrypted file storage operation failed", cause);
    }
}
