package com.omnibid.controller;

import com.omnibid.common.ApiResponse;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.product.ProductCreateRequest;
import com.omnibid.dto.product.ProductListItemResponse;
import com.omnibid.dto.product.ProductResponse;
import com.omnibid.dto.product.ProductUpdateRequest;
import com.omnibid.enums.ProductStatus;
import com.omnibid.security.CurrentUser;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
@PreAuthorize("hasRole('USER')")
public class ProductController {

    private final ProductService productService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductResponse> createProduct(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestPart("product") @Valid ProductCreateRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        return ApiResponse.ok(productService.createProduct(currentUser.id(), request, files));
    }

    @GetMapping("/mine")
    public ApiResponse<PageResponse<ProductListItemResponse>> getMyProducts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ProductStatus status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        return ApiResponse.ok(productService.getMyProducts(currentUser.id(), keyword, status, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> getProductById(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        return ApiResponse.ok(productService.getProductById(currentUser.id(), id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<ProductResponse> updateProduct(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody ProductUpdateRequest request
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        return ApiResponse.ok(productService.updateProduct(currentUser.id(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteProduct(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        productService.deleteProduct(currentUser.id(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductResponse> addProductImages(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        return ApiResponse.ok(productService.addProductImages(currentUser.id(), id, files));
    }

    @DeleteMapping("/{id}/images/{imageId}")
    public ApiResponse<ProductResponse> deleteProductImage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @PathVariable Long imageId
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        return ApiResponse.ok(productService.deleteProductImage(currentUser.id(), id, imageId));
    }
}
