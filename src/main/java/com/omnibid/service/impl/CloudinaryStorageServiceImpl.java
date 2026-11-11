package com.omnibid.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.omnibid.common.AppException;
import com.omnibid.dto.storage.FileUploadResponse;
import com.omnibid.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "cloudinary")
public class CloudinaryStorageServiceImpl implements StorageService {

    private final Cloudinary cloudinary;

    public CloudinaryStorageServiceImpl(
            @Value("${cloudinary.cloud-name}") String cloudName,
            @Value("${cloudinary.api-key}") String apiKey,
            @Value("${cloudinary.api-secret}") String apiSecret) {
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret
        ));
    }

    @Override
    public FileUploadResponse uploadFile(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new AppException("File tải lên không được rỗng");
        }
        try {
            Map<?, ?> uploadParams = ObjectUtils.asMap(
                    "folder", folder != null ? "omnibid/" + folder : "omnibid",
                    "resource_type", "auto"
            );
            Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), uploadParams);

            return FileUploadResponse.builder()
                    .url(result.get("secure_url").toString())
                    .publicId(result.get("public_id").toString())
                    .fileName(file.getOriginalFilename())
                    .fileSize(file.getSize())
                    .contentType(file.getContentType())
                    .build();
        } catch (IOException e) {
            log.error("Cloudinary upload failed", e);
            throw new AppException("Upload ảnh lên Cloudinary thất bại: " + e.getMessage());
        }
    }

    @Override
    public void deleteFile(String publicId) {
        if (publicId == null || publicId.isBlank()) return;
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (IOException e) {
            log.warn("Cloudinary delete failed for publicId: {}", publicId, e);
        }
    }
}