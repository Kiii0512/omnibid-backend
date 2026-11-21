package com.omnibid.controller;

import com.omnibid.common.ApiResponse;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.product.ProductCreateRequest;
import com.omnibid.dto.product.ProductImageResponse;
import com.omnibid.dto.product.ProductResponse;
import com.omnibid.dto.product.ProductUpdateRequest;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<ProductResponse> createProduct(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestPart("product") ProductCreateRequest request,
            @RequestPart("files") List<MultipartFile> files
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        ProductResponse response = productService.createProduct(currentUser.id(), request, files);
        return ApiResponse.success(response);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<PageResponse<ProductResponse>> getMyProducts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ProductStatus status,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        PageResponse<ProductResponse> response = productService.getMyProducts(currentUser.id(), q, status, pageable);
        return ApiResponse.success(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<ProductResponse> getProductById(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        ProductResponse response = productService.getProductById(currentUser.id(), id);
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
        return ApiResponse.success(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Void> deleteProduct(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        productService.deleteProduct(currentUser.id(), id);
        return ApiResponse.success(null);
    }

    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<List<ProductImageResponse>> uploadProductImages(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @RequestPart("files") List<MultipartFile> files
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        List<ProductImageResponse> response = productService.uploadProductImages(currentUser.id(), id, files);
        return ApiResponse.success(response);
    }

    @DeleteMapping("/{id}/images/{imageId}")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Void> deleteProductImage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @PathVariable Long imageId
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        productService.deleteProductImage(currentUser.id(), id, imageId);
        return ApiResponse.success(null);
    }
}
