package com.omnibid.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnibid.common.GlobalExceptionHandler;
import com.omnibid.controller.admin.*;
import com.omnibid.dto.admin.*;
import com.omnibid.dto.auction.AuctionResponse;
import com.omnibid.dto.category.CategoryRequest;
import com.omnibid.dto.category.CategoryResponse;
import com.omnibid.dto.order.OrderResponse;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminControllersTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AdminService adminService;
    @Mock
    private CategoryService categoryService;

    private CustomUserDetails adminUserDetails;

    @BeforeEach
    void setUp() {
        User adminUser = User.builder()
                .id(9L)
                .email("admin@omnibid.com")
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

        AdminUserController userController = new AdminUserController(adminService);
        AdminProductController productController = new AdminProductController(adminService);
        AdminAuctionController auctionController = new AdminAuctionController(adminService);
        AdminOrderController orderController = new AdminOrderController(adminService);
        AdminCategoryController catController = new AdminCategoryController(categoryService);
        AdminStatisticsController statController = new AdminStatisticsController(adminService);

        mockMvc = MockMvcBuilders.standaloneSetup(
                        userController, productController, auctionController,
                        orderController, catController, statController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(authResolver, new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/admin/users/{id}/ban — khóa user trả 200")
    void banUser_Success() throws Exception {
        AdminBanUserResponse response = new AdminBanUserResponse(7L, UserStatus.BANNED, 2, 1);
        when(adminService.banUser(any(), eq(7L), eq("Gian lận đặt giá"))).thenReturn(response);

        AdminBanUserRequest request = new AdminBanUserRequest("Gian lận đặt giá");

        mockMvc.perform(post("/api/v1/admin/users/7/ban")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.status").value("BANNED"))
                .andExpect(jsonPath("$.data.cancelledAuctions").value(2))
                .andExpect(jsonPath("$.data.cancelledOrders").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/admin/users/{id}/unban — mở khóa user trả 200")
    void unbanUser_Success() throws Exception {
        AdminUserResponse response = new AdminUserResponse(
                7L, "User Seven", "seven@test.com", "0123456789", null,
                UserRole.USER, UserStatus.ACTIVE, Instant.now()
        );
        when(adminService.unbanUser(any(), eq(7L), eq("Đã xác minh"))).thenReturn(response);

        AdminBanUserRequest request = new AdminBanUserRequest("Đã xác minh");

        mockMvc.perform(post("/api/v1/admin/users/7/unban")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/products/{id}/hide — ẩn sản phẩm trả 200")
    void hideProduct_Success() throws Exception {
        ProductHideResponse response = new ProductHideResponse(4L, ProductStatus.HIDDEN, List.of(12L), List.of());
        when(adminService.hideProduct(any(), eq(4L), eq("Hàng cấm bán"))).thenReturn(response);

        ProductHideRequest request = new ProductHideRequest("Hàng cấm bán");

        mockMvc.perform(post("/api/v1/admin/products/4/hide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.productId").value(4))
                .andExpect(jsonPath("$.data.status").value("HIDDEN"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/auctions/{id}/cancel — admin hủy phiên trả 200")
    void cancelAuction_Success() throws Exception {
        AuctionResponse response = AuctionResponse.builder()
                .id(12L)
                .status(AuctionStatus.CANCELLED)
                .cancelReason("Vi phạm quy định")
                .build();
        when(adminService.cancelAuction(any(), eq(12L), eq("Vi phạm quy định"))).thenReturn(response);

        AdminCancelAuctionRequest request = new AdminCancelAuctionRequest("Vi phạm quy định");

        mockMvc.perform(post("/api/v1/admin/auctions/12/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(12))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/orders/{id}/cancel — admin hủy đơn trả 200")
    void cancelOrder_Success() throws Exception {
        OrderResponse response = OrderResponse.builder()
                .id(9L)
                .status(OrderStatus.CANCELLED)
                .build();
        when(adminService.cancelOrder(any(), eq(9L), eq("Hủy đơn theo khiếu nại"))).thenReturn(response);

        AdminCancelOrderRequest request = new AdminCancelOrderRequest("Hủy đơn theo khiếu nại");

        mockMvc.perform(post("/api/v1/admin/orders/9/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(9))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/categories — tạo danh mục trả 201")
    void createCategory_Success() throws Exception {
        CategoryResponse response = new CategoryResponse(1L, "Đồng hồ", "dong-ho", "watch", Instant.now());
        when(categoryService.createCategory(any(), any())).thenReturn(response);

        CategoryRequest request = new CategoryRequest("Đồng hồ", null, "watch");

        mockMvc.perform(post("/api/v1/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.slug").value("dong-ho"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/statistics — thống kê hệ thống trả 200")
    void getStatistics_Success() throws Exception {
        AdminStatisticsResponse response = new AdminStatisticsResponse(
                120L,
                Map.of(AuctionStatus.ACTIVE, 12L),
                Map.of(OrderStatus.COMPLETED, 24L),
                BigDecimal.valueOf(18450000),
                3L
        );
        when(adminService.getStatistics(any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalUsers").value(120))
                .andExpect(jsonPath("$.data.completedOrderValue").value(18450000));
    }
}
