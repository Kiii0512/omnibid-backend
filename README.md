# OmniBid - Online Auction System (Backend)

Hệ thống Backend cho nền tảng đấu giá trực tuyến **OmniBid**, được xây dựng theo mô hình kiến trúc phân tầng nguyên khối (**Monolith Layered Architecture**) trên nền tảng **Spring Boot 3** và **Java 17**.

---

## 1. THÀNH VIÊN DỰ ÁN & PHÂN CÔNG NHIỆM VỤ (SCRUM)

| Thành viên | MSSV | Vai trò Scrum | Mảng kỹ thuật chính phụ trách |
| :--- | :---: | :--- | :--- |
| **Nguyễn Huy Hoàng** | 0011968 | Product Owner + Scrum Master | **Backend lõi:** Auction, Bid, khóa dòng (Pessimistic Lock), WebSocket STOMP, AuctionScheduler |
| **Nguyễn Hà Tuấn Anh** | 0001168 | Developer | **Frontend Next.js:** Toàn bộ giao diện Guest / Buyer / Seller / Admin (React + TypeScript + Tailwind CSS) |
| **Nguyễn Công Đương** | 0008968 | Developer | **Backend sau đấu giá:** Order, Payment, Shipment, Report, Admin; Database schema & kiểm thử |

* **Giảng viên hướng dẫn:** ThS. Phạm Hữu Tùng

---

## 2. NGUYÊN TẮC KIẾN TRÚC & PHÂN TẦNG (BẮT BUỘC TUÂN THỦ)

Hệ thống tuân thủ nghiêm ngặt mô hình **Monolith Layered Architecture** (Controller – Service – Repository) kết hợp với các dịch vụ hạ tầng bổ trợ. Toàn bộ các Service là module nghiệp vụ nội bộ chạy trong cùng một ứng dụng Spring Boot, **không tách Microservices**:

```text
HTTP Request (REST) / WebSocket (STOMP)
                    │
                    ▼
┌────────────────────────────────────────────────────────┐
│             1. Controller & Security Layer             │
│  - 13 REST Controllers                                 │
│  - Spring Security 6 + JWT Authentication Filter       │
│  - Bean Validation (@Valid, Request DTOs)              │
│  - Phân quyền RBAC(ROLE_BUYER, ROLE_SELLER, ROLE_ADMIN)│
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│             2. Business Service Layer                  │
│  - 13 Business Services                                │
│  - Concurrency Lock (Pessimistic Write Lock cho Bid)   │
│  - Schedulers (AuctionScheduler & OrderScheduler)      │
│  - WebSocket Publisher (STOMP Topic: /topic/auction/*) │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│             3. Data Access Layer (JPA)                 │
│  - 14 Spring Data JPA Repositories                     │
│  - 14 JPA Entities (Trùng khớp 14 bảng trong Database) │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
           MySQL 8.0+ Database (Aiven Cloud Managed)

```

### 5 Nguyên tắc vàng khi viết code:

1. **Phân tách tầng triệt để:** Controller **chỉ** gọi Service; Controller **tuyệt đối không** gọi trực tiếp Repository hoặc can thiệp Entity.
2. **Không trả Entity ra ngoài Controller:** Toàn bộ dữ liệu trao đổi với Client bắt buộc phải thông qua DTO (`...RequestDTO` và `...ResponseDTO`).
3. **Phản hồi chuẩn hóa (Unified API Response):** Mọi endpoint API đều phải trả về bọc qua `ApiResponse<T>`:
```json
{
  "success": true,
  "message": "Thông báo kết quả",
  "data": { ... },
  "timestamp": "2026-10-28T05:00:00"
}

```


4. **Kiểm tra dữ liệu đầu vào (Validation):** Sử dụng Bean Validation (`@NotNull`, `@NotBlank`, `@Min`, `@DecimalMin`, `@Pattern`) và khai báo `@Valid` tại các Controller method.
5. **Cập nhật trạng thái, không xóa vật lý (Soft Delete / Status change):**
* Khóa tài khoản: `users.status = BANNED`
* Ẩn/gỡ sản phẩm: `products.status = HIDDEN` hoặc `REMOVED`
* Lịch sử đặt giá (`bids`), hóa đơn (`orders`), vận đơn (`shipments`) được lưu trữ vĩnh viễn phục vụ kiểm toán và xử lý khiếu nại.



---

## 3. CẤU TRÚC THƯ MỤC PACKAGE

