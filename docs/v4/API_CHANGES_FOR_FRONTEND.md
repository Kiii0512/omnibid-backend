# Thay đổi hợp đồng API cho Frontend (backend v4)

Danh sách khởi tạo từ phân tích chênh lệch; agent bổ sung chi tiết (tên trường, ví dụ JSON) trong từng phase.

## Cấu trúc chung
- Lỗi: `{ success:false, error:{ code, message, details } }` thay cho `errorCode`/`message` cấp ngoài; bỏ `timestamp`.
- Thời gian trả dạng `2026-11-20T09:00:00+07:00`; tiền là số nguyên (không `.00`).
- Phân trang `{content,page,size,totalElements,totalPages}`, size mặc định 10, tối đa 100.

## Endpoint đổi
| Cũ | Mới |
|---|---|
| `POST /api/v1/bids` (body có auctionId) | `POST /api/v1/auctions/{id}/bids` body `{amount}` |
| `PATCH /orders/{id}/confirm-receipt` | `POST /orders/{id}/confirm-receipt` |
| `GET /orders/{id}/shipment`, `PATCH /shipments/{id}/status` | `POST /orders/{id}/ship` `{trackingCode?}` |
| `PATCH /notifications/{id}/read` | `POST /notifications/{id}/read` |
| `PATCH /admin/reports/{id}/resolve` (action…) | `POST /admin/reports/{id}/resolve` `{decision, note}` |
| `PATCH /admin/users/{id}` | `POST /admin/users/{id}/ban`, `POST /admin/users/{id}/unban` |
| `PATCH /admin/products/{id}` | `POST /admin/products/{id}/hide` |
| `POST /products` (JSON) | `POST /products` multipart: `product` (JSON) + `files` (1–5) |
| `PATCH /users/me/avatar` | chỉ `POST /users/me/avatar` (phần `file`), trả `{avatarUrl}` |
| STOMP `/ws-auction` (SockJS), `/topic/auction/{id}` | STOMP `/ws` (WebSocket thuần), `/topic/auctions/{id}`, payload `AuctionEvent` |

## Endpoint mới
`POST /auth/logout`, `GET /admin/products/{id}`, `GET /admin/auctions`, `GET /admin/auctions/{id}`, `GET /admin/orders/{id}`, `GET /admin/reports/{id}`.

## Endpoint bị bỏ
`GET /categories/{id}`, `GET /products`, `GET /products/mine/summary`, `PATCH /products/{id}/images/{imageId}/primary`, `GET /bids/mine/summary`,
`GET /orders/summary`, `GET /notifications/unread-count`, `PATCH /notifications/read-all`, `GET /admin/audit-logs`, toàn bộ `/watchlist/**`.

