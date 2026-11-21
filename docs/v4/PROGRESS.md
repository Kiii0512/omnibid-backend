# Tiến độ đồng bộ backend theo tài liệu v4

Nhánh: `refactor/v4-sync` · Kit: `.agents/skills/omnibid-v4-sync` · Agent cập nhật tệp này sau mỗi phase.

## Phase
- [x] 0. Chuẩn bị
- [x] 1. Nền tảng (Flyway, cấu hình, UTC/JSON, ApiResponse/ErrorCode)
- [x] 2. Mô hình miền (entity, enum, repository, gỡ tính năng thừa)
- [x] 3. Bảo mật, xác thực
- [x] 4. Ảnh, sản phẩm, danh mục
- [x] 5. Phiên, đặt giá, chốt phiên, WebSocket
- [x] 6. Đơn hàng, thanh toán, giao hàng
- [x] 7. Thông báo, báo cáo vi phạm
- [x] 8. Quản trị
- [x] 9. Kiểm thử, tài liệu, rà soát cuối

## Trạng thái sau Phase 9 (Hoàn tất toàn bộ kit đồng bộ v4)
- `.\mvnw.cmd test`: **124/124 tests PASS (0 failures, 0 errors, 0 skipped)**
- `verify-v4.ps1`: **ALL 7/7 v4 CHECKS PASSED**
  + [ OK ] no forbidden legacy terms in src/main
  + [ OK ] no YAML config
  + [ OK ] V1__init_schema.sql matches reference
  + [ OK ] V2__seed_data.sql matches reference
  + [ OK ] 10 entity tables match
  + [ OK ] REST endpoints = 54 (v4)
  + [ OK ] ErrorCode has all 24 codes
- `.\mvnw.cmd -q compile`: Đạt (0 lỗi biên dịch trên toàn bộ `src/main/java`)
- `.\mvnw.cmd -q test-compile`: Đạt (0 lỗi biên dịch trên toàn bộ `src/test/java`)
- Giới hạn file: 100% tệp trong `src/main/java` <= 400 dòng (0 tệp vi phạm).
- Tài liệu: Đã cập nhật `README.md` chuẩn hóa theo toàn bộ kiến trúc và hợp đồng v4.

## Mục GAP đã xử lý (Phase 9)
- **Chuẩn hóa toàn bộ 25+ tệp test sang hợp đồng v4**:
  - `BaseIT`, `CountingMockPaymentGateway`, `TestSupportConfig`, `Api`, `InvariantCheck`: Cập nhật cấu trúc Seed và DTO theo 10 bảng v4.
  - Sửa và chuẩn hóa các Test Case:
    + `CategoryServiceImpl`: Hỗ trợ ký tự tiếng Việt Đ/đ trong sinh slug tự động (`Đồng hồ Cổ` -> `dong-ho-co`).
    + `AdminServiceImpl` & `AdminControllersTest`: Đồng bộ constructor 5 tham số của `ProductImageResponse` và cập nhật JSON path assertions (`id`, `cancelledAuctions`, `cancelledOrders`, `completedOrderValue`).
    + `OrderControllerTest`: Đăng ký `PageableHandlerMethodArgumentResolver` cho Standalone MockMvc, chuẩn hóa `POST /orders/{id}/confirm-receipt`, sửa `error.code`.
    + `ProductControllerTest`: Loại bỏ trường `message` cũ trên `ApiResponse`, chuẩn hóa MockMultipartFile `product.json` với UTF-8.
    + `DefaultMockPaymentGatewayTest`: Khởi tạo cấu hình với `simulateEnabled = true` để kiểm thử gợi ý thanh toán thất bại `FAILED` với lý do `MOCK_DECLINED`.
    + `AuctionServiceTest` & `BidServiceTest`: Stubbing đúng phương thức `saveAndFlush` trên `AuctionRepository` và `BidRepository`, bổ sung kiểm tra tài khoản `BANNED` ném `ACCOUNT_BANNED` trong `BidServiceImpl`, chuẩn hóa xử lý tranh chấp Unique ở DB ném 409 `CONFLICT`.
    + `NotificationServiceTest`: Chuẩn hóa ngoại lệ `FORBIDDEN` khi người dùng khác đánh dấu đọc thông báo của người khác.
    + `OrderServiceTest`: Chuẩn hóa `ShipOrderRequest("TRK-12345")`.
    + `PaymentServiceTest`: Stubbing `paymentRepository.saveAndFlush(...)`.
    + `ProductServiceTest`: Cung cấp đúng magic bytes JPEG (≥ 12 bytes) cho `ImageValidator` và sửa stubbing `findById(9L)`.
    + `WebSocketPublisherTest`: Chuẩn hóa kênh broadcast `/topic/auctions/{id}` và định dạng payload v4.

## Baseline (phase 0)
- test-compile ban đầu: Đạt (0 lỗi biên dịch)
- Docker: Chưa khả dụng trong môi trường terminal hiện tại (cần lưu ý cho integration test ở phase 9)
- Số tệp test ban đầu: 46 tệp test (`src/test/java`)

