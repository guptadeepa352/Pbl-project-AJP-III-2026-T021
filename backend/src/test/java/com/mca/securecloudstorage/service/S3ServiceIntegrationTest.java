package com.mca.securecloudstorage.service;

import com.mca.securecloudstorage.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "app.aws.s3.bucket=test-private-bucket")
@ActiveProfiles("test")
class S3ServiceIntegrationTest extends IntegrationTestSupport {
    private static final byte[] TEST_CIPHERTEXT = {
        (byte) 0x83, (byte) 0x17, (byte) 0xa2, (byte) 0x4c,
        (byte) 0x6e, (byte) 0xd9, (byte) 0x30, (byte) 0xf1
    };

    @Autowired
    private S3Service s3Service;

    @MockitoBean
    private S3Client s3Client;

    @BeforeEach
    void mockS3Download() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(
                        GetObjectResponse.builder().build(),
                        TEST_CIPHERTEXT));
    }

    @Test
    void uploadsDownloadsAndDeletesCiphertextUsingOnlyMockedS3() {
        String objectKey = s3Service.uploadCiphertext(TEST_CIPHERTEXT);
        assertArrayEquals(TEST_CIPHERTEXT, s3Service.download(objectKey));
        s3Service.delete(objectKey);

        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(s3Client).getObjectAsBytes(any(GetObjectRequest.class));
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }
}
