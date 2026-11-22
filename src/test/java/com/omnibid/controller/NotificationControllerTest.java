package com.omnibid.controller;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.common.GlobalExceptionHandler;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.notification.NotificationResponse;
import com.omnibid.entity.User;
import com.omnibid.enums.NotificationType;
import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.service.NotificationService;
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
class NotificationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController notificationController;

    private CustomUserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .id(2L)
                .email("user@example.com")
                .fullName("Nguyen Van A")
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

        mockMvc = MockMvcBuilders.standaloneSetup(notificationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(authResolver, new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/notifications trả 200 kèm PageResponse")
    void getNotifications_Returns200() throws Exception {
        NotificationResponse res = NotificationResponse.builder()
                .id(1L)
                .type(NotificationType.OUTBID)
                .title("Bạn đã bị vượt giá")
                .content("Nội dung")
                .refType("AUCTION")
                .refId(100L)
                .isRead(false)
                .createdAt(Instant.parse("2026-11-20T02:00:00Z"))
                .build();

        PageResponse<NotificationResponse> pageRes = PageResponse.<NotificationResponse>builder()
                .content(List.of(res))
                .page(0)
                .size(10)
                .totalElements(1)
                .totalPages(1)
                .build();

        when(notificationService.getNotifications(eq(2L), any(), any(), any())).thenReturn(pageRes);

        mockMvc.perform(get("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(1))
                .andExpect(jsonPath("$.data.content[0].type").value("OUTBID"));
    }

    @Test
    @DisplayName("POST /api/v1/notifications/{id}/read trả 200 khi đánh dấu thành công")
    void markAsRead_Success_Returns200() throws Exception {
        NotificationResponse res = NotificationResponse.builder()
                .id(10L)
                .type(NotificationType.AUCTION_RESULT)
                .title("Thắng phiên")
                .content("Nội dung")
                .isRead(true)
                .createdAt(Instant.parse("2026-11-20T02:00:00Z"))
                .build();

        when(notificationService.markAsRead(2L, 10L)).thenReturn(res);

        mockMvc.perform(post("/api/v1/notifications/10/read")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.isRead").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/notifications/{id}/read trả 403 FORBIDDEN khi không phải người nhận")
    void markAsRead_NotOwner_Returns403() throws Exception {
        when(notificationService.markAsRead(2L, 99L))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền truy cập thông báo này"));

        mockMvc.perform(post("/api/v1/notifications/99/read")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }
}
