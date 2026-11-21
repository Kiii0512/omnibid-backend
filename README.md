# OmniBid - Online Auction System (Backend v4)

Hệ thống Backend cho nền tảng đấu giá trực tuyến **OmniBid**, được xây dựng theo mô hình kiến trúc phân tầng nguyên khối (**Monolith Layered Architecture**) trên nền tảng **Spring Boot 4.0.9**, **Java 17** và cơ sở dữ liệu **MySQL 8.0+** được quản lý hoàn toàn bởi **Flyway Migration**.

Hệ thống tuân thủ 100% bộ đặc tả tài liệu v4 chính thức (`01_Proposal_Omnibid_v4`, `02_KhaoSat_Omnibid_v4`, `03_PhanTich_Omnibid_v4`, `04_ThietKe_Omnibid_v4`).

---

## 1. THÀNH VIÊN DỰ ÁN & PHÂN CÔNG NHIỆM VỤ (SCRUM)

| Thành viên | MSSV | Vai trò Scrum | Mảng kỹ thuật chính phụ trách |
| :--- | :---: | :--- | :--- |
| **Nguyễn Huy Hoàng** | 0011968 | Product Owner + Scrum Master | **Backend lõi:** Auction, Bid, khóa dòng bi quan (`PESSIMISTIC_WRITE`), WebSocket STOMP (`/ws`, `/topic/auctions/{id}`), AuctionScheduler |
| **Nguyễn Hà Tuấn Anh** | 0001168 | Developer | **Frontend Next.js:** Toàn bộ giao diện Guest / Buyer / Seller / Admin (React + TypeScript + Tailwind CSS) |
| **Nguyễn Công Đương** | 0008968 | Developer | **Backend sau đấu giá:** Order, Payment mô phỏng (Idempotency-Key), Giao vận, Notification sau COMMIT, Báo cáo vi phạm, Admin APIs, Kiểm thử & Database schema |

* **Giảng viên hướng dẫn:** TS. Phạm Hữu Tùng

---

## 2. NGUYÊN TẮC KIẾN TRÚC & PHÂN TẦNG

Hệ thống tuân thủ nghiêm ngặt mô hình **Monolith Layered Architecture** 3 tầng (Controller – Service – Repository/Entity):

```text
HTTP Request (REST) / WebSocket STOMP
                    │
                    ▼
┌────────────────────────────────────────────────────────┐
│             1. Controller & Security Layer             │
│  - 54 REST Endpoints chuẩn v4                          │
│  - Spring Security + JWT Filter (claim: ver/version)   │
│  - Jakarta Bean Validation (@Valid, Request DTOs)      │
│  - Phân quyền RBAC (ROLE_USER, ROLE_ADMIN)             │
│  - Giới hạn tần suất đăng nhập sai (429 Rate Limit)   │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│             2. Business Service Layer                  │
│  - Business Services & Domain State Transitions        │
│  - Khóa dòng bi quan theo thứ tự: auctions→orders→products│
│  - Đóng phiên định kỳ (AuctionScheduler & AuctionCloser)│
│  - Hết hạn & Tự hoàn tất đơn (OrderScheduler)         │
│  - Thông báo & WebSocket phát STRICTLY sau COMMIT      │
│  - Lưu trữ ảnh Cloudinary với kiểm tra Magic Bytes     │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│             3. Data Access Layer (JPA + Flyway)        │
│  - 10 Spring Data JPA Repositories                     │
│  - 10 JPA Entities khớp 10 bảng v4 trong Database      │
│  - Tiền tệ: BigDecimal ↔ DECIMAL(15,0)                │
│  - Thời gian: Instant ↔ DATETIME(6) UTC               │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
         MySQL 8.0+ Database (InnoDB, UTF8mb4, UTC)
```