## Trạng thái sau Phase 8
- `.\mvnw.cmd -q compile`: Đạt (0 lỗi biên dịch trên toàn bộ mã nguồn `src/main/java`)
- `verify-v4.ps1`: ALL v4 CHECKS PASSED (7/7 checks OK, REST endpoints = 54 v4, 10 bảng entity, 24 ErrorCode)
- Giới hạn file: 100% tệp trong `src/main/java` <= 400 dòng (0 tệp vi phạm).

## Mục GAP đã xử lý (Phase 8)
- **H1 & H2 (Tách nhỏ AdminController & chuẩn hóa Admin Service)**:
  - Tách monolithic `AdminController` thành 6 Controller mô-đun hóa trong gói `controller/admin/`:
    + `AdminUserController`: `GET /api/v1/admin/users`, `POST /api/v1/admin/users/{id}/ban`, `POST /api/v1/admin/users/{id}/unban`.
    + `AdminProductController`: `GET /api/v1/admin/products`, `GET /api/v1/admin/products/{id}`, `POST /api/v1/admin/products/{id}/hide`.
    + `AdminAuctionController`: `GET /api/v1/admin/auctions`, `GET /api/v1/admin/auctions/{id}`, `POST /api/v1/admin/auctions/{id}/cancel`.
    + `AdminOrderController`: `GET /api/v1/admin/orders`, `GET /api/v1/admin/orders/{id}`, `POST /api/v1/admin/orders/{id}/cancel`.
    + `AdminCategoryController`: `POST /api/v1/admin/categories`, `PUT /api/v1/admin/categories/{id}`, `DELETE /api/v1/admin/categories/{id}`.
    + `AdminStatisticsController`: `GET /api/v1/admin/statistics`.
  - Toàn bộ 54 endpoint REST của dự án khớp 100% với đặc tả v4.
- **H3 (Hệ quả BR-17 & BR-18, thứ tự khóa chuẩn)**:
  - `POST /api/v1/admin/users/{id}/ban`: Chuyển status `BANNED`. Hủy toàn bộ phiên SCHEDULED/ACTIVE của seller, hủy đơn PENDING_PAYMENT liên quan, giải phóng sản phẩm (`product.release()`). Thông báo `AUCTION_CANCELLED` cho seller + leader và `ORDER_UPDATE` cho đối tác sau COMMIT. Không cho khóa tài khoản ADMIN (`CANNOT_BAN_ADMIN`). Trả `AdminBanUserResponse`.
  - `POST /api/v1/admin/users/{id}/unban`: Chuyển status `ACTIVE`.
  - `POST /api/v1/admin/products/{id}/hide`: Khóa product (`PESSIMISTIC_WRITE`), hủy toàn bộ phiên SCHEDULED/ACTIVE của product, hủy đơn PENDING_PAYMENT liên quan (các đơn PAID/SHIPPING tiếp tục theo BR-16), chuyển status `HIDDEN`. Trả `ProductHideResponse`.
  - `POST /api/v1/admin/auctions/{id}/cancel`: Khóa auction, chuyển CANCELLED kèm cancelReason, giải phóng sản phẩm (`product.release()`), phát thông báo và WebSocket `AUCTION_CANCELLED` sau COMMIT.
  - `POST /api/v1/admin/orders/{id}/cancel`: Chỉ hủy đơn `PENDING_PAYMENT` (khác trả 409 `INVALID_STATUS_TRANSITION`), giải phóng sản phẩm (`product.release()`), thông báo `ORDER_UPDATE` cho buyer và seller sau COMMIT.
  - Tách riêng `AdminNotificationHelper` để xử lý thông báo sau commit cho các thao tác admin.
- **H4 (Thống kê và chi tiết đơn hàng)**:
  - `GET /api/v1/admin/statistics`: Không nhận tham số, tính lũy kế toàn hệ thống (`totalUsers`, `auctionsByStatus`, `ordersByStatus`, `totalRevenueCompleted`, `pendingReportsCount`).
  - `GET /api/v1/admin/orders/{id}`: Trả `AdminOrderDetailResponse` bao gồm thông tin chi tiết đơn hàng kèm lịch sử thanh toán đầy đủ (`PaymentHistoryResponse`).
  - `GET /api/v1/admin/auctions/{id}`: Trả `AuctionDetailResponse` đầy đủ thông tin kèm ảnh sản phẩm.
- **H5 (Danh mục)**:
  - Gỡ bỏ thuộc tính/getter thừa `iconUrl` trong `Category.java`, chuẩn hóa hoàn toàn trường `icon`.

## Trạng thái sau Phase 7
- `.\mvnw.cmd -q compile`: Đạt (0 lỗi biên dịch trên toàn bộ mã nguồn `src/main/java`)
- Giới hạn file: 100% tệp trong `src/main/java` <= 400 dòng (0 tệp vi phạm).

