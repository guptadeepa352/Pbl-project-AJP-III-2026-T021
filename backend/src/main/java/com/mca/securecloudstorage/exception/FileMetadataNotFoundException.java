package com.mca.securecloudstorage.exception;

public class FileMetadataNotFoundException extends RuntimeException {
    public FileMetadataNotFoundException() {
        super("File metadata not found");
    }
}
