---
name: v4-phase-5-auction-bid
description: Phase 5 đồng bộ v4 Omnibid — tạo/sửa/hủy/tìm kiếm phiên, đặt giá POST /auctions/{id}/bids không trạng thái Bid, chốt phiên theo BR-09, AuctionScheduler, sự kiện WebSocket AuctionEvent.
---
# Phase 5 — Phiên đấu giá và đặt giá

> Tài liệu tham chiếu (references) nằm ở `.agents/skills/omnibid-v4-sync/references/`; script kiểm tra ở `.agents/skills/omnibid-v4-sync/scripts/verify-v4.ps1`. Tiến độ ghi ở `docs/v4/PROGRESS.md`.

Đọc trước: `BUSINESS_RULES_v4.md` BR-01…BR-09, BR-15, BR-21, BR-22, §5 (mục 1–6, 10–11), §6, §7; `API_SPEC_v4.md` §2.3, §3, §6 (đặt giá), §7. Mục GAP: E1–E12.

## 1. Đặt giá (`BidServiceImpl`, `BidController`)
- Route `POST /api/v1/auctions/{id}/bids`, body `BidRequest(amount)` (`@NotNull @Positive @Digits(15,0)`), `hasRole('USER')`, 201.
- Luồng trong một transaction: khóa auction → nếu product của phiên HIDDEN hoặc phiên không tồn tại → 404 → `auction.placeBid(currentUser.id(), amount, Instant.now(clock))` ném `AUCTION_NOT_ACTIVE` / `SELF_BID_NOT_ALLOWED`(409) / `ALREADY_LEADING` / `BID_TOO_LOW{minNextBid}` → lưu Bid → flush (bắt `DataIntegrityViolationException` → `CONFLICT`) → đăng ký sự kiện sau COMMIT: OUTBID cho leader cũ, WebSocket `BID_PLACED`.
- Trạng thái BANNED của người đặt: dựa vào JWT Filter (đã chặn 403). **Không** truy vấn lại users, không khóa users.
- Response `PlaceBidResponse(bidId, auctionId, amount, currentPrice, bidCount, minNextBid)`.
- `GET /auctions/{id}/bids`: phân trang (mặc định sort `createdAt,desc`; whitelist `createdAt`, `amount`), phần tử `BidHistoryItem(id, amount, bidderName (ẩn danh), createdAt)`.
- `GET /bids/mine`: lọc `auctionId`, phần tử gồm auctionId, tiêu đề sản phẩm, amount, createdAt, trạng thái phiên, `leading` (= highestBidderId == tôi). Xóa `/bids/mine/summary`.

## 2. Phiên (`AuctionServiceImpl`, `AuctionController`, `AuctionSpecification`, `AuctionMapper`)
- Tạo (BR-06): sản phẩm của người bán, AVAILABLE, 1–5 ảnh, chưa có phiên mở; startPrice, bidIncrement > 0; reservePrice null hoặc ≥ startPrice; startTime > now; `minDuration ≤ end − start ≤ maxDuration`. Khóa product rồi kiểm tra → `PRODUCT_NOT_AVAILABLE`; lưu auction (currentPrice = startPrice, SCHEDULED), `product.markInAuction()`. Bắt UNIQUE `uq_auctions_active_product` → `CONFLICT`. 201.
- Sửa (BR-07): chỉ chủ phiên, khóa auction, còn SCHEDULED (không thì `AUCTION_NOT_EDITABLE`), kiểm tra lại như tạo; sau COMMIT phát `AUCTION_UPDATED`.
- Hủy bởi người bán (BR-08): khóa auction (rồi product), SCHEDULED hoặc ACTIVE với bidCount = 0, ngược lại `CANNOT_CANCEL`; trạng thái khác → `INVALID_STATUS_TRANSITION`; `reason` bắt buộc; `auction.cancel(reason)`, `product.release()`; sau COMMIT: AUCTION_CANCELLED cho người bán (+ leader nếu có), WebSocket `AUCTION_CANCELLED`.
- Tìm kiếm `GET /auctions`: keyword (tiêu đề sản phẩm), categoryId, minPrice/maxPrice theo currentPrice, status, endBefore (Instant); loại phiên của sản phẩm HIDDEN; sort whitelist 4 giá trị (mặc định `endTime,asc`). Phần tử: id, productId, title, ảnh chính, currentPrice, minNextBid, bidCount, status, startTime, endTime, reserveStatus.
- Chi tiết `GET /auctions/{id}`: thêm mô tả, danh sách ảnh, bidIncrement, startPrice, highestBidderName (ẩn danh), sellerName, reserveStatus; `reservePrice` chỉ khi người gọi là chủ phiên. HIDDEN → 404 (trừ ADMIN qua API admin).
- `GET /auctions/mine`: status, keyword.
- Xóa logic phụ thuộc `version`, watchlist count, v.v.

## 3. Mở/chốt phiên (`AuctionCloser`, `AuctionLifecycleSupport`, `AuctionScheduler`)
- Scheduler: lấy id cần mở/chốt bằng truy vấn chỉ mục, xử lý từng id bằng `TransactionTemplate` riêng, bắt lỗi từng phiên.
- Mở: khóa, còn SCHEDULED và `startTime ≤ now` → `activate`; sau COMMIT `AUCTION_STARTED`.
- Chốt theo BUSINESS_RULES §6 (BR-09): đọc trạng thái user dẫn đầu bằng truy vấn thường (không khóa). ENDED → tạo Order qua `OrderService.createFromAuction(auction, now)` (paymentDeadline = closedAt + `app.order.payment-deadline-hours`), product `reserve()`; UNSOLD → product `release()`. Thứ tự khóa: auction → (order mới tạo, không cần khóa) → product.
- Sau COMMIT: AUCTION_RESULT (người thắng kèm hạn thanh toán + người bán, hoặc chỉ người bán khi UNSOLD), WebSocket `AUCTION_ENDED`.

## 4. WebSocket (`WebSocketPublisherImpl`, `dto/websocket`)
- Thay `AuctionWebSocketMessage` bằng `AuctionEvent(type, auctionId, status, currentPrice, minNextBid, bidCount, highestBidderName, reserveStatus, startTime, endTime, bid, createdAt)`; `bid` chỉ ở BID_PLACED (`BidHistoryItem`).
- Chỉ phát sau COMMIT (giữ cơ chế `afterCommit` hiện có hoặc chuyển sang sự kiện + `@TransactionalEventListener`).

## Cổng
`compile` sạch. Nếu kịp: sửa `BidServiceTest`, `AuctionServiceTest`, `AuctionCloserTest`. Commit `refactor(v4): phase 5 - auctions and bids`.
