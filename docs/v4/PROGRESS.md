# Tiến độ đồng bộ backend theo tài liệu v4

Nhánh: `refactor/v4-sync` · Kit: `.agents/skills/omnibid-v4-sync` · Agent cập nhật tệp này sau mỗi phase.

## Phase
- [x] 0. Chuẩn bị
- [x] 1. Nền tảng (Flyway, cấu hình, UTC/JSON, ApiResponse/ErrorCode)
- [x] 2. Mô hình miền (entity, enum, repository, gỡ tính năng thừa)
- [x] 3. Bảo mật, xác thực
- [x] 4. Ảnh, sản phẩm, danh mục
- [x] 5. Phiên, đặt giá, chốt phiên, WebSocket
- [ ] 6. Đơn hàng, thanh toán, giao hàng
- [ ] 7. Thông báo, báo cáo vi phạm
- [ ] 8. Quản trị
- [ ] 9. Kiểm thử, tài liệu, rà soát cuối

## Baseline (phase 0)
- test-compile ban đầu: Đạt (0 lỗi, `.\mvnw.cmd -q test-compile` exit code 0)
- Docker: Client 29.7.2 (`desktop-linux`), daemon chưa chạy (`npipe:////./pipe/dockerDesktopLinuxEngine` không kết nối được — cần bật Docker Desktop trước phase 9)
- Số tệp test ban đầu: 46 tệp `.java` trong `src/test/java`

## Mục GAP đã xử lý
- **Phase 1** (`refactor(v4): phase 1 - foundation`):
  - `A1`: Thêm `spring-boot-starter-flyway` (4.0.9) và `org.flywaydb:flyway-mysql` (11.14.1), sao chép nguyên văn `V1__init_schema.sql` và `V2__seed_data.sql` sang `src/main/resources/db/migration/`, xóa `db/design_schema.sql` và `db/seed_demo.sql`, cập nhật `MySqlContainerConfig` bỏ mount SQL và bật `utf8mb4_unicode_ci`.
  - `A2` (hạ tầng): Đặt `TimeZone.setDefault(UTC)` trong `main`, `Clock.systemUTC()` + `DISPLAY_ZONE = Asia/Ho_Chi_Minh` trong `DateTimeConfig`, thêm `JacksonConfig` (Jackson 3 `JsonMapperBuilderCustomizer`) trả `+07:00` và nhận mọi offset, thêm `Converter<String, Instant>` trong `WebMvcConfig`. (Chuyển `LocalDateTime` → `Instant` ở entity/DTO/service làm ở Phase 2.)
  - `A4`, `A5`: Tạo `application.properties`, `application-demo.properties`, `application-dev.properties`; cập nhật `application-example.properties`, `src/test/resources/application.properties`, `.gitignore`; viết lại `AppProperties` bằng `Duration` + `@DurationUnit`.
  - `A6`: `AuctionScheduler` và `OrderScheduler` dùng `${app.scheduler.interval-seconds:10}` với `TimeUnit.SECONDS`.
  - `B1`, `B2`, `B3`, `B4`, `B5`: Tạo `ErrorCode` (24 mã Bảng 4.22), `BusinessException`, `SortWhitelist`; cập nhật `ApiResponse`, `GlobalExceptionHandler`, `JwtAuthenticationEntryPoint`, `CustomAccessDeniedHandler`; thay toàn bộ `AppException` sang `BusinessException` ở 19 tệp main và xóa `AppException.java` (`SELF_BID_NOT_ALLOWED` chuyển 422 → 409).
