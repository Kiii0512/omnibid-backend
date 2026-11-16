package com.omnibid.service;

import com.omnibid.common.AppException;
import com.omnibid.dto.product.ProductImageResponse;
import com.omnibid.dto.storage.StorageUploadResult;
import com.omnibid.entity.Product;
import com.omnibid.entity.ProductImage;
import com.omnibid.enums.ProductCondition;
import com.omnibid.enums.ProductStatus;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.repository.CategoryRepository;
import com.omnibid.repository.ProductImageRepository;
import com.omnibid.repository.ProductRepository;
import com.omnibid.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private AuctionRepository auctionRepository;
    @Mock
    private ProductImageRepository productImageRepository;
    @Mock
    private StorageService storageService;

    @InjectMocks
    private ProductServiceImpl productService;

    private Product product;

    @BeforeEach
    void setUp() {
        product = Product.builder()
                .id(9L)
                .sellerId(2L)
                .categoryId(1L)
                .title("Bàn phím cơ Keychron")
                .description("Bàn phím custom")
                .condition(ProductCondition.LIKE_NEW)
                .status(ProductStatus.AVAILABLE)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("uploadProductImage — first image becomes primary")
    void uploadProductImage_FirstImage_BecomesPrimary() {
        when(productRepository.findById(9L)).thenReturn(Optional.of(product));
        when(productImageRepository.countByProductId(9L)).thenReturn(0L);

        StorageUploadResult uploadResult = new StorageUploadResult("/uploads/products/9/uuid-1.jpg", "products/9/uuid-1.jpg");
        when(storageService.upload(any(), eq("products/9"))).thenReturn(uploadResult);

        ProductImage savedImage = ProductImage.builder()
                .id(101L)
                .productId(9L)
                .imageUrl(uploadResult.url())
                .publicId(uploadResult.publicId())
                .isPrimary(true)
                .createdAt(LocalDateTime.now())
                .build();
        when(productImageRepository.save(any(ProductImage.class))).thenReturn(savedImage);

        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "image content".getBytes());
        ProductImageResponse response = productService.uploadProductImage(2L, 9L, file);

        assertThat(response.id()).isEqualTo(101L);
        assertThat(response.imageUrl()).isEqualTo("/uploads/products/9/uuid-1.jpg");
        assertThat(response.isPrimary()).isTrue();
        verify(productImageRepository).save(argThat(img -> Boolean.TRUE.equals(img.getIsPrimary())));
    }

    @Test
    @DisplayName("uploadProductImage — subsequent image is not primary")
    void uploadProductImage_SubsequentImage_NotPrimary() {
        when(productRepository.findById(9L)).thenReturn(Optional.of(product));
        when(productImageRepository.countByProductId(9L)).thenReturn(1L);

        StorageUploadResult uploadResult = new StorageUploadResult("/uploads/products/9/uuid-2.jpg", "products/9/uuid-2.jpg");
        when(storageService.upload(any(), eq("products/9"))).thenReturn(uploadResult);

        ProductImage savedImage = ProductImage.builder()
                .id(102L)
                .productId(9L)
                .imageUrl(uploadResult.url())
                .publicId(uploadResult.publicId())
                .isPrimary(false)
                .createdAt(LocalDateTime.now())
                .build();
        when(productImageRepository.save(any(ProductImage.class))).thenReturn(savedImage);

        MockMultipartFile file = new MockMultipartFile("file", "test2.jpg", "image/jpeg", "image content".getBytes());
        ProductImageResponse response = productService.uploadProductImage(2L, 9L, file);

        assertThat(response.id()).isEqualTo(102L);
        assertThat(response.isPrimary()).isFalse();
        verify(productImageRepository).save(argThat(img -> Boolean.FALSE.equals(img.getIsPrimary())));
    }

    @Test
    @DisplayName("uploadProductImage — throws MAX_IMAGES_REACHED when count >= 5")
    void uploadProductImage_MaxImagesReached_ThrowsBadRequest() {
        when(productRepository.findById(9L)).thenReturn(Optional.of(product));
        when(productImageRepository.countByProductId(9L)).thenReturn(5L);

        MockMultipartFile file = new MockMultipartFile("file", "test5.jpg", "image/jpeg", "image content".getBytes());
        assertThatThrownBy(() -> productService.uploadProductImage(2L, 9L, file))
                .isInstanceOf(AppException.class)
                .satisfies(e -> {
                    AppException ex = (AppException) e;
                    assertThat(ex.getCode()).isEqualTo("MAX_IMAGES_REACHED");
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    @DisplayName("uploadProductImage — throws FORBIDDEN when user does not own product")
    void uploadProductImage_NotOwner_ThrowsForbidden() {
        when(productRepository.findById(9L)).thenReturn(Optional.of(product));

        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "image content".getBytes());
        assertThatThrownBy(() -> productService.uploadProductImage(999L, 9L, file))
                .isInstanceOf(AppException.class)
                .satisfies(e -> {
                    AppException ex = (AppException) e;
                    assertThat(ex.getCode()).isEqualTo("FORBIDDEN");
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    @DisplayName("uploadProductImage — throws FILE_EMPTY when file is empty")
    void uploadProductImage_EmptyFile_ThrowsBadRequest() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);
        assertThatThrownBy(() -> productService.uploadProductImage(2L, 9L, file))
                .isInstanceOf(AppException.class)
                .satisfies(e -> {
                    AppException ex = (AppException) e;
                    assertThat(ex.getCode()).isEqualTo("FILE_EMPTY");
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    @DisplayName("uploadProductImage — throws INVALID_FILE_TYPE when file is not an image")
    void uploadProductImage_InvalidFileType_ThrowsBadRequest() {
        MockMultipartFile file = new MockMultipartFile("file", "script.sh", "application/x-sh", "echo hello".getBytes());
        assertThatThrownBy(() -> productService.uploadProductImage(2L, 9L, file))
                .isInstanceOf(AppException.class)
                .satisfies(e -> {
                    AppException ex = (AppException) e;
                    assertThat(ex.getCode()).isEqualTo("INVALID_FILE_TYPE");
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }
}

