# DB_SPEC_v4 — Cơ sở dữ liệu (trích 04_ThietKe_Omnibid_v4, Chương 3 & Phụ lục A)

Script chuẩn: `sql/V1__init_schema.sql`, `sql/V2__seed_data.sql` (trích nguyên văn Phụ lục A). Sao chép **nguyên văn** sang
`src/main/resources/db/migration/` — không tự ý sửa tên cột, kiểu, ràng buộc. Nếu chạy Flyway trên MySQL thật mà script lỗi,
sửa tối thiểu và ghi rõ vào `docs/v4/PROGRESS.md` (đây là việc nhóm được giao kiểm chứng, CHANGELOG mục b).

## 1. 10 bảng (và chỉ 10 bảng)
`users, categories, products, product_images, auctions, bids, orders, payments, notifications, reports`

Bảng phải **bỏ** so với mã hiện tại: `watchlists`, `shipments`, `shipment_events`, `audit_logs`.

## 2. Quy ước kiểu (mục 3.4)
| DB | Java | Ghi chú |
|---|---|---|
| BIGINT | Long | mọi PK/FK; entity hiện dùng FK dạng `Long xxxId` — **giữ nguyên cách này**, không cần đổi sang `@ManyToOne` |
| DECIMAL(15,0) | BigDecimal scale 0 | `@Column(precision = 15, scale = 0)`; DTO/JSON là số nguyên VND; Bean Validation `@Digits(integer = 15, fraction = 0)` + `@Positive` |
| DATETIME(3) | `java.time.Instant` | Lưu UTC. Bỏ hoàn toàn `LocalDateTime` khỏi entity/service/DTO |
| ENUM | Java enum `@Enumerated(EnumType.STRING)` | giá trị trùng khớp từng ký tự với DB |
| BOOLEAN | Boolean/boolean | |

UTC đồng bộ ba lớp:
- JVM: `TimeZone.setDefault(TimeZone.getTimeZone("UTC"))` trong `main()` trước `SpringApplication.run`.
- JDBC: `...?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true` (quyết định đã duyệt, CHANGELOG c.2).
- Hibernate: `spring.jpa.properties.hibernate.jdbc.time_zone=UTC`.
- Bean `Clock`: `Clock.systemUTC()`. Mọi "bây giờ" lấy bằng `Instant.now(clock)`.

## 3. Khác biệt cột theo bảng (mã hiện tại → v4)
| Bảng | Thay đổi |
|---|---|
| users | + `avatar_public_id VARCHAR(255) NULL`, + `token_version INT NOT NULL DEFAULT 0`; thời gian DATETIME(3) |
| categories | `icon_url VARCHAR(500)` → `icon VARCHAR(50)` (tên icon lucide-react) |
| products | cột `condition` → **`item_condition`** (thuộc tính Java/JSON vẫn là `condition`); enum condition bỏ `USED`; enum status bỏ `REMOVED` |
| product_images | `public_id` → **NOT NULL** |
| auctions | DECIMAL(15,0); bỏ cột `version`; + `active_product_id` cột sinh STORED + `UNIQUE uq_auctions_active_product` (BR-15); + `updated_at` |
| bids | **bỏ** `status`, `invalid_reason`; giữ `UNIQUE (auction_id, amount)`; index `(auction_id, created_at)` |
| orders | bỏ `delivered_at`, `reminder_24h_sent_at`, `reminder_2h_sent_at`; + `tracking_code VARCHAR(50)`, + `shipped_at` |
| payments | method chỉ `MOCK_CARD`; status chỉ `SUCCESS`/`FAILED` (bỏ PENDING); bỏ `transaction_ref`, `pending_order_id` |
| notifications | type ∈ `OUTBID, AUCTION_RESULT, AUCTION_CANCELLED, ORDER_UPDATE, REPORT_RESOLVED`; `ref_type` ∈ AUCTION/ORDER/REPORT |
| reports | bỏ `report_type`, `action`; `order_id` → **`reported_order_id`**; CHECK ≥ 1 trong 3 cột đối tượng |

## 4. Cột sinh (generated) trong JPA
`auctions.active_product_id` và `payments.success_order_id` do MySQL tính. Trong entity: **không ánh xạ**, hoặc ánh xạ chỉ đọc
`@Column(name = "...", insertable = false, updatable = false)`. Không bao giờ ghi vào hai cột này.

## 5. Ràng buộc là lớp bảo vệ cuối (Bảng 3.5)
| Quy tắc | Cơ chế | Service xử lý khi vi phạm |
|---|---|---|
| Không trùng mức giá trong phiên | `uq_bids_auction_amount` | bắt `DataIntegrityViolationException` → 409 `CONFLICT` (hoặc BID_TOO_LOW kèm minNextBid mới nếu đọc lại được) |
| Mỗi phiên ≤ 1 đơn | `uq_orders_auction` | 409 `CONFLICT` |
| Mỗi đơn ≤ 1 SUCCESS | `uq_payments_one_success` | 409 `CONFLICT` |
| Idempotency | `uq_payments_idempotency (order_id, idempotency_key)` | trả lại kết quả cũ |
| Mỗi sản phẩm ≤ 1 phiên mở | `uq_auctions_active_product` | Service kiểm tra trước → `PRODUCT_NOT_AVAILABLE`; nếu vẫn đụng UNIQUE → `CONFLICT` |
| Không xóa lịch sử | FK mặc định InnoDB (RESTRICT); CASCADE chỉ ở product_images, notifications | — |

## 6. Flyway
- Phụ thuộc (Spring Boot 4 đã tách module auto-config): `org.springframework.boot:spring-boot-starter-flyway` + `org.flywaydb:flyway-mysql`. Kiểm tra lại tên artifact bằng `.\mvnw.cmd dependency:tree` nếu biên dịch báo thiếu.
- `spring.flyway.enabled=true`, `spring.flyway.locations=classpath:db/migration`.
- Test: Testcontainers MySQL 8.0 + Flyway chạy migration thật (bỏ cách mount `db/design_schema.sql` vào `docker-entrypoint-initdb.d`).
- DB dev cũ (Aiven) đang có schema 14 bảng không có lịch sử Flyway: tạo **database mới** (ví dụ `omnibid_v4`) cho Flyway, không `baseline` trên schema cũ.
- `db/design_schema.sql` và `db/seed_demo.sql` thuộc thế hệ cũ: xóa. Nếu cần dữ liệu demo, viết lại thành `db/demo/seed_demo_v4.sql` (KHÔNG đặt trong `db/migration`), khớp schema v4, chạy tay.
