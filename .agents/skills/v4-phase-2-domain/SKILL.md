---
name: v4-phase-2-domain
description: Phase 2 đồng bộ v4 Omnibid — entity, enum, repository theo 10 bảng v4 (Instant, DECIMAL(15,0), token_version, Bid không trạng thái, report_order), gỡ Watchlist/Shipment/AuditLog, thêm phương thức chuyển trạng thái.
---
# Phase 2 — Mô hình miền

> Tài liệu tham chiếu (references) nằm ở `.agents/skills/omnibid-v4-sync/references/`; script kiểm tra ở `.agents/skills/omnibid-v4-sync/scripts/verify-v4.ps1`. Tiến độ ghi ở `docs/v4/PROGRESS.md`.

Đọc trước: `DB_SPEC_v4.md` (toàn bộ), `references/sql/V1__init_schema.sql`, `BUSINESS_RULES_v4.md` §3–§4, `DELETE_LIST.md` §1. Mục GAP: A2, A3, E1, E6, F7, G1, D5.

## 1. Gỡ tính năng (DELETE_LIST §1)
Xóa toàn bộ lớp Watchlist, Shipment/ShipmentEvent, AuditLog/AuditService và mọi tham chiếu (AdminServiceImpl, OrderServiceImpl, PaymentServiceImpl, ReportServiceImpl, CategoryServiceImpl, AuctionMapper, DTO `OrderDetailResponse`, `PaymentResponse`). Nơi đang gọi `auditService.log(...)` đổi thành `log.info(...)` SLF4J kèm adminId, hành động, đối tượng, reason.

## 2. Enum (đúng giá trị DB)
- `ProductCondition`: NEW, LIKE_NEW, GOOD, FAIR, POOR (giữ `@JsonCreator` không phân biệt hoa thường).
- `ProductStatus`: AVAILABLE, IN_AUCTION, RESERVED, SOLD, HIDDEN.
- `PaymentMethod`: MOCK_CARD. `PaymentStatus`: SUCCESS, FAILED.
- `NotificationType`: OUTBID, AUCTION_RESULT, AUCTION_CANCELLED, ORDER_UPDATE, REPORT_RESOLVED.
- `ReportStatus`: PENDING, RESOLVED, REJECTED. Thêm `ReportTargetType` (PRODUCT, USER, ORDER) cho bộ lọc, `ReportDecision` (RESOLVED, REJECTED) cho body resolve.
- Xóa `BidStatus`, `ReportAction`, `ReportType`, `ShipmentStatus`, `SimulatedOutcome` (gateway trả `PaymentStatus` hoặc record kết quả).
- Thêm `ReserveStatus` (MET, NOT_MET, NO_RESERVE), `AuctionEventType` (BID_PLACED, AUCTION_UPDATED, AUCTION_STARTED, AUCTION_ENDED, AUCTION_CANCELLED).

## 3. Entity (khớp từng cột với V1)
- Mọi mốc thời gian `Instant`; tiền `precision = 15, scale = 0`. Dùng `@CreationTimestamp`/`@UpdateTimestamp` hoặc để DB mặc định (`insertable=false, updatable=false`) — chọn một cách, nhất quán.
- `User`: + `avatarPublicId`, `tokenVersion` (int, mặc định 0). Phương thức `ban()`, `unban()`, `increaseTokenVersion()`, `isBanned()`.
- `Category`: `icon` (length 50) thay `iconUrl`.
- `Product`: cột `item_condition` (bỏ backtick `condition`); phương thức `markInAuction()`, `reserve()`, `release()`, `markSold()`, `hide()`, `isEditable()` theo BUSINESS_RULES §4 (HIDDEN bất biến).
- `ProductImage`: `publicId` nullable=false.
- `Auction`: bỏ `version`; + `updatedAt`; không ghi `active_product_id`. Phương thức `activate(now)`, `placeBid(bidderId, amount, now)` (trả Bid, kiểm tra BR-01/02 ngoại trừ trạng thái tài khoản), `minNextBid()`, `reserveStatus()`, `closeAsEnded(now)`, `closeAsUnsold(now)`, `cancel(reason)`, `isOpen()`.
- `Bid`: chỉ id, auctionId, bidderId, amount, createdAt.
- `Order`: bỏ `deliveredAt`, reminder_*; + `trackingCode`, `shippedAt`. Phương thức `markPaid(now)`, `markShipped(trackingCode, now, confirmWindow)`, `confirmReceived(now)`, `autoComplete(now)`, `expire(now)`, `cancel(reason, now)`, `isPaymentOverdue(now)`.
- `Payment`: bỏ `transactionRef`; không ghi `success_order_id`.
- `Notification`: type theo enum mới.
- `Report`: bỏ `reportType`, `action`; `orderId` → `reportedOrderId` (cột `reported_order_id`); phương thức `resolve(note, adminId, now)`, `reject(note, adminId, now)`.

## 4. Repository
- Bỏ: `UserRepository.findByIdForUpdate`, `checkCurrentStatus` (LOCK IN SHARE MODE); `BidRepository` các truy vấn theo status; `PaymentRepository.findByTransactionRef*`, `existsByOrderIdAndStatus(PENDING)`, `findByStatusAndCreatedAt…`.
- Thêm/giữ khóa (`PESSIMISTIC_WRITE` + lock timeout 5000): `AuctionRepository.findByIdForUpdate`, `findAllByIdInForUpdateOrderByIdAsc(List<Long>)`; `OrderRepository` tương tự; `ProductRepository.findByIdForUpdate`, `findAllByIdInForUpdateOrderByIdAsc`.
- Truy vấn quét trả id: `findIdsByStatusAndStartTimeLessThanEqual`, `findIdsByStatusAndEndTimeLessThanEqual`, `findIdsByStatusAndPaymentDeadlineLessThan`, `findIdsByStatusAndConfirmDeadlineLessThan`.
- `BidRepository`: `existsByAuctionId`, `findByAuctionId(Pageable)`, `findByBidderId(Long, Pageable)`, `findByBidderIdAndAuctionId(...)`, `countByAuctionId`.
- `PaymentRepository`: `findByOrderIdAndIdempotencyKey`, `findMaxAttemptNo(orderId)`, `findByOrderId(Long, Pageable)`, `existsByOrderIdAndStatus(SUCCESS)`.

## 5. Biên dịch lại phần còn lại
Sửa cơ học các Service/DTO/Mapper cho compile với mô hình mới (đổi kiểu thời gian, bỏ trường đã xóa). Logic nghiệp vụ sai so với v4 (đặt giá, chốt phiên, thanh toán, ban…) được viết lại ở phase 5–8 — ở phase này chỉ cần compile, có thể đánh dấu `// TODO v4-phase-N` tại chỗ cần viết lại.

## Cổng
`.\mvnw.cmd -q compile` sạch; `verify-v4.ps1` mục "10 entity tables" đạt. Cập nhật PROGRESS.md, commit `refactor(v4): phase 2 - domain model`.
