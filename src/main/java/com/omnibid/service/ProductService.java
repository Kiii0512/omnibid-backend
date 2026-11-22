package com.omnibid.service;

import com.omnibid.common.PageResponse;
import com.omnibid.dto.product.ProductCreateRequest;
import com.omnibid.dto.product.ProductListItemResponse;
import com.omnibid.dto.product.ProductResponse;
import com.omnibid.dto.product.ProductUpdateRequest;
import com.omnibid.enums.ProductStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ProductService {

    ProductResponse createProduct(Long sellerId, ProductCreateRequest request, List<MultipartFile> files);

    PageResponse<ProductListItemResponse> getMyProducts(Long sellerId, String keyword, ProductStatus status, Pageable pageable);

    ProductResponse getProductById(Long sellerId, Long id);

    ProductResponse updateProduct(Long sellerId, Long id, ProductUpdateRequest request);

    void deleteProduct(Long sellerId, Long id);

    ProductResponse addProductImages(Long sellerId, Long productId, List<MultipartFile> files);

    ProductResponse deleteProductImage(Long sellerId, Long productId, Long imageId);
}
