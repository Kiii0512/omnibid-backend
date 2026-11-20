---
name: v4-phase-1-foundation
description: Phase 1 đồng bộ v4 Omnibid — Flyway V1/V2, application*.properties và profile demo, UTC + JSON +07:00, ApiResponse/ErrorCode/BusinessException/GlobalExceptionHandler, PageResponse.
---
# Phase 1 — Nền tảng

> Tài liệu tham chiếu (references) nằm ở `.agents/skills/omnibid-v4-sync/references/`; script kiểm tra ở `.agents/skills/omnibid-v4-sync/scripts/verify-v4.ps1`. Tiến độ ghi ở `docs/v4/PROGRESS.md`.

Đọc trước: `omnibid-v4-sync/references/DB_SPEC_v4.md` (§2, §6), `CONFIG_v4.md` (toàn bộ), `API_SPEC_v4.md` (§1, §4, §5). Mục GAP: A1–A6, B1–B5.

## 1. Flyway
- `pom.xml`: thêm `spring-boot-starter-flyway` và `org.flywaydb:flyway-mysql` (không ghi version, để BOM của Spring Boot quản lý). Nếu thiếu artifact, kiểm tra bằng `.\mvnw.cmd dependency:tree` và ghi tên đúng vào PROGRESS.md.
- Tạo `src/main/resources/db/migration/` và **sao chép nguyên văn** `references/sql/V1__init_schema.sql`, `V2__seed_data.sql` (giữ UTF-8 không BOM).
- Xóa `db/design_schema.sql`, `db/seed_demo.sql` (DELETE_LIST §3).
- `src/test/java/.../support/MySqlContainerConfig.java`: bỏ `withCopyFileToContainer(... design_schema.sql ...)`; dùng `@ServiceConnection` để Flyway tự chạy migration. Container khai báo `--character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci`.

## 2. Cấu hình
- Tạo `application.properties`, `application-demo.properties`, `application-dev.properties`, cập nhật `application-example.properties` và `src/test/resources/application.properties` đúng như `CONFIG_v4.md`.
- Sửa `.gitignore` theo `CONFIG_v4.md` §1.
- Viết lại `config/AppProperties.java` bằng `Duration` + `@DurationUnit` (CONFIG §3). Bỏ khóa cũ. Sửa mọi chỗ đọc thuộc tính cũ (tạm thời có thể để TODO ở lớp sẽ viết lại ở phase sau, nhưng phải compile).
- `scheduler/AuctionScheduler`, `scheduler/OrderScheduler`: chu kỳ đọc `app.scheduler.interval-seconds`.

## 3. Thời gian
- `OmnibidBackendApplication.main`: `TimeZone.setDefault(TimeZone.getTimeZone("UTC"))` trước khi chạy Spring.
- `config/DateTimeConfig`: bean `Clock.systemUTC()`; giữ hằng `DISPLAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh")` dùng cho serializer.
- Tạo cấu hình Jackson (CONFIG §6): serializer/deserializer `Instant` toàn cục, `Converter<String, Instant>` cho `@RequestParam`. Xác định dự án đang dùng Jackson 3 (`tools.jackson`) hay Jackson 2 (`com.fasterxml.jackson`) bằng cách xem import hiện có trong `ProductCondition.java` và dependency tree.
- Việc đổi `LocalDateTime` → `Instant` trong entity/DTO/service làm ở phase 2 (cùng lúc với entity) — phase này chỉ chuẩn bị hạ tầng.

## 4. Phản hồi và lỗi
- `common/ErrorCode.java`: enum 24 mã Bảng 4.22, mỗi mã có `HttpStatus` và thông điệp mặc định tiếng Việt.
- `common/BusinessException.java`: `(ErrorCode code, String message, Map<String,Object> details)` + constructor rút gọn.
- Thay **mọi** `AppException` bằng `BusinessException` và ánh xạ mã cũ → mã mới:
  `*_NOT_FOUND`→`NOT_FOUND`; `EMAIL_ALREADY_EXISTS`, `SLUG_*`, `INVALID_PASSWORD`, `MISSING_SHIPPING_INFO`→`VALIDATION_ERROR`; `INVALID_CREDENTIALS`→`UNAUTHORIZED`;
  mã lạ khác → chọn mã gần nghĩa nhất trong Bảng 4.22 và ghi bảng ánh xạ vào PROGRESS.md. Xóa `AppException.java`.
- `common/ApiResponse.java`: chỉ `success`, `data`, `error{code,message,details}` (`@JsonInclude(NON_NULL)`); factory `ok(data)`, `error(code, message, details)`. Bỏ `timestamp`, `errorCode`, `message` cấp ngoài.
- `common/GlobalExceptionHandler.java`:
  - `BusinessException` → status của mã.
  - `MethodArgumentNotValidException`, `ConstraintViolationException`, `HandlerMethodValidationException`, `MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException`, `HttpMessageNotReadableException`, `MissingServletRequestPartException`, `MissingRequestHeaderException` → 400 `VALIDATION_ERROR`, `details.fields`.
  - `MaxUploadSizeExceededException` → 413 `PAYLOAD_TOO_LARGE`.
  - `DataIntegrityViolationException`, `CannotAcquireLockException`, `PessimisticLockingFailureException`, `ObjectOptimisticLockingFailureException` → 409 `CONFLICT`.
  - `AuthenticationException`/`BadCredentialsException` → 401 `UNAUTHORIZED`; `AccessDeniedException`, `AuthorizationDeniedException` → 403 `FORBIDDEN`.
  - `NoResourceFoundException` → 404 `NOT_FOUND`; `HttpRequestMethodNotSupportedException` → 404 hoặc 405 với mã `NOT_FOUND` (ghi quyết định).
  - `Exception` → 500 `INTERNAL_ERROR`, log stack ở server, không trả stack.
- `security/JwtAuthenticationEntryPoint`, `security/CustomAccessDeniedHandler`: ghi JSON cùng cấu trúc (`UNAUTHORIZED`/`FORBIDDEN`/`ACCOUNT_BANNED`).
- `common/PageResponse`: `{content, page, size, totalElements, totalPages}`; thêm tiện ích `SortWhitelist.validate(Pageable, Set<String>)` → `VALIDATION_ERROR`.

## Cổng
- `.\mvnw.cmd -q compile` sạch. Cập nhật PROGRESS.md (A1–A6, B1–B5), commit `refactor(v4): phase 1 - foundation`.
