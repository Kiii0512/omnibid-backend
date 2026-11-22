package com.omnibid.controller;

import com.omnibid.common.GlobalExceptionHandler;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.order.OrderConfirmReceiptResponse;
import com.omnibid.dto.order.OrderDetailResponse;
import com.omnibid.dto.order.OrderSummaryResponse;
import com.omnibid.dto.order.ShipOrderRequest;
import com.omnibid.dto.order.ShipOrderResponse;
import com.omnibid.dto.order.ShippingInfoRequest;
import com.omnibid.entity.User;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.ProductStatus;
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
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    private MockMvc mockMvc;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

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
                .setCustomArgumentResolvers(
                        new PageableHandlerMethodArgumentResolver(),
                        new HandlerMethodArgumentResolver() {
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
                        }
                )
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/orders trả 200 kèm PageResponse<OrderSummaryResponse>")
    void getOrders_Returns200() throws Exception {
        OrderSummaryResponse orderRes = OrderSummaryResponse.builder()
                .id(9L)
                .auctionId(12L)
                .productId(4L)
                .title("Máy ảnh Fuji X100V")
                .finalPrice(BigDecimal.valueOf(520000))
                .status(OrderStatus.PENDING_PAYMENT)
                .createdAt(Instant.now())
                .build();

        PageResponse<OrderSummaryResponse> pageRes = PageResponse.<OrderSummaryResponse>builder()
                .content(List.of(orderRes))
                .page(0)
                .size(10)
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
                .andExpect(jsonPath("$.data.content[0].title").value("Máy ảnh Fuji X100V"));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id} trả 200 với chi tiết đơn")
    void getOrderById_Returns200() throws Exception {
        OrderDetailResponse detail = OrderDetailResponse.builder()
                .id(9L)
                .auctionId(12L)
                .productId(4L)
                .title("Máy ảnh Fuji X100V")
                .status(OrderStatus.PENDING_PAYMENT)
                .viewerRole("BUYER")
                .build();

        when(orderService.getOrderById(any(), eq(9L))).thenReturn(detail);

        mockMvc.perform(get("/api/v1/orders/9")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(9))
                .andExpect(jsonPath("$.data.viewerRole").value("BUYER"));
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
                        .content(jsonMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.shippingAddress").value("12 Trần Hưng Đạo, Hà Nội"));
    }

    @Test
    @DisplayName("PATCH /api/v1/orders/{id}/shipping-info validate lỗi khi phone number sai định dạng")
    void updateShippingInfo_ValidationFails_Returns400() throws Exception {
        ShippingInfoRequest req = ShippingInfoRequest.builder()
                .shippingAddress("12 Trần Hưng Đạo")
                .phoneNumber("abc")
                .build();

        mockMvc.perform(patch("/api/v1/orders/9/shipping-info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/v1/orders/{id}/ship trả 200 khi người bán xác nhận gửi hàng")
    void shipOrder_Returns200() throws Exception {
        Instant now = Instant.now();
        ShipOrderResponse res = ShipOrderResponse.builder()
                .id(9L)
                .orderStatus(OrderStatus.SHIPPING)
                .trackingCode("VN123456")
                .shippedAt(now)
                .confirmDeadline(now.plus(7, ChronoUnit.DAYS))
                .build();

        when(orderService.shipOrder(any(), eq(9L), any())).thenReturn(res);

        mockMvc.perform(post("/api/v1/orders/9/ship")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new ShipOrderRequest("VN123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderStatus").value("SHIPPING"))
                .andExpect(jsonPath("$.data.trackingCode").value("VN123456"));
    }

    @Test
    @DisplayName("POST /api/v1/orders/{id}/confirm-receipt trả 200 khi xác nhận nhận hàng thành công")
    void confirmReceipt_Returns200() throws Exception {
        OrderConfirmReceiptResponse res = OrderConfirmReceiptResponse.builder()
                .id(9L)
                .orderStatus(OrderStatus.COMPLETED)
                .productStatus(ProductStatus.SOLD)
                .build();

        when(orderService.confirmReceipt(any(), eq(9L))).thenReturn(res);

        mockMvc.perform(post("/api/v1/orders/9/confirm-receipt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(9))
                .andExpect(jsonPath("$.data.orderStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.productStatus").value("SOLD"));
    }
}
