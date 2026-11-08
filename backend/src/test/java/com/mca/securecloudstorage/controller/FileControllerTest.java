package com.mca.securecloudstorage.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mca.securecloudstorage.IntegrationTestSupport;
import com.mca.securecloudstorage.entity.FileMetadata;
import com.mca.securecloudstorage.entity.User;
import com.mca.securecloudstorage.security.JwtService;
import com.mca.securecloudstorage.repository.FileMetadataRepository;
import com.mca.securecloudstorage.repository.UserRepository;
import com.mca.securecloudstorage.service.UserService;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.core.ResponseBytes;

import java.io.InputStream;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.aws.s3.bucket=test-private-bucket")
@ActiveProfiles("test")
class FileControllerTest extends IntegrationTestSupport {
    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileMetadataRepository fileMetadataRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private S3Client s3Client;

    @BeforeEach
    void clearFileMetadata() {
        fileMetadataRepository.deleteAll();
    }

    @Test
    void uploadStoresOnlyCiphertextAndReturnsOwnedFileMetadata() throws Exception {
        User owner = createUser();
        byte[] ciphertext = new byte[16];
        String requestBody = uploadJson("ciphertext-only.pdf", "application/pdf", 0, ciphertext);
        HttpHeaders headers = headersFor(owner.getEmail());
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/files/upload",
                new HttpEntity<>(requestBody, headers),
                String.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("ciphertext-only.pdf", body.path("originalFilename").asText());
        assertEquals(0, body.path("fileSize").asLong());
        assertEquals("AES-256-GCM", body.path("encryptionAlgorithm").asText());
        assertEquals("PBKDF2-SHA-256", body.path("keyDerivationFunction").asText());
        assertFalse(body.has("s3ObjectKey"));
        assertFalse(body.has("ciphertext"));

        ArgumentCaptor<PutObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(requestCaptor.capture(), bodyCaptor.capture());
        assertEquals("test-private-bucket", requestCaptor.getValue().bucket());
        try (InputStream uploaded = bodyCaptor.getValue().contentStreamProvider().newStream()) {
            assertTrue(java.util.Arrays.equals(ciphertext, uploaded.readAllBytes()));
        }
        var storedFiles =
                fileMetadataRepository.findAllByOwner_IdOrderByCreatedAtDesc(owner.getId());
        assertEquals(1, storedFiles.size());
        assertTrue(storedFiles.get(0).getS3ObjectKey().matches("objects/[0-9a-f]{64}"));
        assertEquals(600000, storedFiles.get(0).getKeyDerivationIterations());
    }

    @Test
    void uploadRejectsInvalidFilenameAndMismatchedCiphertextLength() throws Exception {
        User owner = createUser();
        HttpHeaders headers = headersFor(owner.getEmail());
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        ResponseEntity<String> invalidFilename = restTemplate.postForEntity(
                "/api/files/upload",
                new HttpEntity<>(uploadJson("../secret.pdf", "application/pdf", 0, new byte[16]), headers),
                String.class);
        ResponseEntity<String> mismatchedLength = restTemplate.postForEntity(
                "/api/files/upload",
                new HttpEntity<>(uploadJson("secret.pdf", "application/pdf", 1, new byte[16]), headers),
                String.class);
        ResponseEntity<String> invalidMimeType = restTemplate.postForEntity(
                "/api/files/upload",
                new HttpEntity<>(uploadJson(
                        "secret.pdf",
                        "application/pdf; charset=utf-8",
                        0,
                        new byte[16]),
                        headers),
                String.class);
        ResponseEntity<String> oversizedFile = restTemplate.postForEntity(
                "/api/files/upload",
                new HttpEntity<>(uploadJson(
                        "large.pdf",
                        "application/pdf",
                        10L * 1024 * 1024 + 1,
                        new byte[16]),
                        headers),
                String.class);

        assertEquals(HttpStatus.BAD_REQUEST, invalidFilename.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, mismatchedLength.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, invalidMimeType.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, oversizedFile.getStatusCode());
        assertEquals(0, fileMetadataRepository.count());
    }

