package com.mca.securecloudstorage.service;

import com.mca.securecloudstorage.entity.FileMetadata;
import com.mca.securecloudstorage.entity.User;
import com.mca.securecloudstorage.exception.FileMetadataDeletionException;
import com.mca.securecloudstorage.repository.FileMetadataRepository;
import com.mca.securecloudstorage.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceDeletionTest {
    @Mock
    private FileMetadataRepository fileMetadataRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private S3Service s3Service;

    @Test
    void reportsMetadataFailureAfterS3RemovalAndPreservesDeletionOrder() {
        User owner = new User("Owner", "owner@example.test", "unused-hash");
        FileMetadata metadata = new FileMetadata(
                owner,
                "document.pdf",
                "objects/" + "a".repeat(64),
                128,
                "application/pdf",
                "AES-256-GCM",
                "PBKDF2-SHA-256",
                600000,
                "AAAAAAAAAAAAAAAA",
                "AAAAAAAAAAAAAAAAAAAAAA");
        when(userRepository.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));
        when(fileMetadataRepository.findByIdAndOwner_Id(7L, owner.getId()))
                .thenReturn(Optional.of(metadata));
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("test database failure"))
                .when(fileMetadataRepository).flush();

        FileService fileService =
                new FileService(fileMetadataRepository, userRepository, s3Service);

        assertThrows(
                FileMetadataDeletionException.class,
                () -> fileService.deleteFile(7L, owner.getEmail()));

        InOrder deletionOrder = inOrder(s3Service, fileMetadataRepository);
        deletionOrder.verify(s3Service).delete(metadata.getS3ObjectKey());
        deletionOrder.verify(fileMetadataRepository).delete(metadata);
        deletionOrder.verify(fileMetadataRepository).flush();
        verify(fileMetadataRepository).findByIdAndOwner_Id(7L, owner.getId());
    }
}
