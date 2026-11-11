package com.omnibid.service.impl;

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
import com.omnibid.enums.ProductStatus;
import com.omnibid.enums.UserRole;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.repository.CategoryRepository;
import com.omnibid.repository.ProductImageRepository;
import com.omnibid.repository.ProductRepository;
import com.omnibid.repository.UserRepository;
import com.omnibid.service.ProductService;
import com.omnibid.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final String PRODUCT_IMAGE_FOLDER = "products";

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final AuctionRepository auctionRepository;
    private final StorageService storageService;

    @Override
    @Transactional
    public ProductResponse createProduct(Long currentUserId, ProductCreateRequest request) {
        User seller = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new AppException("Category not found with id: " + request.getCategoryId(), HttpStatus.NOT_FOUND));

        Product product = Product.builder()
                .sellerId(currentUserId)
                .categoryId(category.getId())
                .title(request.getTitle().trim())
                .description(request.getDescription().trim())
                .condition(request.getCondition())
                .status(ProductStatus.AVAILABLE)
                .build();

        Product savedProduct = productRepository.save(product);
        return buildProductResponse(savedProduct, seller.getFullName(), category.getName(), Collections.emptyList());
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        Product product = findProductOrThrow(id);
        if (product.getStatus() == ProductStatus.REMOVED) {
            throw new AppException("Product not found with id: " + id, HttpStatus.NOT_FOUND);
        }
        return mapToProductResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getAllProducts(Pageable pageable) {
        return productRepository.findByStatus(ProductStatus.AVAILABLE, pageable)
                .map(this::mapToProductResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getMyProducts(Long currentUserId, ProductStatus status, Pageable pageable) {
        Page<Product> productPage = (status != null)
                ? productRepository.findBySellerIdAndStatus(currentUserId, status, pageable)
                : productRepository.findBySellerId(currentUserId, pageable);
        return productPage.map(this::mapToProductResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductSummaryResponse getMyProductsSummary(Long currentUserId) {
        List<Object[]> rows = productRepository.countProductStatusBySellerId(currentUserId);

        long total = 0;
        long available = 0;
        long inAuction = 0;
        long sold = 0;
        long hidden = 0;

        for (Object[] row : rows) {
            ProductStatus productStatus = (ProductStatus) row[0];
            long count = ((Number) row[1]).longValue();
            if (productStatus == ProductStatus.REMOVED) {
                continue;
            }
            total += count;
            switch (productStatus) {
                case AVAILABLE -> available = count;
                case IN_AUCTION -> inAuction = count;
                case SOLD -> sold = count;
                case HIDDEN -> hidden = count;
                default -> {
                }
            }
        }

        return ProductSummaryResponse.builder()
                .total(total)
                .available(available)
                .inAuction(inAuction)
                .sold(sold)
                .hidden(hidden)
                .build();
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Long currentUserId, Long productId, ProductUpdateRequest request) {
        Product product = findProductOrThrow(productId);
        verifyOwnerOrAdmin(product, currentUserId);

        if (product.getStatus() == ProductStatus.IN_AUCTION) {
            throw new AppException("Cannot update product while it is in an active auction", HttpStatus.BAD_REQUEST);
        }

        if (request.getCategoryId() != null) {
            categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new AppException("Category not found with id: " + request.getCategoryId(), HttpStatus.NOT_FOUND));
            product.setCategoryId(request.getCategoryId());
        }

        if (request.getTitle() != null) {
            if (request.getTitle().isBlank()) {
                throw new AppException("Product title cannot be blank", HttpStatus.BAD_REQUEST);
            }
            product.setTitle(request.getTitle().trim());
        }

        if (request.getDescription() != null) {
            if (request.getDescription().isBlank()) {
                throw new AppException("Product description cannot be blank", HttpStatus.BAD_REQUEST);
            }
            product.setDescription(request.getDescription().trim());
        }

        if (request.getCondition() != null) {
            product.setCondition(request.getCondition());
        }

        Product updatedProduct = productRepository.save(product);
        return mapToProductResponse(updatedProduct);
    }

    @Override
    @Transactional
    public void deleteProduct(Long currentUserId, Long productId) {
        Product product = findProductOrThrow(productId);
        verifyOwnerOrAdmin(product, currentUserId);

        if (product.getStatus() == ProductStatus.IN_AUCTION) {
            throw new AppException("Cannot delete product while it is in an active auction", HttpStatus.BAD_REQUEST);
        }

        if (product.getStatus() == ProductStatus.RESERVED || product.getStatus() == ProductStatus.SOLD) {
            throw new AppException("Cannot delete a reserved or sold product", HttpStatus.BAD_REQUEST);
        }

        if (auctionRepository.existsByProductId(productId)) {
            product.setStatus(ProductStatus.REMOVED);
            productRepository.save(product);
            return;
        }

        List<ProductImage> images = productImageRepository.findByProductIdOrderByIsPrimaryDescCreatedAtAsc(productId);
        for (ProductImage image : images) {
            if (image.getPublicId() != null && !image.getPublicId().isBlank()) {
                storageService.deleteFile(image.getPublicId());
            }
        }

        productImageRepository.deleteAllByProductId(productId);
        productRepository.delete(product);
    }

    @Override
    @Transactional
    public ProductImageResponse addProductImage(Long currentUserId, Long productId, MultipartFile file) {
        Product product = findProductOrThrow(productId);
        verifyOwnerOrAdmin(product, currentUserId);

        if (product.getStatus() == ProductStatus.IN_AUCTION) {
            throw new AppException("Cannot modify images while product is in an active auction", HttpStatus.BAD_REQUEST);
        }

        if (file == null || file.isEmpty()) {
            throw new AppException("Image file cannot be empty", HttpStatus.BAD_REQUEST);
        }

        long existingCount = productImageRepository.countByProductId(productId);
        boolean isPrimary = existingCount == 0;

        FileUploadResponse uploadResult = storageService.uploadFile(file, PRODUCT_IMAGE_FOLDER);

        ProductImage productImage = ProductImage.builder()
                .productId(productId)
                .imageUrl(uploadResult.getUrl())
                .publicId(uploadResult.getPublicId())
                .isPrimary(isPrimary)
                .build();

        ProductImage savedImage = productImageRepository.save(productImage);
        return mapToImageResponse(savedImage);
    }

    @Override
    @Transactional
    public void deleteProductImage(Long currentUserId, Long productId, Long imageId) {
        Product product = findProductOrThrow(productId);
        verifyOwnerOrAdmin(product, currentUserId);

        if (product.getStatus() == ProductStatus.IN_AUCTION) {
            throw new AppException("Cannot modify images while product is in an active auction", HttpStatus.BAD_REQUEST);
        }

        ProductImage image = productImageRepository.findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> new AppException("Product image not found", HttpStatus.NOT_FOUND));

        boolean wasPrimary = Boolean.TRUE.equals(image.getIsPrimary());

        if (image.getPublicId() != null && !image.getPublicId().isBlank()) {
            storageService.deleteFile(image.getPublicId());
        }

        productImageRepository.delete(image);

        if (wasPrimary) {
            List<ProductImage> remainingImages = productImageRepository.findByProductIdOrderByIsPrimaryDescCreatedAtAsc(productId);
            if (!remainingImages.isEmpty()) {
                ProductImage nextPrimary = remainingImages.get(0);
                nextPrimary.setIsPrimary(true);
                productImageRepository.save(nextPrimary);
            }
        }
    }

    @Override
    @Transactional
    public ProductImageResponse setPrimaryImage(Long currentUserId, Long productId, Long imageId) {
        Product product = findProductOrThrow(productId);
        verifyOwnerOrAdmin(product, currentUserId);

        ProductImage image = productImageRepository.findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> new AppException("Product image not found", HttpStatus.NOT_FOUND));

        productImageRepository.resetPrimaryByProductId(productId);

        image.setIsPrimary(true);
        ProductImage updatedImage = productImageRepository.save(image);
        return mapToImageResponse(updatedImage);
    }

    private Product findProductOrThrow(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new AppException("Product not found with id: " + productId, HttpStatus.NOT_FOUND));
    }

    private void verifyOwnerOrAdmin(Product product, Long currentUserId) {
        if (product.getSellerId().equals(currentUserId)) {
            return;
        }
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));
        if (currentUser.getRole() != UserRole.ADMIN) {
            throw new AppException("You are not authorized to access or modify this product", HttpStatus.FORBIDDEN);
        }
    }

    private ProductResponse mapToProductResponse(Product product) {
        String sellerName = userRepository.findById(product.getSellerId())
                .map(User::getFullName)
                .orElse(null);

        String categoryName = categoryRepository.findById(product.getCategoryId())
                .map(Category::getName)
                .orElse(null);

        List<ProductImageResponse> images = productImageRepository
                .findByProductIdOrderByIsPrimaryDescCreatedAtAsc(product.getId())
                .stream()
                .map(this::mapToImageResponse)
                .toList();

        return buildProductResponse(product, sellerName, categoryName, images);
    }

    private ProductResponse buildProductResponse(
            Product product,
            String sellerName,
            String categoryName,
            List<ProductImageResponse> images
    ) {
        return ProductResponse.builder()
                .id(product.getId())
                .sellerId(product.getSellerId())
                .sellerName(sellerName)
                .categoryId(product.getCategoryId())
                .categoryName(categoryName)
                .title(product.getTitle())
                .description(product.getDescription())
                .condition(product.getCondition())
                .status(product.getStatus())
                .images(images)
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    private ProductImageResponse mapToImageResponse(ProductImage image) {
        return ProductImageResponse.builder()
                .id(image.getId())
                .imageUrl(image.getImageUrl())
                .publicId(image.getPublicId())
                .isPrimary(image.getIsPrimary())
                .createdAt(image.getCreatedAt())
                .build();
    }
}

