---
name: v4-phase-9-tests-docs
description: Phase 9 đồng bộ v4 Omnibid — sửa và viết test theo v4, chạy unit/integration test, chạy verify-v4.ps1, cập nhật README, chuyển docs cũ sang docs/legacy, tổng kết PROGRESS và thay đổi API cho Frontend.
---
# Phase 9 — Kiểm thử, tài liệu, rà soát cuối

> Tài liệu tham chiếu (references) nằm ở `.agents/skills/omnibid-v4-sync/references/`; script kiểm tra ở `.agents/skills/omnibid-v4-sync/scripts/verify-v4.ps1`. Tiến độ ghi ở `docs/v4/PROGRESS.md`.

Đọc trước: rule `v4-testing`; `GAP_ANALYSIS.md` mục I; `DELETE_LIST.md`.

## 1. Hạ tầng test
- `support/BaseIT`: TRUNCATE 10 bảng; tạo dữ liệu qua `Seed` (cập nhật theo entity mới); admin có thể dùng bản ghi V2 (`admin@omnibid.local`) hoặc tạo trong Seed.
- `support/InvariantCheck`: thay bất biến cũ bằng: (1) mỗi product ≤ 1 auction SCHEDULED/ACTIVE; (2) `auctions.bid_count = COUNT(bids)`; (3) phiên có bid: `current_price = MAX(bids.amount)` và `highest_bidder_id` = bidder của bid max; (4) mỗi order ≤ 1 payment SUCCESS; (5) order PAID/SHIPPING/COMPLETED có đúng 1 SUCCESS; (6) product HIDDEN không có phiên mở; (7) auction ENDED ⇔ có đúng 1 order.
- `TestSupportConfig`: bean `FakeImageStorageService` (@Primary) đếm upload/delete; `MutableClock` UTC.

## 2. Dọn và sửa test hiện có
- Xóa: `ShipmentControllerTest`, `ShipmentServiceTest`, phần test audit/watchlist/BidStatus/Payment PENDING/report action.
- Sửa còn lại theo hợp đồng mới (ApiResponse `error.code`, endpoint mới, Instant, BigDecimal scale 0).

## 3. Test mới bắt buộc (tối thiểu)
| Nhóm | Ca kiểm thử |
|---|---|
| Auth | đăng xuất ⇒ token cũ 401; đổi mật khẩu ⇒ token cũ 401; 5 lần sai ⇒ lần 6 429 kể cả đúng mật khẩu; sau 15 phút (MutableClock) đăng nhập lại được; BANNED với token còn hạn ⇒ 403 ở endpoint bảo vệ và Public |
| Ảnh | tệp `.png` chứa PDF ⇒ 400 INVALID_IMAGE; 6 ảnh ⇒ INVALID_IMAGE; lỗi DB sau upload ⇒ Fake ghi nhận delete (bù trừ); sửa sản phẩm IN_AUCTION ⇒ 409 PRODUCT_NOT_EDITABLE |
| Đặt giá | BID_TOO_LOW kèm minNextBid; SELF_BID 409; ALREADY_LEADING; hết giờ ⇒ AUCTION_NOT_ACTIVE; 20 luồng đồng thời ⇒ bất biến (2)(3) |
| BR-15 | tạo 2 phiên cùng sản phẩm đồng thời ⇒ 1 thành công, 1 PRODUCT_NOT_AVAILABLE/CONFLICT |
| Chốt phiên | có bid đạt sàn ⇒ ENDED + order; chưa đạt sàn ⇒ UNSOLD; leader BANNED ⇒ UNSOLD, không trao người thứ hai |
| Thanh toán | FAILED rồi SUCCESS khóa mới; gửi lặp khóa cũ ⇒ cùng paymentId; quá hạn ⇒ ORDER_EXPIRED; simulate tắt ⇒ luôn SUCCESS |
| Đơn | ship ⇒ confirmDeadline = shippedAt + 7 ngày; confirm ⇒ SOLD; ẩn sản phẩm khi SHIPPING ⇒ COMPLETED vẫn HIDDEN |
| Admin | ban: hủy phiên đang bán + đơn PENDING_PAYMENT, giữ bid; CANNOT_BAN_ADMIN; hide ⇒ HIDDEN và không bỏ ẩn được; Admin gọi `/orders/{id}` ⇒ 403 |
| Báo cáo | resolve thiếu note ⇒ 400; resolve 2 lần ⇒ 409; resolve không đổi user/product |
| JSON | thời gian trả `+07:00`; nhận `Z` và `+07:00`; tiền là số nguyên |
| Hợp đồng | test liệt kê `RequestMappingHandlerMapping` ⇒ đúng 54 endpoint `/api/v1/**` |

## 4. Chạy
`.\mvnw.cmd test-compile` → `.\mvnw.cmd test` → `.\mvnw.cmd verify` (Docker) → `verify-v4.ps1`. Tất cả xanh. Nếu không có Docker: ghi rõ, vẫn phải xanh unit test.
Thử chạy ứng dụng với profile `dev,local` trên database rỗng mới để Flyway tạo schema (nếu người dùng có MySQL); đăng nhập `admin@omnibid.local` / `Admin@123456`.

## 5. Tài liệu
- Di chuyển `docs/01-requirements … docs/04-testing` → `docs/legacy/` + `docs/legacy/README.md`.
- `README.md`: cập nhật kiến trúc (10 bảng, Flyway, Cloudinary, UTC), cách chạy (biến môi trường, profile demo), danh sách 54 endpoint (dẫn tới `API_SPEC_v4.md`), tài khoản admin seed (nhắc đổi mật khẩu), lệnh test.
- `docs/v4/API_CHANGES_FOR_FRONTEND.md`: hoàn thiện bảng "cũ → mới".
- `docs/v4/PROGRESS.md`: đánh dấu đủ GAP A1…I4, liệt kê "Quyết định của agent", "Cần người duyệt", việc tồn.
- Commit `refactor(v4): phase 9 - tests and docs`. Không merge vào `main` — để người dùng review.
