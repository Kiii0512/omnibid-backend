package com.omnibid.service.impl;

import com.omnibid.common.AppException;
import com.omnibid.dto.storage.StorageUploadResult;
import com.omnibid.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
public class LocalStorageServiceImpl implements StorageService {

    private final Path uploadRoot;

    public LocalStorageServiceImpl() {
        this("uploads");
    }

    public LocalStorageServiceImpl(String uploadDir) {
        String dir = (uploadDir == null || uploadDir.isBlank()) ? "uploads" : uploadDir.trim();
        this.uploadRoot = Paths.get(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadRoot);
        } catch (IOException e) {
            log.error("Failed to create upload root directory: {}", this.uploadRoot, e);
        }
    }

    @Override
    public StorageUploadResult upload(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new AppException("FILE_EMPTY", "File cannot be empty", HttpStatus.BAD_REQUEST);
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String filename = UUID.randomUUID() + extension;
        String subFolder = (folder == null || folder.isBlank()) ? "" : folder.trim().replace("\\", "/");
        if (subFolder.startsWith("/")) {
            subFolder = subFolder.substring(1);
        }

        Path targetDir = subFolder.isEmpty() ? uploadRoot : uploadRoot.resolve(subFolder);
        try {
            Files.createDirectories(targetDir);
            Path targetFile = targetDir.resolve(filename);
            Files.copy(file.getInputStream(), targetFile, StandardCopyOption.REPLACE_EXISTING);

            String publicId = subFolder.isEmpty() ? filename : subFolder + "/" + filename;
            String url = "/uploads/" + publicId;
            return new StorageUploadResult(url, publicId);
        } catch (IOException e) {
            log.error("Failed to save uploaded file", e);
            throw new AppException("UPLOAD_FAILED", "Failed to upload file locally", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void delete(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return;
        }
        try {
            Path fileToDelete = uploadRoot.resolve(publicId.replace("\\", "/")).normalize();
            if (fileToDelete.startsWith(uploadRoot)) {
                Files.deleteIfExists(fileToDelete);
            }
        } catch (IOException e) {
            log.warn("Failed to delete local file with publicId: {}", publicId, e);
        }
    }
}