## Mục GAP đã xử lý (Phase 7)
- **G1 & G2 (Hệ thống thông báo chuẩn v4 & phát sau COMMIT)**:
  - Chuẩn hóa `NotificationType` đúng 5 giá trị: `OUTBID`, `AUCTION_RESULT`, `AUCTION_CANCELLED`, `ORDER_UPDATE`, `REPORT_RESOLVED`.
  - Mọi thông báo được kích hoạt strictly sau COMMIT (`AfterCommitExecutor.execute` / `@TransactionalEventListener(phase = AFTER_COMMIT)`). Phương thức `NotificationService.notify` chạy độc lập với `Propagation.REQUIRES_NEW`.
  - Không bao giờ gửi thông báo cho tài khoản ADMIN (kiểm tra `user.getRole() == Role.ADMIN` trước khi lưu/phát).
  - Viết lại `common/NotificationTemplates` chuẩn tiếng Việt cho 5 loại thông báo: tiền tệ định dạng `520.000 ₫`, thời gian hiển thị múi giờ `Asia/Ho_Chi_Minh` (`+07:00`).
  - Gắn sự kiện thông báo tương ứng tại:
    + Đặt giá vượt: gửi `OUTBID` cho người dẫn đầu trước (`BidServiceImpl`).
    + Chốt phiên: gửi `AUCTION_RESULT` cho người thắng (kèm hạn thanh toán) + người bán khi ENDED; gửi cho người bán khi UNSOLD (`AuctionCloser`).
    + Hủy phiên: gửi `AUCTION_CANCELLED` cho người bán và người đang dẫn đầu (`AuctionServiceImpl`).
    + Đơn hàng: gửi `ORDER_UPDATE` khi thanh toán thành công, gửi hàng, xác nhận nhận hàng, tự hoàn tất, hết hạn thanh toán (`PaymentServiceImpl`, `OrderServiceImpl`, `OrderLifecycleSupport`).
    + Báo cáo: gửi `REPORT_RESOLVED` cho người gửi khi Admin xử lý (`ReportServiceImpl`).
- **G3 (NotificationController)**:
  - `GET /api/v1/notifications`: Phân trang, lọc `isRead`, `type`, sort whitelist (`createdAt,desc` mặc định, `isRead`). Trả `PageResponse<NotificationResponse>`.
  - `POST /api/v1/notifications/{id}/read`: Kiểm tra quyền sở hữu thông báo (người khác nhận 403 `FORBIDDEN`), đã đọc vẫn trả 200 (idempotent).
  - Gỡ bỏ `GET /notifications/unread-count` và `PATCH /notifications/read-all`.
- **G4 & G5 (Báo cáo vi phạm v4)**:
  - `POST /api/v1/reports`: Ít nhất 1 đối tượng (`reportedProductId`, `reportedUserId`, `reportedOrderId`), `reason` 10-1000 ký tự. Không cho tự báo cáo bản thân hoặc sản phẩm của mình (400 `VALIDATION_ERROR`). Báo cáo đơn hàng chỉ dành cho các bên tham gia đơn (403 `FORBIDDEN`). Khởi tạo trạng thái `PENDING`. Trả 201 `ReportResponse`.
  - `GET /api/v1/admin/reports`: Admin xem danh sách báo cáo, lọc theo `status`, `targetType` (PRODUCT/USER/ORDER), phân trang, sort whitelist (`createdAt,desc` mặc định, `status`).
  - `GET /api/v1/admin/reports/{id}`: Admin xem chi tiết báo cáo kèm tóm tắt đối tượng bị báo cáo (user, product, order).
  - `POST /api/v1/admin/reports/{id}/resolve`: Khóa report (`findByIdForUpdate`), kiểm tra PENDING (khác trả 409 `INVALID_STATUS_TRANSITION`), `decision` (RESOLVED / REJECTED), `note` bắt buộc. Gửi thông báo `REPORT_RESOLVED` cho người gửi sau COMMIT. Không can thiệp đổi trạng thái user/product/auction/order theo BR-19.
  - Gỡ bỏ hoàn toàn `ReportAction`, `ReportType`, `INVALID_REPORT_ACTION`.

## Trạng thái sau Phase 6
- `.\mvnw.cmd -q compile`: Đạt (0 lỗi biên dịch trên toàn bộ mã nguồn `src/main/java`)
- Giới hạn file: 100% tệp trong `src/main/java` <= 400 dòng (0 tệp vi phạm).

## Mục GAP đã xử lý (Phase 6)
- **F1 (Thanh toán đồng bộ 1 transaction & Idempotency-Key)**:
  - `POST /api/v1/orders/{id}/payments`: Bắt buộc header `Idempotency-Key` chuẩn UUID (sai/thiếu -> 400 `VALIDATION_ERROR`).
  - Thực thi trong 1 transaction đã khóa order: kiểm tra quyền người mua (Admin và người khác trả 403 `FORBIDDEN`). Kiểm tra `(orderId, idempotencyKey)` đã tồn tại -> trả lại đúng kết quả cũ (200 OK).
  - Kiểm tra trạng thái đơn: CANCELLED -> `ORDER_CANCELLED`, EXPIRED / quá hạn thanh toán -> `ORDER_EXPIRED`, khác PENDING_PAYMENT -> `INVALID_STATUS_TRANSITION`, thiếu shippingAddress hoặc phoneNumber -> 400 `VALIDATION_ERROR`.
  - Gọi `MockPaymentGateway.charge(amount, simulateResult)` đồng bộ: `simulateResult` chỉ có hiệu lực khi `app.payment.simulate-enabled=true`, khi tắt luôn trả SUCCESS; thất bại trả `failureReason = "MOCK_DECLINED"`.
  - Lưu Payment với `attemptNo = max + 1`, `PaymentMethod.MOCK_CARD`. Khi SUCCESS gọi `order.markPaid(now)`. Trả 200 `PaymentResponse(paymentId, attemptNo, status, orderStatus, failureReason)`.