## Hành vi đổi
- Đăng ký (`POST /api/v1/auth/register`): không nhận `address`, bắt buộc mật khẩu ≥8 ký tự gồm chữ hoa + chữ thường + chữ số; trả 201 `UserProfile` (`{ id, fullName, email, phoneNumber, avatarUrl, address, role, status, createdAt }`) và **không cấp token** (Frontend chuyển tới trang đăng nhập). Email trùng → 400 `VALIDATION_ERROR` kèm `error.details.fields.email`.
- Đăng nhập (`POST /api/v1/auth/login`): trả 200 `{ accessToken, tokenType: "Bearer", expiresIn: 86400, expiresAt: "...+07:00", role, user: { id, fullName, email, role, status, avatarUrl } }`. Sai email/mật khẩu → 401 `UNAUTHORIZED`; tài khoản bị khóa → 403 `ACCOUNT_BANNED`.
- Đăng xuất (`POST /api/v1/auth/logout`) và đổi mật khẩu (`PATCH /api/v1/users/me/password`, bổ sung bắt buộc `confirmPassword`, trả `{ message }`): tăng `token_version`, vô hiệu toàn bộ JWT đã cấp của tài khoản → Frontend xóa token và về trang đăng nhập.
- Hồ sơ (`PATCH /api/v1/users/me`): chỉ nhận `{ fullName, phoneNumber, address }` (bỏ `avatarUrl`). Bỏ `PATCH /api/v1/users/me/avatar` (chỉ giữ `POST /api/v1/users/me/avatar`).
- Đăng nhập sai 5 lần/15 phút theo `(email, IP)` → 429 `TOO_MANY_REQUESTS` kèm header `Retry-After` và `error.details.retryAfterSeconds`.
- JWT của tài khoản `BANNED` luôn nhận 403 `ACCOUNT_BANNED` ở mọi endpoint (kể cả Public); JWT hỏng/hết hạn/`ver` sai bị bỏ qua ở endpoint Public và trả 401 `UNAUTHORIZED` ở endpoint cần đăng nhập.
- WebSocket: đổi endpoint sang `/ws` (WebSocket thuần, bỏ SockJS và `/ws-auction`), kênh `/topic/auctions/{id}`, chỉ đọc (mọi khung `SEND` và `SUBSCRIBE` ngoài `/topic/auctions/{id}` bị từ chối); CORS `allowCredentials=false`.
- **Phiên đấu giá**:
  - `GET /api/v1/auctions`: query `keyword`, `categoryId`, `minPrice`, `maxPrice`, `status`, `endBefore`, `page`, `size` (mặc định 10), `sort` (chỉ nhận `endTime,asc` mặc định, `createdAt,desc`, `currentPrice,asc`, `currentPrice,desc`); loại phiên của sản phẩm `HIDDEN`; trả `Page<AuctionSummary>` (`id, productId, title, primaryImageUrl, categoryId, categoryName, status, startPrice, currentPrice, minNextBid, bidCount, reserveStatus, startTime, endTime, createdAt`).
  - `GET /api/v1/auctions/{id}`: trả `AuctionDetail` (mở rộng `AuctionSummary` thêm `description, condition, images, bidIncrement, seller: { id, fullName }, highestBidderName, viewerIsSeller, viewerIsLeading, reservePrice?, winnerName, finalPrice, closedAt, cancelReason, orderId?`); `reservePrice` chỉ xuất hiện khi `viewerIsSeller=true`, `orderId` chỉ xuất hiện cho người bán/người thắng khi `ENDED`. Phiên của sản phẩm `HIDDEN` → 404 `NOT_FOUND` (trừ ADMIN).
  - `POST /api/v1/auctions` (201): `startTime` bắt buộc lớn hơn thời điểm hiện tại, thời lượng theo cấu hình (`min-duration-minutes`..`max-duration-days`), luôn tạo ở trạng thái `SCHEDULED`, trả `AuctionDetail`.
  - `PATCH /api/v1/auctions/{id}`: chỉ sửa khi `SCHEDULED` (BR-07; `ACTIVE` dù 0 bid → 409 `AUCTION_NOT_EDITABLE`), phát sự kiện WebSocket `AUCTION_UPDATED`, trả `AuctionDetail`.
  - `POST /api/v1/auctions/{id}/cancel`: bắt buộc body `{ reason }` (`@NotBlank`); hủy `ACTIVE` đã có bid → 409 `CANNOT_CANCEL`, trạng thái đã kết thúc/hủy → 409 `INVALID_STATUS_TRANSITION`; trả `AuctionDetail`.
  - `GET /api/v1/auctions/mine`: hỗ trợ `status`, `keyword`, `page`, `size`, `sort`; trả `Page<MyAuctionItem>` (`AuctionSummary` + `reservePrice, orderId`).
- **Đặt giá & WebSocket**:
  - `POST /api/v1/auctions/{id}/bids` (thay `POST /api/v1/bids`): body `{ amount }`, trả 201 `PlaceBidResponse { bidId, auctionId, amount, currentPrice, bidCount, minNextBid }`; `SELF_BID_NOT_ALLOWED` trả 409; `BID_TOO_LOW` kèm `details.minNextBid`.
  - `GET /api/v1/auctions/{id}/bids`: phân trang `Page<BidHistoryItem>` (`{ id, amount, bidderName, createdAt }`, mặc định `createdAt,desc`, whitelist `createdAt`/`amount`, không còn `bidderId`/`status`).
  - `GET /api/v1/bids/mine`: lọc `auctionId`, trả `Page<MyBidItem>` (`{ id, auctionId, title, primaryImageUrl, amount, createdAt, auctionStatus, currentPrice, endTime, leading, orderId }`). Đã xóa `GET /api/v1/bids/mine/summary`.
  - WebSocket `/topic/auctions/{id}`: payload `AuctionEvent { type, auctionId, status, currentPrice, minNextBid, bidCount, highestBidderName, reserveStatus, startTime, endTime, bid?, createdAt }` với 5 loại `BID_PLACED`, `AUCTION_UPDATED`, `AUCTION_STARTED`, `AUCTION_ENDED`, `AUCTION_CANCELLED` (`bid` chỉ xuất hiện ở `BID_PLACED`).
