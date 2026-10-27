## 1. THÀNH VIÊN DỰ ÁN & VAI TRÒ
- **Nguyễn Huy Hoàng (Leader / Full-stack Developer)** - MSSV: 0011968
- **Nguyễn Hà Tuấn Anh (Developer)** - MSSV: 0001168
- **Nguyễn Công Đương (Developer)** - MSSV: 0008968
- **Giảng viên hướng dẫn:** ThS. Phạm Hữu Tùng

---

## 2. NGUYÊN TẮC KIẾN TRÚC & PHÂN TẦNG (BẮT BUỘC TUÂN THỦ)

Hệ thống tuân thủ nghiêm ngặt mô hình **Monolith Layered Architecture** (Controller – Service – Repository) kết hợp với các dịch vụ hạ tầng bổ trợ. Các Service là các module nghiệp vụ nội bộ trong cùng ứng dụng Spring Boot, **không chia microservices**:

HTTP Request / WebSocket
          │
          ▼
┌────────────────────────────────────────┐
│      1. Controller & Security Layer    │
│  - REST Controllers (13 Controllers)   │
│  - Spring Security + JWT Auth Filter   │
│  - Bean Validation (@Valid, DTOs)      │
│  - Phân quyền RBAC (BUYER/SELLER/ADMIN)│
└───────────────────┬────────────────────┘
                    │
                    ▼
┌────────────────────────────────────────┐
│      2. Business Service Layer         │
│  - 13 Business Services                │
│  - Concurrency Lock (Đặt giá đồng thời)│
│  - Schedulers (Auction & Order Cron)   │
│  - WebSocket Publisher (STOMP)         │
└───────────────────┬────────────────────┘
                    │
                    ▼
┌────────────────────────────────────────┐
│      3. Data Access Layer (JPA)        │
│  - 14 Spring Data JPA Repositories     │
│  - 14 Entities (Trùng khớp 14 bảng DB) │
└───────────────────┬────────────────────┘
                    │
                    ▼
       MySQL 8.0+ Database (Aiven Cloud)

