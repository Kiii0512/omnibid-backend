# Thay đổi hợp đồng API cho Frontend (backend v4)

Danh sách khởi tạo từ phân tích chênh lệch; agent bổ sung chi tiết (tên trường, ví dụ JSON) trong từng phase.

## Cấu trúc chung
- Lỗi: `{ success:false, error:{ code, message, details } }` thay cho `errorCode`/`message` cấp ngoài; bỏ `timestamp`.
- Thời gian trả dạng `2026-11-20T09:00:00+07:00`; tiền là số nguyên (không `.00`).
- Phân trang `{content,page,size,totalElements,totalPages}`, size mặc định 10, tối đa 100.

## Endpoint đổi
| Cũ | Mới |
|---|---|
| `POST /api/v1/bids` (body có auctionId) | `POST /api/v1/auctions/{id}/bids` body `{amount}` |
| `PATCH /orders/{id}/confirm-receipt` | `POST /orders/{id}/confirm-receipt` |
| `GET /orders/{id}/shipment`, `PATCH /shipments/{id}/status` | `POST /orders/{id}/ship` `{trackingCode?}` |
| `PATCH /notifications/{id}/read` | `POST /notifications/{id}/read` |
| `PATCH /admin/reports/{id}/resolve` (action…) | `POST /admin/reports/{id}/resolve` `{decision, note}` |
| `PATCH /admin/users/{id}` | `POST /admin/users/{id}/ban`, `POST /admin/users/{id}/unban` |
| `PATCH /admin/products/{id}` | `POST /admin/products/{id}/hide` |
| `POST /products` (JSON) | `POST /products` multipart: `product` (JSON) + `files` (1–5) |
| `PATCH /users/me/avatar` | chỉ `POST /users/me/avatar` (phần `file`), trả `{avatarUrl}` |
| STOMP `/ws-auction` (SockJS), `/topic/auction/{id}` | STOMP `/ws` (WebSocket thuần), `/topic/auctions/{id}`, payload `AuctionEvent` |

## Endpoint mới
`POST /auth/logout`, `GET /admin/products/{id}`, `GET /admin/auctions`, `GET /admin/auctions/{id}`, `GET /admin/orders/{id}`, `GET /admin/reports/{id}`.

## Endpoint bị bỏ
`GET /categories/{id}`, `GET /products`, `GET /products/mine/summary`, `PATCH /products/{id}/images/{imageId}/primary`, `GET /bids/mine/summary`,
`GET /orders/summary`, `GET /notifications/unread-count`, `PATCH /notifications/read-all`, `GET /admin/audit-logs`, toàn bộ `/watchlist/**`.

## Hành vi đổi
- Đăng ký không trả token (chuyển tới trang đăng nhập). Đăng nhập trả `{accessToken, tokenType, expiresIn, expiresAt, role, user{…}}`.
- Đăng xuất/đổi mật khẩu vô hiệu mọi token của tài khoản → Frontend xóa token và về trang đăng nhập.
- Đăng nhập sai 5 lần/15 phút → 429 `TOO_MANY_REQUESTS`.
- `SELF_BID_NOT_ALLOWED` trả 409. Lịch sử đặt giá công khai không còn `bidderId`, `status`.
- Thanh toán đồng bộ: kết quả SUCCESS/FAILED ngay trong response; không còn trạng thái PENDING/polling.
- Admin không gọi được `/orders/{id}` và `/orders/{id}/payments` (dùng `/admin/orders/{id}`).
- Danh mục dùng `icon` (tên icon lucide-react) thay `iconUrl`.
- Báo cáo: `reportedOrderId` thay `orderId`; không còn `reportType`.
- Thông báo chỉ 5 loại: OUTBID, AUCTION_RESULT, AUCTION_CANCELLED, ORDER_UPDATE, REPORT_RESOLVED.
