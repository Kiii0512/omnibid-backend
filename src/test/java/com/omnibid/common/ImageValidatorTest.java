package com.omnibid.common;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

class ImageValidatorTest {

    @Test
    void testJpegSuccess() {
        byte[] jpegBytes = new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
                0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01
        };
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", jpegBytes);
        ImageValidator.ValidatedImage result = ImageValidator.validate(file);

        assertNotNull(result);
        assertEquals("JPEG", result.format());
        assertArrayEquals(jpegBytes, result.bytes());
    }

    @Test
    void testPngSuccess() {
        byte[] pngBytes = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D
        };
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", pngBytes);
        ImageValidator.ValidatedImage result = ImageValidator.validate(file);

        assertNotNull(result);
        assertEquals("PNG", result.format());
    }

    @Test
    void testWebpSuccess() {
        byte[] webpBytes = new byte[]{
                'R', 'I', 'F', 'F',
                0x00, 0x00, 0x00, 0x00,
                'W', 'E', 'B', 'P'
        };
        MockMultipartFile file = new MockMultipartFile("file", "test.webp", "image/webp", webpBytes);
        ImageValidator.ValidatedImage result = ImageValidator.validate(file);

        assertNotNull(result);
        assertEquals("WEBP", result.format());
    }

    @Test
    void testInvalidMagicBytes_ThrowsBusinessException() {
        byte[] fakeBytes = new byte[]{'G', 'I', 'F', '8', '9', 'a', 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "test.gif", "image/gif", fakeBytes);

        BusinessException ex = assertThrows(BusinessException.class, () -> ImageValidator.validate(file));
        assertEquals(ErrorCode.INVALID_IMAGE, ex.getErrorCode());
    }

    @Test
    void testEmptyFile_ThrowsBusinessException() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);

        BusinessException ex = assertThrows(BusinessException.class, () -> ImageValidator.validate(file));
        assertEquals(ErrorCode.INVALID_IMAGE, ex.getErrorCode());
    }

    @Test
    void testTooLargeFile_ThrowsBusinessException() {
        byte[] largeBytes = new byte[5 * 1024 * 1024 + 1];
        largeBytes[0] = (byte) 0xFF;
        largeBytes[1] = (byte) 0xD8;
        largeBytes[2] = (byte) 0xFF;

        MockMultipartFile file = new MockMultipartFile("file", "large.jpg", "image/jpeg", largeBytes);

        BusinessException ex = assertThrows(BusinessException.class, () -> ImageValidator.validate(file));
        assertEquals(ErrorCode.INVALID_IMAGE, ex.getErrorCode());
    }
}