- **Phase 2** (`refactor(v4): phase 2 - domain model`):
  - **Gỡ tính năng thừa (`DELETE_LIST` §1)**: Xóa toàn bộ 28 tệp main của `Watchlist`, `Shipment`/`ShipmentEvent`, `AuditLog`/`AuditService` và các enum cũ (`BidStatus`, `ReportAction`, `ReportType`, `SimulatedOutcome`); thay mọi `auditService.log(...)` bằng `log.info(...)` SLF4J.
  - **Enum (`E1`, `F7`, `G1`)**: Chuẩn hóa `ProductCondition` (bỏ `USED`), `ProductStatus` (bỏ `REMOVED`), `PaymentMethod` (chỉ `MOCK_CARD`), `PaymentStatus` (chỉ `SUCCESS`, `FAILED`), `NotificationType` (5 giá trị v4); thêm `ReportTargetType`, `ReportDecision`, `ReserveStatus`, `AuctionEventType`.
  - **Entity (`A2`, `A3`, `D5`, `E1`, `E6`)**: Chuyển toàn bộ `LocalDateTime` → `Instant` và tiền sang `DECIMAL(15,0)` trên cả 10 bảng v4; cập nhật `User` (`avatarPublicId`, `tokenVersion`, `ban`/`unban`/`increaseTokenVersion`/`isBanned`), `Category` (`icon` 50), `Product` (`item_condition`, `markInAuction`/`reserve`/`release`/`markSold`/`hide`/`isEditable`), `ProductImage` (`publicId` NOT NULL), `Auction` (bỏ `version`, thêm `updatedAt`, `activate`/`placeBid`/`minNextBid`/`reserveStatus`/`closeAsEnded`/`closeAsUnsold`/`cancel`/`isOpen`), `Bid` (không trạng thái), `Order` (bỏ `deliveredAt`/reminder, thêm `trackingCode`/`shippedAt`, `markPaid`/`markShipped`/`confirmReceived`/`autoComplete`/`expire`/`cancel`/`isPaymentOverdue`), `Payment` (bỏ `transactionRef`), `Report` (`reportedOrderId`, `resolve`/`reject`).
  - **Repository**: Gỡ `UserRepository.findByIdForUpdate`/`checkCurrentStatus`, truy vấn `BidStatus`, `transactionRef`, `reminder_*`; bổ sung `PESSIMISTIC_WRITE` + `jakarta.persistence.lock.timeout=5000` (`findByIdForUpdate`, `findAllByIdInForUpdateOrderByIdAsc`) cho `Auction`/`Order`/`Product`, các truy vấn quét trả ID (`findIdsByStatusAnd...`), `BidRepository` và `PaymentRepository` theo v4.
  - **Biên dịch cơ học phần còn lại**: Cập nhật DTO, `AuctionMapper`, `NotificationTemplates`, `MockPaymentGateway`/`DefaultMockPaymentGateway`, `OrderScheduler` và các Service/Controller còn lại (đánh dấu `// TODO v4-phase-N` tại phần logic sẽ viết lại ở phase 5–8).
