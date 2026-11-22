package com.omnibid.controller;

import com.omnibid.common.GlobalExceptionHandler;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.payment.PaymentHistoryResponse;
import com.omnibid.dto.payment.PaymentRequest;
import com.omnibid.dto.payment.PaymentResultResponse;
import com.omnibid.entity.User;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.PaymentMethod;
import com.omnibid.enums.PaymentStatus;
import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.service.PaymentService;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    private MockMvc mockMvc;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentController paymentController;

    private CustomUserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .id(2L)
                .email("buyer@example.com")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        mockUserDetails = new CustomUserDetails(user);

        HandlerMethodArgumentResolver authenticationPrincipalResolver = new HandlerMethodArgumentResolver() {
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

        mockMvc = MockMvcBuilders.standaloneSetup(paymentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver(), authenticationPrincipalResolver)
                .build();
    }

    @Test
    @DisplayName("POST /orders/{id}/payments thành công trả HTTP 200 với ApiResponse")
    void pay_Success_Returns200() throws Exception {
        String key = UUID.randomUUID().toString();
        PaymentResultResponse response = PaymentResultResponse.builder()
                .paymentId(16L)
                .attemptNo(1)
                .status(PaymentStatus.SUCCESS)
                .orderStatus(OrderStatus.PAID)
                .build();

        when(paymentService.pay(any(), eq(9L), eq(key), any())).thenReturn(response);

        PaymentRequest request = new PaymentRequest(PaymentMethod.MOCK_CARD, null);

        mockMvc.perform(post("/api/v1/orders/9/payments")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.paymentId").value(16))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.orderStatus").value("PAID"));
    }

    @Test
    @DisplayName("POST /orders/{id}/payments thiếu method ném 400 VALIDATION_ERROR")
    void pay_MissingMethod_Returns400() throws Exception {
        mockMvc.perform(post("/api/v1/orders/9/payments")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET /orders/{id}/payments trả PageResponse lịch sử thanh toán 200")
    void getPayments_ReturnsPage() throws Exception {
        PaymentHistoryResponse h1 = PaymentHistoryResponse.builder()
                .id(16L)
                .attemptNo(2)
                .method(PaymentMethod.MOCK_CARD)
                .amount(BigDecimal.valueOf(520000))
                .status(PaymentStatus.SUCCESS)
                .createdAt(Instant.now())
                .build();

        PageResponse<PaymentHistoryResponse> page = PageResponse.<PaymentHistoryResponse>builder()
                .content(List.of(h1))
                .page(0)
                .size(10)
                .totalElements(1)
                .totalPages(1)
                .build();

        when(paymentService.getPaymentsByOrder(any(), eq(9L), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/orders/9/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(16))
                .andExpect(jsonPath("$.data.content[0].status").value("SUCCESS"));
    }
}
