package com.omnibid.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.dto.storage.StoredImage;
import com.omnibid.service.ImageStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Service
public class CloudinaryImageStorageService implements ImageStorageService {

    private final Cloudinary cloudinary;

    public CloudinaryImageStorageService(@Autowired(required = false) Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    @Override
    public StoredImage upload(byte[] bytes, String folder) {
        if (cloudinary == null) {
            throw new BusinessException(
                    ErrorCode.IMAGE_UPLOAD_FAILED,
                    "Dịch vụ lưu trữ Cloudinary chưa được cấu hình"
            );
        }

        try {
            Map<?, ?> options = (folder != null && !folder.isBlank())
                    ? ObjectUtils.asMap("folder", folder)
                    : ObjectUtils.emptyMap();

            Map<?, ?> result = cloudinary.uploader().upload(bytes, options);
            String url = (String) result.get("secure_url");
            String publicId = (String) result.get("public_id");

            return new StoredImage(url, publicId);
        } catch (IOException e) {
            log.error("Failed to upload image to Cloudinary", e);
            throw new BusinessException(
                    ErrorCode.IMAGE_UPLOAD_FAILED,
                    "Tải ảnh lên Cloudinary thất bại: " + e.getMessage()
            );
        }
    }

    @Override
    public void delete(String publicId) {
        if (cloudinary == null || publicId == null || publicId.isBlank()) {
            return;
        }

        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (Exception e) {
            log.warn("Failed to delete asset from Cloudinary: publicId={}, error={}", publicId, e.getMessage());
        }
    }
}