```

 5 Nguyên tắc vàng khi viết code:

1. **Phân tách tầng triệt để:** Controller **chỉ** gọi Service; Controller **không bao giờ** được gọi trực tiếp Repository hoặc can thiệp Entity.
2. **Không trả Entity ra ngoài Controller:** Toàn bộ dữ liệu giao tiếp với Client phải sử dụng DTO (`...RequestDTO` và `...ResponseDTO`).
3. **Phản hồi chuẩn hóa (Unified API Response):** Mọi endpoint API đều phải trả về định dạng `ApiResponse<T>`:

{
  "success": true,
  "message": "Thông báo kết quả",
  "data": { ... },
  "timestamp": "2026-10-28T04:30:00"
}

4. **Kiểm tra dữ liệu đầu vào (Validation):** Sử dụng Bean Validation (`@NotNull`, `@NotBlank`, `@Min`, `@Pattern`, v.v.) và khai báo `@Valid` tại các Controller.
5. **Cập nhật trạng thái, không xóa vật lý (Soft Delete / Status change):**
* Khóa tài khoản: `users.status = BANNED`
* Ẩn/gỡ sản phẩm: `products.status = HIDDEN` hoặc `REMOVED`
* Giữ nguyên lịch sử đặt giá, hóa đơn và vận đơn phục vụ đối soát và báo cáo vi phạm.

---
 ## 3. CẤU TRÚC THƯ MỤC PACKAGE


src/main/java/com/omnibid/
 ├── common/               # ApiResponse, BaseEntity, GlobalExceptionHandler, ErrorCode
 ├── config/               # SecurityConfig, CorsConfig, WebSocketConfig, JpaConfig
 ├── controller/           # 13 REST API Controllers
 ├── dto/                  # Request & Response DTOs theo module
 ├── entity/               # 14 JPA Entities khớp 14 bảng cơ sở dữ liệu
 ├── enums/                # Danh sách Enums trạng thái dùng chung
 ├── repository/           # 14 Spring Data JPA Repositories
 ├── scheduler/            # AuctionScheduler & OrderScheduler (Xử lý định kỳ)
 ├── security/             # JwtProvider, JwtAuthFilter, UserDetailsServiceImpl
 └── service/              # Service Interfaces & Service Implementations


## 4. QUY HOẠCH 14 ENTITY & 13 CONTROLLER / SERVICE

### 14 Bảng Database & Entity tương ứng:

1. `User` (`users`): Thông tin người dùng, mật khẩu băm, phân quyền (`BUYER`, `SELLER`, `ADMIN`), trạng thái (`ACTIVE`, `BANNED`).
2. `Category` (`categories`): Phân loại sản phẩm (tên, slug, icon).
3. `Product` (`products`): Sản phẩm bán, tình trạng hàng, trạng thái (`AVAILABLE`, `IN_AUCTION`, `RESERVED`, `SOLD`, `HIDDEN`, `REMOVED`).
4. `ProductImage` (`product_images`): Ảnh sản phẩm (1 đến 5 ảnh, cờ `is_primary`).
5. `Auction` (`auctions`): Phiên đấu giá, giá khởi điểm, bước giá, giá sàn, giá hiện tại, người giữ giá cao nhất, số lượt bid, thời gian chốt.
6. `Bid` (`bids`): Lịch sử đặt giá, mức giá là duy nhất trong mỗi phiên, trạng thái (`LEADING`, `OUTBID`, `WON`, `NOT_WON`, `INVALIDATED`).
7. `Watchlist` (`watchlists`): Danh sách phiên đấu giá được người dùng quan tâm theo dõi.
8. `Order` (`orders`): Hóa đơn người thắng phiên, hạn thanh toán 48h (`payment_deadline`), hạn tự động nhận hàng 7 ngày (`confirm_deadline`).
9. `Payment` (`payments`): Các lần thanh toán mô phỏng (tối đa 1 `PENDING` và 1 `SUCCESS` cho mỗi đơn).
10. `Shipment` (`shipments`): Vận đơn mô phỏng sau khi thanh toán thành công.
11. `ShipmentEvent` (`shipment_events`): Lịch sử cập nhật trạng thái vận đơn (`CREATED` → `PICKED_UP` → `IN_TRANSIT` → `DELIVERED`).
12. `Notification` (`notifications`): Thông báo người dùng (`OUTBID`, `AUCTION_WON`, `PAYMENT_REMINDER`, v.v.).
13. `Report` (`reports`): Khiếu nại đơn hàng và báo cáo vi phạm gửi Admin giải quyết.
14. `AuditLog` (`audit_logs`): Nhật ký ghi vết các hành động quản trị hệ thống.

### 13 Controller & Service tương ứng:

1. `AuthController` / `AuthService`: Đăng ký, đăng nhập JWT, giải mã token.
2. `UserController` / `UserService`: Quản lý hồ sơ, đổi mật khẩu cá nhân.
3. `CategoryController` / `CategoryService`: CRUD danh mục sản phẩm.
4. `ProductController` / `ProductService`: Đăng sản phẩm, sửa/xóa/ẩn sản phẩm.
5. `AuctionController` / `AuctionService`: Khởi tạo phiên, duyệt danh sách, tìm kiếm, hủy phiên.
6. `BidController` / `BidService`: Đặt giá thời gian thực, quản lý tranh chấp bằng khóa dòng phiên.
7. `WatchlistController` / `WatchlistService`: Thêm/xóa danh sách theo dõi.
8. `OrderController` / `OrderService`: Quản lý đơn hàng, người mua xác nhận nhận hàng, hủy đơn.
9. `PaymentController` / `PaymentService`: Giao dịch thanh toán mô phỏng đảm bảo tính bất biến (idempotency).
10. `ShipmentController` / `ShipmentService`: Tiến trình giao vận mô phỏng.
11. `NotificationController` / `NotificationService`: Đánh dấu đọc, phát thông báo WebSocket.
12. `ReportController` / `ReportService`: Tiếp nhận khiếu nại và xử lý báo cáo.
13. `AdminController` / `AdminService`: Bảng điều khiển quản trị, duyệt sản phẩm, khóa tài khoản, xem audit log.


## 5. QUY TẮC NGHIỆP VỤ CỐT LÕI (CORE BUSINESS RULES)

1. **Quy tắc đặt giá (Bidding):**
* Phiên phải ở trạng thái `ACTIVE` và thời gian máy chủ còn hợp lệ (`server_time < end_time`).
* Người đặt giá phải có tài khoản `ACTIVE`, không phải là chủ sở hữu sản phẩm/phiên, và không phải là người đang dẫn đầu (`LEADING`).
* Mức giá hợp lệ: `amount >= current_price + bid_increment` (hoặc `>= start_price` nếu phiên chưa có lượt đặt nào).
* Bắt buộc xử lý đặt giá bên trong một Transaction có khóa dòng bi quan (`Pessimistic Lock`) để ngăn chặn tình trạng race condition khi nhiều người cùng đặt giá ở mili-giây cuối.


2. **Quy tắc đóng phiên & Tạo đơn:**
* Khi hết giờ: Nếu phiên có bid hợp lệ và đạt giá sàn (`reserve_price`), phiên chuyển sang `ENDED`, Bid thắng chuyển `WON`, Sản phẩm chuyển `RESERVED`, và hệ thống tự động sinh 1 đơn hàng `Order` ở trạng thái `PENDING_PAYMENT`.
* Ngược lại (không có bid hoặc không đạt giá sàn), phiên chuyển `UNSOLD`, Bid cao nhất chuyển `NOT_WON`, Sản phẩm trả về `AVAILABLE`.
* Hạn chốt thanh toán: `payment_deadline = closed_at + 48 giờ`. Quá hạn mà không có giao dịch PENDING thì `Order` chuyển `EXPIRED`, Sản phẩm trả lại `AVAILABLE` (không trao giải cho người về nhì).


3. **Quy tắc thanh toán & Vận chuyển mô phỏng:**
* Mỗi lần bấm thanh toán sinh 1 `idempotency_key` để chống giao dịch trùng lặp.
* Khi thanh toán `SUCCESS`, đơn hàng chuyển sang `PAID` và tự động sinh 1 vận đơn `Shipment` ở trạng thái `CREATED`.
* Trạng thái vận đơn chỉ chuyển đơn chiều tuần tự: `CREATED` → `PICKED_UP` → `IN_TRANSIT` → `DELIVERED`.
* Sau khi giao hàng thành công (`DELIVERED`), hệ thống tính `confirm_deadline = delivered_at + 7 ngày`. Nếu người mua xác nhận hoặc quá 7 ngày không có khiếu nại `PENDING`, đơn hàng chuyển `COMPLETED` và sản phẩm chuyển `SOLD`.



---

## 6. HƯỚNG DẪN MÔI TRƯỜNG & KHỞI CHẠY (LOCAL SETUP)

1. **Yêu cầu môi trường:**
* **Java SDK:** OpenJDK 17.
* **Build Tool:** Maven (sử dụng sẵn file `mvnw` đi kèm dự án).
* **Cơ sở dữ liệu:** MySQL 8.0+ (kết nối trực tiếp máy chủ Aiven Cloud qua cổng SSL 14985).


2. **Cấu hình kết nối (`src/main/resources/application.properties`):**

spring.application.name=omnibid-backend
server.port=8080

spring.datasource.url=jdbc:mysql://[omnibid-mysql-omnibid-project.b.aivencloud.com:14985/defaultdb?sslMode=REQUIRED&trustServerCertificate=true&serverTimezone=Asia/Ho_Chi_Minh](https://omnibid-mysql-omnibid-project.b.aivencloud.com:14985/defaultdb?sslMode=REQUIRED&trustServerCertificate=true&serverTimezone=Asia/Ho_Chi_Minh)
spring.datasource.username=avnadmin
spring.datasource.password=AVNS_6_q67mKUt5swx77piEW
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect
spring.jpa.hibernate.ddl-auto=none
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

```


3. **Lệnh chạy ứng dụng:**

.\mvnw.cmd clean spring-boot:run

```


4. **Cổng kiểm thử:** `http://localhost:8080`.
'@ -Encoding utf8

```