- **Phase 3** (`refactor(v4): phase 3 - security`):
  - `C1`: Cập nhật `JwtTokenProvider` (`sub=userId`, claim `role`, `ver`, dùng `Clock`, thêm `JwtClaims`, `JwtValidationException`, `issueToken`/`generateToken`); cập nhật `CustomUserDetails` + `UserDetailsServiceImpl.loadUserById`.
  - `C2`, `C3`, `C10`: Thêm `POST /api/v1/auth/logout` và `AuthService.logout` tăng `tokenVersion`; cập nhật `ChangePasswordRequest` (`confirmPassword`, regex mật khẩu v4), `UserServiceImpl.changePassword` tăng `tokenVersion`, trả `MessageResponse`; cập nhật `UpdateProfileRequest` (bỏ `avatarUrl`) và xóa `PATCH /api/v1/users/me/avatar`.
  - `C4`: Thêm `LoginAttemptLimiter` (bộ nhớ `ConcurrentHashMap`, `Clock`, key `(email lowercase, remoteAddr)`, 5 lần/15 phút → 429 `TOO_MANY_REQUESTS` + `retryAfterSeconds`).
  - `C5`: Thêm `PublicEndpointMatcher` dùng chung giữa `SecurityConfig` và `JwtAuthenticationFilter`; token sai/hết hạn/`ver` khác bị bỏ qua ở Public và trả 401 `UNAUTHORIZED` ở endpoint bảo vệ; user `BANNED` luôn nhận 403 `ACCOUNT_BANNED` ở mọi endpoint kể cả Public.
  - `C6`: Thay `AuthResponse` bằng `LoginResponse` + `UserSummary`; `POST /auth/register` (bỏ `address`, thêm regex mật khẩu) trả 201 `UserProfileResponse` không kèm token, email trùng → 400 `VALIDATION_ERROR` với `details.fields.email`; sai mật khẩu → 401 `UNAUTHORIZED`.
  - `C7`, `C8`: Cập nhật `SecurityConfig` (permit 5 nhóm Public + `/ws/**`, đặt `/api/v1/auctions/mine` trước matcher Public, bỏ `/uploads/**`, `/api/v1/products/**`, `/ws-auction/**`, CORS `allowCredentials=false`); đồng bộ `@PreAuthorize("hasRole('USER')")` / `hasAnyRole('USER','ADMIN')` trên các controller.
  - `C9`: Cập nhật `WebSocketConfig` (`/ws` thuần, bỏ SockJS, origins từ `app.cors.allowed-origins`, broker `/topic`, interceptor kiểm tra CONNECT JWT `ver`/BANNED, SUBSCRIBE chỉ `/topic/auctions/{id}`, chặn mọi SEND) và đổi topic trong `WebSocketPublisherImpl` sang `/topic/auctions/{id}`.
  - Cập nhật `docs/v4/API_CHANGES_FOR_FRONTEND.md`, `JwtTokenProviderTest`, `AuthServiceTest` và thêm `LoginAttemptLimiterTest`.
- **Phase 4** (`refactor(v4): phase 4 - images, products, categories`):
  - `D1`, `D2`, `D4`: Thêm `ImageValidator` (magic bytes JPEG/PNG/WEBP, ≤ 5 MB, 1–5 tệp), `AfterCommitExecutor`, `StoredImage`, `ImageStorageService`, `CloudinaryImageStorageService`, `CloudinaryConfig` (thiếu cấu hình vẫn khởi động + log WARN, upload trả 502 `IMAGE_UPLOAD_FAILED`). Xóa `StorageService`, `StorageConfig`, `LocalStorageServiceImpl`, `CloudinaryStorageServiceImpl`, `StorageUploadResult`, `ProductSummaryResponse`, handler `/uploads/**` trong `WebMvcConfig` và thư mục `uploads/`.
  - `D1`, `D2`: Viết lại `ProductController`/`ProductServiceImpl` theo quy trình 4 bước (validate → upload Cloudinary ngoài transaction → `TransactionTemplate` khóa `PESSIMISTIC_WRITE` + BR-23 → bù trừ xóa asset nếu lỗi DB; xóa ảnh/sản phẩm xóa asset sau COMMIT), bỏ `GET /products`, `GET /products/mine/summary`, `PATCH .../primary`, chuẩn hóa `ProductImageResponse`, `ProductListItemResponse`, `ProductResponse` theo `RESPONSE_DTO_v4.md` (gồm `activeAuctionId`, `everAuctioned`, `primaryImageUrl`, `imageCount`).
  - `D3`: Cập nhật `POST /api/v1/users/me/avatar` dùng `ImageValidator` + `ImageStorageService` ngoài transaction, lưu `avatarPublicId`, xóa ảnh cũ sau COMMIT, bù trừ khi lỗi DB, trả 200 `AvatarResponse { avatarUrl }`.
  - `D5`: Cập nhật `CategoryController`/`CategoryServiceImpl`/`AdminController` (`icon` ≤ 50 ký tự `@Pattern("^[a-z0-9-]+$")`, `slug` tùy chọn/kiểm tra trùng 400 `VALIDATION_ERROR`, `GET /categories` phân trang + `keyword` trả `Page<CategoryResponse>`, xóa `GET /categories/{id}`, `/admin/categories` trả `AdminCategoryResponse` có `productCount`, xóa khi còn sản phẩm → 409 `CATEGORY_IN_USE`).
  - Thêm `ImageValidatorTest` và cập nhật `docs/v4/API_CHANGES_FOR_FRONTEND.md`.