- **F2 (Giao hàng v4)**:
  - `POST /api/v1/orders/{id}/ship`: Chỉ người bán, khóa order, đơn phải ở trạng thái `PAID` (ngược lại `INVALID_STATUS_TRANSITION`). `trackingCode` tùy chọn (≤ 50 ký tự). Chuyển sang `SHIPPING`, gán `shippedAt = now`, `confirmDeadline = now + confirmDeadlineDays`. Trả 200 `OrderShipResponse(id, orderStatus, shippedAt, confirmDeadline)`.
- **F3 (Xác nhận nhận hàng v4)**:
  - Chuyển `POST /api/v1/orders/{id}/confirm-receipt`: Chỉ người mua, khóa order và product, đơn phải ở `SHIPPING` (ngược lại `INVALID_STATUS_TRANSITION`). Chuyển đơn sang `COMPLETED`, `completedAt = now`. Cập nhật sản phẩm `markSold()` (giữ `HIDDEN` nếu sản phẩm đã bị ẩn). Trả 200 `OrderConfirmReceiptResponse(id, orderStatus, productStatus)`.
- **F4 (Chính sách truy cập đơn & phân quyền v4)**:
  - `OrderAccessPolicy`: `GET /orders/{id}` và `GET /orders` chỉ cho phép người mua hoặc người bán tham gia đơn (Admin nhận 403 `FORBIDDEN` và phải dùng `/admin/orders`).
  - `GET /orders/{id}/payments`: Chỉ người mua (Admin nhận 403 `FORBIDDEN`), hỗ trợ phân trang với `PageResponse` và sort whitelist (`attemptNo,desc` mặc định).
  - `GET /orders`: Lọc theo `role` (BUYER/SELLER), `status`, phân trang, sort whitelist (`createdAt`, `finalPrice`, `status`).
- **F5 & F6 & F7**:
  - Gỡ bỏ `GET /orders/summary` và `sendPaymentReminders`.
  - Gỡ bỏ `OrderUserSummaryResponse`.
  - Chuẩn hóa `PaymentMethod` chỉ dùng `MOCK_CARD`.
- **F8 & F9 (OrderScheduler & vòng đời đơn theo BR-16, BR-19)**:
  - `OrderScheduler` quét mỗi 10s:
    + Đơn PENDING_PAYMENT quá hạn `paymentDeadline` -> `expireOne`: khóa order -> `order.expire(now)` -> khóa product -> `product.release()` (trừ HIDDEN).
    + Đơn SHIPPING quá hạn `confirmDeadline` -> `autoCompleteOne`: khóa order -> `order.autoComplete(now)` -> khóa product -> `product.markSold()` (trừ HIDDEN).
    + Báo cáo vi phạm không làm thay đổi trạng thái đơn và không tạm dừng tự hoàn tất (BR-19).

## Trạng thái sau Phase 5
- `.\mvnw.cmd -q compile`: Đạt (0 lỗi biên dịch trên toàn bộ mã nguồn `src/main/java`)
- Giới hạn file: 100% tệp trong `src/main/java` <= 400 dòng (0 tệp vi phạm).

## Mục GAP đã xử lý (Phase 5)
- **E1 & E2 (Đặt giá)**:
  - `POST /api/v1/auctions/{id}/bids` (USER): Transaction khóa auction, kiểm tra product không bị HIDDEN, gọi `auction.placeBid(...)` (kiểm tra `AUCTION_NOT_ACTIVE`, `SELF_BID_NOT_ALLOWED`(409), `ALREADY_LEADING`, `BID_TOO_LOW`). Lưu Bid, cập nhật auction (highestBidderId, currentPrice, bidCount), flush bắt `DataIntegrityViolationException` -> `CONFLICT`. Phát WebSocket `BID_PLACED` sau COMMIT. Trả 201 `PlaceBidResponse`.
  - `GET /api/v1/auctions/{id}/bids`: Công khai, phân trang, sort whitelist (`createdAt`, `amount`), trả `BidHistoryItem` (ẩn danh bidderName theo chuẩn `N***n`).
  - `GET /api/v1/bids/mine`: Phân trang, lọc `auctionId`, trả `MyBidResponse` kèm cờ `leading`.
