package com.omnibid.controller;

import com.omnibid.common.GlobalExceptionHandler;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.report.AdminReportDetailResponse;
import com.omnibid.dto.report.AdminReportItemResponse;
import com.omnibid.dto.report.ReportRequest;
import com.omnibid.dto.report.ReportResolveRequest;
import com.omnibid.dto.report.ReportResponse;
import com.omnibid.dto.report.ReportTargetSummary;
import com.omnibid.dto.user.UserRef;
import com.omnibid.entity.User;
import com.omnibid.enums.ReportDecision;
import com.omnibid.enums.ReportStatus;
import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.service.ReportService;
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
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private ReportService reportService;

    private CustomUserDetails currentPrincipal;

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
        currentPrincipal = new CustomUserDetails(user);

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
                return currentPrincipal;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ReportController(reportService),
                        new AdminReportController(reportService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(authPrincipalResolver, new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/reports — tạo báo cáo thành công trả 201")
    void createReport_Success_Returns201() throws Exception {
        ReportResponse response = new ReportResponse(
                21L,
                ReportStatus.PENDING,
                Instant.parse("2026-11-20T02:00:00Z")
        );
        when(reportService.createReport(any(), any())).thenReturn(response);

        ReportRequest request = new ReportRequest(null, null, 9L, "Hàng nhận được khác mô tả");

        mockMvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(21))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("POST /api/v1/reports — thiếu reason trả 400 VALIDATION_ERROR")
    void createReport_MissingReason_Returns400() throws Exception {
        ReportRequest request = new ReportRequest(null, null, 9L, "   ");

        mockMvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/reports — trả danh sách báo cáo 200")
    void getAdminReports_Success_Returns200() throws Exception {
        AdminReportItemResponse item = new AdminReportItemResponse(
                21L,
                new UserRef(2L, "Nguyen Buyer"),
                new ReportTargetSummary(null, null, null),
                "Hàng hư hỏng",
                ReportStatus.PENDING,
                Instant.parse("2026-11-20T02:00:00Z")
        );
        PageResponse<AdminReportItemResponse> pageResponse = PageResponse.<AdminReportItemResponse>builder()
                .content(List.of(item))
                .page(0)
                .size(10)
                .totalElements(1)
                .totalPages(1)
                .build();

        when(reportService.getAdminReports(any(), any(), any(), any())).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/admin/reports")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(21))
                .andExpect(jsonPath("$.data.content[0].reporter.fullName").value("Nguyen Buyer"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/reports/{id} — trả chi tiết báo cáo 200")
    void getAdminReportById_Success_Returns200() throws Exception {
        AdminReportDetailResponse detail = new AdminReportDetailResponse(
                21L,
                new UserRef(2L, "Nguyen Buyer"),
                new ReportTargetSummary(null, null, null),
                "Hàng hư hỏng",
                ReportStatus.PENDING,
                Instant.parse("2026-11-20T02:00:00Z"),
                null,
                null,
                null
        );
        when(reportService.getAdminReportById(any(), eq(21L))).thenReturn(detail);

        mockMvc.perform(get("/api/v1/admin/reports/21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(21));
    }

    @Test
    @DisplayName("POST /api/v1/admin/reports/{id}/resolve — xử lý thành công trả 200")
    void resolveReport_Success_Returns200() throws Exception {
        AdminReportDetailResponse response = new AdminReportDetailResponse(
                21L,
                new UserRef(2L, "Nguyen Buyer"),
                new ReportTargetSummary(null, null, null),
                "Hàng hư hỏng",
                ReportStatus.RESOLVED,
                Instant.parse("2026-11-20T02:00:00Z"),
                "Đã xử lý báo cáo",
                new UserRef(1L, "Admin"),
                Instant.parse("2026-11-20T03:00:00Z")
        );
        when(reportService.resolveReport(any(), eq(21L), any())).thenReturn(response);

        ReportResolveRequest request = new ReportResolveRequest(ReportDecision.RESOLVED, "Đã xử lý báo cáo");

        mockMvc.perform(post("/api/v1/admin/reports/21/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(21))
                .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                .andExpect(jsonPath("$.data.adminNote").value("Đã xử lý báo cáo"))
                .andExpect(jsonPath("$.data.resolvedBy.id").value(1));
    }
}
