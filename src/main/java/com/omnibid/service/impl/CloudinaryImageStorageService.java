package com.omnibid.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.dto.storage.StoredImage;
import com.omnibid.service.ImageStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Map;

@Slf4j
public class CloudinaryImageStorageService implements ImageStorageService {

    private final Cloudinary cloudinary;
    private final int readTimeoutMs;

    public CloudinaryImageStorageService(Cloudinary cloudinary) {
        this(cloudinary, 15_000);
    }

    public CloudinaryImageStorageService(Cloudinary cloudinary, int readTimeoutMs) {
        this.cloudinary = cloudinary;
        this.readTimeoutMs = Math.max(1000, readTimeoutMs);
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    @Override
    public StoredImage upload(byte[] bytes, String folder) {
        if (cloudinary == null) {
            log.error("Cloudinary chưa được cấu hình, từ chối tải ảnh lên thư mục {}", folder);
            throw new BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED, "Dịch vụ lưu trữ ảnh chưa được cấu hình");
        }
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE, "Tệp ảnh không được để trống");
        }

        try {
            Map<?, ?> options = StringUtils.hasText(folder)
                    ? ObjectUtils.asMap("folder", folder, "resource_type", "image", "timeout", readTimeoutMs)
                    : ObjectUtils.asMap("resource_type", "image", "timeout", readTimeoutMs);

            Map<?, ?> result = cloudinary.uploader().upload(bytes, options);
            String url = result != null ? (String) result.get("secure_url") : null;
            String publicId = result != null ? (String) result.get("public_id") : null;

            if (!StringUtils.hasText(url) || !StringUtils.hasText(publicId)) {
                log.error("Cloudinary trả về thiếu secure_url hoặc public_id: {}", result);
                throw new BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED, "Không thể tải ảnh lên dịch vụ lưu trữ");
            }

            return new StoredImage(url, publicId);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Tải ảnh lên Cloudinary thất bại (folder={}): {}", folder, ex.getMessage(), ex);
            throw new BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED, "Không thể tải ảnh lên dịch vụ lưu trữ");
        }
    }

    @Override
    public void delete(String publicId) {
        if (!StringUtils.hasText(publicId)) {
            return;
        }
        if (cloudinary == null) {
            log.warn("Bỏ qua xóa ảnh {} vì Cloudinary chưa được cấu hình", publicId);
            return;
        }
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", "image"));
        } catch (Exception ex) {
            log.warn("Xóa ảnh trên Cloudinary thất bại (publicId={}): {}", publicId, ex.getMessage());
        }
    }
}
