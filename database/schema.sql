CREATE DATABASE IF NOT EXISTS secure_cloud_storage
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE secure_cloud_storage;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(60) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS file_metadata (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    s3_object_key VARCHAR(1024) NOT NULL,
    file_size BIGINT NOT NULL,
    mime_type VARCHAR(127) NOT NULL,
    encryption_algorithm VARCHAR(64) NOT NULL,
    key_derivation_function VARCHAR(64) NOT NULL,
    key_derivation_iterations INT NOT NULL,
    iv VARCHAR(128) NOT NULL,
    salt VARCHAR(128) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_file_metadata_owner_created (owner_id, created_at),
    CONSTRAINT fk_file_metadata_owner
        FOREIGN KEY (owner_id) REFERENCES users (id)
);
