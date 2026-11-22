package com.omnibid.service;

import com.omnibid.common.AfterCommitExecutor;
import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.common.ImageValidator;
import com.omnibid.dto.product.ProductCreateRequest;
import com.omnibid.dto.product.ProductImageResponse;
import com.omnibid.dto.product.ProductResponse;
import com.omnibid.dto.product.ProductUpdateRequest;
import com.omnibid.entity.Category;
import com.omnibid.entity.Product;
import com.omnibid.entity.ProductImage;
import com.omnibid.enums.ProductCondition;
import com.omnibid.enums.ProductStatus;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.repository.CategoryRepository;
import com.omnibid.repository.ProductImageRepository;
import com.omnibid.repository.ProductRepository;
import com.omnibid.service.impl.ProductServiceImpl;
import com.omnibid.support.FakeImageStorageService;
import com.omnibid.test.DirectTransactionTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final byte[] VALID_PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D
    };
    private static final byte[] FAKE_PDF = "%PDF-1.7\n1 0 obj".getBytes();

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductImageRepository productImageRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private AuctionRepository auctionRepository;

    private FakeImageStorageService fakeStorage;
    private ProductServiceImpl productService;
    private Product product;

    @BeforeEach
    void setUp() {
        fakeStorage = new FakeImageStorageService();
        ImageValidator imageValidator = new ImageValidator();
        AfterCommitExecutor afterCommitExecutor = new AfterCommitExecutor();
        DirectTransactionTemplate txTemplate = new DirectTransactionTemplate();

        productService = new ProductServiceImpl(
                productRepository,
                productImageRepository,
                categoryRepository,
                auctionRepository,
                imageValidator,
                fakeStorage,
                afterCommitExecutor,
                txTemplate
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
    @DisplayName("createProduct: tệp .png chứa PDF -> 400 INVALID_IMAGE, không gọi storage")
    void createProduct_PdfDisguisedAsPng_ThrowsInvalidImage() {
        ProductCreateRequest req = new ProductCreateRequest(1L, "Laptop", "Mô tả", ProductCondition.NEW);
        MockMultipartFile pdfAsPng = new MockMultipartFile("files", "fake.png", "image/png", FAKE_PDF);

        assertThatThrownBy(() -> productService.createProduct(2L, req, List.of(pdfAsPng)))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getCode() == ErrorCode.INVALID_IMAGE);

        assertThat(fakeStorage.uploadCount()).isZero();
    }

    @Test
    @DisplayName("createProduct: 6 ảnh -> 400 INVALID_IMAGE, không gọi storage")
    void createProduct_SixImages_ThrowsInvalidImage() {
        ProductCreateRequest req = new ProductCreateRequest(1L, "Laptop", "Mô tả", ProductCondition.NEW);
        List<MultipartFile> sixFiles = List.of(
                pngFile("1.png"), pngFile("2.png"), pngFile("3.png"),
                pngFile("4.png"), pngFile("5.png"), pngFile("6.png")
        );

        assertThatThrownBy(() -> productService.createProduct(2L, req, sixFiles))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getCode() == ErrorCode.INVALID_IMAGE);

        assertThat(fakeStorage.uploadCount()).isZero();
    }

    @Test
    @DisplayName("createProduct: lỗi DB sau upload -> bù trừ xóa toàn bộ asset đã upload")
    void createProduct_DbFailureAfterUpload_CompensatesByDeletingUploadedAssets() {
        ProductCreateRequest req = new ProductCreateRequest(1L, "Laptop", "Mô tả", ProductCondition.NEW);
        Category category = Category.builder().id(1L).name("Electronics").slug("electronics").build();

        when(categoryRepository.existsById(1L)).thenReturn(true);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenThrow(new DataIntegrityViolationException("DB fail"));

        assertThatThrownBy(() -> productService.createProduct(2L, req, List.of(pngFile("1.png"), pngFile("2.png"))))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(fakeStorage.uploadCount()).isEqualTo(2);
        assertThat(fakeStorage.deleteCount()).isEqualTo(2);
        assertThat(fakeStorage.deletedPublicIds()).containsExactlyInAnyOrderElementsOf(fakeStorage.uploadedPublicIds());
    }

    @Test
    @DisplayName("createProduct: thành công với 2 ảnh -> ảnh đầu là isPrimary=true")
    void createProduct_Success_FirstImageIsPrimary() {
        ProductCreateRequest req = new ProductCreateRequest(1L, "Laptop", "Mô tả", ProductCondition.NEW);
        Category category = Category.builder().id(1L).name("Electronics").slug("electronics").build();

        when(categoryRepository.existsById(1L)).thenReturn(true);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(50L);
            return p;
        });
        when(productImageRepository.save(any(ProductImage.class))).thenAnswer(inv -> {
            ProductImage img = inv.getArgument(0);
            img.setId(Boolean.TRUE.equals(img.getIsPrimary()) ? 101L : 102L);
            return img;
        });

        ProductResponse response = productService.createProduct(2L, req, List.of(pngFile("1.png"), pngFile("2.png")));

        assertThat(response.id()).isEqualTo(50L);
        assertThat(response.images()).hasSize(2);
        assertThat(response.images().get(0).isPrimary()).isTrue();
        assertThat(response.images().get(1).isPrimary()).isFalse();
        assertThat(fakeStorage.uploadCount()).isEqualTo(2);
        assertThat(fakeStorage.deleteCount()).isZero();
    }

    @Test
    @DisplayName("updateProduct: sản phẩm đang IN_AUCTION -> 409 PRODUCT_NOT_EDITABLE")
    void updateProduct_InAuction_ThrowsProductNotEditable() {
        product.setStatus(ProductStatus.IN_AUCTION);
        when(productRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(product));

        ProductUpdateRequest req = new ProductUpdateRequest(1L, "Tên mới", "Mô tả mới", ProductCondition.NEW);

        assertThatThrownBy(() -> productService.updateProduct(2L, 9L, req))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getCode() == ErrorCode.PRODUCT_NOT_EDITABLE);
    }

    @Test
    @DisplayName("addProductImages: vượt quá 5 ảnh -> 400 INVALID_IMAGE, không upload")
    void addProductImages_ExceedsMax_ThrowsInvalidImage() {
        when(productRepository.findById(9L)).thenReturn(Optional.of(product));
        when(productImageRepository.countByProductId(9L)).thenReturn(5L);

        assertThatThrownBy(() -> productService.addProductImages(2L, 9L, List.of(pngFile("6.png"))))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getCode() == ErrorCode.INVALID_IMAGE);

        assertThat(fakeStorage.uploadCount()).isZero();
    }

    @Test
    @DisplayName("addProductImages: người khác không phải chủ sở hữu -> 403 FORBIDDEN")
    void addProductImages_NotOwner_ThrowsForbidden() {
        when(productRepository.findById(9L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.addProductImages(999L, 9L, List.of(pngFile("1.png"))))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getCode() == ErrorCode.FORBIDDEN);

        assertThat(fakeStorage.uploadCount()).isZero();
    }

    @Test
    @DisplayName("addProductImages: thêm ảnh thứ 2 thành công -> isPrimary=false")
    void addProductImages_SubsequentImage_NotPrimary() {
        Category category = Category.builder().id(1L).name("Electronics").slug("electronics").build();
        ProductImage existing = ProductImage.builder()
                .id(201L).productId(9L).imageUrl("https://cdn/1.png").publicId("omnibid/1").isPrimary(true).build();
        when(productRepository.findById(9L)).thenReturn(Optional.of(product));
        when(productRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(product));
        when(productImageRepository.countByProductId(9L)).thenReturn(1L);
        when(productImageRepository.save(any(ProductImage.class))).thenAnswer(inv -> {
            ProductImage img = inv.getArgument(0);
            img.setId(202L);
            return img;
        });
        when(productImageRepository.findByProductIdOrderByIdAsc(9L))
                .thenReturn(List.of(existing, ProductImage.builder()
                        .id(202L).productId(9L).imageUrl("https://cdn/2.png").publicId("omnibid/2").isPrimary(false).build()));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(auctionRepository.findFirstByProductIdAndStatusIn(eq(9L), any())).thenReturn(Optional.empty());
        when(auctionRepository.existsByProductId(9L)).thenReturn(false);

        ProductResponse updated = productService.addProductImages(2L, 9L, List.of(pngFile("2.png")));

        assertThat(updated.images()).hasSize(2);
        assertThat(updated.images().get(1).id()).isEqualTo(202L);
        assertThat(updated.images().get(1).isPrimary()).isFalse();
    }

    private MockMultipartFile pngFile(String name) {
        return new MockMultipartFile("files", name, "image/png", VALID_PNG);
    }
}
