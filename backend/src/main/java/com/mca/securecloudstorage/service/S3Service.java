package com.mca.securecloudstorage.service;

import org.springframework.beans.factory.annotation.Value;
import com.mca.securecloudstorage.exception.FileStorageException;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

import java.security.SecureRandom;
import java.util.HexFormat;

@Service
public class S3Service {
    private static final String OBJECT_KEY_PREFIX = "objects/";
    private static final String OBJECT_KEY_PATTERN = "objects/[0-9a-f]{64}";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final S3Client s3Client;
    private final String bucketName;

    public S3Service(
            S3Client s3Client,
            @Value("${app.aws.s3.bucket:}") String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName.trim();
    }

    public String uploadCiphertext(byte[] ciphertext) {
        if (ciphertext == null || ciphertext.length == 0) {
            throw new IllegalArgumentException("Ciphertext must not be empty");
        }

        String objectKey = generateObjectKey();
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(requiredBucketName())
                .key(objectKey)
                .contentType("application/octet-stream")
                .serverSideEncryption(ServerSideEncryption.AES256)
                .build();
        try {
            s3Client.putObject(request, RequestBody.fromBytes(ciphertext));
        } catch (SdkException exception) {
            throw new FileStorageException(exception);
        }
        return objectKey;
    }

    public byte[] download(String objectKey) {
        validateObjectKey(objectKey);
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(requiredBucketName())
                .key(objectKey)
                .build();
        try {
            ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(request);
            return response.asByteArray();
        } catch (SdkException exception) {
            throw new FileStorageException(exception);
        }
    }

    public void delete(String objectKey) {
        validateObjectKey(objectKey);
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(requiredBucketName())
                .key(objectKey)
                .build();
        try {
            s3Client.deleteObject(request);
        } catch (SdkException exception) {
            throw new FileStorageException(exception);
        }
    }

    private String requiredBucketName() {
        if (bucketName.isEmpty()) {
            throw new IllegalStateException("S3 bucket is not configured; set S3_BUCKET");
        }
        return bucketName;
    }

    private static String generateObjectKey() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return OBJECT_KEY_PREFIX + HexFormat.of().formatHex(randomBytes);
    }

    private static void validateObjectKey(String objectKey) {
        if (objectKey == null || !objectKey.matches(OBJECT_KEY_PATTERN)) {
            throw new IllegalArgumentException("Invalid S3 object key");
        }
    }
}