### 5 Nguyên tắc vàng:
1. **Phân tách tầng triệt để:** Controller chỉ inject Service. Controller không bao giờ gọi Repository và không bao giờ trả Entity ra ngoài.
2. **Không xóa cứng dữ liệu nghiệp vụ:** Xử lý bằng chuyển trạng thái (`User.status = BANNED`, `Product.status = HIDDEN`, `Auction.status = CANCELLED`, `Order.status = CANCELLED`). Dữ liệu lịch sử (`bids`, `payments`, `reports`) lưu vĩnh viễn.
3. **Phản hồi chuẩn hóa (Unified API Response v4):**
   - Thành công:
     ```json
     { "success": true, "data": { ... } }
     ```
   - Lỗi:
     ```json
     {
       "success": false,
       "error": {
         "code": "AUCTION_NOT_ACTIVE",
         "message": "Phiên đấu giá không ở trạng thái hoạt động",
         "details": null
       }
     }
     ```
4. **Khóa bi quan chống tranh chấp:** Thứ tự khóa cố định **auctions → orders → products** (cùng bảng khóa theo `id` tăng dần). Không khóa bảng `users`. Chờ khóa tối đa 5 giây (quá hạn hoặc deadlock trả HTTP 409 `CONFLICT`).
5. **Thời gian & Tiền tệ chuẩn:** Toàn bộ lưu trữ UTC trong DB và xử lý bằng `java.time.Instant`. JSON trả về định dạng ISO-8601 với múi giờ `Asia/Ho_Chi_Minh` (`+07:00`). Tiền tệ lưu `DECIMAL(15,0)` làm tròn scale 0.

---

## 3. CẤU TRÚC THƯ MỤC PACKAGE

```text
src/main/java/com/omnibid/
 ├── common/               # ApiResponse, ErrorCode (24 mã), BusinessException, GlobalExceptionHandler, ImageValidator, PageResponse
 ├── config/               # SecurityConfig, WebSocketConfig, DateTimeConfig, AppProperties (.properties only)
 ├── controller/           # 54 REST API Controllers (kèm gói con controller/admin/)
 ├── dto/                  # Request & Response Records theo từng module (auth, user, product, auction, bid, order, payment, notification, report, admin)
 ├── entity/               # 10 JPA Entities khớp 10 bảng v4
 ├── enums/                # Enums nghiệp vụ (UserRole, UserStatus, ProductCondition, ProductStatus, AuctionStatus, OrderStatus, PaymentStatus...)
 ├── gateway/              # MockPaymentGateway mô phỏng cổng thanh toán
 ├── repository/           # 10 Spring Data JPA Repositories (với findByIdForUpdate khóa dòng)
 ├── scheduler/            # AuctionScheduler & OrderScheduler (chạy nền định kỳ)
 ├── security/             # JwtTokenProvider (claim ver), JwtAuthenticationFilter, CustomUserDetails, LoginRateLimiter
 └── service/              # Service Interfaces, Implementations, AuctionCloser, WebSocketPublisher, ImageStorageService
```

---

## 4. QUY HOẠCH 10 BẢNG DATABASE & ENTITY (v4 SPEC)

Schema được khởi tạo và kiểm soát 100% bằng Flyway Migration:
- `db/migration/V1__init_schema.sql`: Khởi tạo đúng 10 bảng chuẩn.
- `db/migration/V2__seed_data.sql`: Dữ liệu seed mặc định.