    @Test
    void uploadAndDownloadPreserveEncryptedPayloadWithoutExposingKeys() throws Exception {
        User owner = createUser();
        byte[] ciphertext = new byte[32];
        new java.security.SecureRandom().nextBytes(ciphertext);
        String requestBody = uploadJson("encrypted.pdf", "application/pdf", 16, ciphertext);
        HttpHeaders headers = headersFor(owner.getEmail());
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        ResponseEntity<String> uploadResponse = restTemplate.postForEntity(
                "/api/files/upload",
                new HttpEntity<>(requestBody, headers),
                String.class);
        assertEquals(HttpStatus.CREATED, uploadResponse.getStatusCode());

        long fileId = objectMapper.readTree(uploadResponse.getBody()).path("id").asLong();
        when(s3Client.getObjectAsBytes(org.mockito.ArgumentMatchers.any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(
                        GetObjectResponse.builder().build(),
                        ciphertext));
        ResponseEntity<String> downloadResponse =
                get("/api/files/" + fileId + "/download", owner.getEmail());

        assertEquals(HttpStatus.OK, downloadResponse.getStatusCode());
        JsonNode downloaded = objectMapper.readTree(downloadResponse.getBody());
        assertEquals(Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext),
                downloaded.path("ciphertext").asText());
        assertFalse(downloadResponse.getBody().contains("encryptionSecret"));
        assertFalse(downloadResponse.getBody().contains("password"));
        ArgumentCaptor<PutObjectRequest> putRequest =
                ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> putBody = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(putRequest.capture(), putBody.capture());
        try (InputStream uploaded = putBody.getValue().contentStreamProvider().newStream()) {
            assertTrue(java.util.Arrays.equals(ciphertext, uploaded.readAllBytes()));
        }
    }

    @Test
    void uploadRequiresAuthentication() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/files/upload",
                new HttpEntity<>("{}"),
                String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void downloadReturnsCiphertextOnlyToTheOwningUser() throws Exception {
        User owner = createUser();
        User otherUser = createUser();
        FileMetadata metadata = createFile(owner, "download.pdf");
        byte[] ciphertext = new byte[(int) metadata.getFileSize() + 16];
        java.util.Arrays.fill(ciphertext, (byte) 0x5a);
        when(s3Client.getObjectAsBytes(org.mockito.ArgumentMatchers.any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(
                        GetObjectResponse.builder().build(),
                        ciphertext));

        ResponseEntity<String> ownerResponse =
                get("/api/files/" + metadata.getId() + "/download", owner.getEmail());
        ResponseEntity<String> otherUserResponse =
                get("/api/files/" + metadata.getId() + "/download", otherUser.getEmail());

        assertEquals(HttpStatus.OK, ownerResponse.getStatusCode());
        JsonNode body = objectMapper.readTree(ownerResponse.getBody());
        assertEquals(metadata.getId(), body.path("id").asLong());
        assertEquals("download.pdf", body.path("originalFilename").asText());
        assertEquals(Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext),
                body.path("ciphertext").asText());
        assertFalse(body.has("s3ObjectKey"));
        assertFalse(body.has("encryptionSecret"));
        assertFalse(body.has("key"));
        assertEquals(HttpStatus.NOT_FOUND, otherUserResponse.getStatusCode());

        ArgumentCaptor<GetObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObjectAsBytes(requestCaptor.capture());
        assertEquals(metadata.getS3ObjectKey(), requestCaptor.getValue().key());
    }

    @Test
    void downloadRejectsUnauthenticatedAndCrossUserRequestsWithoutCallingS3() {
        User owner = createUser();
        User otherUser = createUser();
        FileMetadata metadata = createFile(owner, "private.pdf");

        ResponseEntity<String> unauthenticated = restTemplate.getForEntity(
                "/api/files/" + metadata.getId() + "/download",
                String.class);
        ResponseEntity<String> crossUser =
                get("/api/files/" + metadata.getId() + "/download", otherUser.getEmail());

        assertEquals(HttpStatus.UNAUTHORIZED, unauthenticated.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, crossUser.getStatusCode());
        verify(s3Client, never())
                .getObjectAsBytes(org.mockito.ArgumentMatchers.any(GetObjectRequest.class));
    }

    @Test
    void listFilesReturnsOnlyMetadataOwnedByAuthenticatedUser() throws Exception {
        User firstUser = createUser();
        User secondUser = createUser();
        createFile(firstUser, "first.pdf");
        createFile(secondUser, "second.pdf");

        ResponseEntity<String> response = get("/api/files", firstUser.getEmail());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode files = objectMapper.readTree(response.getBody());
        assertEquals(1, files.size());
        assertEquals("first.pdf", files.get(0).path("originalFilename").asText());
        assertFalse(files.get(0).has("s3ObjectKey"));
        assertFalse(files.get(0).has("owner"));
    }

