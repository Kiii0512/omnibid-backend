package com.omnibid.common;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public final class ImageValidator {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB

    private ImageValidator() {}

    public record ValidatedImage(byte[] bytes, String format) {}

    public static ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE, "Tệp ảnh không được để trống");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE, "Kích thước ảnh vượt quá 5MB");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE, "Không thể đọc dữ liệu tệp ảnh");
        }

        if (bytes.length < 12) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE, "Định dạng ảnh không hợp lệ");
        }

        String format = detectFormat(bytes);
        if (format == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_IMAGE,
                    "Định dạng ảnh không hợp lệ, chỉ chấp nhận JPEG, PNG, WEBP"
            );
        }

        return new ValidatedImage(bytes, format);
    }

    private static String detectFormat(byte[] b) {
        // JPEG: FF D8 FF
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "JPEG";
        }
        // PNG: 89 50 4E 47
        if ((b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47) {
            return "PNG";
        }
        // WEBP: RIFF....WEBP (0-3: RIFF, 8-11: WEBP)
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "WEBP";
        }
        return null;
    }
}
