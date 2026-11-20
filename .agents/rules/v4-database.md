---
trigger: glob
globs: "src/main/resources/db/**, **/entity/**, **/repository/**, **/enums/**"
description: "Quy tắc schema, entity, repository theo DB v4."
---
# Database & JPA (v4)
- Schema chỉ do Flyway (`src/main/resources/db/migration/V1__init_schema.sql`, `V2__seed_data.sql`) — bản sao nguyên văn từ `references/sql/`. Không dùng `ddl-auto` khác `none`. Muốn đổi schema: thêm `V3__...sql`, không sửa V1/V2 sau khi đã chạy trên DB dùng chung.
- 10 bảng: users, categories, products, product_images, auctions, bids, orders, payments, notifications, reports.
- Thời gian: `Instant` + cột `DATETIME(3)`; tiền: `BigDecimal` + `@Column(precision = 15, scale = 0)`. Không `LocalDateTime`, không `scale = 2`.
- `@Enumerated(EnumType.STRING)`, giá trị enum khớp ENUM của DB. `products.item_condition` ↔ thuộc tính `condition`.
- Cột sinh `auctions.active_product_id`, `payments.success_order_id`: không ghi (không map hoặc `insertable = false, updatable = false`).
- Repository khóa dòng: `@Lock(LockModeType.PESSIMISTIC_WRITE)` + `@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))`. Chỉ cho auctions, orders, products (và reports nếu cần). Không có truy vấn khóa trên `users`, không `LOCK IN SHARE MODE`.
- Truy vấn quét định kỳ dùng chỉ mục `(status, end_time)`, `(status, start_time)`, `(status, payment_deadline)`, `(status, confirm_deadline)`; trả danh sách id rồi xử lý từng id trong transaction riêng.
- Entity có phương thức chuyển trạng thái theo `BUSINESS_RULES_v4.md` §4; Service không `setStatus(...)` trực tiếp ngoài các phương thức đó.
