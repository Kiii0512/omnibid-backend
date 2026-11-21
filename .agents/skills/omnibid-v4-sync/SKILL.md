---
name: omnibid-v4-sync
description: Điều phối việc sửa backend Omnibid cho khớp bộ tài liệu v4 (Flyway 10 bảng, 54 endpoint, token_version, Bid không trạng thái, báo cáo RESOLVED/REJECTED, Cloudinary, UTC, DECIMAL(15,0)). Dùng khi người dùng yêu cầu đồng bộ/sửa backend theo tài liệu v4 hoặc hỏi phase tiếp theo.
---
# Omnibid — Đồng bộ backend theo tài liệu v4

## Khi được gọi
1. Đọc `docs/v4/PROGRESS.md` để biết phase nào đã xong. Nếu tệp chưa có dấu [x] nào, bắt đầu ở phase 0.
2. Đọc `references/GAP_ANALYSIS.md` (bức tranh tổng thể) và tài liệu tham chiếu mà phase hiện tại chỉ định.
3. Thực hiện **một phase mỗi lần** bằng cách làm theo skill tương ứng bên dưới. Hết phase: chạy cổng kiểm tra, cập nhật PROGRESS.md, commit, rồi **dừng lại báo cáo ngắn** cho người dùng (đã làm gì, quyết định nào tự đưa ra, việc tồn). Chỉ chuyển phase kế khi người dùng đồng ý, trừ khi họ yêu cầu chạy liên tục.

## Thứ tự phase
| Phase | Skill | Nội dung | Cổng |
|---|---|---|---|
| 0 | `v4-phase-0-prepare` | Nhánh, commit WIP, baseline | build gốc ghi nhận |
| 1 | `v4-phase-1-foundation` | Flyway V1/V2, properties + profile demo, UTC & Jackson +07:00, ApiResponse/ErrorCode/BusinessException/GlobalExceptionHandler, PageResponse | `compile` |
| 2 | `v4-phase-2-domain` | Entity/enum/repository theo 10 bảng; gỡ Watchlist, Shipment, AuditLog, BidStatus…; phương thức chuyển trạng thái | `compile` |
| 3 | `v4-phase-3-security` | JWT `ver`, filter, logout, đổi mật khẩu, LoginAttemptLimiter, SecurityConfig, WebSocket `/ws` chỉ đọc, auth/users DTO | `compile` |
| 4 | `v4-phase-4-images` | ImageValidator (magic bytes), ImageStorageService (Cloudinary), sản phẩm multipart, ảnh, avatar, danh mục `icon` | `compile` |
| 5 | `v4-phase-5-auction-bid` | Tạo/sửa/hủy/tìm phiên, đặt giá, chốt phiên BR-09, AuctionEvent | `compile` |
| 6 | `v4-phase-6-order-payment` | Thanh toán đồng bộ + idempotency, ship/confirm, OrderScheduler, quyền xem đơn | `compile` |
| 7 | `v4-phase-7-notification-report` | 5 loại thông báo, POST read, báo cáo decision/note | `compile` |
| 8 | `v4-phase-8-admin` | ban/unban/hide/cancel, danh sách & chi tiết admin, thống kê, thứ tự khóa | `compile` + `verify-v4.ps1` mục endpoint |
| 9 | `v4-phase-9-tests-docs` | Sửa/viết test, README, docs legacy, rà soát cuối | `test-compile`, `test`, `verify`, `verify-v4.ps1` xanh |

## Tài liệu tham chiếu (trong `references/`)
- `API_SPEC_v4.md` — 54 endpoint, tham số, HTTP, mã lỗi, JSON mẫu, phân quyền.
- `RESPONSE_DTO_v4.md` — hình dạng `data` của từng endpoint (hợp đồng chung với frontend; bản giống hệt ở repo frontend). Khi mô tả response trong skill phase khác tệp này, **tệp này thắng**. Không đổi tên trường nếu chưa sửa cả bản ở frontend.
- `DB_SPEC_v4.md` + `sql/` — schema, kiểu dữ liệu, Flyway.
- `BUSINESS_RULES_v4.md` — BR/NFR nguyên văn, chuyển trạng thái, khóa dòng, chốt phiên, thông báo, ảnh, xác thực.
- `CONFIG_v4.md` — properties, profile demo, bẫy Instant/+07:00.
- `GAP_ANALYSIS.md` — chênh lệch theo tệp (A1…I4). Khi hoàn thành một mục, ghi mã mục vào PROGRESS.md.
- `DELETE_LIST.md` — những gì phải gỡ.
- `scripts/verify-v4.ps1` — kiểm tra tự động (thuật ngữ cũ, migration, 10 bảng, 54 endpoint, ErrorCode, profile demo).

## Nguyên tắc khi sửa
- Ưu tiên **sửa tại chỗ** lớp hiện có (giữ tên lớp Service/Controller khi hợp lý) thay vì viết lại toàn bộ; chỉ tạo lớp mới khi references yêu cầu (ví dụ `ErrorCode`, `BusinessException`, `ImageStorageService`, `LoginAttemptLimiter`, `ImageValidator`).
- Đọc kỹ tệp trước khi sửa. Không sinh lại tệp lớn từ trí nhớ; sửa từng đoạn.
- Mỗi thay đổi hành vi phải truy được tới một BR/NFR/bảng trong references. Ghi mã đó trong Javadoc ngắn của phương thức Service (ví dụ `// BR-09`).
- Không thay đổi Frontend (repo khác). Ghi các thay đổi hợp đồng API vào `docs/v4/API_CHANGES_FOR_FRONTEND.md` (endpoint đổi method/path, trường đổi tên, response mới) để nhóm Frontend cập nhật.
- Khi phát hiện mâu thuẫn giữa các references: dừng mục đó, ghi vào PROGRESS.md "Cần người duyệt", làm tiếp mục khác.
