# OmniBid — Online Auction System (Backend v4)

Hệ thống Backend cho nền tảng đấu giá trực tuyến **OmniBid**, xây dựng theo kiến trúc phân tầng nguyên khối (**Monolith Layered Architecture**) trên **Spring Boot 4.0.9**, **Java 17**, **MySQL 8.0+**, **Flyway**, **Cloudinary** và **WebSocket STOMP**.

---

## 1. Kiến trúc & Nguyên tắc cốt lõi (v4)

- **Monolith 3 tầng:** `Controller → Service → Repository/Entity`. Controller chỉ gọi Service và không bao giờ trả Entity trực tiếp.
- **10 bảng dữ liệu quản lý bởi Flyway (`src/main/resources/db/migration/`):**
  1. `users` (có `token_version` vô hiệu hóa JWT khi đăng xuất/đổi mật khẩu, `avatar_public_id`)
  2. `categories` (`icon` cho Lucide icons)
  3. `products` (`item_condition`, trạng thái `AVAILABLE | IN_AUCTION | RESERVED | SOLD | HIDDEN`)
  4. `product_images` (1–5 ảnh/sản phẩm lưu trên **Cloudinary**, `public_id` NOT NULL)
  5. `auctions` (cột sinh `active_product_id` + UNIQUE đảm bảo BR-15: tối đa 1 phiên `SCHEDULED`/`ACTIVE` mỗi sản phẩm)
  6. `bids` (bất biến, không có cột trạng thái — lịch sử đặt giá giữ nguyên kể cả khi người đặt bị khóa)
  7. `orders` (tích hợp giao hàng đơn bước: `tracking_code`, `shipped_at`, `payment_deadline` +48h, `confirm_deadline` +7d)
  8. `payments` (thanh toán mô phỏng đồng bộ, `Idempotency-Key` UUID, chỉ `SUCCESS`/`FAILED`, cột sinh `success_order_id` + UNIQUE đảm bảo tối đa 1 `SUCCESS`/đơn)
  9. `notifications` (5 loại: `OUTBID`, `AUCTION_RESULT`, `AUCTION_CANCELLED`, `ORDER_UPDATE`, `REPORT_RESOLVED`; chỉ ghi sau `COMMIT`)
  10. `reports` (báo cáo sản phẩm/người dùng/đơn hàng, xử lý bằng `RESOLVED`/`REJECTED` kèm `note`, tách biệt với hành động quản trị)
- **Thời gian & Tiền tệ:**
  - Lưu trữ nội bộ và DB hoàn toàn theo **UTC** (`Instant`, `TimeZone.setDefault(UTC)`, `hibernate.jdbc.time_zone=UTC`).
  - JSON response trả thời gian kèm múi giờ **`+07:00`** (`Asia/Ho_Chi_Minh`); nhận đầu vào cả `Z` và `+07:00`.
  - Tiền tệ dùng `DECIMAL(15,0)` / `BigDecimal` scale 0 (số nguyên VND, không `.00`).
- **Định dạng phản hồi chuẩn hóa (`ApiResponse<T>`):**
  - Thành công: `{ "success": true, "data": ... }`
  - Lỗi: `{ "success": false, "error": { "code": "...", "message": "...", "details": ... } }` (24 mã lỗi chuẩn hóa trong `ErrorCode`).
- **Đồng thời & An toàn dữ liệu:**
  - Khóa dòng bi quan (`PESSIMISTIC_WRITE`, timeout 5s) theo thứ tự cố định **`auctions → orders → products`** (cùng bảng theo `id` tăng dần). Không khóa bảng `users`.
  - Thông báo và sự kiện WebSocket (`/ws`, topic `/topic/auctions/{id}`, chỉ đọc) chỉ phát **sau `COMMIT`** (`AFTER_COMMIT`).
  - Ảnh tải lên kiểm tra bằng **magic bytes** (JPEG/PNG/WEBP, ≤ 5 MB), upload Cloudinary ngoài transaction, có bù trừ xóa asset khi lỗi DB và xóa asset cũ sau `COMMIT`.

---

## 2. Cấu trúc thư mục

```text
src/main/java/com/omnibid/
 ├── common/          # ApiResponse, ErrorCode, BusinessException, GlobalExceptionHandler, ImageValidator, PageResponse, SortWhitelist
 ├── config/          # SecurityConfig, WebSocketConfig, DateTimeConfig, JacksonConfig, CloudinaryConfig, AppProperties
 ├── controller/      # REST Controllers (và controller/admin/*) — đúng 54 endpoint /api/v1/**
 ├── dto/             # Request & Response DTOs
 ├── entity/          # 10 JPA Entities khớp Flyway V1
 ├── enums/           # Enums trạng thái nghiệp vụ v4
 ├── gateway/         # MockPaymentGateway & DefaultMockPaymentGateway
 ├── listener/        # NotificationPushListener (AFTER_COMMIT)
 ├── repository/      # 10 Spring Data JPA Repositories + Specifications
 ├── scheduler/       # AuctionScheduler & OrderScheduler
 ├── security/        # JwtTokenProvider (ver), JwtAuthenticationFilter, LoginAttemptLimiter (429), PublicEndpointMatcher
 └── service/         # Business Services, AuctionCloser, OrderLifecycleSupport, CloudinaryImageStorageService
```

---

## 3. Tài khoản Admin mặc định (Flyway V2)

Khi Flyway chạy migration `V2__seed_data.sql` trên database mới, hệ thống tạo sẵn 1 tài khoản quản trị:

