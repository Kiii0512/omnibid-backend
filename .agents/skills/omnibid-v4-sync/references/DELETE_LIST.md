# DELETE_LIST — Những gì phải gỡ khỏi mã nguồn để khớp v4

Agent được phép xóa các mục dưới đây. Trước khi xóa một lớp, tìm mọi chỗ tham chiếu (`grep -r "TênLớp" src`) và sửa/xóa theo.

## 1. Tính năng không có trong v4 (xóa hẳn)
| Tính năng | Tệp main | Tệp test |
|---|---|---|
| Watchlist | `controller/WatchlistController`, `service/WatchlistService`, `service/impl/WatchlistServiceImpl`, `repository/WatchlistRepository`, `entity/Watchlist`, `dto/watchlist/*` | (tham chiếu trong Seed/InvariantCheck) |
| Shipment (vận chuyển nhiều bước) | `controller/ShipmentController`, `service/ShipmentService`, `service/impl/ShipmentServiceImpl`, `repository/ShipmentRepository`, `repository/ShipmentEventRepository`, `entity/Shipment`, `entity/ShipmentEvent`, `enums/ShipmentStatus`, `dto/shipment/*`, `dto/payment/ShipmentSummaryDto` | `controller/ShipmentControllerTest`, `service/ShipmentServiceTest` |
| Audit log | `service/AuditService`, `service/impl/AuditServiceImpl`, `repository/AuditLogRepository`, `entity/AuditLog`, `dto/admin/AuditLogResponse`; endpoint `GET /admin/audit-logs` | phần audit trong `AdminServiceTest`, `AdminIntegrationIT`, `BaseIT` (TRUNCATE) |
| Thanh toán bất đồng bộ | trạng thái `PaymentStatus.PENDING`, `SimulatedOutcome.PROCESSING`, `transactionRef`, `finalizePayment`, `timeoutStalePayments`, rate-limit thanh toán, mọi `@Async`/delay mô phỏng | test liên quan PENDING/timeout |
| Nhắc hạn thanh toán | `OrderService.sendPaymentReminders`, cột reminder_*, `NotificationType.PAYMENT_REMINDER` | |
| Lưu ảnh cục bộ | `service/impl/LocalStorageServiceImpl`, handler `/uploads/**` trong `WebMvcConfig`, thư mục `uploads/` ở gốc repo, `app.storage.upload-dir` | |
| Hành động trong báo cáo | `enums/ReportAction`, `enums/ReportType`, `Report.action`, `Report.reportType`, `AdminService.completeOrder`, các nhánh "thực thi hành động" trong `ReportServiceImpl` | |
| Trạng thái Bid | `enums/BidStatus`, `Bid.status`, `Bid.invalidReason`, `BidRepository.findByAuctionIdAndStatusForUpdate`, `findValidBidsDesc(…statuses…)`, `countByBidderIdAndStatus` | |
| Optimistic version | `Auction.version` | |
| Enum dư | `ProductCondition.USED`, `ProductStatus.REMOVED`, `PaymentMethod.MOCK_BANK`, `PaymentMethod.MOCK_WALLET`, NotificationType ngoài 5 giá trị v4 | |

## 2. Endpoint phải bỏ (không thuộc 54 endpoint)
| Endpoint hiện có | Xử lý |
|---|---|
| `GET /api/v1/categories/{id}` | bỏ |
| `GET /api/v1/products` (toàn bộ sản phẩm) | bỏ |
| `GET /api/v1/products/mine/summary` | bỏ |
| `PATCH /api/v1/products/{id}/images/{imageId}/primary` | bỏ (quy tắc ảnh chính tự động, xem BUSINESS_RULES §8) |
| `POST /api/v1/bids` | đổi thành `POST /api/v1/auctions/{id}/bids` |
| `GET /api/v1/bids/mine/summary` | bỏ |
| `PATCH /api/v1/users/me/avatar` | bỏ (chỉ giữ `POST`) |
| `GET /api/v1/orders/summary` | bỏ |
| `PATCH /api/v1/orders/{id}/confirm-receipt` | đổi thành `POST` |
| `GET /api/v1/orders/{orderId}/shipment`, `PATCH /api/v1/shipments/{id}/status` | bỏ; thay bằng `POST /api/v1/orders/{id}/ship` |
| `PATCH /api/v1/notifications/{id}/read` | đổi thành `POST` |
| `GET /api/v1/notifications/unread-count`, `PATCH /api/v1/notifications/read-all` | bỏ |
| `PATCH /api/v1/admin/reports/{id}/resolve` | đổi thành `POST` |
| `PATCH /api/v1/admin/users/{id}` | tách thành `POST …/ban`, `POST …/unban` |
| `PATCH /api/v1/admin/products/{id}` | đổi thành `POST …/hide` |
| `GET /api/v1/admin/audit-logs` | bỏ |
| `/api/v1/watchlist/**` (4 endpoint) | bỏ |
| STOMP `/ws-auction` (+ SockJS) | đổi thành `/ws`; kênh `/topic/auction/{id}` → `/topic/auctions/{id}`; bỏ `/queue`, `/user` prefix nếu không dùng |

## 3. Tệp DB/tài liệu thế hệ cũ
- `db/design_schema.sql`, `db/seed_demo.sql` → xóa (thay bằng Flyway V1/V2; dữ liệu demo nếu cần viết lại ở `db/demo/seed_demo_v4.sql`).
- `docs/01-requirements/`, `docs/02-design/`, `docs/03-integration/`, `docs/04-testing/` → **di chuyển** sang `docs/legacy/` kèm `docs/legacy/README.md` ghi "Tài liệu thế hệ trước v4, không còn hiệu lực". Không sửa nội dung bên trong.
- `README.md`: viết lại các phần sai (14 bảng, Aiven, topic, endpoint, Shipment/Watchlist/Audit) ở phase cuối.
