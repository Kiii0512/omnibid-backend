---
name: v4-phase-6-order-payment
description: Phase 6 đồng bộ v4 Omnibid — thanh toán mô phỏng đồng bộ có Idempotency-Key, gửi hàng POST /orders/{id}/ship, xác nhận nhận hàng, OrderScheduler hết hạn/tự hoàn tất, quyền xem đơn và lịch sử thanh toán.
---
# Phase 6 — Đơn hàng, thanh toán, giao hàng

> Tài liệu tham chiếu (references) nằm ở `.agents/skills/omnibid-v4-sync/references/`; script kiểm tra ở `.agents/skills/omnibid-v4-sync/scripts/verify-v4.ps1`. Tiến độ ghi ở `docs/v4/PROGRESS.md`.

Đọc trước: `BUSINESS_RULES_v4.md` BR-10…BR-16, §5 mục 5, §7; `API_SPEC_v4.md` §2.4, §6 (thanh toán, gửi hàng, xác nhận), §8. Mục GAP: F1–F9.

## 1. Thanh toán (`PaymentServiceImpl`, `PaymentController`, `gateway/*`)
- Viết lại `pay(currentUser, orderId, idempotencyKey, request)` thành **một** transaction:
  1. Header `Idempotency-Key` bắt buộc, dạng UUID (sai/thiếu → 400 VALIDATION_ERROR).
  2. Khóa order; không phải người mua → 403; tìm `(orderId, idempotencyKey)` đã có → trả lại đúng kết quả cũ (200).
  3. Kiểm tra: CANCELLED → `ORDER_CANCELLED`; EXPIRED hoặc `now ≥ paymentDeadline` → `ORDER_EXPIRED`; khác PENDING_PAYMENT → `INVALID_STATUS_TRANSITION`; thiếu shippingAddress/phoneNumber → `VALIDATION_ERROR`.
  4. `attemptNo = max + 1`; gọi `MockPaymentGateway.charge(amount, simulateResult)` — đồng bộ; `simulateResult` chỉ dùng khi `app.payment.simulate-enabled=true`, ngược lại luôn SUCCESS. FAILED → `failureReason = "MOCK_DECLINED"`.
  5. Lưu Payment; SUCCESS → `order.markPaid(now)` (sản phẩm giữ RESERVED/HIDDEN). Bắt UNIQUE (idempotency hoặc one_success) → đọc lại theo khóa và trả kết quả cũ, nếu không có → `CONFLICT`.
  6. Sau COMMIT (SUCCESS): ORDER_UPDATE cho người mua và người bán.
- Response `PaymentResultResponse(paymentId, attemptNo, status, orderStatus, failureReason?)`, luôn 200.
- `GET /orders/{id}/payments`: chỉ người mua, phân trang (mặc định `attemptNo,desc`).
- Xóa: `finalizePayment`, `timeoutStalePayments`, `transactionRef`, rate-limit, constructor thừa, `PaymentMethod` khác MOCK_CARD, delay mô phỏng. `DefaultMockPaymentGateway` giữ lớp nhưng đơn giản hóa.

## 2. Đơn (`OrderServiceImpl`, `OrderController`, `OrderLifecycleSupport`, `security/OrderAccessPolicy`)
- `GET /orders`: `role` = BUYER | SELLER (không truyền → cả hai), `status`; phân trang.
- `GET /orders/{id}`: chỉ người mua hoặc người bán (ADMIN → 403; Admin dùng API quản trị). Bỏ `requireSellerOrAdmin`.
- `PATCH /orders/{id}/shipping-info`: chỉ người mua, chỉ khi PENDING_PAYMENT (khác → `INVALID_STATUS_TRANSITION`).
- `POST /orders/{id}/ship` (mới): chỉ người bán, khóa order, PAID → `markShipped(trackingCode, now, confirmDeadlineDays)`; trackingCode tùy chọn (≤ 50 ký tự). Response `{id, orderStatus, shippedAt, confirmDeadline}`. Sau COMMIT: ORDER_UPDATE cho người mua.
- `POST /orders/{id}/confirm-receipt` (đổi từ PATCH): chỉ người mua, SHIPPING → `confirmReceived(now)`; khóa order → product; product `markSold()` (HIDDEN giữ HIDDEN). Response `{id, orderStatus, productStatus}`. Sau COMMIT: ORDER_UPDATE cho người bán.
- `createFromAuction(auction, closedAt)`: tạo PENDING_PAYMENT, `paymentDeadline = closedAt + paymentDeadlineHours` (BR-10).
- Xóa `/orders/summary`, `sendPaymentReminders`, mọi tham chiếu Shipment.

## 3. OrderScheduler (UC10)
- Mỗi chu kỳ: id đơn PENDING_PAYMENT quá `payment_deadline` → từng đơn: khóa order → kiểm tra lại (chưa có SUCCESS, còn PENDING_PAYMENT) → `expire(now)` → khóa product → `release()` (trừ HIDDEN) → sau COMMIT ORDER_UPDATE hai bên.
- Id đơn SHIPPING quá `confirm_deadline` → `autoComplete(now)` → product `markSold()` (trừ HIDDEN) → ORDER_UPDATE hai bên.
- Báo cáo vi phạm không ảnh hưởng (BR-19).

## Cổng
`compile` sạch. Nếu kịp: sửa `PaymentServiceTest`, `OrderServiceTest`, `OrderSchedulerTest`, `DefaultMockPaymentGatewayTest`. Commit `refactor(v4): phase 6 - orders and payments`.
