package com.mca.securecloudstorage.exception;

public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException() {
        super("An account with this email address already exists");
    }

    public EmailAlreadyExistsException(Throwable cause) {
        super("An account with this email address already exists", cause);
    }
}