- **Phase 5** (`refactor(v4): phase 5 - auctions and bids`):
  - `E1`, `E2`, `E3`, `E8`: Đổi route đặt giá sang `POST /api/v1/auctions/{id}/bids` (`BidRequest(amount)`), trả 201 `PlaceBidResponse`; đặt giá khóa auction, kiểm tra sản phẩm không `HIDDEN`, không truy vấn/không khóa bảng `users`, lưu `Bid` không trạng thái + flush (`DataIntegrityViolationException` → 409 `CONFLICT`), gửi `OUTBID` cho leader cũ và phát `AuctionEvent(BID_PLACED)` sau COMMIT. `GET /auctions/{id}/bids` phân trang `Page<BidHistoryItem>` (ẩn danh, bỏ `bidderId`/`status`, whitelist `createdAt`/`amount`, chặn sản phẩm `HIDDEN` trừ ADMIN). `GET /bids/mine` lọc `auctionId`, trả `Page<MyBidItem>` (có `leading`, `orderId`). Xóa `GET /bids/mine/summary`, `BidResponse`, `BidSummaryResponse`.
  - `E5`, `E9`, `E10`, `E11`, `E12`: Chuẩn hóa `AuctionController`/`AuctionServiceImpl`/`AuctionSpecification`/`AuctionMapper` theo `RESPONSE_DTO_v4.md` (`AuctionSummaryResponse`, `AuctionDetailResponse`, `MyAuctionItemResponse`, `UserRef`): tạo phiên `SCHEDULED` với `startTime > now`, thời lượng theo `AppProperties` (`min-duration-minutes`..`max-duration-days`), khóa product + kiểm tra BR-06/BR-15 (`PRODUCT_NOT_AVAILABLE`, bắt UNIQUE → `CONFLICT`); sửa phiên chỉ khi `SCHEDULED` (BR-07 → `AUCTION_NOT_EDITABLE`) và phát `AUCTION_UPDATED`; hủy bởi người bán bắt buộc `reason`, chỉ `SCHEDULED` hoặc `ACTIVE` 0 bid (`CANNOT_CANCEL` / `INVALID_STATUS_TRANSITION`), gửi `AUCTION_CANCELLED` cho cả người bán và leader; tìm kiếm `GET /auctions` hỗ trợ `keyword`, `categoryId`, `minPrice`, `maxPrice`, `status`, `endBefore`, loại sản phẩm `HIDDEN`, sort whitelist 4 cặp; `GET /auctions/mine` hỗ trợ `status`, `keyword`.
  - `E4`, `E7`: Viết lại `AuctionCloser` theo BR-09 (đọc trạng thái leader bằng truy vấn thường, leader `BANNED` → `UNSOLD`, không trao cho người thứ hai; `ENDED` tạo `Order` qua `OrderService.createFromAuction(auction, now)`, khóa product `reserve()`, gửi `AUCTION_RESULT` cho người thắng kèm hạn thanh toán + người bán; `UNSOLD` `release()`, giữ nguyên `HIDDEN` theo BR-16). Thay `AuctionWebSocketMessage` bằng `AuctionEvent` 12 trường phát sau COMMIT qua `AfterCommitExecutor` trên `/topic/auctions/{id}` (cập nhật cả `AdminServiceImpl`).
  - Cập nhật `MaskingUtils.maskName` theo quy tắc v4 (`ký tự đầu + *** + ký tự cuối`), sửa 4 unit test Phase 5 (`BidServiceTest`, `AuctionServiceTest`, `AuctionCloserTest`, `WebSocketPublisherTest`) và cập nhật `docs/v4/API_CHANGES_FOR_FRONTEND.md`.

