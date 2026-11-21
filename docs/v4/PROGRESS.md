# Tiến độ đồng bộ backend theo tài liệu v4

Nhánh: `refactor/v4-sync` · Kit: `.agents/skills/omnibid-v4-sync` · Agent cập nhật tệp này sau mỗi phase.

## Phase
- [x] 0. Chuẩn bị
- [x] 1. Nền tảng (Flyway, cấu hình, UTC/JSON, ApiResponse/ErrorCode)
- [ ] 2. Mô hình miền (entity, enum, repository, gỡ tính năng thừa)
- [ ] 3. Bảo mật, xác thực
- [ ] 4. Ảnh, sản phẩm, danh mục
- [ ] 5. Phiên, đặt giá, chốt phiên, WebSocket
- [ ] 6. Đơn hàng, thanh toán, giao hàng
- [ ] 7. Thông báo, báo cáo vi phạm
- [ ] 8. Quản trị
- [ ] 9. Kiểm thử, tài liệu, rà soát cuối

## Baseline (phase 0)
- test-compile ban đầu: Đạt (0 lỗi biên dịch)
- Docker: Chưa khả dụng trong môi trường terminal hiện tại (cần lưu ý cho integration test ở phase 9)
- Số tệp test ban đầu: 46 tệp test (`src/test/java`)

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