```text
src/main/java/com/omnibid/
 ├── common/               # ApiResponse, AppException, GlobalExceptionHandler, Constants
 ├── config/               # SecurityConfig, CorsConfig, WebSocketConfig, JpaConfig
 ├── controller/           # 13 REST API Controllers
 ├── dto/                  # Request & Response DTOs theo từng module (auth, auction, bid, order...)
 ├── entity/               # 14 JPA Entities khớp 14 bảng cơ sở dữ liệu
 ├── enums/                # Danh sách Enums trạng thái dùng chung trong hệ thống
 ├── repository/           # 14 Spring Data JPA Repositories
 ├── scheduler/            # AuctionScheduler & OrderScheduler (Xử lý tác vụ định kỳ)
 ├── security/             # JwtTokenProvider, JwtAuthenticationFilter, CustomUserDetails, UserDetailsServiceImpl
 └── service/              # Service Interfaces & Service Implementations (impl/)

```

---

## 4. QUY HOẠCH 14 ENTITY & 13 CONTROLLER / SERVICE

### 14 Bảng Database & Entity tương ứng:

1. `User` (`users`): Thông tin người dùng, mật khẩu băm BCrypt, vai trò (`BUYER`, `SELLER`, `ADMIN`), trạng thái (`ACTIVE`, `BANNED`).
2. `Category` (`categories`): Danh mục phân loại sản phẩm (tên, slug, icon).
3. `Product` (`products`): Sản phẩm đăng bán, tình trạng (`condition`), trạng thái (`AVAILABLE`, `IN_AUCTION`, `RESERVED`, `SOLD`, `HIDDEN`, `REMOVED`).
4. `ProductImage` (`product_images`): Ảnh sản phẩm (1 đến 5 ảnh, cờ `is_primary`).
5. `Auction` (`auctions`): Phiên đấu giá, giá khởi điểm, bước giá, giá sàn, giá hiện tại, người giữ giá cao nhất, số lượt bid, thời gian chốt.
6. `Bid` (`bids`): Lịch sử đặt giá, mức giá là duy nhất trong mỗi phiên, trạng thái (`LEADING`, `OUTBID`, `WON`, `NOT_WON`, `INVALIDATED`).
7. `Watchlist` (`watchlists`): Danh sách phiên đấu giá được người dùng lưu theo dõi.
8. `Order` (`orders`): Hóa đơn phiên kết thúc, hạn thanh toán 48h (`payment_deadline`), hạn tự động nhận hàng 7 ngày (`confirm_deadline`).
9. `Payment` (`payments`): Các lần thanh toán mô phỏng (tối đa 1 `PENDING` và 1 `SUCCESS` cho mỗi đơn).
10. `Shipment` (`shipments`): Vận đơn mô phỏng sau khi đơn hàng được thanh toán.
11. `ShipmentEvent` (`shipment_events`): Lịch sử cập nhật trạng thái vận đơn (`CREATED` → `PICKED_UP` → `IN_TRANSIT` → `DELIVERED`).
12. `Notification` (`notifications`): Thông báo hệ thống (`OUTBID`, `AUCTION_WON`, `PAYMENT_REMINDER`...).
13. `Report` (`reports`): Khiếu nại đơn hàng và báo cáo vi phạm gửi Admin giải quyết.
14. `AuditLog` (`audit_logs`): Nhật ký ghi vết các hành động quản trị hệ thống.

### 13 Controller & Service tương ứng:

1. `AuthController` / `AuthService`: Đăng ký, đăng nhập JWT, phân quyền tài khoản.
2. `UserController` / `UserService`: Quản lý hồ sơ cá nhân, đổi mật khẩu.
3. `CategoryController` / `CategoryService`: CRUD danh mục sản phẩm.
4. `ProductController` / `ProductService`: Đăng sản phẩm, tải ảnh, chỉnh sửa/ẩn sản phẩm.
5. `AuctionController` / `AuctionService`: Khởi tạo phiên, duyệt danh sách, tìm kiếm/lọc, hủy phiên.
6. `BidController` / `BidService`: Đặt giá thời gian thực, quản lý tranh chấp với khóa bi quan.
7. `WatchlistController` / `WatchlistService`: Quản lý danh sách theo dõi phiên.
8. `OrderController` / `OrderService`: Quản lý đơn hàng, người mua xác nhận nhận hàng, hủy đơn.
9. `PaymentController` / `PaymentService`: Giao dịch thanh toán mô phỏng đảm bảo tính bất biến (`idempotency_key`).
10. `ShipmentController` / `ShipmentService`: Tiến trình giao vận mô phỏng.
11. `NotificationController` / `NotificationService`: Đánh dấu đọc, phát thông báo WebSocket.
12. `ReportController` / `ReportService`: Tiếp nhận khiếu nại và xử lý báo cáo.
13. `AdminController` / `AdminService`: Bảng điều khiển quản trị, duyệt sản phẩm, khóa tài khoản, xem audit log.

