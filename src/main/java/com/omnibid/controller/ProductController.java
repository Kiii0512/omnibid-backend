package com.omnibid.controller;

import com.omnibid.common.ApiResponse;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.product.*;
import com.omnibid.enums.ProductStatus;
import com.omnibid.security.CurrentUser;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<ProductResponse> createProduct(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ProductCreateRequest request
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        ProductResponse response = productService.createProduct(currentUser.id(), request);
        return ApiResponse.success("Product created successfully", response);
    }

    @GetMapping
    public ApiResponse<PageResponse<ProductResponse>> getProducts(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        PageResponse<ProductResponse> response = productService.getProducts(pageable);
        return ApiResponse.success(response);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<PageResponse<ProductResponse>> getMyProducts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) ProductStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        PageResponse<ProductResponse> response = productService.getMyProducts(currentUser.id(), status, pageable);
        return ApiResponse.success(response);
    }

    @GetMapping("/mine/summary")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<ProductSummaryResponse> getMyProductsSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        ProductSummaryResponse response = productService.getMyProductsSummary(currentUser.id());
        return ApiResponse.success(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<ProductResponse> getProductById(@PathVariable Long id) {
        ProductResponse response = productService.getProductById(id);
        return ApiResponse.success(response);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<ProductResponse> updateProduct(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody ProductUpdateRequest request
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        ProductResponse response = productService.updateProduct(currentUser.id(), id, request);
        return ApiResponse.success("Product updated successfully", response);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Void> deleteProduct(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        productService.deleteProduct(currentUser.id(), id);
        return ApiResponse.success("Product deleted successfully", null);
    }

    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<ProductImageResponse> uploadProductImage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        ProductImageResponse response = productService.uploadProductImage(currentUser.id(), id, file);
        return ApiResponse.success("Image uploaded successfully", response);
    }

    @DeleteMapping("/{id}/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Void> deleteProductImage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @PathVariable Long imageId
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        productService.deleteProductImage(currentUser.id(), id, imageId);
        return ApiResponse.success("Image deleted successfully", null);
    }

    @PatchMapping("/{id}/images/{imageId}/primary")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<ProductImageResponse> setPrimaryImage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @PathVariable Long imageId
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        ProductImageResponse response = productService.setPrimaryImage(currentUser.id(), id, imageId);
        return ApiResponse.success("Primary image updated successfully", response);
    }
}

