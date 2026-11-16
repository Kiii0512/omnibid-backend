package com.omnibid.service;

import com.omnibid.common.PageResponse;
import com.omnibid.dto.product.*;
import com.omnibid.enums.ProductStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface ProductService {

    ProductResponse createProduct(Long sellerId, ProductCreateRequest request);

    PageResponse<ProductResponse> getProducts(Pageable pageable);

    ProductResponse getProductById(Long id);

    ProductResponse updateProduct(Long sellerId, Long id, ProductUpdateRequest request);

    void deleteProduct(Long sellerId, Long id);

    PageResponse<ProductResponse> getMyProducts(Long sellerId, ProductStatus status, Pageable pageable);

    ProductSummaryResponse getMyProductsSummary(Long sellerId);

    ProductImageResponse uploadProductImage(Long sellerId, Long productId, MultipartFile file);

    void deleteProductImage(Long sellerId, Long productId, Long imageId);

    ProductImageResponse setPrimaryImage(Long sellerId, Long productId, Long imageId);
}