- **E3…E6 (Quản lý phiên đấu giá)**:
  - `POST /api/v1/auctions`: Validate thời gian bắt đầu ở tương lai, thời gian kết thúc sau bắt đầu, độ dài phiên từ `minDuration` đến `maxDuration` theo `appProperties.auction`, `reservePrice >= startPrice`. Khóa product kiểm tra AVAILABLE, 1-5 ảnh, chưa có phiên mở -> `product.markInAuction()`. Tạo phiên trạng thái `SCHEDULED`.
  - `PATCH /api/v1/auctions/{id}`: Chỉ chủ phiên, chỉ khi SCHEDULED (ngược lại `AUCTION_NOT_EDITABLE`). Khóa auction, kiểm tra hợp lệ, phát `AUCTION_UPDATED` sau COMMIT.
  - `POST /api/v1/auctions/{id}/cancel`: Chỉ chủ phiên, khóa auction và product, SCHEDULED hoặc ACTIVE với 0 bid (ngược lại `CANNOT_CANCEL`). Trạng thái khác `INVALID_STATUS_TRANSITION`. Reason bắt buộc. Gọi `auction.cancel(reason)`, `product.release()`, phát `AUCTION_CANCELLED` sau COMMIT.
  - `GET /api/v1/auctions`: Lọc theo từ khóa `q`, `categoryId`, `minPrice`, `maxPrice`, `status`, `endBefore`. Bỏ qua các sản phẩm HIDDEN. Sort whitelist (`endTime,asc` mặc định, `createdAt,desc`, `currentPrice,asc`, `currentPrice,desc`). Trả `AuctionResponse`.
  - `GET /api/v1/auctions/{id}`: Trả `AuctionDetailResponse` đầy đủ thông tin, ẩn danh người dẫn đầu, ẩn `reservePrice` đối với người không phải chủ phiên. Sản phẩm HIDDEN trả 404 `NOT_FOUND`.
  - `GET /api/v1/auctions/mine`: Phân trang lọc theo `status` và `keyword`.
- **E7…E9 (Mở/chốt phiên tự động - BR-09)**:
  - Khắc phục triệt để lỗi phiên quá hạn bị kẹt `ACTIVE`: `AuctionScheduler` quét mỗi 10s lấy danh sách ID phiên cần mở (`startTime <= now`) và chốt (`endTime <= now`), gọi `AuctionCloser.startOne(id)` và `AuctionCloser.closeOne(id)` trong transaction độc lập.
  - `AuctionCloser.closeOne`: Khóa auction. Nếu có `highestBidderId` hợp lệ, người thắng ACTIVE và đạt `reservePrice` -> trạng thái `ENDED`, `product.reserve()`, tạo đơn hàng `Order` ở trạng thái `PENDING_PAYMENT` với hạn thanh toán `closedAt + paymentDeadlineHours`. Ngược lại -> trạng thái `UNSOLD`, `product.release()`. Phát `AUCTION_ENDED` sau COMMIT.
- **E10 (WebSocket Event v4)**:
  - Tạo `dto/websocket/AuctionEvent` chuẩn v4 thay thế message cũ: `{ type, auctionId, status, currentPrice, minNextBid, bidCount, highestBidderName, reserveStatus, startTime, endTime, bid, createdAt }`.
  - Phát sự kiện tới `/topic/auctions/{id}` strictly sau COMMIT (`@TransactionalEventListener` / `AfterCommitExecutor`).

## Trạng thái sau Phase 4
- `.\mvnw.cmd -q compile`: Đạt (0 lỗi biên dịch trên toàn bộ mã nguồn `src/main/java`)
- Unit test `ImageValidatorTest`: Đạt 100% (kiểm tra JPEG, PNG, WEBP, rỗng, sai magic bytes, quá 5MB).
- Giới hạn file: 100% tệp trong `src/main/java` <= 400 dòng (0 tệp vi phạm).

## Mục GAP đã xử lý (Phase 4)
- **D1 & D2**: Tạo `common/ImageValidator` kiểm tra định dạng theo magic bytes (JPEG FF D8 FF, PNG 89 50 4E 47, WEBP RIFF...WEBP), tối đa 5MB/tệp, không rỗng; lỗi trả 400 `INVALID_IMAGE`.
- **D3**: Tạo `ImageStorageService` và `CloudinaryImageStorageService` với Cloudinary Java SDK dùng chung cho ảnh sản phẩm và ảnh đại diện (`omnibid/products`, `omnibid/avatars`). Lỗi Cloudinary trả 502 `IMAGE_UPLOAD_FAILED`. Tạo `AfterCommitExecutor` để xóa tài nguyên Cloudinary sau khi commit transaction thành công.
- **D4**: Xóa bỏ hoàn toàn lưu trữ cục bộ: xóa `LocalStorageServiceImpl`, `StorageConfig`, `StorageService`, `StorageUploadResult`, `CloudinaryStorageServiceImpl`, handler `/uploads/**` trong `WebMvcConfig`.
- **D5 (Tạo sản phẩm & ảnh)**:
  - `POST /api/v1/products` multipart: `@RequestPart("product")` và `@RequestPart("files")` (1-5 ảnh). Quy trình 4 bước chuẩn v4: validate -> upload Cloudinary ngoài tx -> transaction lưu product + images (ảnh đầu `is_primary=true`, `public_id` NOT NULL) -> lỗi thì xóa asset Cloudinary (bù trừ).
  - `POST /api/v1/products/{id}/images`: multipart `files`, validate, upload ngoài tx, transaction khóa product kiểm tra BR-23 + tổng <= 5 ảnh; lỗi thì bù trừ.
  - `DELETE /api/v1/products/{id}/images/{imageId}`: khóa product, BR-23, không cho xóa ảnh cuối cùng (INVALID_IMAGE), tự động chuyển ảnh chính sang ảnh có id nhỏ nhất còn lại, xóa asset Cloudinary sau commit.
  - `DELETE /api/v1/products/{id}`: chỉ xóa khi chưa từng lên phiên (`auctionRepository.existsByProductId`), xóa asset Cloudinary sau commit.
  - `PATCH /api/v1/products/{id}`: khóa product, kiểm tra `isEditable()` (AVAILABLE) theo BR-23.
  - `GET /api/v1/products/{id}`: kiểm tra quyền sở hữu (chỉ chủ sản phẩm xem được, người khác 403 FORBIDDEN).
  - `GET /api/v1/products/mine`: phân trang, lọc theo keyword `q` và `status`.
  - Gỡ bỏ các endpoint không thuộc v4: `GET /products`, `GET /products/mine/summary`, `PATCH .../primary`.
