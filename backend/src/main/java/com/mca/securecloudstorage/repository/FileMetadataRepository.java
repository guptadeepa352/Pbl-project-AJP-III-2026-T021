package com.mca.securecloudstorage.repository;

import com.mca.securecloudstorage.entity.FileMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {
    List<FileMetadata> findAllByOwner_IdOrderByCreatedAtDesc(Long ownerId);

    Optional<FileMetadata> findByIdAndOwner_Id(Long id, Long ownerId);
}