- **Đơn hàng, thanh toán, giao hàng**:
  - `GET /api/v1/orders`: chỉ `USER`, lọc `role` (`BUYER` | `SELLER`, bỏ trống → cả hai) và `status`, phân trang (`size` mặc định 10, `sort` mặc định `createdAt,desc`, whitelist `createdAt`/`finalPrice`/`paymentDeadline`/`confirmDeadline`/`status`), trả `Page<OrderSummary>` (`id, auctionId, productId, title, primaryImageUrl, finalPrice, status, buyer: { id, fullName }, seller: { id, fullName }, paymentDeadline, confirmDeadline, createdAt`). Đã xóa `GET /api/v1/orders/summary`.
  - `GET /api/v1/orders/{id}`: chỉ người mua hoặc người bán (`ADMIN` → 403 `FORBIDDEN`, dùng `/admin/orders/{id}`); trả `OrderDetail` (mở rộng `OrderSummary` thêm `shippingAddress, phoneNumber, trackingCode, paidAt, shippedAt, completedAt, cancelledAt, cancelReason, productStatus, viewerRole`; bỏ `latestPayment`, `allowedActions`, `sellerId`/`buyerId` phẳng).
  - `PATCH /api/v1/orders/{id}/shipping-info`: chỉ người mua, chỉ khi đơn `PENDING_PAYMENT` (trạng thái khác → 409 `INVALID_STATUS_TRANSITION`), trả `OrderDetail`.
  - `POST /api/v1/orders/{id}/payments`: chỉ người mua, bắt buộc header `Idempotency-Key` chuẩn UUID (sai/thiếu → 400 `VALIDATION_ERROR`), body `{ method: "MOCK_CARD", simulateResult? }`; xử lý đồng bộ, luôn trả 200 `{ paymentId, attemptNo, status, orderStatus, failureReason? }` (gửi lặp cùng khóa trả kết quả cũ; không còn `PENDING`/polling; `simulateResult` chỉ có hiệu lực khi `app.payment.simulate-enabled=true`).
  - `GET /api/v1/orders/{id}/payments`: chỉ người mua (`ADMIN` → 403 `FORBIDDEN`), phân trang (`attemptNo,desc` mặc định, whitelist `attemptNo`/`createdAt`/`paidAt`/`amount`/`status`), trả `Page<PaymentHistoryItem>` (`{ id, attemptNo, amount, method, status, failureReason, createdAt, paidAt }`, đổi `paymentId` → `id`).
  - `POST /api/v1/orders/{id}/ship` (mới, thay cho luồng Shipment): chỉ người bán khi đơn `PAID`, body tùy chọn `{ trackingCode? }` (≤ 50 ký tự), trả 200 `{ id, orderStatus, shippedAt, confirmDeadline, trackingCode }`.
  - `POST /api/v1/orders/{id}/confirm-receipt` (đổi từ `PATCH`): chỉ người mua khi đơn `SHIPPING`, trả 200 `{ id, orderStatus, productStatus }` (`HIDDEN` giữ nguyên theo BR-16).
- Danh mục dùng `icon` (tên icon lucide-react) thay `iconUrl`.
- **Ảnh đại diện (`POST /api/v1/users/me/avatar`)**: multipart `file` (JPEG/PNG/WEBP kiểm tra bằng magic bytes, ≤ 5 MB), trả 200 `{ avatarUrl }` (không còn trả cả `UserProfile`); tự xóa ảnh cũ trên Cloudinary sau COMMIT.
- **Sản phẩm**:
  - `POST /api/v1/products`: đổi sang `multipart/form-data` với part `product` (JSON: `{ title, description, categoryId, condition }`) và part `files` (1–5 ảnh JPEG/PNG/WEBP ≤ 5 MB); trả 201 `Product`.
  - `GET /api/v1/products/mine`: hỗ trợ `keyword`, `status`, `page`, `size` (mặc định 10), `sort` (`id`, `createdAt`, `updatedAt`, `title`, `status`); trả `Page<ProductListItem>` gồm `id, title, categoryId, categoryName, condition, status, primaryImageUrl, imageCount, activeAuctionId, everAuctioned, createdAt`.
  - `GET /api/v1/products/{id}`: chỉ chủ sở hữu (người khác → 403 `FORBIDDEN`, không tồn tại → 404 `NOT_FOUND`); trả `Product` (mở rộng `ProductListItem` thêm `description, images, updatedAt`, không còn `sellerId`/`sellerName`). `ProductImage` chỉ còn `{ id, imageUrl, isPrimary }` (không lộ `publicId`, `createdAt`).
  - `PATCH /api/v1/products/{id}`: chỉ khi sản phẩm `AVAILABLE` (BR-23), khác → 409 `PRODUCT_NOT_EDITABLE`.
  - `DELETE /api/v1/products/{id}`: trả **200** `data: null` (trước đây 204); từ chối 409 `INVALID_STATUS_TRANSITION` nếu sản phẩm đã từng lên phiên.
  - `POST /api/v1/products/{id}/images`: multipart `files` (nhiều tệp, tổng sau khi thêm ≤ 5, BR-23), trả 201 `Product` (đã gồm ảnh mới).
  - `DELETE /api/v1/products/{id}/images/{imageId}`: trả **200** `Product` (không xóa được ảnh cuối → 400 `INVALID_IMAGE`; nếu xóa ảnh chính sẽ tự chuyển sang ảnh còn lại có `id` nhỏ nhất).
  - Đã xóa: `GET /api/v1/products`, `GET /api/v1/products/mine/summary`, `PATCH /api/v1/products/{id}/images/{imageId}/primary`.
