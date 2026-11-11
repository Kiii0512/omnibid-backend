package com.omnibid.service;

import com.omnibid.common.AppException;
import com.omnibid.dto.product.ProductCreateRequest;
import com.omnibid.dto.product.ProductImageResponse;
import com.omnibid.dto.product.ProductResponse;
import com.omnibid.dto.product.ProductSummaryResponse;
import com.omnibid.dto.product.ProductUpdateRequest;
import com.omnibid.dto.storage.FileUploadResponse;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
    private StorageService storageService;

    @InjectMocks
    private ProductServiceImpl productService;

    private User seller;
    private Category category;
    private Product product;

    @BeforeEach
    void setUp() {
        seller = User.builder()
                .id(1L)
                .fullName("Seller A")
                .email("seller@example.com")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();

        category = Category.builder()
                .id(2L)
                .name("Electronics")
                .slug("electronics")
                .build();

        product = Product.builder()
                .id(10L)
                .sellerId(1L)
                .categoryId(2L)
                .title("iPhone 15 Pro")
                .description("Brand new sealed")
                .condition(ProductCondition.NEW)
                .status(ProductStatus.AVAILABLE)
                .build();
    }

    @Test
    void createProduct_Success() {
        ProductCreateRequest request = ProductCreateRequest.builder()
                .categoryId(2L)
                .title("iPhone 15 Pro")
                .description("Brand new sealed")
                .condition(ProductCondition.NEW)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(seller));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenReturn(product);

        ProductResponse response = productService.createProduct(1L, request);

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals("Seller A", response.getSellerName());
        assertEquals("Electronics", response.getCategoryName());
        assertEquals(ProductStatus.AVAILABLE, response.getStatus());
    }

    @Test
    void getMyProductsSummary_Success() {
        List<Object[]> mockCounts = Arrays.asList(
                new Object[]{ProductStatus.AVAILABLE, 3L},
                new Object[]{ProductStatus.IN_AUCTION, 2L},
                new Object[]{ProductStatus.SOLD, 1L},
                new Object[]{ProductStatus.HIDDEN, 1L}
        );

        when(productRepository.countProductStatusBySellerId(1L)).thenReturn(mockCounts);

        ProductSummaryResponse summary = productService.getMyProductsSummary(1L);

        assertEquals(7L, summary.getTotal());
        assertEquals(3L, summary.getAvailable());
        assertEquals(2L, summary.getInAuction());
        assertEquals(1L, summary.getSold());
        assertEquals(1L, summary.getHidden());
    }

    @Test
    void updateProduct_InAuction_ThrowsBadRequest() {
        product.setStatus(ProductStatus.IN_AUCTION);
        ProductUpdateRequest request = ProductUpdateRequest.builder()
                .title("Updated Title")
                .build();

        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        AppException ex = assertThrows(AppException.class, () -> productService.updateProduct(1L, 10L, request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void deleteProduct_NoAuctionHistory_DeletesFilesAndDb() {
        ProductImage img1 = ProductImage.builder()
                .id(101L)
                .productId(10L)
                .imageUrl("http://localhost:8080/uploads/products/a.jpg")
                .publicId("products/a.jpg")
                .isPrimary(true)
                .build();

        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(auctionRepository.existsByProductId(10L)).thenReturn(false);
        when(productImageRepository.findByProductIdOrderByIsPrimaryDescCreatedAtAsc(10L)).thenReturn(List.of(img1));

        productService.deleteProduct(1L, 10L);

        verify(storageService).deleteFile("products/a.jpg");
        verify(productImageRepository).deleteAllByProductId(10L);
        verify(productRepository).delete(product);
    }

    @Test
    void addProductImage_FirstImage_SetsPrimaryTrue() {
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "imgdata".getBytes());
        FileUploadResponse uploadResponse = FileUploadResponse.builder()
                .url("http://localhost:8080/uploads/products/test.jpg")
                .publicId("products/test.jpg")
                .build();

        ProductImage savedImage = ProductImage.builder()
                .id(101L)
                .productId(10L)
                .imageUrl(uploadResponse.getUrl())
                .publicId(uploadResponse.getPublicId())
                .isPrimary(true)
                .build();

        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(productImageRepository.countByProductId(10L)).thenReturn(0L);
        when(storageService.uploadFile(file, "products")).thenReturn(uploadResponse);
        when(productImageRepository.save(any(ProductImage.class))).thenReturn(savedImage);

        ProductImageResponse response = productService.addProductImage(1L, 10L, file);

        assertTrue(response.getIsPrimary());
        assertEquals("products/test.jpg", response.getPublicId());
    }

    @Test
    void deleteProductImage_PrimaryDeleted_PromotesNextImage() {
        ProductImage primaryImg = ProductImage.builder()
                .id(101L)
                .productId(10L)
                .publicId("products/1.jpg")
                .isPrimary(true)
                .build();

        ProductImage secondImg = ProductImage.builder()
                .id(102L)
                .productId(10L)
                .publicId("products/2.jpg")
                .isPrimary(false)
                .build();

        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(productImageRepository.findByIdAndProductId(101L, 10L)).thenReturn(Optional.of(primaryImg));
        when(productImageRepository.findByProductIdOrderByIsPrimaryDescCreatedAtAsc(10L)).thenReturn(List.of(secondImg));

        productService.deleteProductImage(1L, 10L, 101L);

        verify(storageService).deleteFile("products/1.jpg");
        verify(productImageRepository).delete(primaryImg);
        assertTrue(secondImg.getIsPrimary());
        verify(productImageRepository).save(secondImg);
    }

    @Test
    void setPrimaryImage_Success() {
        ProductImage targetImg = ProductImage.builder()
                .id(102L)
                .productId(10L)
                .imageUrl("http://localhost:8080/uploads/products/2.jpg")
                .publicId("products/2.jpg")
                .isPrimary(false)
                .build();

        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(productImageRepository.findByIdAndProductId(102L, 10L)).thenReturn(Optional.of(targetImg));
        when(productImageRepository.save(targetImg)).thenReturn(targetImg);

        ProductImageResponse response = productService.setPrimaryImage(1L, 10L, 102L);

        verify(productImageRepository).resetPrimaryByProductId(10L);
        assertTrue(response.getIsPrimary());
    }
}

