package com.mca.securecloudstorage.service;

import com.mca.securecloudstorage.dto.FileMetadataResponse;
import com.mca.securecloudstorage.dto.FileDownloadResponse;
import com.mca.securecloudstorage.dto.FileUploadRequest;
import com.mca.securecloudstorage.entity.FileMetadata;
import com.mca.securecloudstorage.entity.User;
import com.mca.securecloudstorage.exception.FileStorageException;
import com.mca.securecloudstorage.exception.FileMetadataDeletionException;
import com.mca.securecloudstorage.exception.FileMetadataNotFoundException;
import com.mca.securecloudstorage.repository.FileMetadataRepository;
import com.mca.securecloudstorage.repository.UserRepository;
import com.mca.securecloudstorage.util.FileUploadLimits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class FileService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileService.class);
    private static final Pattern MIME_TYPE_PATTERN =
            Pattern.compile("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+");
    private static final Pattern SAFE_FILENAME_PATTERN =
            Pattern.compile("[^/\\\\\\p{Cntrl}]+");
    private static final String ENCRYPTION_ALGORITHM = "AES-256-GCM";
    private static final String KEY_DERIVATION_FUNCTION = "PBKDF2-SHA-256";
    private static final int KEY_DERIVATION_ITERATIONS = 600000;

    private final FileMetadataRepository fileMetadataRepository;
    private final UserRepository userRepository;
    private final S3Service s3Service;

    public FileService(
            FileMetadataRepository fileMetadataRepository,
            UserRepository userRepository,
            S3Service s3Service) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.userRepository = userRepository;
        this.s3Service = s3Service;
    }

    @Transactional
    public FileMetadataResponse uploadFile(
            FileUploadRequest request,
            String authenticatedEmail) {
        validateUpload(request);
        User owner = findOwner(authenticatedEmail);
        byte[] ciphertext = decodeCiphertext(request.ciphertext());
        if (ciphertext.length != request.fileSize() + FileUploadLimits.AES_GCM_TAG_SIZE_BYTES) {
            throw new IllegalArgumentException("Ciphertext length does not match encrypted file metadata");
        }

        String objectKey = s3Service.uploadCiphertext(ciphertext);
        FileMetadata metadata = new FileMetadata(
                owner,
                request.originalFilename(),
                objectKey,
                request.fileSize(),
                request.mimeType(),
                request.encryptionAlgorithm(),
                request.keyDerivationFunction(),
                request.keyDerivationIterations(),
                request.iv(),
                request.salt());
        try {
            return toResponse(fileMetadataRepository.saveAndFlush(metadata));
        } catch (RuntimeException exception) {
            try {
                s3Service.delete(objectKey);
            } catch (FileStorageException cleanupException) {
                exception.addSuppressed(cleanupException);
                LOGGER.error(
                        "Could not remove encrypted S3 object after metadata persistence failed",
                        cleanupException);
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<FileMetadataResponse> listFiles(String authenticatedEmail) {
        User owner = findOwner(authenticatedEmail);
        return fileMetadataRepository.findAllByOwner_IdOrderByCreatedAtDesc(owner.getId())
                .stream()
                .map(FileService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public FileMetadataResponse getFile(Long id, String authenticatedEmail) {
        User owner = findOwner(authenticatedEmail);
        return fileMetadataRepository.findByIdAndOwner_Id(id, owner.getId())
                .map(FileService::toResponse)
                .orElseThrow(FileMetadataNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public FileDownloadResponse downloadFile(Long id, String authenticatedEmail) {
        User owner = findOwner(authenticatedEmail);
        FileMetadata metadata = fileMetadataRepository.findByIdAndOwner_Id(id, owner.getId())
                .orElseThrow(FileMetadataNotFoundException::new);
        byte[] ciphertext = s3Service.download(metadata.getS3ObjectKey());
        if (ciphertext.length != metadata.getFileSize() + FileUploadLimits.AES_GCM_TAG_SIZE_BYTES) {
            throw new FileStorageException(
                    new IllegalStateException("Stored ciphertext length does not match metadata"));
        }
        return new FileDownloadResponse(
                metadata.getId(),
                metadata.getOriginalFilename(),
                metadata.getFileSize(),
                metadata.getMimeType(),
                metadata.getEncryptionAlgorithm(),
                metadata.getKeyDerivationFunction(),
                metadata.getKeyDerivationIterations(),
                metadata.getIv(),
                metadata.getSalt(),
                metadata.getCreatedAt(),
                Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext));
    }

    @Transactional
    public void deleteFile(Long id, String authenticatedEmail) {
        User owner = findOwner(authenticatedEmail);
        FileMetadata metadata = fileMetadataRepository.findByIdAndOwner_Id(id, owner.getId())
                .orElseThrow(FileMetadataNotFoundException::new);
        s3Service.delete(metadata.getS3ObjectKey());
        try {
            fileMetadataRepository.delete(metadata);
            fileMetadataRepository.flush();
        } catch (DataAccessException exception) {
            LOGGER.error(
                    "File metadata deletion failed after its encrypted object was removed",
                    exception);
            throw new FileMetadataDeletionException(exception);
        }
    }

    private User findOwner(String authenticatedEmail) {
        return userRepository.findByEmail(authenticatedEmail.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(FileMetadataNotFoundException::new);
    }

    private static FileMetadataResponse toResponse(FileMetadata metadata) {
        return new FileMetadataResponse(
                metadata.getId(),
                metadata.getOriginalFilename(),
                metadata.getFileSize(),
                metadata.getMimeType(),
                metadata.getEncryptionAlgorithm(),
                metadata.getKeyDerivationFunction(),
                metadata.getKeyDerivationIterations(),
                metadata.getIv(),
                metadata.getSalt(),
                metadata.getCreatedAt());
    }

    private static void validateUpload(FileUploadRequest request) {
        String filename = request.originalFilename();
        if (!SAFE_FILENAME_PATTERN.matcher(filename).matches()
                || filename.isBlank()
                || filename.equals(".")
                || filename.equals("..")
                || !filename.equals(filename.strip())) {
            throw new IllegalArgumentException("Filename must be a plain filename without path characters");
        }
        if (!MIME_TYPE_PATTERN.matcher(request.mimeType()).matches()) {
            throw new IllegalArgumentException("MIME type is invalid");
        }
        if (!ENCRYPTION_ALGORITHM.equals(request.encryptionAlgorithm())
                || !KEY_DERIVATION_FUNCTION.equals(request.keyDerivationFunction())
                || request.keyDerivationIterations() != KEY_DERIVATION_ITERATIONS) {
            throw new IllegalArgumentException("Unsupported encryption parameters");
        }
        byte[] iv = decodeMetadata(request.iv(), 12, "IV");
        byte[] salt = decodeMetadata(request.salt(), 16, "salt");
        if (iv.length != 12 || salt.length != 16) {
            throw new IllegalArgumentException("Encryption metadata has an invalid IV or salt length");
        }
    }

    private static byte[] decodeCiphertext(String encodedCiphertext) {
        try {
            return Base64.getUrlDecoder().decode(encodedCiphertext);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Ciphertext must be valid base64url data", exception);
        }
    }

    private static byte[] decodeMetadata(String encoded, int maxLength, String fieldName) {
        if (encoded.length() > maxLength * 2) {
            throw new IllegalArgumentException(fieldName + " encoding is too long");
        }
        try {
            return Base64.getUrlDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(fieldName + " must be valid base64url data", exception);
        }
    }
}