- **D6 (Ảnh đại diện)**:
  - `POST /api/v1/users/me/avatar`: validate magic bytes -> upload ngoài tx -> transaction lưu `avatarUrl` và `avatarPublicId` -> xóa ảnh cũ sau commit. Trả 200 `{ "avatarUrl": "..." }`.
- **D7 (Danh mục)**:
  - Chuẩn hóa trường `icon` (≤ 50 ký tự, regex `^[a-z0-9-]+$`).
  - `GET /api/v1/categories`: công khai, nhận `q` (keyword) + phân trang `Pageable`, trả `PageResponse<CategoryResponse>`.
  - Kiểm tra slug trùng (400 `VALIDATION_ERROR` kèm `details.fields.slug`), không cho xóa danh mục còn sản phẩm (409 `CATEGORY_IN_USE`).
  - Gỡ bỏ `GET /api/v1/categories/{id}`.

## Trạng thái sau Phase 3
- `.\mvnw.cmd -q compile`: Đạt (0 lỗi biên dịch trên toàn bộ mã nguồn `src/main/java`)
- Unit test Auth & Security: `JwtTokenProviderTest`, `AuthServiceTest`, `LoginAttemptLimiterTest` pass.
- Giới hạn file: 100% tệp trong `src/main/java` <= 400 dòng (0 tệp vi phạm).

## Mục GAP đã xử lý (Phase 3)
- **C1 & C2**: Viết lại `JwtTokenProvider` phát sinh JWT với claim `sub`=userId, `role`, `ver`=tokenVersion (HS256, 24 giờ). `parseClaims` nạp claim, trả `JwtClaims(userId, role, tokenVersion, expiresAt)`.
- **C3**: `CustomUserDetails` và `UserDetailsServiceImpl` hỗ trợ nạp user theo ID `loadUserById(Long id)` cho filter; principal lưu `id, email, role, status, tokenVersion`.
- **C4**: `JwtAuthenticationFilter` sử dụng `SecurityPaths.PUBLIC_REQUEST_MATCHER` (chuẩn PathPatternRequestMatcher của Spring Security 7):
  - Request không token: đi tiếp (anonymous).
  - Request có token nhưng sai/hết hạn/ver lệch: nếu endpoint Public thì bỏ qua (anonymous), ngược lại trả ngay 401 UNAUTHORIZED JSON.
  - Request có token của tài khoản BANNED: trả ngay 403 ACCOUNT_BANNED JSON ở mọi endpoint (kể cả Public).
- **C5**: Tạo `LoginAttemptLimiter` đếm số lần đăng nhập sai theo `(email, remoteAddr)` dùng `Clock`, tối đa 5 lần trong 15 phút, chặn 15 phút trả 429 `TOO_MANY_REQUESTS`. Đăng nhập thành công reset đếm.
- **C6**: `AuthService.login` trả `LoginResponse` chuẩn v4 kèm `UserSummary`. Đăng nhập kiểm tra chặn trước khi tìm user.
- **C7**: `AuthService.register` nhận `RegisterRequest(fullName, email, password, phoneNumber)` (mật khẩu regex tối thiểu 8 ký tự, 1 hoa, 1 thường, 1 số); email trùng trả 400 `VALIDATION_ERROR` kèm `details.fields.email`. Đăng ký trả `UserProfileResponse` 201, không cấp token.
- **C8**: Thêm `POST /api/v1/auth/logout` (USER, ADMIN) gọi `user.increaseTokenVersion()`.
- **C9**: `UserController`/`UserServiceImpl`: `GET/PATCH /users/me`, `PATCH /users/me/password` (đổi mật khẩu gọi `increaseTokenVersion()`); xóa `PATCH /users/me/avatar`.
- **C10**: `SecurityConfig` cấu hình permit list chuẩn, khớp `/api/v1/auctions/mine` (authenticated) trước public auctions, bỏ permit `/uploads/**`, `/api/v1/products/**`, `/ws-auction/**`, `allowCredentials=false`.
- **C11**: `WebSocketConfig` chuyển sang endpoint `/ws` (bỏ SockJS), broker `/topic`, topic `/topic/auctions/{id}` trong `WebSocketPublisherImpl`, ChannelInterceptor: CONNECT xác thực JWT tùy chọn (kiểm tra signature, ver, BANNED), SUBSCRIBE chỉ cho phép `/topic/auctions/**`, mọi khung SEND bị từ chối với AccessDeniedException.

