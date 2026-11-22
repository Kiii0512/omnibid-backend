package com.omnibid.common;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageValidatorTest {

    private final ImageValidator validator = new ImageValidator();

    @Test
    void acceptsValidJpegPngWebpMagicBytesRegardlessOfExtensionOrContentType() {
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        byte[] webp = new byte[]{'R', 'I', 'F', 'F', 0x00, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};

        var j = validator.validate(new MockMultipartFile("file", "fake.txt", "text/plain", jpeg));
        var p = validator.validate(new MockMultipartFile("file", "fake.bin", "application/octet-stream", png));
        var w = validator.validate(new MockMultipartFile("file", "fake.jpg", "image/jpeg", webp));

        assertThat(j.detectedFormat()).isEqualTo("jpeg");
        assertThat(p.detectedFormat()).isEqualTo("png");
        assertThat(w.detectedFormat()).isEqualTo("webp");
    }

    @Test
    void rejectsSpoofedImageWithWrongMagicBytes() {
        MockMultipartFile spoofed = new MockMultipartFile(
                "file", "evil.jpg", "image/jpeg", "<html>not an image</html>".getBytes()
        );

        assertThatThrownBy(() -> validator.validate(spoofed))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.INVALID_IMAGE);
    }

    @Test
    void rejectsEmptyOrNullFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.INVALID_IMAGE);

        assertThatThrownBy(() -> validator.validate(empty))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.INVALID_IMAGE);
    }

    @Test
    void rejectsFileLargerThan5MB() {
        byte[] oversized = new byte[(int) ImageValidator.MAX_FILE_SIZE_BYTES + 1];
        oversized[0] = (byte) 0xFF;
        oversized[1] = (byte) 0xD8;
        oversized[2] = (byte) 0xFF;
        MockMultipartFile file = new MockMultipartFile("file", "big.jpg", "image/jpeg", oversized);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.INVALID_IMAGE);
    }

    @Test
    void rejectsOutOfRangeImageCount() {
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
        MultipartFile img = new MockMultipartFile("files", "a.jpg", "image/jpeg", jpeg);

        assertThatThrownBy(() -> validator.validateAll(List.of(), 1, 5))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.INVALID_IMAGE);

        assertThatThrownBy(() -> validator.validateAll(List.of(img, img, img, img, img, img), 1, 5))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.INVALID_IMAGE);
    }
}