## Bảng ánh xạ mã lỗi cũ → mới (phase 1)
| Mã cũ (`AppException`) | Mã mới (`ErrorCode`) | HTTP mới | Ghi chú |
|---|---|---|---|
| `EMAIL_ALREADY_EXISTS` | `VALIDATION_ERROR` | 400 | Theo Bảng 4.22 |
| `INVALID_CREDENTIALS` | `UNAUTHORIZED` | 401 | Theo Bảng 4.22 |
| `INVALID_PASSWORD` | `VALIDATION_ERROR` | 400 | Theo Bảng 4.22 |
| `AUCTION_NOT_FOUND`, `USER_NOT_FOUND` | `NOT_FOUND` | 404 | Quy về `NOT_FOUND` |
| `BAD_REQUEST` | `VALIDATION_ERROR` | 400 | Kiểm tra ảnh sản phẩm khi tạo phiên |
| `CANNOT_UPDATE` (`AuctionServiceImpl`) | `AUCTION_NOT_EDITABLE` | 409 | Sửa phiên |
| `CANNOT_UPDATE` (`ProductServiceImpl`) | `PRODUCT_NOT_EDITABLE` | 409 | Sửa sản phẩm |
| `CANNOT_DELETE` (`ProductServiceImpl`) | `INVALID_STATUS_TRANSITION` | 409 | Theo Bảng 4.22 |
| `FILE_EMPTY`, `INVALID_FILE_TYPE`, `MAX_IMAGES_REACHED` | `INVALID_IMAGE` | 400 | Theo Bảng 4.22 |
| `UPLOAD_FAILED` | `IMAGE_UPLOAD_FAILED` | 502 | Theo Bảng 4.22 |
| `SELF_BID_NOT_ALLOWED` (422) | `SELF_BID_NOT_ALLOWED` | 409 | Khắc phục GAP B3 |
| `PAYMENT_IN_PROGRESS` | `CONFLICT` | 409 | Tạm thời đến Phase 6 (bỏ `PENDING`) |
| `PAYMENT_RATE_LIMITED` | `TOO_MANY_REQUESTS` | 429 | Tạm thời đến Phase 6 |
| `COMPLAINT_PENDING`, `COMPLAINT_WINDOW_CLOSED` | `INVALID_STATUS_TRANSITION` | 409 | Tạm thời đến Phase 7 |
| `DUPLICATE_PENDING_REPORT` | `CONFLICT` | 409 | Tạm thời đến Phase 7 |
| 2-arg `(msg, HttpStatus.NOT_FOUND/FORBIDDEN)` | `NOT_FOUND` / `FORBIDDEN` | 404 / 403 | Khớp mã trạng thái |
| `VALIDATION_ERROR` (slug trùng, 409 cũ) | `VALIDATION_ERROR` | 400 | Theo Bảng 4.22 |

