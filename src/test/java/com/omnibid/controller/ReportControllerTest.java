package com.omnibid.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnibid.common.GlobalExceptionHandler;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.report.ReportDetailResponse;
import com.omnibid.dto.report.ReportRequest;
import com.omnibid.dto.report.ReportResolveRequest;
import com.omnibid.dto.report.ReportResolveResponse;
import com.omnibid.dto.report.ReportResponse;
import com.omnibid.entity.User;
import com.omnibid.enums.ReportStatus;
import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.service.ReportService;
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
class ReportControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ReportService reportService;

    @InjectMocks
    private ReportController reportController;

    private CustomUserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .id(2L)
                .email("user@omnibid.com")
                .passwordHash("hashed")
                .fullName("Nguyen Van A")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        mockUserDetails = new CustomUserDetails(user);

        HandlerMethodArgumentResolver authPrincipalResolver = new HandlerMethodArgumentResolver() {
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

        mockMvc = MockMvcBuilders.standaloneSetup(reportController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(authPrincipalResolver, new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/reports — tạo báo cáo thành công trả 201")
    void createReport_Success_Returns201() throws Exception {
        ReportResponse response = new ReportResponse(
                21L,
                2L,
                com.omnibid.enums.ReportTargetType.ORDER,
                null,
                null,
                9L,
                "Hàng nhận được khác với mô tả",
                ReportStatus.PENDING,
                Instant.now(),
                null,
                null,
                null
        );
        when(reportService.createReport(any(), any())).thenReturn(response);

        ReportRequest request = new ReportRequest(
                null,
                null,
                9L,
                "Hàng nhận được khác với mô tả"
        );

        mockMvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(21))
                .andExpect(jsonPath("$.data.reportedOrderId").value(9))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("POST /api/v1/reports — thiếu reason trả 400")
    void createReport_MissingReason_Returns400() throws Exception {
        ReportRequest request = new ReportRequest(
                null,
                null,
                9L,
                null
        );

        mockMvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/admin/reports — trả danh sách báo cáo 200")
    void getAdminReports_Success_Returns200() throws Exception {
        ReportDetailResponse item = new ReportDetailResponse(
                21L,
                com.omnibid.enums.ReportTargetType.ORDER,
                new ReportDetailResponse.ReporterSummary(2L, "Nguyen Buyer"),
                null,
                null,
                new ReportDetailResponse.ReportOrderSummary(9L, "SHIPPING"),
                "Hang hu hong",
                ReportStatus.PENDING,
                null,
                null,
                null,
                Instant.now()
        );
        PageResponse<ReportDetailResponse> pageResponse = PageResponse.<ReportDetailResponse>builder()
                .content(List.of(item))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .build();

        when(reportService.getAdminReports(any(), any(), any(), any())).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/admin/reports")
                        .param("status", "PENDING")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(21))
                .andExpect(jsonPath("$.data.content[0].reporter.fullName").value("Nguyen Buyer"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/reports/{id}/resolve — xử lý thành công trả 200")
    void resolveReport_Success_Returns200() throws Exception {
        ReportResolveResponse response = new ReportResolveResponse(
                21L,
                ReportStatus.RESOLVED,
                com.omnibid.enums.ReportDecision.RESOLVED,
                "Chap nhan khieu nai, hoan tat don",
                1L,
                Instant.now()
        );
        when(reportService.resolveReport(any(), eq(21L), any())).thenReturn(response);

        ReportResolveRequest request = new ReportResolveRequest(
                com.omnibid.enums.ReportDecision.RESOLVED,
                "Chap nhan khieu nai, hoan tat don"
        );

        mockMvc.perform(post("/api/v1/admin/reports/21/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(21))
                .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                .andExpect(jsonPath("$.data.note").value("Chap nhan khieu nai, hoan tat don"));
    }
}