| STT | Bảng Database | Entity JPA | Mô tả chức năng |
| :---: | :--- | :--- | :--- |
| 1 | `users` | `User` | Tài khoản, vai trò (`USER`, `ADMIN`), trạng thái (`ACTIVE`, `BANNED`), cơ chế `token_version` thu hồi JWT |
| 2 | `categories` | `Category` | Danh mục sản phẩm (tên, slug duy nhất, icon Lucide React) |
| 3 | `products` | `Product` | Sản phẩm đăng bán, tình trạng (`NEW`, `LIKE_NEW`, `GOOD`, `FAIR`), trạng thái (`AVAILABLE`, `IN_AUCTION`, `SOLD`, `HIDDEN`) |
| 4 | `product_images` | `ProductImage` | Ảnh sản phẩm (1–5 ảnh, cờ `is_primary`, lưu Cloudinary / local fallback) |
| 5 | `auctions` | `Auction` | Phiên đấu giá, giá khởi điểm, bước giá, giá sàn, giá hiện tại, người giữ giá cao nhất, số lượt bid, trạng thái (`SCHEDULED`, `ACTIVE`, `ENDED`, `UNSOLD`, `CANCELLED`) |
| 6 | `bids` | `Bid` | Lịch sử đặt giá không trạng thái (stateless), mức giá duy nhất trong phiên (`uq_bids_auction_amount`) |
| 7 | `orders` | `Order` | Đơn hàng sinh ra khi phiên `ENDED`, hạn thanh toán 48h (`payment_deadline`), hạn xác nhận nhận hàng 7 ngày (`confirm_deadline`), trạng thái (`PENDING_PAYMENT`, `PAID`, `SHIPPING`, `COMPLETED`, `CANCELLED`) |
| 8 | `payments` | `Payment` | Bản ghi thanh toán mô phỏng đồng bộ có `Idempotency-Key` UUID, trạng thái (`PENDING`, `SUCCESS`, `FAILED`) |
| 9 | `notifications` | `Notification` | 5 loại thông báo hệ thống (`OUTBID`, `AUCTION_RESULT`, `AUCTION_CANCELLED`, `ORDER_UPDATE`, `REPORT_RESOLVED`) |
| 10 | `reports` | `Report` | Báo cáo vi phạm (sản phẩm, người dùng, hoặc đơn hàng), trạng thái (`PENDING`, `RESOLVED`, `REJECTED`) |

---

## 5. DANH SÁCH 54 REST ENDPOINTS & WEBSOCKET

Toàn bộ 54 endpoint REST dưới `/api/v1` cùng kênh WebSocket `/ws`:

### 5.1. Xác thực & Hồ sơ cá nhân (8 endpoints)
- `POST /api/v1/auth/register`: Đăng ký tài khoản mới (`USER`, `ACTIVE`).
- `POST /api/v1/auth/login`: Đăng nhập, nhận JWT access token (claim `ver`).
- `POST /api/v1/auth/logout`: Đăng xuất, tăng `token_version` thu hồi token.
- `GET /api/v1/users/me`: Xem hồ sơ cá nhân của người dùng hiện tại.
- `PATCH /api/v1/users/me`: Cập nhật thông tin cá nhân (họ tên, số điện thoại, địa chỉ).
- `PATCH /api/v1/users/me/password`: Đổi mật khẩu, tăng `token_version` đăng xuất các thiết bị khác.
- `GET /api/v1/users/me/profile`: Xem hồ sơ chi tiết kèm số liệu thống kê cá nhân.
- `GET /api/v1/users/{id}`: Xem thông tin công khai của người dùng.

### 5.2. Danh mục sản phẩm (2 endpoints)
- `GET /api/v1/categories`: Lấy danh sách danh mục (công khai).
- `GET /api/v1/categories/{id}`: Lấy chi tiết danh mục theo ID.

### 5.3. Sản phẩm (6 endpoints)
- `POST /api/v1/products`: Tạo sản phẩm mới kèm 1–5 ảnh (multipart/form-data, magic bytes JPEG/PNG/WEBP).
- `GET /api/v1/products/mine`: Danh sách sản phẩm của tôi (phân trang, lọc theo trạng thái).
- `GET /api/v1/products/{id}`: Xem chi tiết sản phẩm của tôi.
- `PATCH /api/v1/products/{id}`: Chỉnh sửa thông tin sản phẩm (chỉ khi `AVAILABLE`).
- `DELETE /api/v1/products/{id}`: Xóa sản phẩm (chỉ khi chưa từng lên phiên).
- `POST /api/v1/products/{id}/images`: Thêm ảnh cho sản phẩm (tổng không quá 5 ảnh).
- `DELETE /api/v1/products/{id}/images/{imageId}`: Xóa một ảnh của sản phẩm.