## Quyết định của agent
- **Jackson 3**: Dự án dùng Spring Boot 4.0.9 kèm `tools.jackson.core:jackson-databind:3.1.7` (Jackson 3) và `com.fasterxml.jackson.core:jackson-annotations:2.21`. Đã dùng `JsonMapperBuilderCustomizer` + `tools.jackson.databind.module.SimpleModule` trong `JacksonConfig`, và inject `tools.jackson.databind.json.JsonMapper` vào `JwtAuthenticationEntryPoint`, `CustomAccessDeniedHandler`.
- **`HttpRequestMethodNotSupportedException`**: Trả HTTP **405 Method Not Allowed** kèm `error.code = "NOT_FOUND"` (theo Bảng 4.22 không có mã lỗi riêng cho 405).
- **Multipart giới hạn**: Đặt `spring.servlet.multipart.max-file-size=25MB` và `max-request-size=25MB` theo khuyến nghị `CONFIG_v4.md` để Spring ném `MaxUploadSizeExceededException` → 413 `PAYLOAD_TOO_LARGE`; kiểm tra 5 MB/tệp thực hiện tại `ImageValidator` (Phase 4) → 400 `INVALID_IMAGE`.
- **Tương thích `ApiResponse.success(...)`**: Giữ tạm 2 hàm alias `success(T)` và `success(String, T)` gọi thẳng `ok(T)` (không xuất trường `message` cấp ngoài, đảm bảo JSON đúng chuẩn v4) để các controller cũ vẫn biên dịch sạch trước khi dọn ở Phase 3–8.
- **Timestamp entity (Phase 2)**: Thống nhất dùng `@CreationTimestamp` (`updatable = false`) và `@UpdateTimestamp` với kiểu `Instant` trên toàn bộ 10 entity (bổ sung `@CreationTimestamp` cho `Payment.createdAt` để nhất quán). Không ánh xạ hai cột sinh `auctions.active_product_id` và `payments.success_order_id`.
- **`Order.isPaymentOverdue(Instant now)`**: Kiểm tra `paymentDeadline != null && now.isAfter(paymentDeadline)` để khớp chặt truy vấn quét `o.paymentDeadline < :now`.
- **`MaskingUtils.maskName` & `winnerName` (Phase 5)**: Thống nhất ẩn danh theo đúng ví dụ `API_SPEC_v4.md` §3 (`ký tự đầu + *** + ký tự cuối`, 1 ký tự → `ký tự đầu + ***`), áp dụng cho cả `highestBidderName`, `bidderName` và `winnerName` ở response công khai.
- **Quyền ADMIN xem chi tiết/lịch sử đặt giá của sản phẩm `HIDDEN` (Phase 5)**: `GET /api/v1/auctions` luôn loại sản phẩm `HIDDEN`; `GET /api/v1/auctions/{id}` và `GET /api/v1/auctions/{id}/bids` trả 404 `NOT_FOUND` với mọi người gọi không phải ADMIN (cho phép ADMIN tra cứu lịch sử đặt giá vì không có `/admin/auctions/{id}/bids` riêng).
- **Trường tùy chọn `?` trong `AuctionDetailResponse` & `AuctionEvent` (Phase 5)**: Gắn `@JsonInclude(JsonInclude.Include.NON_NULL)` riêng trên `reservePrice`, `orderId` (`AuctionDetailResponse`) và `bid` (`AuctionEvent`) để chỉ xuất hiện khi thỏa điều kiện; các trường `T | null` khác luôn có mặt trong JSON.

## Cần người duyệt

## Việc tồn / test còn lỗi
- Sau Phase 5: `.\mvnw.cmd -q -o "-Daether.syncContext.named.factory=rwlock-local" compile` đạt **0 lỗi**; `verify-v4.ps1` sạch toàn bộ 9 endpoint phiên/đặt giá và 0 từ khóa cấm.
- Đã cập nhật 4 tệp unit test Phase 5 (`BidServiceTest`, `AuctionServiceTest`, `AuctionCloserTest`, `WebSocketPublisherTest`) biên dịch sạch theo v4.
- **17 tệp test** cũ trong `src/test/java` còn lỗi `test-compile` do thuộc Phase 6–8 hoặc tham chiếu lớp/enum cũ (`NotificationControllerTest`, `OrderControllerTest`, `PaymentControllerTest`, `ReportControllerTest`, `ShipmentControllerTest`, `DefaultMockPaymentGatewayTest`, `AuthorizationIT`, `NotificationIntegrationIT`, `AdminServiceTest`, `CategoryServiceTest`, `NotificationServiceTest`, `OrderServiceTest`, `PaymentServiceTest`, `ProductServiceTest`, `ReportServiceTest`, `ShipmentServiceTest`, `Seed`) — sẽ sửa/gỡ ở Phase 9.
- Đã thêm `src/test/java/com/omnibid/common/ImageValidatorTest.java` và `src/test/java/com/omnibid/config/JacksonConfigTest.java`.
