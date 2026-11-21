---
name: v4-phase-8-admin
description: Phase 8 đồng bộ v4 Omnibid — API quản trị POST ban/unban/hide/cancel, danh sách và chi tiết sản phẩm/phiên/đơn, hệ quả BR-17/BR-18 với thứ tự khóa auctions→orders→products, thống kê tích lũy, danh mục admin.
---
# Phase 8 — Quản trị

> Tài liệu tham chiếu (references) nằm ở `.agents/skills/omnibid-v4-sync/references/`; script kiểm tra ở `.agents/skills/omnibid-v4-sync/scripts/verify-v4.ps1`. Tiến độ ghi ở `docs/v4/PROGRESS.md`.

Đọc trước: `BUSINESS_RULES_v4.md` BR-08, BR-14, BR-16…BR-20, §5 (mục 2, 8, 9), §7; `API_SPEC_v4.md` §2.6, §2.7. Mục GAP: H1–H7.

## 1. Controller
Tách `AdminController` (230 dòng) thành các controller nhỏ dưới `controller/admin/` nếu tiện: `AdminUserController`, `AdminProductController`, `AdminAuctionController`, `AdminOrderController`, `AdminCategoryController`, `AdminStatisticsController` (báo cáo đã làm ở phase 7). Tất cả `@PreAuthorize("hasRole('ADMIN')")`. Endpoint đúng §2.6–2.7, xóa `PATCH /admin/users/{id}`, `PATCH /admin/products/{id}`, `GET /admin/audit-logs`.

## 2. Khóa tài khoản `POST /admin/users/{id}/ban` (BR-17, §5.9)
1. User không tồn tại → 404; role ADMIN → `CANNOT_BAN_ADMIN`; đã BANNED → `INVALID_STATUS_TRANSITION`.
2. `userRepository.updateStatus(id, BANNED)` (một câu UPDATE, không khóa trước). `reason` (tùy chọn) → `log.info`.
3. Vòng 1: id các auction SCHEDULED/ACTIVE do user bán → khóa theo id tăng → kiểm tra lại còn mở → `cancel("Tài khoản người bán bị khóa")`; ghi nhận (auctionId, sellerId, leaderId) để thông báo.
4. Vòng 2: id các order PENDING_PAYMENT mà user là buyer hoặc seller → khóa theo id tăng → kiểm tra lại → `cancel(...)`; ghi nhận bên còn lại.
5. Vòng 3: id product liên quan (từ các phiên/đơn vừa hủy) → khóa theo id tăng → `release()` (trừ HIDDEN).
6. **Không** chạm vào bids (giữ nguyên, BR-03). Phiên của người khác mà user này đang dẫn đầu: không đổi gì (sẽ UNSOLD khi chốt — BR-09).
7. Sau COMMIT: AUCTION_CANCELLED cho seller (chính user bị khóa — vẫn ghi DB) và leader của từng phiên; ORDER_UPDATE cho **bên còn lại** của từng đơn; WebSocket `AUCTION_CANCELLED` cho từng phiên.
8. Response: `{ id, status: "BANNED", cancelledAuctions: n, cancelledOrders: m }`.
- `POST /admin/users/{id}/unban`: chỉ khi BANNED → ACTIVE; không khôi phục gì. (Không thay token_version — JWT cũ dùng lại được vì hết BANNED; ghi quyết định.)

## 3. Ẩn sản phẩm `POST /admin/products/{id}/hide` (BR-18, BR-16, §5.8)
Đọc không khóa id auction mở & order PENDING_PAYMENT của sản phẩm → khóa auctions (id tăng) → hủy → khóa orders (id tăng) → hủy → khóa product → `hide()` (đã HIDDEN → `INVALID_STATUS_TRANSITION`). Đơn PAID/SHIPPING giữ nguyên. Sau COMMIT: AUCTION_CANCELLED (seller + leader), ORDER_UPDATE (buyer + seller), WebSocket. Không có API bỏ ẩn.

## 4. Hủy phiên, hủy đơn
- `POST /admin/auctions/{id}/cancel`: `reason` bắt buộc; SCHEDULED/ACTIVE bất kể bidCount; khóa auction → product `release()`; thông báo như trên.
- `POST /admin/orders/{id}/cancel`: `reason` bắt buộc; chỉ PENDING_PAYMENT (PAID/SHIPPING → `INVALID_STATUS_TRANSITION`, EXPIRED/CANCELLED → `INVALID_STATUS_TRANSITION`); khóa order → product `release()`; ORDER_UPDATE hai bên.
- Xóa `completeOrder`.

## 5. Danh sách và chi tiết
- `GET /admin/users` (keyword theo tên/email, status, role), `GET /admin/products` (keyword, categoryId, status) + `GET /admin/products/{id}` (gồm cả HIDDEN, ảnh, seller), `GET /admin/auctions` (keyword, status, sellerId) + `GET /admin/auctions/{id}` (gồm reservePrice, highestBidder đầy đủ), `GET /admin/orders` (status, buyerId, sellerId, fromDate, toDate theo created_at) + `GET /admin/orders/{id}` (kèm lịch sử thanh toán).
- Sort whitelist mỗi endpoint (ít nhất `createdAt`, `id`); mặc định `createdAt,desc`.

## 6. Thống kê `GET /admin/statistics`
Không tham số. `{ totalUsers, auctionsByStatus{SCHEDULED,ACTIVE,ENDED,UNSOLD,CANCELLED}, ordersByStatus{…6 trạng thái}, completedOrderValue, pendingReports }` — dùng truy vấn `COUNT … GROUP BY`, trả đủ khóa kể cả 0.

## 7. Danh mục admin
`GET/POST/PUT/DELETE /admin/categories` dùng `CategoryService` (phase 4): name, slug (unique, kebab), icon.

## Hình dạng response
Mọi DTO trả về của phase này phải khớp `omnibid-v4-sync/references/RESPONSE_DTO_v4.md` (kể cả các trường dẫn xuất như `viewerIsSeller`, `viewerIsLeading`, `orderId`, `activeAuctionId`, `everAuctioned`, `productCount`, `bannedUsers`, `targets`). Mô tả trường trong skill này chỉ là tóm tắt.

## Cổng
`compile` sạch và `verify-v4.ps1` mục endpoint báo đúng 54. Commit `refactor(v4): phase 8 - admin`.
