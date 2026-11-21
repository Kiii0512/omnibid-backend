package com.omnibid.service;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.dto.product.ProductImageResponse;
import com.omnibid.dto.product.ProductResponse;
import com.omnibid.dto.product.ProductUpdateRequest;
import com.omnibid.entity.Category;
import com.omnibid.entity.Product;
import com.omnibid.entity.ProductImage;
import com.omnibid.entity.User;
import com.omnibid.enums.ProductCondition;
import com.omnibid.enums.ProductStatus;
import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.repository.CategoryRepository;
import com.omnibid.repository.ProductImageRepository;
import com.omnibid.repository.ProductRepository;
import com.omnibid.repository.UserRepository;
import com.omnibid.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
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
    private ProductImageRepository productImageRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuctionRepository auctionRepository;
    @Mock
    private ImageStorageService imageStorageService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private ProductServiceImpl productService;
    private Product product;

    private static final byte[] VALID_JPEG = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01};

    @BeforeEach
    void setUp() {
        productService = new ProductServiceImpl(
                productRepository,
                productImageRepository,
                categoryRepository,
                userRepository,
                auctionRepository,
                imageStorageService,
                null,
                transactionTemplate
        );

        product = Product.builder()
                .id(9L)
                .sellerId(2L)
                .categoryId(1L)
                .title("Bàn phím cơ Keychron")
                .description("Bàn phím custom")
                .condition(ProductCondition.LIKE_NEW)
                .status(ProductStatus.AVAILABLE)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("uploadProductImages — ném INVALID_IMAGE khi tổng số ảnh vượt quá 5")
    void uploadProductImages_MaxImagesReached_ThrowsInvalidImage() {
        when(productRepository.findById(9L)).thenReturn(Optional.of(product));
        when(productImageRepository.countByProductId(9L)).thenReturn(4L);

        MockMultipartFile file1 = new MockMultipartFile("files", "img1.jpg", "image/jpeg", VALID_JPEG);
        MockMultipartFile file2 = new MockMultipartFile("files", "img2.jpg", "image/jpeg", VALID_JPEG);

        assertThatThrownBy(() -> productService.uploadProductImages(2L, 9L, List.of(file1, file2)))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.INVALID_IMAGE);
    }

    @Test
    @DisplayName("uploadProductImages — ném FORBIDDEN khi người dùng không phải chủ sản phẩm")
    void uploadProductImages_NotOwner_ThrowsForbidden() {
        when(productRepository.findById(9L)).thenReturn(Optional.of(product));

        MockMultipartFile file = new MockMultipartFile("files", "img.jpg", "image/jpeg", VALID_JPEG);
        assertThatThrownBy(() -> productService.uploadProductImages(999L, 9L, List.of(file)))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("uploadProductImages — ném INVALID_IMAGE khi tệp rỗng hoặc sai magic bytes")
    void uploadProductImages_InvalidFileType_ThrowsInvalidImage() {
        MockMultipartFile file = new MockMultipartFile("files", "script.sh", "application/x-sh", "echo hello".getBytes());
        assertThatThrownBy(() -> productService.uploadProductImages(2L, 9L, List.of(file)))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.INVALID_IMAGE);
    }

    @Test
    @DisplayName("updateProduct — ném PRODUCT_NOT_EDITABLE khi sản phẩm IN_AUCTION")
    void updateProduct_InAuction_ThrowsProductNotEditable() {
        product.setStatus(ProductStatus.IN_AUCTION);
        when(productRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(product));

        ProductUpdateRequest request = new ProductUpdateRequest(1L, "New Title", "New Desc", ProductCondition.NEW);
        assertThatThrownBy(() -> productService.updateProduct(2L, 9L, request))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.PRODUCT_NOT_EDITABLE);
    }

    @Test
    @DisplayName("deleteProduct — ném PRODUCT_ALREADY_AUCTIONED khi sản phẩm đã từng lên phiên")
    void deleteProduct_AlreadyAuctioned_ThrowsConflict() {
        when(productRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(product));
        when(auctionRepository.existsByProductId(9L)).thenReturn(true);

        assertThatThrownBy(() -> productService.deleteProduct(2L, 9L))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.INVALID_STATUS_TRANSITION);
    }
}
