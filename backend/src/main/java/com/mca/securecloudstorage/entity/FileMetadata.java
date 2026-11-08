package com.mca.securecloudstorage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "file_metadata",
        indexes = @Index(
                name = "idx_file_metadata_owner_created",
                columnList = "owner_id, created_at"))
public class FileMetadata {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "owner_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_file_metadata_owner"))
    private User owner;

    @NotBlank
    @Size(max = 255)
    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @NotBlank
    @Size(max = 1024)
    @Column(name = "s3_object_key", nullable = false, length = 1024)
    private String s3ObjectKey;

    @PositiveOrZero
    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @NotBlank
    @Size(max = 127)
    @Column(name = "mime_type", nullable = false, length = 127)
    private String mimeType;

    @NotBlank
    @Size(max = 64)
    @Column(name = "encryption_algorithm", nullable = false, length = 64)
    private String encryptionAlgorithm;

    @NotBlank
    @Size(max = 64)
    @Column(name = "key_derivation_function", nullable = false, length = 64)
    private String keyDerivationFunction;

    @PositiveOrZero
    @Column(name = "key_derivation_iterations", nullable = false)
    private int keyDerivationIterations;

    @NotBlank
    @Size(max = 128)
    @Column(nullable = false, length = 128)
    private String iv;

    @NotBlank
    @Size(max = 128)
    @Column(nullable = false, length = 128)
    private String salt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FileMetadata() {
    }

    public FileMetadata(
            User owner,
            String originalFilename,
            String s3ObjectKey,
            long fileSize,
            String mimeType,
            String encryptionAlgorithm,
            String keyDerivationFunction,
            int keyDerivationIterations,
            String iv,
            String salt) {
        this.owner = owner;
        this.originalFilename = originalFilename;
        this.s3ObjectKey = s3ObjectKey;
        this.fileSize = fileSize;
        this.mimeType = mimeType;
        this.encryptionAlgorithm = encryptionAlgorithm;
        this.keyDerivationFunction = keyDerivationFunction;
        this.keyDerivationIterations = keyDerivationIterations;
        this.iv = iv;
        this.salt = salt;
    }

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getS3ObjectKey() {
        return s3ObjectKey;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getEncryptionAlgorithm() {
        return encryptionAlgorithm;
    }

    public String getKeyDerivationFunction() {
        return keyDerivationFunction;
    }

    public int getKeyDerivationIterations() {
        return keyDerivationIterations;
    }

    public String getIv() {
        return iv;
    }

    public String getSalt() {
        return salt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