### 5.4. Phiên đấu giá & Đặt giá (8 endpoints)
- `POST /api/v1/auctions`: Tạo phiên đấu giá cho sản phẩm (`SCHEDULED`).
- `GET /api/v1/auctions`: Tìm kiếm, lọc phiên đấu giá công khai (phân trang, lọc theo danh mục, trạng thái, giá, từ khóa).
- `GET /api/v1/auctions/mine`: Danh sách phiên đấu giá do tôi tạo.
- `GET /api/v1/auctions/{id}`: Xem chi tiết phiên đấu giá.
- `PATCH /api/v1/auctions/{id}`: Sửa phiên đấu giá (chỉ khi `SCHEDULED`).
- `POST /api/v1/auctions/{id}/cancel`: Người bán hủy phiên (chỉ khi chưa có ai đặt giá).
- `GET /api/v1/auctions/{id}/bids`: Lấy lịch sử đặt giá của phiên (mã hóa danh tính bidder dạng `N***n`).
- `POST /api/v1/auctions/{id}/bids`: Đặt giá phiên đấu giá (khóa dòng bi quan, kiểm tra bước giá, chặn người bán & người đang dẫn đầu).
- `GET /api/v1/bids/mine`: Lịch sử các lượt đặt giá của tôi.

### 5.5. Đơn hàng & Thanh toán (6 endpoints)
- `GET /api/v1/orders`: Danh sách đơn hàng của tôi (vai trò BUYER hoặc SELLER).
- `GET /api/v1/orders/{id}`: Xem chi tiết đơn hàng (chỉ buyer hoặc seller của đơn).
- `PATCH /api/v1/orders/{id}/shipping-info`: Người mua cập nhật địa chỉ và SĐT nhận hàng.
- `POST /api/v1/orders/{id}/payments`: Người mua thực hiện thanh toán mô phỏng (header `Idempotency-Key` UUID).
- `GET /api/v1/orders/{id}/payments`: Lịch sử các lần thanh toán của đơn hàng.
- `POST /api/v1/orders/{id}/ship`: Người bán xác nhận đã gửi hàng kèm mã vận đơn `trackingCode` (`PAID` → `SHIPPING`).
- `POST /api/v1/orders/{id}/confirm-receipt`: Người mua xác nhận đã nhận hàng (`SHIPPING` → `COMPLETED`, sản phẩm chuyển `SOLD`).

### 5.6. Thông báo & Báo cáo vi phạm (4 endpoints)
- `GET /api/v1/notifications`: Danh sách thông báo của tôi (phân trang, lọc `isRead`, `type`).
- `POST /api/v1/notifications/{id}/read`: Đánh dấu thông báo đã đọc.
- `POST /api/v1/reports`: Gửi báo cáo vi phạm sản phẩm, người dùng hoặc đơn hàng.
- `GET /api/v1/admin/reports`: Admin xem danh sách báo cáo vi phạm.
- `GET /api/v1/admin/reports/{id}`: Admin xem chi tiết báo cáo vi phạm.
- `POST /api/v1/admin/reports/{id}/resolve`: Admin giải quyết báo cáo (`decision`: `RESOLVED` hoặc `REJECTED` kèm ghi chú `note`).

### 5.7. Quản trị hệ thống (Admin) (14 endpoints)
- `GET /api/v1/admin/users`: Danh sách người dùng hệ thống.
- `POST /api/v1/admin/users/{id}/ban`: Khóa người dùng, tự động hủy các phiên mở & đơn pending liên quan (BR-17).
- `POST /api/v1/admin/users/{id}/unban`: Mở khóa tài khoản người dùng.
- `GET /api/v1/admin/products`: Danh sách sản phẩm toàn hệ thống.
- `GET /api/v1/admin/products/{id}`: Xem chi tiết sản phẩm bất kỳ.
- `POST /api/v1/admin/products/{id}/hide`: Ẩn sản phẩm vi phạm, hủy các phiên mở & đơn pending liên quan (BR-18).
- `GET /api/v1/admin/auctions`: Danh sách phiên đấu giá toàn hệ thống.
- `GET /api/v1/admin/auctions/{id}`: Chi tiết phiên đấu giá bất kỳ.
- `POST /api/v1/admin/auctions/{id}/cancel`: Admin hủy phiên đấu giá vi phạm.
- `GET /api/v1/admin/orders`: Danh sách đơn hàng toàn hệ thống.
- `GET /api/v1/admin/orders/{id}`: Chi tiết đơn hàng bất kỳ kèm lịch sử thanh toán.
- `POST /api/v1/admin/orders/{id}/cancel`: Admin hủy đơn hàng PENDING_PAYMENT.
- `POST /api/v1/admin/categories`: Tạo danh mục mới (tự sinh slug tiếng Việt sạch).
- `PUT /api/v1/admin/categories/{id}`: Sửa danh mục.
- `DELETE /api/v1/admin/categories/{id}`: Xóa danh mục (chặn nếu đang có sản phẩm: 409 `CATEGORY_IN_USE`).
- `GET /api/v1/admin/statistics`: Báo cáo thống kê lũy kế toàn hệ thống.