- **Danh mục**:
  - `GET /api/v1/categories`: trả `Page<Category>` (`{ id, name, slug, icon }`), hỗ trợ `keyword`, `page`, `size` (mặc định 10), `sort` (`id`, `name`, `slug`, `createdAt`). Đã xóa `GET /api/v1/categories/{id}`.
  - `GET /api/v1/admin/categories`: trả `Page<AdminCategory>` (`{ id, name, slug, icon, productCount }`), hỗ trợ `keyword`, phân trang và sắp xếp.
  - `POST /api/v1/admin/categories` (201) và `PUT /api/v1/admin/categories/{id}` (200): nhận `{ name, slug?, icon? }` (`icon` ≤ 50 ký tự, `^[a-z0-9-]+$`), trả `AdminCategory`; slug trùng → 400 `VALIDATION_ERROR` kèm `details.fields.slug`.
  - `DELETE /api/v1/admin/categories/{id}`: trả 200 `data: null`; còn sản phẩm → 409 `CATEGORY_IN_USE`.
- **Thông báo (Phase 7)**:
  - Chỉ còn 5 loại `NotificationType`: `OUTBID`, `AUCTION_RESULT`, `AUCTION_CANCELLED`, `ORDER_UPDATE`, `REPORT_RESOLVED`. Tiền trong nội dung định dạng `520.000 ₫`, thời gian theo `+07:00`. Không gửi cho ADMIN.
  - `GET /api/v1/notifications`: chỉ `USER`, lọc `isRead`, `type`, phân trang (`size` mặc định 10, `sort` mặc định `createdAt,desc`, whitelist `createdAt`/`id`), trả `Page<Notification>` (`{ id, type, title, content, refType, refId, isRead, createdAt }`, không còn `unreadCount`).
  - `POST /api/v1/notifications/{id}/read` (đổi từ `PATCH`): chỉ người nhận (khác → 403 `FORBIDDEN`; đã đọc → vẫn 200 idempotent), trả `Notification`.
  - Đã xóa `GET /api/v1/notifications/unread-count` và `PATCH /api/v1/notifications/read-all`.
- **Báo cáo vi phạm (Phase 7)**:
  - `POST /api/v1/reports` (201, `USER`): body `{ reportedProductId?, reportedUserId?, reportedOrderId?, reason }` (bắt buộc ≥ 1 đối tượng, `reason` `@NotBlank` ≤ 1000 ký tự; báo cáo đơn chỉ cho bên tham gia → khác 403 `FORBIDDEN`; tự báo cáo bản thân/sản phẩm của mình → 400 `VALIDATION_ERROR`); trả tối giản `{ id, status: "PENDING", createdAt }`. Không còn `reportType`.
  - `GET /api/v1/admin/reports` (`ADMIN`): lọc `status` (không còn mặc định `PENDING`), `targetType` (`PRODUCT`/`USER`/`ORDER`), phân trang (`size` mặc định 10, `sort` mặc định `createdAt,desc`, whitelist `createdAt`/`id`/`status`/`resolvedAt`), trả `Page<AdminReportItem>` (`{ id, reporter: UserRef, targets: ReportTargetSummary, reason, status, createdAt }`).
  - `GET /api/v1/admin/reports/{id}` (mới, `ADMIN`): trả `AdminReportDetail` (mở rộng `AdminReportItem` thêm `adminNote, resolvedBy: UserRef | null, resolvedAt`). `targets` chỉ chứa các trường đối tượng có mặt (`product?`, `user?`, `order?`).
  - `POST /api/v1/admin/reports/{id}/resolve` (đổi từ `PATCH`, `ADMIN`): body `{ decision: "RESOLVED" | "REJECTED", note }` (`note` `@NotBlank`), chỉ đổi trạng thái báo cáo và gửi `REPORT_RESOLVED` sau COMMIT (không khóa user/ẩn sản phẩm/hủy phiên/đổi đơn), trả `AdminReportDetail`.
