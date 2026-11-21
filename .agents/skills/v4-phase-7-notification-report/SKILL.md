---
name: v4-phase-7-notification-report
description: Phase 7 đồng bộ v4 Omnibid — 5 loại thông báo phát sau COMMIT, POST /notifications/{id}/read, gửi báo cáo vi phạm với reportedOrderId, xử lý báo cáo bằng decision RESOLVED/REJECTED kèm note.
---
# Phase 7 — Thông báo và báo cáo vi phạm

> Tài liệu tham chiếu (references) nằm ở `.agents/skills/omnibid-v4-sync/references/`; script kiểm tra ở `.agents/skills/omnibid-v4-sync/scripts/verify-v4.ps1`. Tiến độ ghi ở `docs/v4/PROGRESS.md`.

Đọc trước: `BUSINESS_RULES_v4.md` BR-19, BR-22, §7; `API_SPEC_v4.md` §2.5, §2.7 (#47–49). Mục GAP: G1–G5.

## 1. Thông báo
- `NotificationService.notify(...)` chỉ ghi DB qua sự kiện sau COMMIT (giữ `NotificationPushListener` AFTER_COMMIT; nếu hiện đang ghi trực tiếp trong transaction nghiệp vụ, chuyển sang publish `NotificationRequested` event rồi listener AFTER_COMMIT ghi trong `REQUIRES_NEW`).
- Rà toàn bộ lời gọi `notify` trong mã: đúng loại, đúng người nhận theo bảng §7; không gửi cho ADMIN.
- `common/NotificationTemplates`: chỉ còn mẫu cho 5 loại (tiêu đề, nội dung tiếng Việt; tiền định dạng `520.000 ₫`; thời gian hiển thị theo +07:00).
- `NotificationController`: `GET /notifications` (isRead, type; mặc định sort `createdAt,desc`), `POST /notifications/{id}/read` (chỉ người nhận, khác → 403; đã đọc → vẫn 200, idempotent). Xóa `unread-count`, `read-all`. Vai trò: `hasRole('USER')`.

## 2. Báo cáo vi phạm
- `ReportRequest(reportedProductId, reportedUserId, reportedOrderId, reason)`: ít nhất 1 đối tượng (`@AssertTrue` hoặc kiểm tra Service → VALIDATION_ERROR); `reason` `@NotBlank` ≤ 1000 ký tự; các đối tượng phải tồn tại (404). Báo cáo đơn chỉ khi người gửi là một bên của đơn (403). Không cho tự báo cáo chính mình (VALIDATION_ERROR — ghi quyết định). 201.
- Chuyển các endpoint admin báo cáo sang `controller/AdminReportController` (hoặc `AdminController`), service `ReportService` **không phụ thuộc** `AdminService`:
  - `GET /admin/reports`: status, targetType (PRODUCT → reported_product_id not null, USER → reported_user_id not null, ORDER → reported_order_id not null); sort mặc định `createdAt,desc`.
  - `GET /admin/reports/{id}`: chi tiết gồm thông tin tóm tắt đối tượng bị báo cáo (tên sản phẩm + trạng thái, tên người dùng + trạng thái, mã đơn + trạng thái) để Frontend hiển thị nút tắt.
  - `POST /admin/reports/{id}/resolve` body `ReportResolveRequest(decision: ReportDecision, note @NotBlank)`: khóa report, phải PENDING (khác → `INVALID_STATUS_TRANSITION`), `resolve/reject(note, adminId, now)`; sau COMMIT REPORT_RESOLVED cho người gửi. **Không** khóa user, ẩn sản phẩm, hủy phiên hay đổi đơn.
- Xóa mọi nhánh xử lý `ReportAction`, `ReportType`, `INVALID_REPORT_ACTION`.

## Hình dạng response
Mọi DTO trả về của phase này phải khớp `omnibid-v4-sync/references/RESPONSE_DTO_v4.md` (kể cả các trường dẫn xuất như `viewerIsSeller`, `viewerIsLeading`, `orderId`, `activeAuctionId`, `everAuctioned`, `productCount`, `bannedUsers`, `targets`). Mô tả trường trong skill này chỉ là tóm tắt.

## Cổng
`compile` sạch. Nếu kịp: sửa `ReportServiceTest`, `NotificationServiceTest`, `ReportControllerTest`, `NotificationControllerTest`. Commit `refactor(v4): phase 7 - notifications and reports`.
