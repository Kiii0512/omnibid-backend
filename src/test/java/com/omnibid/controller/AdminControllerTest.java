package com.omnibid.controller;

import com.omnibid.common.GlobalExceptionHandler;
import com.omnibid.controller.admin.*;
import com.omnibid.dto.admin.*;
import com.omnibid.dto.category.AdminCategoryResponse;
import com.omnibid.dto.category.CategoryRequest;
import com.omnibid.entity.User;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.ProductStatus;
import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.service.AdminService;
import com.omnibid.service.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    private MockMvc mockMvc;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private AdminService adminService;
    @Mock
    private CategoryService categoryService;

    private CustomUserDetails adminUserDetails;

    @BeforeEach
    void setUp() {
        User adminUser = User.builder()
                .id(9L)
                .email("admin@omnibid.local")
                .fullName("Admin System")
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();
        adminUserDetails = new CustomUserDetails(adminUser);

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
                return adminUserDetails;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AdminUserController(adminService),
                        new AdminProductController(adminService),
                        new AdminAuctionController(adminService),
                        new AdminOrderController(adminService),
                        new AdminCategoryController(categoryService),
                        new AdminStatisticsController(adminService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver(), authResolver)
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/admin/users/{id}/ban — khóa user trả 200")
    void banUser_Success() throws Exception {
        AdminBanUserResponse response = new AdminBanUserResponse(7L, UserStatus.BANNED, 2, 1);
        when(adminService.banUser(any(), eq(7L), eq("Gian lận"))).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/users/7/ban")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new AdminReasonRequest("Gian lận"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.status").value("BANNED"))
                .andExpect(jsonPath("$.data.cancelledAuctions").value(2))
                .andExpect(jsonPath("$.data.cancelledOrders").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/admin/products/{id}/hide — ẩn sản phẩm trả 200")
    void hideProduct_Success() throws Exception {
        ProductHideResponse response = new ProductHideResponse(4L, ProductStatus.HIDDEN, 1, 0);
        when(adminService.hideProduct(any(), eq(4L), eq("Hàng cấm"))).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/products/4/hide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new AdminReasonRequest("Hàng cấm"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(4))
                .andExpect(jsonPath("$.data.status").value("HIDDEN"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/auctions/{id}/cancel — admin hủy phiên trả 200")
    void cancelAuction_Success() throws Exception {
        AdminAuctionDetailResponse response = new AdminAuctionDetailResponse(
                12L, 4L, "Item", null, 1L, "Cat", AuctionStatus.CANCELLED,
                BigDecimal.valueOf(100000), BigDecimal.valueOf(100000), BigDecimal.valueOf(100000),
                0, null, Instant.now(), Instant.now(), Instant.now(),
                "Desc", null, java.util.List.of(), BigDecimal.valueOf(10000), null,
                null, null, null, null, Instant.now(), "Vi phạm quy định", ProductStatus.AVAILABLE, null
        );
        when(adminService.cancelAuction(any(), eq(12L), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/auctions/12/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new AdminCancelAuctionRequest("Vi phạm quy định"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(12))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/orders/{id}/cancel — admin hủy đơn trả 200")
    void cancelOrder_Success() throws Exception {
        AdminOrderDetailResponse response = new AdminOrderDetailResponse(
                9L, 12L, 4L, "Item", null, BigDecimal.valueOf(500000), OrderStatus.CANCELLED,
                null, null, Instant.now(), null, Instant.now(),
                null, null, null, null, null, null, Instant.now(), "Hủy đơn", ProductStatus.AVAILABLE, java.util.List.of()
        );
        when(adminService.cancelOrder(any(), eq(9L), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/orders/9/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new AdminCancelOrderRequest("Hủy đơn"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(9))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/categories — tạo danh mục trả 201")
    void createCategory_Success() throws Exception {
        AdminCategoryResponse response = new AdminCategoryResponse(1L, "Đồng hồ", "dong-ho", "watch", 0L);
        when(categoryService.createCategory(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new CategoryRequest("Đồng hồ", null, "watch"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.slug").value("dong-ho"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/statistics — thống kê tích lũy trả 200")
    void getStatistics_Success() throws Exception {
        AdminStatisticsResponse response = new AdminStatisticsResponse(
                120L,
                3L,
                Map.of(AuctionStatus.ACTIVE, 12L, AuctionStatus.ENDED, 30L),
                Map.of(OrderStatus.COMPLETED, 24L),
                BigDecimal.valueOf(18450000),
                5L
        );
        when(adminService.getStatistics(any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalUsers").value(120))
                .andExpect(jsonPath("$.data.pendingReports").value(5));
    }
}
