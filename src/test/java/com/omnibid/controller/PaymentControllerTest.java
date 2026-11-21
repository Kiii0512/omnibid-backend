package com.omnibid.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnibid.common.GlobalExceptionHandler;
import com.omnibid.dto.payment.PaymentHistoryResponse;
import com.omnibid.dto.payment.PaymentRequest;
import com.omnibid.dto.payment.PaymentResponse;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
                .setCustomArgumentResolvers(authenticationPrincipalResolver, new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("POST /orders/{id}/payments thành công trả HTTP 200 với ApiResponse")
    void pay_Success_Returns200() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .paymentId(16L)
                .attemptNo(1)
                .status(PaymentStatus.SUCCESS)
                .orderStatus(OrderStatus.PAID)
                .build();

        when(paymentService.pay(any(), eq(9L), eq("key-12345678"), any())).thenReturn(response);

        PaymentRequest request = new PaymentRequest(PaymentMethod.MOCK_CARD, null);

        mockMvc.perform(post("/api/v1/orders/9/payments")
                        .header("Idempotency-Key", "key-12345678")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
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
                        .header("Idempotency-Key", "key-12345678")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET /orders/{id}/payments trả danh sách lịch sử thanh toán 200")
    void getPayments_ReturnsList() throws Exception {
        PaymentHistoryResponse h1 = PaymentHistoryResponse.builder()
                .paymentId(16L)
                .attemptNo(2)
                .method(PaymentMethod.MOCK_CARD)
                .amount(BigDecimal.valueOf(520000))
                .status(PaymentStatus.SUCCESS)
                .createdAt(Instant.now())
                .build();

        when(paymentService.getPaymentsByOrder(any(), eq(9L), any()))
                .thenReturn(new PageImpl<>(List.of(h1), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/orders/9/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].paymentId").value(16))
                .andExpect(jsonPath("$.data.content[0].status").value("SUCCESS"));
    }
}