### 5.8. Kênh WebSocket thời gian thực (Bảng 4.8 & 4.18)
- **Điểm kết nối STOMP:** `/ws` (xác thực token qua header `Authorization` khi CONNECT).
- **Kênh lắng nghe sự kiện phiên:** `/topic/auctions/{id}` (chú ý `auctions` số nhiều, chỉ cho phép SUBSCRIBE).
- **Thông điệp `AuctionEvent`:** Gồm `type` (`BID_PLACED`, `AUCTION_UPDATED`, `AUCTION_STARTED`, `AUCTION_ENDED`, `AUCTION_CANCELLED`), `currentPrice`, `minNextBid`, `bidCount`, `highestBidderName`, `reserveStatus`... phát STRICTLY sau COMMIT giao dịch.

---

## 6. TÀI KHOẢN MẪU KHỞI TẠO (SEED DATA)

Dữ liệu khởi tạo từ `db/migration/V2__seed_data.sql`:

| Vai trò | Email đăng nhập | Mật khẩu mặc định | Ghi chú |
| :--- | :--- | :--- | :--- |
| **Quản trị viên (ADMIN)** | `admin@omnibid.local` | `Admin@123456` | Bắt buộc đổi mật khẩu sau lần đăng nhập đầu tiên (`PATCH /api/v1/users/me/password`) |

### Danh mục mẫu ban đầu (6 danh mục):
1. **Đồng hồ** (`dong-ho`, icon: `watch`)
2. **Máy ảnh** (`may-anh`, icon: `camera`)
3. **Điện thoại** (`dien-thoai`, icon: `smartphone`)
4. **Sách** (`sach`, icon: `book-open`)
5. **Đồ sưu tầm** (`do-suu-tam`, icon: `gem`)
6. **Thời trang** (`thoi-trang`, icon: `shirt`)

---

## 7. HƯỚNG DẪN MÔI TRƯỜNG & KHỞI CHẠY (LOCAL SETUP)

### 7.1. Yêu cầu môi trường:
- **Java SDK:** OpenJDK 17.
- **Build Tool:** Maven Wrapper (`.\mvnw.cmd` trên Windows hoặc `./mvnw` trên Linux/macOS).
- **Cơ sở dữ liệu:** MySQL 8.0+ (InnoDB, UTF8mb4).

### 7.2. Cấu hình biến môi trường:
Chép mẫu `src/main/resources/application-example.properties` sang `src/main/resources/application.properties` (hoặc tạo `application-local.properties` đã nằm trong `.gitignore`):
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/omnibid_v4?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true
spring.datasource.username=root
spring.datasource.password=YourPassword
jwt.secret=Your256BitSecretKeyHereMustBeAtLeast32BytesLong!
app.cors.allowed-origins=http://localhost:3000
```

### 7.3. Các lệnh kiểm tra và thực thi:
- **Biên dịch mã nguồn:**
  ```powershell
  .\mvnw.cmd -q compile
  ```
- **Biên dịch bộ kiểm thử:**
  ```powershell
  .\mvnw.cmd -q test-compile
  ```
- **Chạy toàn bộ 124 Unit Tests:**
  ```powershell
  .\mvnw.cmd test
  ```
- **Rà soát chuẩn tài liệu v4:**
  ```powershell
  powershell -ExecutionPolicy Bypass -File .agents\skills\omnibid-v4-sync\scripts\verify-v4.ps1
  ```
- **Khởi chạy ứng dụng:**
  ```powershell
  .\mvnw.cmd spring-boot:run
  ```
- **Khởi chạy ứng dụng với cấu hình Demo (rút ngắn thời gian phiên/đơn để test nhanh):**
  ```powershell
  .\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=demo
  ```
- **Cổng API:** `http://localhost:8080`
- **Kênh WebSocket:** `ws://localhost:8080/ws`
