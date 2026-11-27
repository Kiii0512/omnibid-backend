package com.omnibid.service.impl;

import com.omnibid.common.AfterCommitExecutor;
import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.common.ImageValidator;
import com.omnibid.common.PageResponse;
import com.omnibid.common.SortWhitelist;
import com.omnibid.dto.product.ProductCreateRequest;
import com.omnibid.dto.product.ProductImageResponse;
import com.omnibid.dto.product.ProductListItemResponse;
import com.omnibid.dto.product.ProductResponse;
import com.omnibid.dto.product.ProductUpdateRequest;
import com.omnibid.dto.storage.StoredImage;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Category;
import com.omnibid.entity.Product;
import com.omnibid.entity.ProductImage;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.ProductStatus;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.repository.CategoryRepository;
import com.omnibid.repository.ProductImageRepository;
import com.omnibid.repository.ProductRepository;
import com.omnibid.repository.ReportRepository;
import com.omnibid.service.ImageStorageService;
import com.omnibid.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final int MIN_PRODUCT_IMAGES = 1;
    private static final int MAX_PRODUCT_IMAGES = 5;
    private static final Set<AuctionStatus> OPEN_AUCTION_STATUSES = EnumSet.of(AuctionStatus.SCHEDULED, AuctionStatus.ACTIVE);
    private static final Set<String> MY_PRODUCT_SORT_FIELDS = Set.of("id", "createdAt", "updatedAt", "title", "status");

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final CategoryRepository categoryRepository;
    private final AuctionRepository auctionRepository;
    private final ReportRepository reportRepository;
    private final ImageValidator imageValidator;
    private final ImageStorageService imageStorageService;
    private final AfterCommitExecutor afterCommitExecutor;
    private final TransactionTemplate transactionTemplate;

    @Value("${cloudinary.folder:omnibid}")
    private String cloudinaryFolder = "omnibid";

    @Override
    public ProductResponse createProduct(Long sellerId, ProductCreateRequest request, List<MultipartFile> files) {
        // Bước 1: kiểm tra tất cả ảnh (magic bytes, <= 5 MB, 1..5 tệp) trước khi upload
        List<ImageValidator.ValidatedImage> validatedImages =
                imageValidator.validateAll(files, MIN_PRODUCT_IMAGES, MAX_PRODUCT_IMAGES);

        if (!categoryRepository.existsById(request.categoryId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy danh mục");
        }

        // Bước 2: upload lên Cloudinary ngoài transaction
        String folder = normalizeBaseFolder() + "/products/tmp";
        List<StoredImage> uploaded = uploadAllWithCompensation(validatedImages, folder);

        // Bước 3: lưu DB trong transaction ngắn; Bước 4: lỗi thì bù trừ xóa asset vừa upload
        try {
            return transactionTemplate.execute(status -> {
                Category category = categoryRepository.findById(request.categoryId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy danh mục"));

                Product product = Product.builder()
                        .sellerId(sellerId)
                        .categoryId(category.getId())
                        .title(request.title().trim())
                        .description(request.description().trim())
                        .condition(request.condition())
                        .status(ProductStatus.AVAILABLE)
                        .build();
                Product savedProduct = productRepository.save(product);

                List<ProductImage> savedImages = new ArrayList<>(uploaded.size());
                for (int i = 0; i < uploaded.size(); i++) {
                    StoredImage img = uploaded.get(i);
                    ProductImage entity = ProductImage.builder()
                            .productId(savedProduct.getId())
                            .imageUrl(img.url())
                            .publicId(img.publicId())
                            .isPrimary(i == 0)
                            .build();
                    savedImages.add(productImageRepository.save(entity));
                }

                return toProductResponse(savedProduct, category.getName(), savedImages, null, false);
            });
        } catch (RuntimeException ex) {
            deleteQuietly(uploaded);
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductListItemResponse> getMyProducts(
            Long sellerId,
            String keyword,
            ProductStatus status,
            Pageable pageable
    ) {
        SortWhitelist.validate(pageable, MY_PRODUCT_SORT_FIELDS);
        String normalizedKeyword = com.omnibid.common.SearchKeywordUtils.escapeLike(keyword);

        Page<Product> page = productRepository.findMyProducts(sellerId, status, normalizedKeyword, pageable);
        List<Product> products = page.getContent();
        if (products.isEmpty()) {
            return PageResponse.from(page, p -> null);
        }

        List<Long> productIds = products.stream().map(Product::getId).toList();
        Set<Long> categoryIds = products.stream().map(Product::getCategoryId).collect(Collectors.toSet());

        Map<Long, String> categoryNames = categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));

        Map<Long, List<ProductImage>> imagesByProduct = productImageRepository.findByProductIdInOrderByIdAsc(productIds)
                .stream()
                .collect(Collectors.groupingBy(ProductImage::getProductId));

        Map<Long, Long> activeAuctionByProduct = new HashMap<>();
        for (Auction auction : auctionRepository.findByProductIdInAndStatusIn(productIds, OPEN_AUCTION_STATUSES)) {
            activeAuctionByProduct.putIfAbsent(auction.getProductId(), auction.getId());
        }

        Set<Long> everAuctionedIds = auctionRepository.findProductIdsWithAnyAuction(productIds);

        return PageResponse.from(page, product -> {
            List<ProductImage> imgs = imagesByProduct.getOrDefault(product.getId(), List.of());
            return new ProductListItemResponse(
                    product.getId(),
                    product.getTitle(),
                    product.getCategoryId(),
                    categoryNames.get(product.getCategoryId()),
                    product.getCondition(),
                    product.getStatus(),
                    resolvePrimaryImageUrl(imgs),
                    imgs.size(),
                    activeAuctionByProduct.get(product.getId()),
                    everAuctionedIds.contains(product.getId()),
                    product.getCreatedAt()
            );
        });
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long sellerId, Long id) {
        Product product = getOwnedProduct(sellerId, id);
        return mapToResponse(product);
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Long sellerId, Long id, ProductUpdateRequest request) {
        Product product = getOwnedProductForUpdate(sellerId, id);
        if (!product.isEditable()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_EDITABLE, "Chỉ được sửa sản phẩm khi ở trạng thái AVAILABLE");
        }

        if (request.categoryId() != null && !categoryRepository.existsById(request.categoryId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy danh mục");
        }

        if (request.title() != null) {
            if (request.title().isBlank()) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "Tiêu đề sản phẩm không được để trống",
                        Map.of("fields", Map.of("title", "Tiêu đề sản phẩm không được để trống"))
                );
            }
            product.setTitle(request.title().trim());
        }
        if (request.description() != null) {
            if (request.description().isBlank()) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "Mô tả sản phẩm không được để trống",
                        Map.of("fields", Map.of("description", "Mô tả sản phẩm không được để trống"))
                );
            }
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
        Product product = getOwnedProductForUpdate(sellerId, id);

        if (!product.isEditable()) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATUS_TRANSITION,
                    "Chỉ được xóa sản phẩm khi ở trạng thái AVAILABLE"
            );
        }

        if (auctionRepository.existsByProductId(id)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "Không thể xóa sản phẩm đã từng lên phiên đấu giá");
        }

        if (reportRepository.existsByReportedProductId(id)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "Không thể xóa sản phẩm có báo cáo vi phạm liên quan");
        }

        List<String> publicIds = productImageRepository.findByProductId(id).stream()
                .map(ProductImage::getPublicId)
                .filter(pid -> pid != null && !pid.isBlank())
                .toList();

        productImageRepository.deleteByProductId(id);
        productRepository.delete(product);

        if (!publicIds.isEmpty()) {
            afterCommitExecutor.runAfterCommit(() -> publicIds.forEach(imageStorageService::delete));
        }
    }

    @Override
    public ProductResponse addProductImages(Long sellerId, Long productId, List<MultipartFile> files) {
        // Bước 1: kiểm tra ảnh
        List<ImageValidator.ValidatedImage> validatedImages =
                imageValidator.validateAll(files, MIN_PRODUCT_IMAGES, MAX_PRODUCT_IMAGES);

        // Kiểm tra nhanh trước khi upload (vẫn kiểm tra lại sau khi khóa dòng)
        Product snapshot = getOwnedProduct(sellerId, productId);
        if (!snapshot.isEditable()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_EDITABLE, "Chỉ được thêm ảnh khi sản phẩm ở trạng thái AVAILABLE");
        }
        long currentCount = productImageRepository.countByProductId(productId);
        if (currentCount + validatedImages.size() > MAX_PRODUCT_IMAGES) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE, "Sản phẩm chỉ được có tối đa 5 ảnh");
        }

        // Bước 2: upload ngoài transaction
        String folder = normalizeBaseFolder() + "/products/" + productId;
        List<StoredImage> uploaded = uploadAllWithCompensation(validatedImages, folder);

        // Bước 3: transaction ngắn khóa product, kiểm tra BR-23 + <= 5 ảnh, lưu DB; Bước 4: bù trừ nếu lỗi
        try {
            return transactionTemplate.execute(status -> {
                Product locked = getOwnedProductForUpdate(sellerId, productId);
                if (!locked.isEditable()) {
                    throw new BusinessException(ErrorCode.PRODUCT_NOT_EDITABLE, "Chỉ được thêm ảnh khi sản phẩm ở trạng thái AVAILABLE");
                }

                long existingCount = productImageRepository.countByProductId(productId);
                if (existingCount + uploaded.size() > MAX_PRODUCT_IMAGES) {
                    throw new BusinessException(ErrorCode.INVALID_IMAGE, "Sản phẩm chỉ được có tối đa 5 ảnh");
                }

                for (int i = 0; i < uploaded.size(); i++) {
                    StoredImage img = uploaded.get(i);
                    ProductImage entity = ProductImage.builder()
                            .productId(locked.getId())
                            .imageUrl(img.url())
                            .publicId(img.publicId())
                            .isPrimary(existingCount == 0 && i == 0)
                            .build();
                    productImageRepository.save(entity);
                }

                return mapToResponse(locked);
            });
        } catch (RuntimeException ex) {
            deleteQuietly(uploaded);
            throw ex;
        }
    }

    @Override
    @Transactional
    public ProductResponse deleteProductImage(Long sellerId, Long productId, Long imageId) {
        Product product = getOwnedProductForUpdate(sellerId, productId);
        if (!product.isEditable()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_EDITABLE, "Chỉ được xóa ảnh khi sản phẩm ở trạng thái AVAILABLE");
        }

        ProductImage image = productImageRepository.findById(imageId)
                .filter(img -> img.getProductId().equals(productId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy ảnh sản phẩm"));

        long count = productImageRepository.countByProductId(productId);
        if (count <= MIN_PRODUCT_IMAGES) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE, "Không thể xóa ảnh cuối cùng của sản phẩm");
        }

        String publicId = image.getPublicId();
        boolean wasPrimary = Boolean.TRUE.equals(image.getIsPrimary());
        productImageRepository.delete(image);
        productImageRepository.flush();

        if (wasPrimary) {
            List<ProductImage> remaining = productImageRepository.findByProductIdOrderByIdAsc(productId);
            if (!remaining.isEmpty()) {
                ProductImage nextPrimary = remaining.get(0);
                nextPrimary.setIsPrimary(true);
                productImageRepository.save(nextPrimary);
            }
        }

        if (publicId != null && !publicId.isBlank()) {
            afterCommitExecutor.runAfterCommit(() -> imageStorageService.delete(publicId));
        }

        return mapToResponse(product);
    }

    private List<StoredImage> uploadAllWithCompensation(List<ImageValidator.ValidatedImage> images, String folder) {
        List<StoredImage> uploaded = new ArrayList<>(images.size());
        try {
            for (ImageValidator.ValidatedImage img : images) {
                uploaded.add(imageStorageService.upload(img.bytes(), folder));
            }
            return uploaded;
        } catch (RuntimeException ex) {
            deleteQuietly(uploaded);
            throw ex;
        }
    }

    private void deleteQuietly(List<StoredImage> uploaded) {
        for (StoredImage img : uploaded) {
            if (img != null && img.publicId() != null) {
                imageStorageService.delete(img.publicId());
            }
        }
    }

    private String normalizeBaseFolder() {
        if (cloudinaryFolder == null || cloudinaryFolder.isBlank()) {
            return "omnibid";
        }
        return cloudinaryFolder.endsWith("/")
                ? cloudinaryFolder.substring(0, cloudinaryFolder.length() - 1)
                : cloudinaryFolder.trim();
    }

    private Product getOwnedProduct(Long sellerId, Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy sản phẩm"));
        if (!product.getSellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền thao tác với sản phẩm này");
        }
        return product;
    }

    private Product getOwnedProductForUpdate(Long sellerId, Long productId) {
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy sản phẩm"));
        if (!product.getSellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền thao tác với sản phẩm này");
        }
        return product;
    }

    private ProductResponse mapToResponse(Product product) {
        String categoryName = categoryRepository.findById(product.getCategoryId())
                .map(Category::getName)
                .orElse(null);
        List<ProductImage> images = productImageRepository.findByProductIdOrderByIdAsc(product.getId());
        Long activeAuctionId = auctionRepository
                .findFirstByProductIdAndStatusIn(product.getId(), OPEN_AUCTION_STATUSES)
                .map(Auction::getId)
                .orElse(null);
        boolean everAuctioned = activeAuctionId != null || auctionRepository.existsByProductId(product.getId());
        return toProductResponse(product, categoryName, images, activeAuctionId, everAuctioned);
    }

    private ProductResponse toProductResponse(
            Product product,
            String categoryName,
            List<ProductImage> images,
            Long activeAuctionId,
            boolean everAuctioned
    ) {
        List<ProductImageResponse> imageDtos = images.stream()
                .map(img -> new ProductImageResponse(img.getId(), img.getImageUrl(), Boolean.TRUE.equals(img.getIsPrimary())))
                .toList();

        return new ProductResponse(
                product.getId(),
                product.getTitle(),
                product.getDescription(),
                product.getCategoryId(),
                categoryName,
                product.getCondition(),
                product.getStatus(),
                resolvePrimaryImageUrl(images),
                imageDtos.size(),
                activeAuctionId,
                everAuctioned,
                imageDtos,
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }

    private String resolvePrimaryImageUrl(List<ProductImage> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        return images.stream()
                .filter(img -> Boolean.TRUE.equals(img.getIsPrimary()))
                .map(ProductImage::getImageUrl)
                .findFirst()
                .orElse(images.get(0).getImageUrl());
    }
}
