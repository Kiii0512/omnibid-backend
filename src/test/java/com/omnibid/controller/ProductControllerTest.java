package com.omnibid.controller;

import com.omnibid.common.GlobalExceptionHandler;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.product.ProductCreateRequest;
import com.omnibid.dto.product.ProductImageResponse;
import com.omnibid.dto.product.ProductResponse;
import com.omnibid.dto.product.ProductUpdateRequest;
import com.omnibid.entity.User;
import com.omnibid.enums.ProductCondition;
import com.omnibid.enums.ProductStatus;
import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ProductService productService;

    @InjectMocks
    private ProductController productController;

    private CustomUserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .id(2L)
                .email("seller@example.com")
                .fullName("Seller User")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        mockUserDetails = new CustomUserDetails(user);

        HandlerMethodArgumentResolver authResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter,
                                          ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest,
                                          WebDataBinderFactory binderFactory) {
                return mockUserDetails;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(productController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(authResolver)
                .build();
    }

    @Test
    @DisplayName("PATCH /api/v1/products/{id} with USED condition returns 200 OK")
    void updateProduct_WithUsedCondition_Returns200() throws Exception {
        ProductResponse response = new ProductResponse(
                5L,
                2L,
                "Seller User",
                3L,
                "Antiques",
                "Bình gốm thời Chu thế kỷ 18",
                "Mô tả",
                ProductCondition.USED,
                ProductStatus.AVAILABLE,
                Collections.emptyList(),
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(productService.updateProduct(eq(2L), eq(5L), any(ProductUpdateRequest.class)))
                .thenReturn(response);

        String jsonBody = "{\"categoryId\": 3, \"title\": \"Bình gốm thời Chu thế kỷ 18\", \"description\": \"Mô tả\", \"condition\": \"USED\"}";

        mockMvc.perform(patch("/api/v1/products/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Product updated successfully"))
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.condition").value("USED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/products/{id} with partial fields returns 200 OK")
    void updateProduct_PartialUpdate_Returns200() throws Exception {
        ProductResponse response = new ProductResponse(
                5L,
                2L,
                "Seller User",
                3L,
                "Antiques",
                "Bình gốm thời Chu thế kỷ 18 mới",
                "Mô tả gốc",
                ProductCondition.GOOD,
                ProductStatus.AVAILABLE,
                Collections.emptyList(),
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(productService.updateProduct(eq(2L), eq(5L), any(ProductUpdateRequest.class)))
                .thenReturn(response);

        String jsonBody = "{\"title\": \"Bình gốm thời Chu thế kỷ 18 mới\"}";

        mockMvc.perform(patch("/api/v1/products/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Product updated successfully"))
                .andExpect(jsonPath("$.data.title").value("Bình gốm thời Chu thế kỷ 18 mới"));
    }

    @Test
    @DisplayName("POST /api/v1/products with USED condition returns 201 Created")
    void createProduct_WithUsedCondition_Returns201() throws Exception {
        ProductResponse response = new ProductResponse(
                6L,
                2L,
                "Seller User",
                3L,
                "Antiques",
                "Đĩa gốm cổ",
                "Mô tả đĩa",
                ProductCondition.USED,
                ProductStatus.AVAILABLE,
                Collections.emptyList(),
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(productService.createProduct(eq(2L), any(ProductCreateRequest.class)))
                .thenReturn(response);

        String jsonBody = "{\"categoryId\": 3, \"title\": \"Đĩa gốm cổ\", \"description\": \"Mô tả đĩa\", \"condition\": \"USED\"}";

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(6))
                .andExpect(jsonPath("$.data.condition").value("USED"));
    }

    @Test
    @DisplayName("GET /api/v1/products/{id} returns 200 OK")
    void getProductById_Returns200() throws Exception {
        ProductResponse response = new ProductResponse(
                5L,
                2L,
                "Seller User",
                3L,
                "Antiques",
                "Bình gốm thời Chu thế kỷ 18",
                "Mô tả",
                ProductCondition.USED,
                ProductStatus.AVAILABLE,
                Collections.emptyList(),
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(productService.getProductById(5L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/products/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(5));
    }

    @Test
    @DisplayName("DELETE /api/v1/products/{id} returns 204 No Content")
    void deleteProduct_Returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/products/5"))
                .andExpect(status().isNoContent());

        verify(productService).deleteProduct(2L, 5L);
    }

    @Test
    @DisplayName("POST /api/v1/products/{id}/images uploads image successfully and returns 201 Created")
    void uploadProductImage_Success() throws Exception {
        ProductImageResponse response = new ProductImageResponse(
                10L,
                "/uploads/products/9/uuid-keychron.jpg",
                "products/9/uuid-keychron.jpg",
                true,
                LocalDateTime.now()
        );

        when(productService.uploadProductImage(eq(2L), eq(9L), any())).thenReturn(response);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "keychron.jpg",
                "image/jpeg",
                "fake image content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/products/9/images")
                        .file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Image uploaded successfully"))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.imageUrl").value("/uploads/products/9/uuid-keychron.jpg"))
                .andExpect(jsonPath("$.data.isPrimary").value(true));
    }

    @Test
    @DisplayName("DELETE /api/v1/products/{id}/images/{imageId} returns 204 No Content")
    void deleteProductImage_Returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/products/9/images/10"))
                .andExpect(status().isNoContent());

        verify(productService).deleteProductImage(2L, 9L, 10L);
    }

    @Test
    @DisplayName("PATCH /api/v1/products/{id}/images/{imageId}/primary returns 200 OK")
    void setPrimaryImage_Returns200() throws Exception {
        ProductImageResponse response = new ProductImageResponse(
                10L,
                "/uploads/products/9/uuid-keychron.jpg",
                "products/9/uuid-keychron.jpg",
                true,
                LocalDateTime.now()
        );

        when(productService.setPrimaryImage(2L, 9L, 10L)).thenReturn(response);

        mockMvc.perform(patch("/api/v1/products/9/images/10/primary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.isPrimary").value(true));
    }
}
