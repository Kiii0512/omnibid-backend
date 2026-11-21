package com.omnibid.service.impl;

import com.omnibid.common.AfterCommitExecutor;
import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.common.ImageValidator;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.product.ProductCreateRequest;
import com.omnibid.dto.product.ProductImageResponse;
import com.omnibid.dto.product.ProductResponse;
import com.omnibid.dto.product.ProductUpdateRequest;
import com.omnibid.dto.storage.StoredImage;
import com.omnibid.entity.Category;
import com.omnibid.entity.Product;
import com.omnibid.entity.ProductImage;
import com.omnibid.entity.User;
import com.omnibid.enums.ProductStatus;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.repository.CategoryRepository;
import com.omnibid.repository.ProductImageRepository;
import com.omnibid.repository.ProductRepository;
import com.omnibid.repository.UserRepository;
import com.omnibid.service.ImageStorageService;
import com.omnibid.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
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
    private final ImageStorageService imageStorageService;
    private final AfterCommitExecutor afterCommitExecutor;
    private final TransactionTemplate transactionTemplate;

    @Override
    public ProductResponse createProduct(Long sellerId, ProductCreateRequest request, List<MultipartFile> files) {
        if (files == null || files.isEmpty() || files.size() > 5) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE, "Số lượng ảnh sản phẩm phải từ 1 đến 5 ảnh");
        }

        List<ImageValidator.ValidatedImage> validated = files.stream().map(ImageValidator::validate).toList();

        if (!categoryRepository.existsById(request.categoryId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Danh mục không tồn tại");
        }

        List<StoredImage> uploaded = new ArrayList<>();
        try {
            for (ImageValidator.ValidatedImage img : validated) {
                uploaded.add(imageStorageService.upload(img.bytes(), "omnibid/products"));
            }
        } catch (Exception ex) {
            for (StoredImage stored : uploaded) {
                imageStorageService.delete(stored.publicId());
            }
            throw ex;
        }

        try {
            return transactionTemplate.execute(status -> {
                Product product = Product.builder()
                        .sellerId(sellerId)
                        .categoryId(request.categoryId())
                        .title(request.title().trim())
                        .description(request.description().trim())
                        .condition(request.condition())
                        .status(ProductStatus.AVAILABLE)
                        .build();
                Product saved = productRepository.save(product);

                for (int i = 0; i < uploaded.size(); i++) {
                    StoredImage stored = uploaded.get(i);
                    ProductImage pImg = ProductImage.builder()
                            .productId(saved.getId())
                            .imageUrl(stored.url())
                            .publicId(stored.publicId())
                            .isPrimary(i == 0)
                            .build();
                    productImageRepository.save(pImg);
                }

                return mapToResponse(saved);
            });
        } catch (Exception ex) {
            for (StoredImage stored : uploaded) {
                imageStorageService.delete(stored.publicId());
            }
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> getMyProducts(Long sellerId, String keyword, ProductStatus status, Pageable pageable) {
        Page<Product> page = productRepository.findMyProducts(sellerId, status, keyword, pageable);
        return PageResponse.from(page, this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long sellerId, Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Sản phẩm không tồn tại"));

        if (!product.getSellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền xem sản phẩm này");
        }
        return mapToResponse(product);
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Long sellerId, Long id, ProductUpdateRequest request) {
        Product product = productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Sản phẩm không tồn tại"));

        if (!product.getSellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền chỉnh sửa sản phẩm này");
        }

        if (!product.isEditable()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_EDITABLE, "Sản phẩm không ở trạng thái cho phép chỉnh sửa");
        }

        if (request.categoryId() != null && !categoryRepository.existsById(request.categoryId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Danh mục không tồn tại");
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
        Product product = productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Sản phẩm không tồn tại"));

        if (!product.getSellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền xóa sản phẩm này");
        }

        if (!product.isEditable()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_EDITABLE, "Sản phẩm không ở trạng thái cho phép xóa");
        }

        if (auctionRepository.existsByProductId(id)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "Không thể xóa sản phẩm đã từng lên phiên");
        }

        List<ProductImage> images = productImageRepository.findByProductId(id);
        List<String> publicIds = images.stream().map(ProductImage::getPublicId).toList();

        productImageRepository.deleteByProductId(id);
        productRepository.delete(product);

        afterCommitExecutor.execute(() -> {
            for (String pid : publicIds) {
                imageStorageService.delete(pid);
            }
        });
    }

    @Override
    public List<ProductImageResponse> uploadProductImages(Long sellerId, Long productId, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE, "Danh sách ảnh không được để trống");
        }

        List<ImageValidator.ValidatedImage> validated = files.stream().map(ImageValidator::validate).toList();

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Sản phẩm không tồn tại"));

        if (!product.getSellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền thêm ảnh cho sản phẩm này");
        }
        if (!product.isEditable()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_EDITABLE, "Sản phẩm không ở trạng thái cho phép chỉnh sửa");
        }

        long currentCount = productImageRepository.countByProductId(productId);
        if (currentCount + files.size() > 5) {
            throw new BusinessException(
                    ErrorCode.INVALID_IMAGE,
                    "Tổng số ảnh sản phẩm không được vượt quá 5 ảnh (hiện có " + currentCount + " ảnh)"
            );
        }

        List<StoredImage> uploaded = new ArrayList<>();
        try {
            for (ImageValidator.ValidatedImage img : validated) {
                uploaded.add(imageStorageService.upload(img.bytes(), "omnibid/products"));
            }
        } catch (Exception ex) {
            for (StoredImage stored : uploaded) {
                imageStorageService.delete(stored.publicId());
            }
            throw ex;
        }

        try {
            return transactionTemplate.execute(status -> {
                Product locked = productRepository.findByIdForUpdate(productId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Sản phẩm không tồn tại"));
                if (!locked.isEditable()) {
                    throw new BusinessException(ErrorCode.PRODUCT_NOT_EDITABLE, "Sản phẩm không ở trạng thái cho phép chỉnh sửa");
                }
                long count = productImageRepository.countByProductId(productId);
                if (count + uploaded.size() > 5) {
                    throw new BusinessException(ErrorCode.INVALID_IMAGE, "Tổng số ảnh vượt quá 5 ảnh");
                }

                List<ProductImageResponse> result = new ArrayList<>();
                for (StoredImage stored : uploaded) {
                    ProductImage pImg = ProductImage.builder()
                            .productId(productId)
                            .imageUrl(stored.url())
                            .publicId(stored.publicId())
                            .isPrimary(count == 0 && result.isEmpty())
                            .build();
                    ProductImage saved = productImageRepository.save(pImg);
                    result.add(toImageResponse(saved));
                }
                return result;
            });
        } catch (Exception ex) {
            for (StoredImage stored : uploaded) {
                imageStorageService.delete(stored.publicId());
            }
            throw ex;
        }
    }

    @Override
    @Transactional
    public void deleteProductImage(Long sellerId, Long productId, Long imageId) {
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Sản phẩm không tồn tại"));

        if (!product.getSellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền xóa ảnh sản phẩm này");
        }

        if (!product.isEditable()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_EDITABLE, "Sản phẩm không ở trạng thái cho phép chỉnh sửa");
        }

        long count = productImageRepository.countByProductId(productId);
        if (count <= 1) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE, "Không thể xóa ảnh cuối cùng của sản phẩm");
        }

        ProductImage image = productImageRepository.findById(imageId)
                .filter(img -> img.getProductId().equals(productId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Ảnh không tồn tại"));

        String publicId = image.getPublicId();
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

        afterCommitExecutor.execute(() -> imageStorageService.delete(publicId));
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
