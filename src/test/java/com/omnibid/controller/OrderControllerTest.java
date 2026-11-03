package com.omnibid.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnibid.common.AppException;
import com.omnibid.common.GlobalExceptionHandler;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.order.OrderDetailResponse;
import com.omnibid.dto.order.OrderResponse;
import com.omnibid.dto.order.ShippingInfoRequest;
import com.omnibid.entity.User;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderController orderController;

    private CustomUserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .id(2L)
                .email("buyer@example.com")
                .fullName("Buyer One")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        mockUserDetails = new CustomUserDetails(user);

        mockMvc = MockMvcBuilders.standaloneSetup(orderController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
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
                })
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/orders trả 200 kèm PageResponse<OrderResponse>")
    void getOrders_Returns200() throws Exception {
        OrderResponse orderRes = OrderResponse.builder()
                .id(9L)
                .auctionId(12L)
                .productId(4L)
                .productTitle("Máy ảnh Fuji X100V")
                .finalPrice(BigDecimal.valueOf(520000))
                .status(OrderStatus.PENDING_PAYMENT)
                .viewerRole("BUYER")
                .createdAt(LocalDateTime.now())
                .build();

        PageResponse<OrderResponse> pageRes = PageResponse.<OrderResponse>builder()
                .content(List.of(orderRes))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .build();

        when(orderService.getOrders(any(), any(), any(), any())).thenReturn(pageRes);

        mockMvc.perform(get("/api/v1/orders")
                        .param("role", "buyer")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(9))
                .andExpect(jsonPath("$.data.content[0].viewerRole").value("BUYER"));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id} trả 200 với chi tiết đơn")
    void getOrderById_Returns200() throws Exception {
        OrderDetailResponse detail = OrderDetailResponse.builder()
                .id(9L)
                .auctionId(12L)
                .productId(4L)
                .productTitle("Máy ảnh Fuji X100V")
                .status(OrderStatus.PENDING_PAYMENT)
                .viewerRole("BUYER")
                .allowedActions(List.of("PAY", "UPDATE_SHIPPING_INFO"))
                .build();

        when(orderService.getOrderById(any(), eq(9L))).thenReturn(detail);

        mockMvc.perform(get("/api/v1/orders/9")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(9))
                .andExpect(jsonPath("$.data.allowedActions[0]").value("PAY"));
    }

    @Test
    @DisplayName("PATCH /api/v1/orders/{id}/shipping-info trả 200 khi cập nhật thành công")
    void updateShippingInfo_Returns200() throws Exception {
        ShippingInfoRequest req = ShippingInfoRequest.builder()
                .shippingAddress("12 Trần Hưng Đạo, Hà Nội")
                .phoneNumber("0900000000")
                .build();

        OrderDetailResponse detail = OrderDetailResponse.builder()
                .id(9L)
                .shippingAddress(req.getShippingAddress())
                .phoneNumber(req.getPhoneNumber())
                .status(OrderStatus.PENDING_PAYMENT)
                .build();

        when(orderService.updateShippingInfo(any(), eq(9L), any())).thenReturn(detail);

        mockMvc.perform(patch("/api/v1/orders/9/shipping-info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.shippingAddress").value("12 Trần Hưng Đạo, Hà Nội"));
    }

    @Test
    @DisplayName("PATCH /api/v1/orders/{id}/shipping-info validate lỗi khi phone number sai định dạng")
    void updateShippingInfo_ValidationFails_Returns400() throws Exception {
        ShippingInfoRequest req = ShippingInfoRequest.builder()
                .shippingAddress("12 Trần Hưng Đạo")
                .phoneNumber("abc") // Invalid phone
                .build();

        mockMvc.perform(patch("/api/v1/orders/9/shipping-info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }
}
