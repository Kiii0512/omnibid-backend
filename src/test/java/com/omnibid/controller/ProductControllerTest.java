package com.omnibid.controller;

import com.omnibid.common.GlobalExceptionHandler;
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

import java.time.Instant;
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

    private ProductResponse sampleProduct(Long id, ProductCondition condition, String title) {
        ProductImageResponse img = new ProductImageResponse(10L, "https://cdn/1.png", true);
        return new ProductResponse(
                id, title, "Mô tả", 3L, "Antiques", condition, ProductStatus.AVAILABLE,
                "https://cdn/1.png", 1, null, false, List.of(img), Instant.now(), Instant.now()
        );
    }

    @Test
    @DisplayName("POST /api/v1/products multipart trả 201 Created")
    void createProduct_Multipart_Returns201() throws Exception {
        when(productService.createProduct(eq(2L), any(ProductCreateRequest.class), any()))
                .thenReturn(sampleProduct(6L, ProductCondition.GOOD, "Đĩa gốm cổ"));

        MockMultipartFile productPart = new MockMultipartFile(
                "product", "", MediaType.APPLICATION_JSON_VALUE,
                "{\"categoryId\":3,\"title\":\"Đĩa gốm cổ\",\"description\":\"Mô tả đĩa\",\"condition\":\"GOOD\"}".getBytes()
        );
        MockMultipartFile filePart = new MockMultipartFile(
                "files", "1.png", MediaType.IMAGE_PNG_VALUE, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}
        );

        mockMvc.perform(multipart("/api/v1/products").file(productPart).file(filePart))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(6))
                .andExpect(jsonPath("$.data.condition").value("GOOD"));
    }

    @Test
    @DisplayName("PATCH /api/v1/products/{id} trả 200 OK")
    void updateProduct_Returns200() throws Exception {
        when(productService.updateProduct(eq(2L), eq(5L), any(ProductUpdateRequest.class)))
                .thenReturn(sampleProduct(5L, ProductCondition.LIKE_NEW, "Bình gốm mới"));

        String jsonBody = "{\"categoryId\": 3, \"title\": \"Bình gốm mới\", \"description\": \"Mô tả\", \"condition\": \"LIKE_NEW\"}";

        mockMvc.perform(patch("/api/v1/products/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.title").value("Bình gốm mới"));
    }

    @Test
    @DisplayName("GET /api/v1/products/{id} trả 200 OK")
    void getProductById_Returns200() throws Exception {
        when(productService.getProductById(2L, 5L))
                .thenReturn(sampleProduct(5L, ProductCondition.NEW, "Bình gốm"));

        mockMvc.perform(get("/api/v1/products/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(5));
    }

    @Test
    @DisplayName("DELETE /api/v1/products/{id} trả 200 OK")
    void deleteProduct_Returns200() throws Exception {
        mockMvc.perform(delete("/api/v1/products/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(productService).deleteProduct(2L, 5L);
    }

    @Test
    @DisplayName("POST /api/v1/products/{id}/images trả 201 Created")
    void addProductImages_Returns201() throws Exception {
        when(productService.addProductImages(eq(2L), eq(9L), any()))
                .thenReturn(sampleProduct(9L, ProductCondition.NEW, "Bàn phím"));

        MockMultipartFile file = new MockMultipartFile("files", "keychron.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/v1/products/9/images").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(9));
    }

    @Test
    @DisplayName("DELETE /api/v1/products/{id}/images/{imageId} trả 200 OK")
    void deleteProductImage_Returns200() throws Exception {
        when(productService.deleteProductImage(2L, 9L, 10L))
                .thenReturn(sampleProduct(9L, ProductCondition.NEW, "Bàn phím"));

        mockMvc.perform(delete("/api/v1/products/9/images/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(productService).deleteProductImage(2L, 9L, 10L);
    }
}
