package com.mca.securecloudstorage.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3ServiceTest {
    @Mock
    private S3Client s3Client;

    @Test
    void uploadsCiphertextWithUnpredictableKeyAndNoPublicAcl() throws Exception {
        byte[] ciphertext = {0, 1, 2, 3, 4, 5};
        S3Service service = new S3Service(s3Client, "test-private-bucket");

        String objectKey = service.uploadCiphertext(ciphertext);

        assertTrue(objectKey.matches("objects/[0-9a-f]{64}"));
        ArgumentCaptor<PutObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(requestCaptor.capture(), bodyCaptor.capture());

        PutObjectRequest request = requestCaptor.getValue();
        assertEquals("test-private-bucket", request.bucket());
        assertEquals(objectKey, request.key());
        assertEquals("application/octet-stream", request.contentType());
        assertEquals("AES256", request.serverSideEncryptionAsString());
        assertNull(request.acl());
        try (InputStream stream = bodyCaptor.getValue().contentStreamProvider().newStream()) {
            assertArrayEquals(ciphertext, stream.readAllBytes());
        }
    }

    @Test
    void downloadsAndDeletesOnlyGeneratedKeyShapes() {
        byte[] ciphertext = {9, 8, 7};
        String objectKey = "objects/" + "a".repeat(64);
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(
                        GetObjectResponse.builder().build(),
                        ciphertext));
        S3Service service = new S3Service(s3Client, "test-private-bucket");

        assertArrayEquals(ciphertext, service.download(objectKey));
        service.delete(objectKey);

        verify(s3Client).getObjectAsBytes(GetObjectRequest.builder()
                .bucket("test-private-bucket")
                .key(objectKey)
                .build());
        verify(s3Client).deleteObject(DeleteObjectRequest.builder()
                .bucket("test-private-bucket")
                .key(objectKey)
                .build());
    }

    @Test
    void rejectsInvalidKeyAndEmptyCiphertextBeforeCallingS3() {
        S3Service service = new S3Service(s3Client, "test-private-bucket");

        assertThrows(IllegalArgumentException.class, () -> service.uploadCiphertext(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> service.download("../other-object"));
        assertThrows(IllegalArgumentException.class, () -> service.delete("users/some-key"));
    }

    @Test
    void reportsMissingBucketWhenUsingStorageOperations() {
        S3Service service = new S3Service(s3Client, "");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.uploadCiphertext(new byte[]{1}));

        assertEquals("S3 bucket is not configured; set S3_BUCKET", exception.getMessage());
    }
}