    @Test
    void getFileAllowsOwnerAndHidesAnotherUsersMetadata() throws Exception {
        User owner = createUser();
        User otherUser = createUser();
        FileMetadata metadata = createFile(owner, "private.pdf");

        ResponseEntity<String> ownerResponse =
                get("/api/files/" + metadata.getId(), owner.getEmail());
        ResponseEntity<String> otherUserResponse =
                get("/api/files/" + metadata.getId(), otherUser.getEmail());

        assertEquals(HttpStatus.OK, ownerResponse.getStatusCode());
        assertEquals("private.pdf",
                objectMapper.readTree(ownerResponse.getBody()).path("originalFilename").asText());
        assertEquals(HttpStatus.NOT_FOUND, otherUserResponse.getStatusCode());
        assertEquals("File metadata not found",
                objectMapper.readTree(otherUserResponse.getBody()).path("message").asText());
    }

    @Test
    void deleteFileOnlyDeletesMetadataOwnedByAuthenticatedUser() {
        User owner = createUser();
        User otherUser = createUser();
        FileMetadata metadata = createFile(owner, "private.pdf");

        ResponseEntity<Void> denied = delete("/api/files/" + metadata.getId(), otherUser.getEmail());

        assertEquals(HttpStatus.NOT_FOUND, denied.getStatusCode());
        assertTrue(fileMetadataRepository.existsById(metadata.getId()));
        verify(s3Client, never()).deleteObject(
                org.mockito.ArgumentMatchers.any(DeleteObjectRequest.class));

        ResponseEntity<Void> deleted = delete("/api/files/" + metadata.getId(), owner.getEmail());

        assertEquals(HttpStatus.NO_CONTENT, deleted.getStatusCode());
        assertFalse(fileMetadataRepository.existsById(metadata.getId()));
        verify(s3Client).deleteObject(org.mockito.ArgumentMatchers.argThat(
                (DeleteObjectRequest request) -> metadata.getS3ObjectKey().equals(request.key())));
    }

    @Test
    void deleteDoesNotRemoveMetadataWhenS3DeletionFails() {
        User owner = createUser();
        FileMetadata metadata = createFile(owner, "retry-delete.pdf");
        when(s3Client.deleteObject(org.mockito.ArgumentMatchers.any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder()
                        .message("S3 test failure")
                        .statusCode(503)
                        .build());

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/files/" + metadata.getId(),
                HttpMethod.DELETE,
                new HttpEntity<>(headersFor(owner.getEmail())),
                String.class);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertTrue(fileMetadataRepository.existsById(metadata.getId()));
        verify(s3Client).deleteObject(
                org.mockito.ArgumentMatchers.any(DeleteObjectRequest.class));
    }

    @Test
    void fileEndpointsRequireAuthentication() {
        ResponseEntity<String> listResponse = restTemplate.getForEntity("/api/files", String.class);
        ResponseEntity<String> getResponse =
                restTemplate.getForEntity("/api/files/1", String.class);
        ResponseEntity<String> deleteResponse = restTemplate.exchange(
                "/api/files/1",
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, listResponse.getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, getResponse.getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, deleteResponse.getStatusCode());
        ResponseEntity<String> downloadResponse =
                restTemplate.getForEntity("/api/files/1/download", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, downloadResponse.getStatusCode());
    }

    private User createUser() {
        String email = UUID.randomUUID() + "@example.test";
        userService.createUser("File Test User", email, "SecureFileTestPassword1!");
        return userRepository.findByEmail(email).orElseThrow();
    }

    private FileMetadata createFile(User owner, String filename) {
        FileMetadata metadata = new FileMetadata(
                owner,
                filename,
                "objects/" + UUID.randomUUID().toString().replace("-", "").repeat(2),
                4096,
                "application/pdf",
                "AES-256-GCM",
                "PBKDF2-SHA-256",
                600000,
                "dGVzdC1pdi12YWx1ZQ==",
                "dGVzdC1zYWx0LXZhbHVl");
        return fileMetadataRepository.saveAndFlush(metadata);
    }

    private ResponseEntity<String> get(String path, String email) {
        HttpHeaders headers = headersFor(email);
        return restTemplate.exchange(
                path,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
    }

    private ResponseEntity<Void> delete(String path, String email) {
        HttpHeaders headers = headersFor(email);
        return restTemplate.exchange(
                path,
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
                Void.class);
    }

    private HttpHeaders headersFor(String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtService.generateToken(email));
        return headers;
    }

    private String uploadJson(String filename, String mimeType, long fileSize, byte[] ciphertext)
            throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "originalFilename", filename,
                "mimeType", mimeType,
                "fileSize", fileSize,
                "encryptionAlgorithm", "AES-256-GCM",
                "keyDerivationFunction", "PBKDF2-SHA-256",
                "keyDerivationIterations", 600000,
                "iv", "AAAAAAAAAAAAAAAA",
                "salt", "AAAAAAAAAAAAAAAAAAAAAA",
                "ciphertext", Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext)));
    }
}
