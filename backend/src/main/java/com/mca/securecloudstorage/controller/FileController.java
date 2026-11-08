package com.mca.securecloudstorage.controller;

import com.mca.securecloudstorage.dto.FileMetadataResponse;
import com.mca.securecloudstorage.dto.FileDownloadResponse;
import com.mca.securecloudstorage.dto.FileUploadRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import com.mca.securecloudstorage.service.FileService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/upload")
    public ResponseEntity<FileMetadataResponse> uploadFile(
            @Valid @RequestBody FileUploadRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fileService.uploadFile(request, userDetails.getUsername()));
    }

    @GetMapping
    public List<FileMetadataResponse> listFiles(
            @AuthenticationPrincipal UserDetails userDetails) {
        return fileService.listFiles(userDetails.getUsername());
    }

    @GetMapping("/{id}")
    public FileMetadataResponse getFile(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return fileService.getFile(id, userDetails.getUsername());
    }

    @GetMapping("/{id}/download")
    public FileDownloadResponse downloadFile(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return fileService.downloadFile(id, userDetails.getUsername());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFile(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        fileService.deleteFile(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
