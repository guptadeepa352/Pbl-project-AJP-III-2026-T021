package com.mca.securecloudstorage.exception;

public class FileMetadataDeletionException extends RuntimeException {
    public FileMetadataDeletionException(Throwable cause) {
        super("File metadata deletion failed after the encrypted object was removed", cause);
    }
}
