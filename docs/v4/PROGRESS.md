# Tiến độ đồng bộ backend theo tài liệu v4

Nhánh: `refactor/v4-sync` · Kit: `.agents/skills/omnibid-v4-sync` · Agent cập nhật tệp này sau mỗi phase.

## Phase
- [x] 0. Chuẩn bị
- [x] 1. Nền tảng (Flyway, cấu hình, UTC/JSON, ApiResponse/ErrorCode)
- [x] 2. Mô hình miền (entity, enum, repository, gỡ tính năng thừa)
- [ ] 3. Bảo mật, xác thực
- [ ] 4. Ảnh, sản phẩm, danh mục
- [ ] 5. Phiên, đặt giá, chốt phiên, WebSocket
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

## Cần người duyệt

## Việc tồn / test còn lỗi
- Sau Phase 2: `.\mvnw.cmd -q compile` đạt **0 lỗi** (cổng bắt buộc phase 1–8); `verify-v4.ps1` đạt mục `10 entity tables match`, `no YAML config`, `V1/V2 matches reference`, `ErrorCode has all 24 codes`, và không còn từ khóa cấm nào thuộc Phase 2 (`LocalDateTime`, `BidStatus`, `Shipment`, `Watchlist`, `AuditLog`, `iconUrl`, `precision=12`, `PENDING`, `SimulatedOutcome`, `REMOVED`, `USED`, `reminder`, `deliveredAt`, ...).
- **21 tệp test** cũ trong `src/test/java` còn lỗi `test-compile` do tham chiếu lớp/enum/kiểu cũ (`NotificationControllerTest`, `OrderControllerTest`, `PaymentControllerTest`, `ReportControllerTest`, `ShipmentControllerTest`, `DefaultMockPaymentGatewayTest`, `AuthorizationIT`, `NotificationIntegrationIT`, `AdminServiceTest`, `AuctionCloserTest`, `AuctionServiceTest`, `AuthServiceTest`, `BidServiceTest`, `CategoryServiceTest`, `NotificationServiceTest`, `OrderServiceTest`, `PaymentServiceTest`, `ProductServiceTest`, `ReportServiceTest`, `ShipmentServiceTest`, `Seed`) — sẽ sửa/gỡ ở Phase 9.
- Đã thêm `src/test/java/com/omnibid/config/JacksonConfigTest.java` kiểm tra serialize `+07:00`, deserialize `Z`/`+07:00` và `SortWhitelist`.
