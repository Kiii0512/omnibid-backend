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
- Đăng ký (`POST /api/v1/auth/register`): không nhận `address`, bắt buộc mật khẩu ≥8 ký tự gồm chữ hoa + chữ thường + chữ số; trả 201 `UserProfile` (`{ id, fullName, email, phoneNumber, avatarUrl, address, role, status, createdAt }`) và **không cấp token** (Frontend chuyển tới trang đăng nhập). Email trùng → 400 `VALIDATION_ERROR` kèm `error.details.fields.email`.
- Đăng nhập (`POST /api/v1/auth/login`): trả 200 `{ accessToken, tokenType: "Bearer", expiresIn: 86400, expiresAt: "...+07:00", role, user: { id, fullName, email, role, status, avatarUrl } }`. Sai email/mật khẩu → 401 `UNAUTHORIZED`; tài khoản bị khóa → 403 `ACCOUNT_BANNED`.
- Đăng xuất (`POST /api/v1/auth/logout`) và đổi mật khẩu (`PATCH /api/v1/users/me/password`, bổ sung bắt buộc `confirmPassword`, trả `{ message }`): tăng `token_version`, vô hiệu toàn bộ JWT đã cấp của tài khoản → Frontend xóa token và về trang đăng nhập.
- Hồ sơ (`PATCH /api/v1/users/me`): chỉ nhận `{ fullName, phoneNumber, address }` (bỏ `avatarUrl`). Bỏ `PATCH /api/v1/users/me/avatar` (chỉ giữ `POST /api/v1/users/me/avatar`).
- Đăng nhập sai 5 lần/15 phút theo `(email, IP)` → 429 `TOO_MANY_REQUESTS` kèm header `Retry-After` và `error.details.retryAfterSeconds`.
- JWT của tài khoản `BANNED` luôn nhận 403 `ACCOUNT_BANNED` ở mọi endpoint (kể cả Public); JWT hỏng/hết hạn/`ver` sai bị bỏ qua ở endpoint Public và trả 401 `UNAUTHORIZED` ở endpoint cần đăng nhập.
- WebSocket: đổi endpoint sang `/ws` (WebSocket thuần, bỏ SockJS và `/ws-auction`), kênh `/topic/auctions/{id}`, chỉ đọc (mọi khung `SEND` và `SUBSCRIBE` ngoài `/topic/auctions/{id}` bị từ chối); CORS `allowCredentials=false`.
- `SELF_BID_NOT_ALLOWED` trả 409. Lịch sử đặt giá công khai không còn `bidderId`, `status`.
- Thanh toán đồng bộ: kết quả SUCCESS/FAILED ngay trong response; không còn trạng thái PENDING/polling.
- Admin không gọi được `/orders/{id}` và `/orders/{id}/payments` (dùng `/admin/orders/{id}`).
- Danh mục dùng `icon` (tên icon lucide-react) thay `iconUrl`.
- Báo cáo: `reportedOrderId` thay `orderId`; không còn `reportType`.
- Thông báo chỉ 5 loại: OUTBID, AUCTION_RESULT, AUCTION_CANCELLED, ORDER_UPDATE, REPORT_RESOLVED.
