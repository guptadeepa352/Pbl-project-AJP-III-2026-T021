package com.mca.securecloudstorage.exception;

import com.mca.securecloudstorage.dto.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }

        return errorResponse(
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                fieldErrors);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateEmail(
            EmailAlreadyExistsException exception) {
        return errorResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                Map.of());
    }

    @ExceptionHandler(FileMetadataNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleFileMetadataNotFound() {
        return errorResponse(
                HttpStatus.NOT_FOUND,
                "File metadata not found",
                Map.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidFileMetadata(
            IllegalArgumentException exception) {
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                Map.of());
    }

    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<ApiErrorResponse> handleFileStorageFailure() {
        return errorResponse(
                HttpStatus.BAD_GATEWAY,
                "Encrypted file storage is temporarily unavailable",
                Map.of());
    }

    @ExceptionHandler(FileMetadataDeletionException.class)
    public ResponseEntity<ApiErrorResponse> handleFileMetadataDeletionFailure() {
        return errorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "File removal could not be completed; retry the request",
                Map.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableRequest() {
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                "Request body is missing or contains invalid JSON",
                Map.of());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials() {
        return errorResponse(
                HttpStatus.UNAUTHORIZED,
                "Invalid email or password",
                Map.of());
    }

    private ResponseEntity<ApiErrorResponse> errorResponse(
            HttpStatus status,
            String message,
            Map<String, String> fieldErrors) {
        ApiErrorResponse body = new ApiErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                message,
                fieldErrors);
        return ResponseEntity.status(status).body(body);
    }
}
