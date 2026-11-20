# CONFIG_v4 — Cấu hình (Bảng 1.2, mục 3.4, 6.1, 6.3 của Thiết kế v4)

Chỉ dùng `.properties`. Không YAML.

## 1. Tệp và vai trò
| Tệp | Git | Nội dung |
|---|---|---|
| `src/main/resources/application.properties` | **commit** | Giá trị mặc định v4; bí mật đọc từ biến môi trường `${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}`, `${JWT_SECRET}`, `${CLOUDINARY_CLOUD_NAME}`, `${CLOUDINARY_API_KEY}`, `${CLOUDINARY_API_SECRET}` |
| `src/main/resources/application-demo.properties` | commit | Chỉ 5 dòng ghi đè (mục 4 bên dưới) |
| `src/main/resources/application-dev.properties` | commit | `app.payment.simulate-enabled=true` (+ show-sql nếu muốn) |
| `src/main/resources/application-local.properties` | **không commit** | Bí mật máy cá nhân; thêm vào `.gitignore` |
| `src/main/resources/application-example.properties` | commit | Mẫu, không bí mật |
| `src/test/resources/application.properties` | commit | Cấu hình test |

Sửa `.gitignore`: bỏ dòng `src/main/resources/application.properties` và `application-example.properties`; thêm `src/main/resources/application-local.properties`.
Kích hoạt local: `spring.profiles.active=dev,local` (qua biến môi trường hoặc Run Configuration).

## 2. application.properties (mặc định)
```properties
spring.application.name=omnibid-backend
server.port=8080

# DB (UTC ở cả phiên MySQL)
spring.datasource.url=${DB_URL:jdbc:mysql://localhost:3306/omnibid_v4?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true}
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD:}
spring.datasource.hikari.transaction-isolation=TRANSACTION_READ_COMMITTED

spring.jpa.hibernate.ddl-auto=none
spring.jpa.open-in-view=false
spring.jpa.properties.hibernate.jdbc.time_zone=UTC
# Chờ khóa dòng tối đa 5 giây (mục 5.2)
spring.jpa.properties.jakarta.persistence.lock.timeout=5000

spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration

# JSON: trả +07:00
spring.jackson.time-zone=Asia/Ho_Chi_Minh

# Phân trang
spring.data.web.pageable.default-page-size=10
spring.data.web.pageable.max-page-size=100

# JWT
jwt.secret=${JWT_SECRET}
jwt.expiration-ms=86400000

# CORS (REST + /ws)
app.cors.allowed-origins=http://localhost:3000

# Nghiệp vụ
app.order.payment-deadline-hours=48
app.order.confirm-deadline-days=7
app.auction.min-duration-minutes=60
app.auction.max-duration-days=30
app.scheduler.interval-seconds=10
app.payment.simulate-enabled=false

# Đăng nhập sai
app.security.login.max-attempts=5
app.security.login.window-minutes=15
app.security.login.block-minutes=15

# Ảnh
spring.servlet.multipart.max-file-size=5MB
spring.servlet.multipart.max-request-size=25MB
cloudinary.cloud-name=${CLOUDINARY_CLOUD_NAME:}
cloudinary.api-key=${CLOUDINARY_API_KEY:}
cloudinary.api-secret=${CLOUDINARY_API_SECRET:}
cloudinary.folder=omnibid
```
Ghi chú: `max-file-size=5MB` khiến Spring ném `MaxUploadSizeExceededException` cho tệp > 5 MB; GlobalExceptionHandler phải phân biệt:
tệp đơn > 5 MB → 400 `INVALID_IMAGE`; cả request > 25 MB → 413 `PAYLOAD_TOO_LARGE`. Cách đơn giản và chắc chắn: đặt
`max-file-size=25MB`, `max-request-size=25MB` (Spring trả 413) và tự kiểm tra 5 MB/tệp trong `ImageValidator` (400 INVALID_IMAGE). **Dùng cách này.**

## 3. AppProperties (ánh xạ kiểu)
Dùng `@ConfigurationProperties(prefix = "app")` (record hoặc class) với `java.time.Duration` + `@DurationUnit` (quyết định CHANGELOG c.1):
```java
public record Order(
    @DurationUnit(ChronoUnit.HOURS) Duration paymentDeadlineHours,   // "48" = 48h, "3m" = 3 phút
    @DurationUnit(ChronoUnit.DAYS)  Duration confirmDeadlineDays) {}  // "7" = 7 ngày
public record Auction(
    @DurationUnit(ChronoUnit.MINUTES) Duration minDurationMinutes,
    @DurationUnit(ChronoUnit.DAYS)    Duration maxDurationDays) {}
public record Scheduler(@DurationUnit(ChronoUnit.SECONDS) Duration intervalSeconds) {}
public record Payment(boolean simulateEnabled) {}
```
Bỏ các khóa cũ: `app.order.confirm-window-days`, `app.order.reminder-hours`, `app.payment.mock.*`, `app.payment.pending-timeout-minutes`,
`app.payment.rate-limit.*`, `app.scheduler.fixed-rate-ms`, `app.scheduler.order-delay`, `app.auction.min-duration-hours`, `app.storage.upload-dir`.

Scheduler dùng chu kỳ cấu hình: `@Scheduled(fixedDelayString = "${app.scheduler.interval-seconds:10}", timeUnit = TimeUnit.SECONDS)`
(hoặc `SchedulingConfigurer` đọc `Duration`). Không hard-code `fixedRate = 10000`.

## 4. application-demo.properties (nguyên văn mục 1.3)
```properties
app.auction.min-duration-minutes=2
app.order.payment-deadline-hours=3m
app.order.confirm-deadline-days=3m
app.scheduler.interval-seconds=5
app.payment.simulate-enabled=true
```

## 5. Test (`src/test/resources/application.properties`)
- Datasource do Testcontainers (`@ServiceConnection`), Flyway bật, `ddl-auto=none`, `open-in-view=false`.
- `spring.task.scheduling.enabled=false` hoặc tắt Scheduler bằng profile; test gọi tick thủ công.
- `jwt.secret` cố định chỉ dùng cho test; `app.payment.simulate-enabled=true`.
- Bean `ImageStorageService` giả (in-memory) trong `TestSupportConfig` để không gọi Cloudinary.

## 6. Bẫy cần tránh: Instant và +07:00
Jackson luôn ghi `Instant` dạng `...Z` (UTC), **bỏ qua** `spring.jackson.time-zone`. Để API trả `+07:00` như Thiết kế 4.1:
- Đăng ký một module Jackson toàn cục (Spring Boot 4 dùng Jackson 3, package `tools.jackson.*`; dùng bean
  `JsonMapperBuilderCustomizer` — nếu dự án còn Jackson 2 thì dùng `Jackson2ObjectMapperBuilderCustomizer`) gồm:
  - Serializer `Instant` → `instant.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime().format(ISO_OFFSET_DATE_TIME)`.
  - Deserializer `Instant` ← `OffsetDateTime.parse(text).toInstant()` (nhận mọi offset, kể cả `Z`).
- `@RequestParam` kiểu `Instant` (endBefore, fromDate, toDate): thêm `Converter<String, Instant>` cùng logic vào `WebMvcConfig`.
- Viết một test kiểm tra JSON trả `+07:00` và nhận được `2026-11-20T02:00:00Z` lẫn `2026-11-20T09:00:00+07:00`.
