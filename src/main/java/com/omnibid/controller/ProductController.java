package com.omnibid.controller;

import com.omnibid.common.ApiResponse;
import com.omnibid.common.AppException;
import com.omnibid.dto.product.ProductCreateRequest;
import com.omnibid.dto.product.ProductImageResponse;
import com.omnibid.dto.product.ProductResponse;
import com.omnibid.dto.product.ProductSummaryResponse;
import com.omnibid.dto.product.ProductUpdateRequest;
import com.omnibid.enums.ProductStatus;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ProductResponse> createProduct(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ProductCreateRequest request
    ) {
        ProductResponse response = productService.createProduct(requireUserId(userDetails), request);
        return ApiResponse.success("Product created successfully", response);
    }

    @GetMapping
    public ApiResponse<Page<ProductResponse>> getAllProducts(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(productService.getAllProducts(pageable));
    }

    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<ProductResponse>> getMyProducts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) ProductStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<ProductResponse> response = productService.getMyProducts(requireUserId(userDetails), status, pageable);
        return ApiResponse.success(response);
    }

    @GetMapping("/mine/summary")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ProductSummaryResponse> getMyProductsSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        ProductSummaryResponse response = productService.getMyProductsSummary(requireUserId(userDetails));
        return ApiResponse.success(response);
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> getProductById(@PathVariable Long id) {
        return ApiResponse.success(productService.getProductById(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ProductResponse> updateProduct(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody ProductUpdateRequest request
    ) {
        ProductResponse response = productService.updateProduct(requireUserId(userDetails), id, request);
        return ApiResponse.success("Product updated successfully", response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteProduct(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        productService.deleteProduct(requireUserId(userDetails), id);
        return ApiResponse.success("Product deleted successfully", null);
    }

    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ProductImageResponse> addProductImage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file
    ) {
        ProductImageResponse response = productService.addProductImage(requireUserId(userDetails), id, file);
        return ApiResponse.success("Product image uploaded successfully", response);
    }

    @DeleteMapping("/{id}/images/{imageId}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteProductImage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @PathVariable Long imageId
    ) {
        productService.deleteProductImage(requireUserId(userDetails), id, imageId);
        return ApiResponse.success("Product image deleted successfully", null);
    }

    @PatchMapping("/{id}/images/{imageId}/primary")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ProductImageResponse> setPrimaryImage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @PathVariable Long imageId
    ) {
        ProductImageResponse response = productService.setPrimaryImage(requireUserId(userDetails), id, imageId);
        return ApiResponse.success("Primary image updated successfully", response);
    }

    private Long requireUserId(CustomUserDetails userDetails) {
        if (userDetails == null) {
            throw new AppException("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        return userDetails.getId();
    }
}

