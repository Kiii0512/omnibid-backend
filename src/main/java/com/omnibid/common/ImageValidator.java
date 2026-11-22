package com.omnibid.common;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class ImageValidator {

    public static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024; // 5 MB

    public record ValidatedImage(byte[] bytes, String detectedFormat) {
    }

    public ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE, "Tệp ảnh không được để trống");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE, "Kích thước ảnh vượt quá giới hạn 5 MB");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE, "Không thể đọc nội dung tệp ảnh");
        }

        if (bytes.length == 0) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE, "Tệp ảnh không được để trống");
        }
        if (bytes.length > MAX_FILE_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE, "Kích thước ảnh vượt quá giới hạn 5 MB");
        }

        String format = detectFormat(bytes);
        if (format == null) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE, "Chỉ hỗ trợ định dạng ảnh JPEG, PNG hoặc WEBP");
        }

        return new ValidatedImage(bytes, format);
    }

    public List<ValidatedImage> validateAll(List<MultipartFile> files, int minCount, int maxCount) {
        if (files == null || files.size() < minCount || files.size() > maxCount) {
            throw new BusinessException(
                    ErrorCode.INVALID_IMAGE,
                    "Số lượng ảnh phải từ " + minCount + " đến " + maxCount
            );
        }
        List<ValidatedImage> result = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            result.add(validate(file));
        }
        return result;
    }

    private String detectFormat(byte[] bytes) {
        // JPEG: FF D8 FF
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return "jpeg";
        }
        // PNG: 89 50 4E 47
        if (bytes.length >= 4
                && (bytes[0] & 0xFF) == 0x89
                && (bytes[1] & 0xFF) == 0x50
                && (bytes[2] & 0xFF) == 0x4E
                && (bytes[3] & 0xFF) == 0x47) {
            return "png";
        }
        // WEBP: RIFF....WEBP (bytes 0..3 = "RIFF", bytes 8..11 = "WEBP")
        if (bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "webp";
        }
        return null;
    }
}