- **Quản trị (Phase 8)**:
  - `GET /api/v1/admin/users`: lọc `keyword` (tên/email, đổi từ `q`), `status`, `role`, phân trang (`size` mặc định 10, `sort` mặc định `createdAt,desc`, whitelist `createdAt`/`id`/`fullName`/`email`), trả `Page<AdminUser>` (`id, fullName, email, role, status, avatarUrl, phoneNumber, createdAt`).
  - `POST /api/v1/admin/users/{id}/ban` (thay `PATCH /admin/users/{id}`): body tùy chọn `{ reason? }`, không khóa `ADMIN` (409 `CANNOT_BAN_ADMIN`), đã `BANNED` → 409 `INVALID_STATUS_TRANSITION`, giữ nguyên lượt đặt giá (BR-03), trả `{ id, status: "BANNED", cancelledAuctions, cancelledOrders }`.
  - `POST /api/v1/admin/users/{id}/unban` (thay `PATCH /admin/users/{id}`): body tùy chọn `{ reason? }`, chỉ khi `BANNED` → `ACTIVE`, không khôi phục phiên/đơn đã hủy, trả `{ id, status: "ACTIVE" }`.
  - `GET /api/v1/admin/products`: lọc `keyword`, `categoryId`, `status`, phân trang (`createdAt,desc`, whitelist `createdAt`/`id`/`title`), trả `Page<AdminProductItem>` (`ProductListItem` + `seller: UserRef`).
  - `GET /api/v1/admin/products/{id}` (mới): trả `AdminProductDetail` (`Product` + `seller: UserSummary`, bao gồm cả sản phẩm `HIDDEN`).
  - `POST /api/v1/admin/products/{id}/hide` (thay `PATCH /admin/products/{id}`): body tùy chọn `{ reason? }`, `HIDDEN` là trạng thái cuối không có API bỏ ẩn (đã `HIDDEN`/`SOLD` → 409 `INVALID_STATUS_TRANSITION`), trả `{ id, status: "HIDDEN", cancelledAuctions, cancelledOrders }`.
  - `GET /api/v1/admin/auctions` (mới): lọc `keyword`, `status`, `sellerId`, phân trang (`createdAt,desc`, whitelist `createdAt`/`id`/`startTime`/`endTime`/`currentPrice`), trả `Page<AdminAuctionItem>` (`AuctionSummary` + `seller: UserRef, reservePrice`).
  - `GET /api/v1/admin/auctions/{id}` (mới) và `POST /api/v1/admin/auctions/{id}/cancel` (body `{ reason }` bắt buộc): trả `AdminAuctionDetail` (gồm `reservePrice`, `seller`/`highestBidder`/`winner` dạng `UserSummary`, `productStatus`, `orderId`).
  - `GET /api/v1/admin/orders`: lọc `status`, `buyerId`, `sellerId`, `fromDate`, `toDate` (đổi từ `from`/`to`, lọc theo `created_at`), phân trang (`createdAt,desc`, whitelist `createdAt`/`id`/`finalPrice`/`paymentDeadline`), trả `Page<OrderSummary>`.
  - `GET /api/v1/admin/orders/{id}` (mới) và `POST /api/v1/admin/orders/{id}/cancel` (body `{ reason }` bắt buộc, chỉ cho `PENDING_PAYMENT`, các trạng thái khác → 409 `INVALID_STATUS_TRANSITION`): trả `AdminOrderDetail` (gồm `buyer`/`seller` dạng `UserSummary`, `productStatus`, `payments: PaymentHistoryItem[]`).
  - `GET /api/v1/admin/statistics`: bỏ tham số `from`/`to`, trả số liệu tích lũy `{ totalUsers, bannedUsers, auctionsByStatus, ordersByStatus, completedOrderValue, pendingReports }` đủ mọi khóa trạng thái kể cả 0.
  - Đã xóa `GET /api/v1/admin/audit-logs`, `PATCH /api/v1/admin/users/{id}`, `PATCH /api/v1/admin/products/{id}`.
