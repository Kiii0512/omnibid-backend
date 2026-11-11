package com.omnibid.service;

import com.omnibid.dto.product.ProductCreateRequest;
import com.omnibid.dto.product.ProductImageResponse;
import com.omnibid.dto.product.ProductResponse;
import com.omnibid.dto.product.ProductSummaryResponse;
import com.omnibid.dto.product.ProductUpdateRequest;
import com.omnibid.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface ProductService {

    ProductResponse createProduct(Long currentUserId, ProductCreateRequest request);

    ProductResponse getProductById(Long id);

    Page<ProductResponse> getAllProducts(Pageable pageable);

    Page<ProductResponse> getMyProducts(Long currentUserId, ProductStatus status, Pageable pageable);

    ProductSummaryResponse getMyProductsSummary(Long currentUserId);

    ProductResponse updateProduct(Long currentUserId, Long productId, ProductUpdateRequest request);

    void deleteProduct(Long currentUserId, Long productId);

    ProductImageResponse addProductImage(Long currentUserId, Long productId, MultipartFile file);

    void deleteProductImage(Long currentUserId, Long productId, Long imageId);

    ProductImageResponse setPrimaryImage(Long currentUserId, Long productId, Long imageId);
}