## Trạng thái sau Phase 2
- `.\mvnw.cmd -q compile`: Đạt (0 lỗi biên dịch trên toàn bộ mã nguồn `src/main/java`)
- `verify-v4.ps1`: Mục `10 entity tables match` đạt [OK].
- Giới hạn file: 100% tệp trong `src/main/java` <= 400 dòng (0 tệp vi phạm).
- Số test còn lỗi biên dịch: Các test tham chiếu Watchlist, Shipment, AuditLog (sẽ được viết lại/chuẩn hóa hoàn toàn ở Phase 9).

## Mục GAP đã xử lý (Phase 2)
- **A2 & E1**: Chuẩn hóa toàn bộ 10 Entity khớp 10 bảng V1 Flyway (`Instant` cho thời gian, `BigDecimal` scale 0 cho `DECIMAL(15,0)`).
- **A3**: Bổ sung `tokenVersion` (int) và `avatarPublicId` vào `User`; các domain helper methods `ban()`, `unban()`, `increaseTokenVersion()`, `isBanned()`.
- **E6 & G1**: Gỡ bỏ hoàn toàn 3 tính năng không có trong v4: Watchlist, Shipment/ShipmentEvent, AuditLog/AuditService (thay bằng SLF4J `log.info(...)`).
- **D5**: Gỡ bỏ trạng thái `Bid` (`BidStatus`), `Bid` trở thành bất biến và phi trạng thái.
- **F7**: Gỡ bỏ `ReportAction` và `ReportType`, đổi sang `ReportTargetType` (PRODUCT/USER/ORDER), `ReportDecision` (RESOLVED/REJECTED), `reportedOrderId`.
- **Enums v4**: Chuẩn hóa toàn bộ enums theo đặc tả: `ProductCondition`, `ProductStatus`, `PaymentMethod`, `PaymentStatus`, `NotificationType`, `ReportStatus`, `ReportTargetType`, `ReportDecision`, `ReserveStatus`, `AuctionEventType`.
- **Repositories**: Xóa các query khoá `users` (`findByIdForUpdate` trên User); thêm `PESSIMISTIC_WRITE` (timeout 5000ms) trên `AuctionRepository`, `OrderRepository`, `ProductRepository`.
- **Entity Domain Methods**: Thêm các phương thức chuyển trạng thái nghiệp vụ chuẩn trên Entity (`Auction.activate/placeBid/closeAsEnded/closeAsUnsold/cancel`, `Order.markPaid/markShipped/confirmReceived/autoComplete/expire/cancel`, `Product.markInAuction/reserve/release/markSold/hide`).

## Trạng thái sau Phase 1
- `.\mvnw.cmd -q compile`: Đạt (0 lỗi biên dịch)
- `.\mvnw.cmd -q test-compile`: Đạt (0 lỗi biên dịch)
- `.\mvnw.cmd test`: 135/215 tests pass (80 test assertions fail do assert `isInstanceOf(AppException.class)` - sẽ được chuẩn hóa sang `BusinessException` ở Phase 9)
- Script rà soát `verify-v4.ps1`:
  - `no forbidden legacy terms in src/main` đối với `AppException`, `UNPROCESSABLE_ENTITY`, `FOR SHARE` -> ĐẠT
  - `no YAML config` -> ĐẠT
  - `V1__init_schema.sql matches reference` -> ĐẠT
  - `V2__seed_data.sql matches reference` -> ĐẠT
  - `ErrorCode has all 24 codes` -> ĐẠT

