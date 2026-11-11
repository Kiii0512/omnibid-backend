package com.omnibid.service.impl;

import com.omnibid.common.AppException;
import com.omnibid.dto.storage.FileUploadResponse;
import com.omnibid.service.StorageService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageServiceImpl implements StorageService {

    @Value("${app.storage.local.upload-dir:uploads}")
    private String uploadDir;

    @Value("${app.storage.local.base-url:http://localhost:8080/uploads}")
    private String baseUrl;

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(uploadDir));
        } catch (IOException e) {
            log.error("Could not initialize upload folder", e);
            throw new AppException("Không thể khởi tạo thư mục lưu trữ ảnh: " + e.getMessage());
        }
    }

    @Override
    public FileUploadResponse uploadFile(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new AppException("File tải lên không được rỗng");
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "image.jpg");
        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = originalFilename.substring(dotIndex);
        }

        String uniqueFileName = UUID.randomUUID() + extension;
        Path targetFolder = Paths.get(uploadDir, folder != null ? folder : "");

        try {
            Files.createDirectories(targetFolder);
            Path targetLocation = targetFolder.resolve(uniqueFileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            String relativePath = (folder != null && !folder.isBlank() ? folder + "/" : "") + uniqueFileName;
            String fileUrl = baseUrl + "/" + relativePath;

            return FileUploadResponse.builder()
                    .url(fileUrl)
                    .publicId(relativePath)
                    .fileName(originalFilename)
                    .fileSize(file.getSize())
                    .contentType(file.getContentType())
                    .build();
        } catch (IOException e) {
            log.error("Failed to store file {}", originalFilename, e);
            throw new AppException("Lỗi khi lưu file: " + e.getMessage());
        }
    }

    @Override
    public void deleteFile(String publicIdOrPath) {
        if (publicIdOrPath == null || publicIdOrPath.isBlank()) return;
        try {
            Path targetFile = Paths.get(uploadDir).resolve(publicIdOrPath).normalize();
            Files.deleteIfExists(targetFile);
        } catch (IOException e) {
            log.warn("Failed to delete local file: {}", publicIdOrPath, e);
        }
    }
}