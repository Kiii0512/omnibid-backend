package com.omnibid.service.impl;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.product.*;
import com.omnibid.dto.storage.StorageUploadResult;
import com.omnibid.entity.Category;
import com.omnibid.entity.Product;
import com.omnibid.entity.ProductImage;
import com.omnibid.entity.User;
import com.omnibid.enums.ProductStatus;
import com.omnibid.repository.*;
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

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final AuctionRepository auctionRepository;
    private final StorageService storageService;

    @Override
    @Transactional
    public ProductResponse createProduct(Long sellerId, ProductCreateRequest request) {
        if (!categoryRepository.existsById(request.categoryId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Category not found");
        }

        Product product = Product.builder()
                .sellerId(sellerId)
                .categoryId(request.categoryId())
                .title(request.title().trim())
                .description(request.description().trim())
                .condition(request.condition())
                .status(ProductStatus.AVAILABLE)
                .build();

        Product saved = productRepository.save(product);
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> getProducts(Pageable pageable) {
        Page<Product> page = productRepository.findByStatus(ProductStatus.AVAILABLE, pageable);
        return PageResponse.from(page, this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Product not found with id: " + id));
        return mapToResponse(product);
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Long sellerId, Long id, ProductUpdateRequest request) {
        Product product = getOwnedProduct(sellerId, id);

        if (product.getStatus() == ProductStatus.IN_AUCTION) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_EDITABLE, "Cannot update product currently in auction");
        }

        if (request.categoryId() != null && !categoryRepository.existsById(request.categoryId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Category not found");
        }

        if (request.title() != null && !request.title().isBlank()) {
            product.setTitle(request.title().trim());
        }
        if (request.description() != null && !request.description().isBlank()) {
            product.setDescription(request.description().trim());
        }
        if (request.condition() != null) {
            product.setCondition(request.condition());
        }
        if (request.categoryId() != null) {
            product.setCategoryId(request.categoryId());
        }

        Product updated = productRepository.save(product);
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public void deleteProduct(Long sellerId, Long id) {
        Product product = getOwnedProduct(sellerId, id);

        if (product.getStatus() == ProductStatus.IN_AUCTION) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "Cannot delete product currently in auction");
        }

        if (auctionRepository.existsByProductId(id)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "Cannot delete product that has been in an auction");
        }
        List<ProductImage> images = productImageRepository.findByProductId(id);
        for (ProductImage img : images) {
            storageService.delete(img.getPublicId());
        }
        productImageRepository.deleteByProductId(id);
        productRepository.delete(product);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> getMyProducts(Long sellerId, ProductStatus status, Pageable pageable) {
        Page<Product> page = (status != null)
                ? productRepository.findBySellerIdAndStatus(sellerId, status, pageable)
                : productRepository.findBySellerId(sellerId, pageable);
        return PageResponse.from(page, this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductSummaryResponse getMyProductsSummary(Long sellerId) {
        long total = productRepository.countBySellerId(sellerId);
        long available = productRepository.countBySellerIdAndStatus(sellerId, ProductStatus.AVAILABLE);
        long inAuction = productRepository.countBySellerIdAndStatus(sellerId, ProductStatus.IN_AUCTION);
        long sold = productRepository.countBySellerIdAndStatus(sellerId, ProductStatus.SOLD);
        long hidden = productRepository.countBySellerIdAndStatus(sellerId, ProductStatus.HIDDEN);

        return new ProductSummaryResponse(total, available, inAuction, sold, hidden);
    }

    @Override
    @Transactional
    public ProductImageResponse uploadProductImage(Long sellerId, Long productId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE, "File cannot be empty");
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.startsWith("image/") && !contentType.equals("application/octet-stream")) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE, "Only image files are allowed");
        }

        Product product = getOwnedProduct(sellerId, productId);
        long count = productImageRepository.countByProductId(productId);
        if (count >= 5) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE, "Product can have at most 5 images");
        }

        StorageUploadResult uploadResult = storageService.upload(file, "products/" + productId);
        boolean isPrimary = (count == 0);

        ProductImage image = ProductImage.builder()
                .productId(product.getId())
                .imageUrl(uploadResult.url())
                .publicId(uploadResult.publicId())
                .isPrimary(isPrimary)
                .build();

        ProductImage saved = productImageRepository.save(image);
        return toImageResponse(saved);
    }

    @Override
    @Transactional
    public void deleteProductImage(Long sellerId, Long productId, Long imageId) {
        getOwnedProduct(sellerId, productId);
        ProductImage image = productImageRepository.findById(imageId)
                .filter(img -> img.getProductId().equals(productId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Product image not found"));

        storageService.delete(image.getPublicId());
        boolean wasPrimary = Boolean.TRUE.equals(image.getIsPrimary());
        productImageRepository.delete(image);

        if (wasPrimary) {
            List<ProductImage> remaining = productImageRepository.findByProductIdOrderByIdAsc(productId);
            if (!remaining.isEmpty()) {
                ProductImage nextPrimary = remaining.get(0);
                nextPrimary.setIsPrimary(true);
                productImageRepository.save(nextPrimary);
            }
        }
    }

    @Override
    @Transactional
    public ProductImageResponse setPrimaryImage(Long sellerId, Long productId, Long imageId) {
        getOwnedProduct(sellerId, productId);
        List<ProductImage> images = productImageRepository.findByProductId(productId);
        ProductImage target = images.stream()
                .filter(img -> img.getId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Product image not found"));

        for (ProductImage img : images) {
            boolean shouldBePrimary = img.getId().equals(imageId);
            if (!img.getIsPrimary().equals(shouldBePrimary)) {
                img.setIsPrimary(shouldBePrimary);
                productImageRepository.save(img);
            }
        }

        target.setIsPrimary(true);
        return toImageResponse(target);
    }

    private Product getOwnedProduct(Long sellerId, Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Product not found with id: " + productId));

        if (!product.getSellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "You do not own this product");
        }
        return product;
    }

    private ProductResponse mapToResponse(Product product) {
        String sellerName = userRepository.findById(product.getSellerId()).map(User::getFullName).orElse(null);
        String categoryName = categoryRepository.findById(product.getCategoryId()).map(Category::getName).orElse(null);
        List<ProductImageResponse> images = productImageRepository.findByProductIdOrderByIdAsc(product.getId())
                .stream()
                .map(this::toImageResponse)
                .toList();

        return new ProductResponse(
                product.getId(),
                product.getSellerId(),
                sellerName,
                product.getCategoryId(),
                categoryName,
                product.getTitle(),
                product.getDescription(),
                product.getCondition(),
                product.getStatus(),
                images,
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }

    private ProductImageResponse toImageResponse(ProductImage image) {
        return new ProductImageResponse(
                image.getId(),
                image.getImageUrl(),
                image.getPublicId(),
                image.getIsPrimary(),
                image.getCreatedAt()
        );
    }
}