## Mục GAP đã xử lý (Phase 1)
- **A1**: Bổ sung `spring-boot-starter-flyway` và `org.flywaydb:flyway-mysql` vào `pom.xml`; tạo `db/migration/V1__init_schema.sql` và `V2__seed_data.sql` (10 bảng); xóa `db/design_schema.sql` và `db/seed_demo.sql`; cấu hình container MySQL `--character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci`.
- **A2 (Hạ tầng)**: `TimeZone.setDefault(TimeZone.getTimeZone("UTC"))` tại `OmnibidBackendApplication.main`; bean `Clock.systemUTC()` và `DISPLAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh")` tại `DateTimeConfig`; đăng ký Jackson `SimpleModule` toàn cục serialize `Instant` -> `+07:00` và deserialize nhận mọi offset; `Converter<String, Instant>` trong `WebMvcConfig`.
- **A4**: Viết lại `AppProperties` dạng record với `Duration` + `@DurationUnit` (order, auction, scheduler, payment, security, cors) khớp Bảng 1.2.
- **A5**: Cập nhật `.gitignore` (loại trừ `application-local.properties`, theo dõi `application.properties`); tạo `application.properties`, `application-demo.properties`, `application-dev.properties`, `application-example.properties`, `src/test/resources/application.properties`.
- **A6**: Cập nhật `AuctionScheduler` và `OrderScheduler` sử dụng `@Scheduled(fixedDelayString = "${app.scheduler.interval-seconds:10}", timeUnit = TimeUnit.SECONDS)`.
- **B1**: Chuẩn hóa `ApiResponse<T>` chỉ chứa `{ "success": true, "data": ... }` hoặc `{ "success": false, "error": { "code", "message", "details" } }`. Bỏ `timestamp`, `errorCode`, `message` ngoài cùng.
- **B2**: Tạo enum `ErrorCode` với chính xác 24 mã chuẩn Bảng 4.22 kèm `HttpStatus` và thông điệp tiếng Việt. Tạo `BusinessException`. Thay thế toàn bộ `AppException` trong `src/main/java` và xóa `src/main/java/com/omnibid/common/AppException.java`.
- **B3**: Chuyển mã lỗi `SELF_BID_NOT_ALLOWED` sang HTTP 409 (CONFLICT).
- **B4**: Bổ sung các handler ngoại lệ trong `GlobalExceptionHandler`: `BusinessException`, validation (`VALIDATION_ERROR`, details.fields), 413 `PAYLOAD_TOO_LARGE`, 409 `CONFLICT` (deadlock, lock timeout, data integrity), 401 `UNAUTHORIZED`, 403 `FORBIDDEN`, 404 `NOT_FOUND`, 405 -> `NOT_FOUND`, 500 `INTERNAL_ERROR`.
- **B5**: Thêm `SortWhitelist.validate(Pageable, Set<String>)` tại `common/SortWhitelist.java`.

## Bảng ánh xạ mã lỗi cũ → mới (phase 1)
| Mã cũ | Mã mới (ErrorCode v4) | HTTP |
|---|---|---|
| `*_NOT_FOUND` (AUCTION_NOT_FOUND, USER_NOT_FOUND, PRODUCT_NOT_FOUND, CATEGORY_NOT_FOUND, ORDER_NOT_FOUND, REPORT_NOT_FOUND) | `NOT_FOUND` | 404 |
| `EMAIL_ALREADY_EXISTS` | `VALIDATION_ERROR` | 400 |
| `SLUG_ALREADY_EXISTS` | `VALIDATION_ERROR` | 400 |
| `INVALID_PASSWORD` | `VALIDATION_ERROR` | 400 |
| `MISSING_SHIPPING_INFO` | `VALIDATION_ERROR` | 400 |
| `BAD_REQUEST` | `VALIDATION_ERROR` | 400 |
| `INVALID_CREDENTIALS` | `UNAUTHORIZED` | 401 |
| `FILE_EMPTY` / `INVALID_FILE_TYPE` / `MAX_IMAGES_REACHED` / `FILE_TOO_LARGE` | `INVALID_IMAGE` | 400 |
| `CANNOT_DELETE` | `INVALID_STATUS_TRANSITION` | 409 |
| `CANNOT_UPDATE` | `PRODUCT_NOT_EDITABLE` | 409 |
| `COMPLAINT_PENDING` / `DUPLICATE_PENDING_REPORT` / `PAYMENT_IN_PROGRESS` | `CONFLICT` | 409 |
| `COMPLAINT_WINDOW_CLOSED` | `INVALID_STATUS_TRANSITION` | 409 |
| `PAYMENT_RATE_LIMITED` | `TOO_MANY_REQUESTS` | 429 |
| `UPLOAD_FAILED` | `IMAGE_UPLOAD_FAILED` | 502 |
| `SELF_BID_NOT_ALLOWED` | `SELF_BID_NOT_ALLOWED` | 409 |

## Quyết định của agent
1. **Jackson Time Serializer**: Spring Boot 4 sử dụng module Jackson 2 với `SimpleModule` đăng ký `@Bean` thay vì class builder customizer không tương thích gói, đảm bảo tương thích 100% với Spring Boot 4 và trả định dạng `+07:00`.
2. **HttpRequestMethodNotSupportedException**: Ánh xạ sang HTTP 405 với error code `NOT_FOUND` trong body theo hướng dẫn của SKILL.md.
3. **Multipart configuration**: Đặt `max-file-size=25MB` và `max-request-size=25MB` tại tầng container/servlet để Spring bắt 413 `PAYLOAD_TOO_LARGE`, logic kiểm tra 5MB/ảnh sẽ do `ImageValidator` đảm nhiệm (trả 400 `INVALID_IMAGE`).
4. **Hỗ trợ tương thích ngược test trong Phase 1-8**: Tạo adapter `AppException extends BusinessException` bên trong `src/test/java` và bổ sung phương thức phụ trợ trên `AppProperties` để toàn bộ 48 tệp test `test-compile` đạt 0 lỗi biên dịch, không làm ảnh hưởng tới `src/main` đã sạch 100% terms v4.

## Cần người duyệt
- Không có vấn đề chặn. Sẵn sàng chuyển tiếp sang Phase 2 (Domain).

## Việc tồn / test còn lỗi
- 80 test assertions trong unit test kỳ vọng `AppException.class` cần được chuẩn hóa sang `BusinessException` trong Phase 9.
- Đổi kiểu `LocalDateTime` sang `Instant` trong các Entity và DTO sẽ được thực hiện tại Phase 2.