| Email | Mật khẩu mặc định | Vai trò |
| :--- | :---: | :---: |
| `admin@omnibid.local` | `Admin@123456` | `ADMIN` |

> [!WARNING]
> **Bắt buộc đổi mật khẩu ngay** qua `PATCH /api/v1/users/me/password` sau lần đăng nhập đầu tiên trên môi trường thật hoặc chia sẻ.

---

## 4. Hướng dẫn cài đặt & khởi chạy

### Yêu cầu
- **Java 17** (OpenJDK 17)
- **MySQL 8.0+** (charset `utf8mb4`, collation `utf8mb4_unicode_ci`)
- **Docker Desktop** (chỉ cần khi chạy Integration Test `verify`)

### Biến môi trường cần thiết

Các cấu hình nhạy cảm được đọc từ biến môi trường hoặc tệp `.env` ở thư mục gốc dự án (tạo từ mẫu [.env.example](.env.example), đã nằm trong `.gitignore`):

| Biến môi trường | Mô tả |
| :--- | :--- |
| `DB_URL` | JDBC URL MySQL (ví dụ `jdbc:mysql://localhost:3306/omnibid_v4?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`) |
| `DB_USERNAME`, `DB_PASSWORD` | Tài khoản kết nối MySQL |
| `JWT_SECRET` | Chuỗi bí mật ký HS256 (tối thiểu 32 ký tự / 256-bit) |
| `ADMIN_INITIAL_PASSWORD` | *(Tùy chọn, khuyến nghị trên Production)* Tự động đổi mật khẩu tài khoản `admin@omnibid.local` khi khởi động nếu còn dùng mật khẩu mặc định |
| `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` | Thông tin tài khoản Cloudinary (nếu bỏ trống ứng dụng vẫn khởi động nhưng API upload ảnh sẽ trả `502 IMAGE_UPLOAD_FAILED`) |

### Khởi chạy ứng dụng

- **Môi trường phát triển cục bộ (đọc biến từ `.env`):**
  ```powershell
  Copy-Item .env.example .env
  .\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
  ```
- **Chế độ Demo (nạp dữ liệu mẫu `V3__demo_data.sql`, rút ngắn thời lượng phiên tối thiểu xuống 2 phút, scheduler 5s):**
  ```powershell
  .\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=demo
  ```

Ứng dụng lắng nghe tại `http://localhost:8080` và WebSocket STOMP tại `ws://localhost:8080/ws`.

---

## 5. Danh sách 54 API Endpoint (v4)

Toàn bộ đặc tả 54 endpoint REST `/api/v1/**`, tham số, mã HTTP, 24 mã lỗi, và cấu trúc WebSocket `/ws` + `/topic/auctions/{id}` được tài liệu hóa chi tiết tại:
- [API_SPEC_v4.md](.agents/skills/omnibid-v4-sync/references/API_SPEC_v4.md)
- [RESPONSE_DTO_v4.md](.agents/skills/omnibid-v4-sync/references/RESPONSE_DTO_v4.md)
- [API_CHANGES_FOR_FRONTEND.md](docs/v4/API_CHANGES_FOR_FRONTEND.md) (bảng tổng hợp thay đổi từ bản cũ sang v4 cho Frontend)

Tóm tắt nhóm endpoint:
- **Xác thực (`/api/v1/auth`):** 3 endpoint (`register`, `login`, `logout`)
- **Hồ sơ cá nhân (`/api/v1/users/me`):** 4 endpoint (xem, sửa, đổi mật khẩu, đổi avatar)
- **Danh mục công khai (`/api/v1/categories`):** 1 endpoint
- **Sản phẩm người bán (`/api/v1/products`):** 7 endpoint (tạo multipart 1–5 ảnh, danh sách của tôi, chi tiết, sửa, xóa, thêm ảnh, xóa ảnh)
- **Phiên đấu giá & Đặt giá (`/api/v1/auctions`, `/api/v1/bids`):** 10 endpoint (tìm kiếm, chi tiết, tạo, sửa, hủy, của tôi, đặt giá `POST /auctions/{id}/bids`, lịch sử phiên, lịch sử của tôi)
- **Đơn hàng & Thanh toán (`/api/v1/orders`):** 7 endpoint (danh sách, chi tiết, cập nhật giao hàng, thanh toán đồng bộ, lịch sử thanh toán, gửi hàng `/ship`, xác nhận nhận hàng `/confirm-receipt`)
- **Thông báo & Báo cáo (`/api/v1/notifications`, `/api/v1/reports`):** 3 endpoint
- **Quản trị (`/api/v1/admin/**`):** 19 endpoint (người dùng, sản phẩm, phiên, đơn hàng, báo cáo, danh mục, thống kê tích lũy)

---

## 6. Lệnh kiểm thử & rà soát

```powershell
# Biên dịch mã nguồn và kiểm thử
.\mvnw.cmd test-compile

# Chạy toàn bộ Unit Test (không cần Docker)
.\mvnw.cmd test

# Chạy toàn bộ Unit Test + Integration Test với Testcontainers MySQL 8.0 (cần Docker Desktop)
.\mvnw.cmd verify

# Rà soát tuân thủ chuẩn v4 (10 bảng, 54 endpoint, 24 mã lỗi, không từ khóa cũ)
powershell -ExecutionPolicy Bypass -File .agents\skills\omnibid-v4-sync\scripts\verify-v4.ps1
```