---

## 5. QUY TẮC NGHIỆP VỤ CỐT LÕI (CORE BUSINESS RULES)

1. **Quy tắc Đặt giá (Bidding) & Xử lý đồng thời:**
* Phiên phải ở trạng thái `ACTIVE` và thời gian máy chủ còn hiệu lực (`now < end_time`).
* Người đặt giá phải có trạng thái tài khoản `ACTIVE`, không phải là chủ sở hữu sản phẩm/phiên, và không phải là người đang dẫn đầu (`LEADING`).
* Mức giá hợp lệ: `amount >= current_price + bid_increment` (hoặc `>= start_price` nếu phiên chưa có ai đặt).
* Bắt buộc xử lý đặt giá bên trong một Transaction có khóa dòng bi quan (`@Lock(LockModeType.PESSIMISTIC_WRITE)` qua `findByIdForUpdate`) để ngăn ngừa tranh chấp đồng thời khi nhiều người cùng đặt giá ở những giây cuối.


2. **Quy tắc Đóng phiên & Tạo đơn hàng (AuctionScheduler):**
* Khi hết giờ: Nếu phiên có bid hợp lệ và đạt giá sàn (`reserve_price`), phiên chuyển sang `ENDED`, Bid thắng chuyển `WON`, Sản phẩm chuyển `RESERVED`, và hệ thống tự động sinh 1 đơn hàng `Order` ở trạng thái `PENDING_PAYMENT`.
* Ngược lại (không có bid hoặc không đạt giá sàn), phiên chuyển `UNSOLD`, Bid cao nhất chuyển `NOT_WON`, Sản phẩm trả về `AVAILABLE`.
* Hạn chốt thanh toán: `payment_deadline = closed_at + 48 giờ`. Quá hạn mà không có thanh toán thành công, `Order` chuyển `EXPIRED`, Sản phẩm trả lại `AVAILABLE` (không tự động trao giải cho người về nhì).


3. **Quy tắc Thanh toán & Giao vận mô phỏng:**
* Mỗi lần bấm thanh toán tạo 1 bản ghi `Payment` với `idempotency_key` chống thanh toán lặp.
* Khi thanh toán `SUCCESS`, đơn hàng chuyển sang `PAID` và tự động sinh 1 vận đơn `Shipment` ở trạng thái `CREATED`.
* Trạng thái vận đơn chỉ cập nhật đơn chiều tuần tự: `CREATED` → `PICKED_UP` → `IN_TRANSIT` → `DELIVERED`.
* Sau khi giao hàng thành công (`DELIVERED`), hệ thống tính `confirm_deadline = delivered_at + 7 ngày`. Nếu người mua xác nhận nhận hàng hoặc quá 7 ngày không có khiếu nại `PENDING`, đơn hàng chuyển `COMPLETED` và sản phẩm chuyển `SOLD`.



---

## 6. HƯỚNG DẪN MÔI TRƯỜNG & KHỞI CHẠY (LOCAL SETUP)

1. **Yêu cầu môi trường:**
* **Java SDK:** OpenJDK 17.
* **Build Tool:** Maven Wrapper (`.\mvnw.cmd` trên Windows hoặc `./mvnw` trên Linux/macOS).
* **Cơ sở dữ liệu:** MySQL 8.0+ (kết nối trực tiếp máy chủ Aiven Cloud qua SSL).


2. **Cấu hình kết nối (`src/main/resources/application.properties`):**
```properties
spring.application.name=omnibid-backend
server.port=8080

spring.datasource.url=jdbc:mysql://[omnibid-mysql-omnibid-project.b.aivencloud.com:14985/defaultdb?sslMode=REQUIRED&trustServerCertificate=true&serverTimezone=Asia/Ho_Chi_Minh](https://omnibid-mysql-omnibid-project.b.aivencloud.com:14985/defaultdb?sslMode=REQUIRED&trustServerCertificate=true&serverTimezone=Asia/Ho_Chi_Minh)
spring.datasource.username=avnadmin
spring.datasource.password=${DB_PASSWORD:AVNS_6_q67mKUt5swx77piEW}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect
spring.jpa.hibernate.ddl-auto=none
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
jwt.expiration-ms=86400000

```


3. **Lệnh biên dịch kiểm tra:**
```powershell
.\mvnw.cmd test-compile

```


4. **Lệnh khởi chạy ứng dụng:**
```powershell
.\mvnw.cmd spring-boot:run

```


5. **Cổng kiểm thử API:** `http://localhost:8080`.


5. **Cổng kiểm thử API:** `http://localhost:8080`.

