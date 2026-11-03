package com.omnibid.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnibid.common.GlobalExceptionHandler;
import com.omnibid.dto.shipment.ShipmentDetailResponse;
import com.omnibid.dto.shipment.ShipmentEventDto;
import com.omnibid.dto.shipment.ShipmentStatusResponse;
import com.omnibid.dto.shipment.ShipmentStatusUpdateRequest;
import com.omnibid.entity.User;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.ShipmentStatus;
import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.service.ShipmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ShipmentControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ShipmentService shipmentService;

    @InjectMocks
    private ShipmentController shipmentController;

    private CustomUserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .id(1L)
                .email("seller@example.com")
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

        mockMvc = MockMvcBuilders.standaloneSetup(shipmentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(authenticationPrincipalResolver)
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/orders/{orderId}/shipment trả HTTP 200 và chi tiết vận đơn")
    void getShipment_Returns200() throws Exception {
        ShipmentEventDto event = ShipmentEventDto.builder()
                .status(ShipmentStatus.CREATED)
                .note("Vận đơn mô phỏng được tạo")
                .by("SYSTEM")
                .createdAt(LocalDateTime.now())
                .build();

        ShipmentDetailResponse res = ShipmentDetailResponse.builder()
                .id(4L)
                .orderId(9L)
                .trackingCode("SIM-000009")
                .carrierName("SIMULATED_CARRIER")
                .status(ShipmentStatus.CREATED)
                .events(List.of(event))
                .build();

        when(shipmentService.getShipmentByOrder(any(), eq(9L))).thenReturn(res);

        mockMvc.perform(get("/api/v1/orders/9/shipment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(4))
                .andExpect(jsonPath("$.data.trackingCode").value("SIM-000009"))
                .andExpect(jsonPath("$.data.events[0].by").value("SYSTEM"));
    }

    @Test
    @DisplayName("PATCH /api/v1/shipments/{id}/status cập nhật thành công trả HTTP 200")
    void updateStatus_Returns200() throws Exception {
        ShipmentStatusResponse res = ShipmentStatusResponse.builder()
                .id(4L)
                .status(ShipmentStatus.PICKED_UP)
                .orderStatus(OrderStatus.SHIPPING)
                .build();

        when(shipmentService.updateShipmentStatus(any(), eq(4L), any())).thenReturn(res);

        ShipmentStatusUpdateRequest req = new ShipmentStatusUpdateRequest(ShipmentStatus.PICKED_UP, "Đã bàn giao");

        mockMvc.perform(patch("/api/v1/shipments/4/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PICKED_UP"))
                .andExpect(jsonPath("$.data.orderStatus").value("SHIPPING"));
    }

    @Test
    @DisplayName("PATCH /api/v1/shipments/{id}/status thiếu status ném 400 VALIDATION_ERROR")
    void updateStatus_MissingStatus_Returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/shipments/4/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }
}
