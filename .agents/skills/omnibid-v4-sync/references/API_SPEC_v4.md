# API_SPEC_v4 — Hợp đồng API (trích 04_ThietKe_Omnibid_v4, Chương 4 & 6)

## 1. Quy ước chung (mục 4.1)
- Tiền tố `/api/v1`, JSON UTF-8. Chỉ 3 endpoint dùng `multipart/form-data`: `POST /products`, `POST /products/{id}/images`, `POST /users/me/avatar`.
- Thời gian lưu UTC. API **trả** ISO 8601 ở `+07:00` (`spring.jackson.time-zone=Asia/Ho_Chi_Minh`), ví dụ `2026-11-20T09:00:00+07:00`; **nhận** mọi offset và quy về `Instant`.
- Đường dẫn kebab-case, danh từ số nhiều; trường JSON camelCase; enum viết HOA_GẠCH_DƯỚI.
- Hành động/chuyển trạng thái: `POST` trên tài nguyên con (cancel, ship, confirm-receipt, read, resolve, ban, unban, hide), trả **200**. Cập nhật một phần: `PATCH`. `PUT` chỉ cho danh mục. Tạo mới: **201**.
- Xác thực `Authorization: Bearer <JWT>`. Endpoint Public: JWT không hợp lệ bị **bỏ qua** (coi như Guest). Endpoint khác: thiếu/sai chữ ký/hết hạn/`ver` ≠ `token_version` → 401 `UNAUTHORIZED`. Tài khoản BANNED → 403 `ACCOUNT_BANNED`.
- Phân quyền 2 lớp: Spring Security theo vai trò (`/api/v1/admin/**` chỉ ADMIN), Service kiểm tra quyền sở hữu trên dữ liệu mới nhất.
- Phân trang: `page` (từ 0, mặc định 0), `size` (mặc định 10, tối đa 100), `sort=trường,chiều`; chỉ nhận trường sắp xếp trong danh sách cho phép của từng endpoint (ngoài danh sách → 400 `VALIDATION_ERROR`).

## 2. Danh mục 54 endpoint REST
Cột "Quyền": vai trò được gọi; ngoặc là điều kiện sở hữu kiểm tra ở Service. "USER" nghĩa là **chỉ** vai trò USER (ADMIN gọi → 403 `FORBIDDEN`), trừ khi ghi "USER, ADMIN".

### 2.1 Xác thực, người dùng (7)
| # | Method | Path | Quyền | Body / Query | HTTP |
|---|---|---|---|---|---|
| 1 | POST | /auth/register | Public | fullName, email, password, phoneNumber | 201 |
| 2 | POST | /auth/login | Public | email, password | 200 |
| 3 | POST | /auth/logout | USER, ADMIN | — | 200 |
| 4 | GET | /users/me | USER, ADMIN | — | 200 |
| 5 | PATCH | /users/me | USER, ADMIN | fullName, phoneNumber, address (**không** có avatarUrl, không đổi email) | 200 |
| 6 | PATCH | /users/me/password | USER, ADMIN | currentPassword, newPassword, confirmPassword | 200 |
| 7 | POST | /users/me/avatar | USER, ADMIN | multipart: `file` (JPG/JPEG/PNG/WEBP, ≤5 MB) | **200** |

### 2.2 Danh mục, sản phẩm (8)
| # | Method | Path | Quyền | Body / Query | HTTP |
|---|---|---|---|---|---|
| 8 | GET | /categories | Public | q: keyword, page, size, sort | 200 |
| 9 | POST | /products | USER | multipart: phần `product` (JSON: title, description, categoryId, condition) + phần `files` (1–5 ảnh) | 201 |
| 10 | GET | /products/mine | USER | q: keyword, status, page, size, sort | 200 |
| 11 | GET | /products/{id} | USER (chủ sản phẩm) | — | 200 |
| 12 | PATCH | /products/{id} | USER (chủ) | title, description, categoryId, condition | 200 |
| 13 | DELETE | /products/{id} | USER (chủ) | — (chỉ khi chưa từng lên phiên) | 200 |
| 14 | POST | /products/{id}/images | USER (chủ) | multipart `files` (tổng sau khi thêm ≤ 5) | 201 |
| 15 | DELETE | /products/{id}/images/{imageId} | USER (chủ) | — | 200 |

### 2.3 Phiên, đặt giá (9)
| # | Method | Path | Quyền | Body / Query | HTTP |
|---|---|---|---|---|---|
| 16 | GET | /auctions | Public | q: keyword, categoryId, minPrice, maxPrice, status, endBefore, sort, page, size | 200 |
| 17 | GET | /auctions/{id} | Public | — | 200 |
| 18 | POST | /auctions | USER | productId, startPrice, bidIncrement, reservePrice, startTime, endTime | 201 |
| 19 | PATCH | /auctions/{id} | USER (chủ phiên) | startPrice, bidIncrement, reservePrice, startTime, endTime | 200 |
| 20 | POST | /auctions/{id}/cancel | USER (chủ phiên) | reason | 200 |
| 21 | GET | /auctions/mine | USER | q: status, keyword, page, size, sort | 200 |
| 22 | GET | /auctions/{id}/bids | Public | q: page, size, sort | 200 |
| 23 | POST | /auctions/{id}/bids | USER (không phải chủ phiên) | amount | 201 |
| 24 | GET | /bids/mine | USER | q: auctionId, page, size, sort | 200 |

`GET /auctions` sort chỉ nhận: `endTime,asc` (mặc định), `createdAt,desc`, `currentPrice,asc`, `currentPrice,desc`.
Phiên của sản phẩm HIDDEN → 404 `NOT_FOUND` với người gọi không phải ADMIN (cả ở danh sách lẫn chi tiết, lịch sử đặt giá).

### 2.4 Đơn hàng, thanh toán, giao hàng (7)
| # | Method | Path | Quyền | Body / Query | HTTP |
|---|---|---|---|---|---|
| 25 | GET | /orders | USER (người mua hoặc bán) | q: status, role (BUYER/SELLER), page, size, sort | 200 |
| 26 | GET | /orders/{id} | USER (bên của đơn) — **không có ADMIN** | — | 200 |
| 27 | PATCH | /orders/{id}/shipping-info | USER (người mua) | shippingAddress, phoneNumber | 200 |
| 28 | POST | /orders/{id}/payments | USER (người mua) | method, simulateResult; header bắt buộc `Idempotency-Key` (UUID) | 200 |
| 29 | GET | /orders/{id}/payments | USER (người mua) — **không có ADMIN** | q: page, size, sort | 200 |
| 30 | POST | /orders/{id}/ship | USER (người bán) | trackingCode (tùy chọn) | 200 |
| 31 | POST | /orders/{id}/confirm-receipt | USER (người mua) | — | 200 |

`simulateResult` chỉ có hiệu lực khi `app.payment.simulate-enabled=true`; khi tắt, bỏ qua và luôn SUCCESS. Thanh toán trả 200 cho cả lần mới và lần gửi lặp cùng khóa.

### 2.5 Thông báo, báo cáo (3)
| # | Method | Path | Quyền | Body / Query | HTTP |
|---|---|---|---|---|---|
| 32 | GET | /notifications | USER | q: isRead, type, page, size, sort | 200 |
| 33 | POST | /notifications/{id}/read | USER (người nhận) | — | 200 |
| 34 | POST | /reports | USER | reportedProductId, reportedUserId, reportedOrderId (≥ 1 trường), reason | 201 |

### 2.6 Quản trị người dùng, sản phẩm, phiên (9)
| # | Method | Path | Body / Query | HTTP |
|---|---|---|---|---|
| 35 | GET | /admin/users | q: keyword, status, role, page, size, sort | 200 |
| 36 | POST | /admin/users/{id}/ban | reason (tùy chọn, ghi log ứng dụng) | 200 |
| 37 | POST | /admin/users/{id}/unban | reason (tùy chọn) | 200 |
| 38 | GET | /admin/products | q: keyword, categoryId, status, page, size, sort | 200 |
| 39 | GET | /admin/products/{id} | — | 200 |
| 40 | POST | /admin/products/{id}/hide | reason (tùy chọn) | 200 |
| 41 | GET | /admin/auctions | q: keyword, status, sellerId, page, size, sort | 200 |
| 42 | GET | /admin/auctions/{id} | — | 200 |
| 43 | POST | /admin/auctions/{id}/cancel | reason (**bắt buộc**, lưu cancel_reason) | 200 |

### 2.7 Quản trị đơn, báo cáo, danh mục, thống kê (11)
| # | Method | Path | Body / Query | HTTP |
|---|---|---|---|---|
| 44 | GET | /admin/orders | q: status, buyerId, sellerId, fromDate, toDate, page, size, sort | 200 |
| 45 | GET | /admin/orders/{id} | — (chi tiết đơn **kèm lịch sử thanh toán**) | 200 |
| 46 | POST | /admin/orders/{id}/cancel | reason (**bắt buộc**) — chỉ khi PENDING_PAYMENT | 200 |
| 47 | GET | /admin/reports | q: status, targetType (PRODUCT/USER/ORDER), page, size, sort | 200 |
| 48 | GET | /admin/reports/{id} | — | 200 |
| 49 | POST | /admin/reports/{id}/resolve | decision (RESOLVED/REJECTED), note (**bắt buộc**) | 200 |
| 50 | GET | /admin/categories | q: keyword, page, size, sort | 200 |
| 51 | POST | /admin/categories | name, slug, icon | 201 |
| 52 | PUT | /admin/categories/{id} | name, slug, icon | 200 |
| 53 | DELETE | /admin/categories/{id} | — (409 CATEGORY_IN_USE nếu còn sản phẩm) | 200 |
| 54 | GET | /admin/statistics | — (không tham số; số liệu tích lũy) | 200 |

`GET /admin/statistics` trả: tổng số người dùng, số phiên theo từng trạng thái, số đơn theo từng trạng thái, tổng `final_price` của đơn COMPLETED, số báo cáo PENDING.

**Ngoài 54 endpoint trên không được có endpoint REST nào khác** dưới `/api/v1` (xem DELETE_LIST.md).

## 3. WebSocket (Bảng 4.8, 4.18)
- Điểm kết nối STOMP: `/ws` (CONNECT; JWT trong header `Authorization` của khung CONNECT là tùy chọn, nếu có thì xác thực như REST: chữ ký, hạn, `ver`, BANNED).
- Kênh: `/topic/auctions/{id}` (chú ý **auctions** số nhiều). Client chỉ được SUBSCRIBE `/topic/**`; mọi khung SEND (đặc biệt tới `/app/**`) bị từ chối.
- CORS của `/ws` lấy từ `app.cors.allowed-origins` (không dùng `*`).
- Thông điệp `AuctionEvent`: `type, auctionId, status, currentPrice, minNextBid, bidCount, highestBidderName, reserveStatus, startTime, endTime, bid, createdAt`.
  - `type`: `BID_PLACED` (có `bid`), `AUCTION_UPDATED` (người bán sửa phiên SCHEDULED, BR-07), `AUCTION_STARTED`, `AUCTION_ENDED` (ENDED hoặc UNSOLD), `AUCTION_CANCELLED`.
  - `highestBidderName` ẩn danh: ký tự đầu + `***` + ký tự cuối (ví dụ `n***h`).
  - `reserveStatus`: `MET` / `NOT_MET` / `NO_RESERVE`.

## 4. Định dạng phản hồi (mục 4.4)
```json
{ "success": true, "data": { ... } }
{ "success": true, "data": { "content": [], "page": 0, "size": 10, "totalElements": 0, "totalPages": 0 } }
{ "success": false, "error": { "code": "VALIDATION_ERROR", "message": "Dữ liệu đầu vào không hợp lệ",
  "details": { "fields": { "email": "Email không hợp lệ" } } } }
```
Không còn trường `errorCode`, `message` (cấp ngoài), `timestamp` trong `ApiResponse`. `details` chỉ xuất hiện khi có.

## 5. Mã lỗi (Bảng 4.22) — enum `ErrorCode` phải có đúng các giá trị này
| HTTP | Mã | Khi nào |
|---|---|---|
| 400 | VALIDATION_ERROR | Thiếu/sai định dạng, giá ≤ 0, thời gian sai, mật khẩu yếu, **email hoặc slug đã tồn tại**, **mật khẩu hiện tại sai**, đơn chưa có thông tin nhận hàng khi thanh toán, sort không cho phép |
| 400 | INVALID_IMAGE | Ảnh sai định dạng theo magic bytes, > 5 MB, số ảnh sản phẩm ngoài 1–5 |
| 401 | UNAUTHORIZED | Thiếu/sai/hết hạn JWT, `ver` khác `token_version`; **sai email hoặc mật khẩu khi đăng nhập** |
| 403 | FORBIDDEN | Sai vai trò hoặc không sở hữu tài nguyên |
| 403 | ACCOUNT_BANNED | Tài khoản bị khóa (đăng nhập hoặc gọi API) |
| 404 | NOT_FOUND | Không tìm thấy (kể cả phiên của sản phẩm HIDDEN với người không phải Admin) |
| 409 | AUCTION_NOT_ACTIVE | Phiên không ACTIVE hoặc đã hết giờ |
| 409 | BID_TOO_LOW | Giá < mức tối thiểu; `details.minNextBid` |
| 409 | ALREADY_LEADING | Đang dẫn đầu |
| 409 | SELF_BID_NOT_ALLOWED | Người đặt là chủ phiên (**409**, không còn 422) |
| 409 | AUCTION_NOT_EDITABLE | Phiên không còn SCHEDULED |
| 409 | CANNOT_CANCEL | Người bán hủy phiên ACTIVE đã có lượt đặt giá |
| 409 | PRODUCT_NOT_AVAILABLE | Sản phẩm không đủ điều kiện tạo phiên |
| 409 | PRODUCT_NOT_EDITABLE | Sửa/thêm/xóa ảnh khi sản phẩm không AVAILABLE (BR-23) |
| 409 | INVALID_STATUS_TRANSITION | Chuyển trạng thái sai, đã ở trạng thái đích, báo cáo đã xử lý, xóa sản phẩm đã từng lên phiên |
| 409 | CANNOT_BAN_ADMIN | Khóa tài khoản ADMIN |
| 409 | CATEGORY_IN_USE | Xóa danh mục còn sản phẩm |
| 409 | ORDER_EXPIRED | Đơn quá hạn thanh toán |
| 409 | ORDER_CANCELLED | Đơn đã bị hủy |
| 409 | CONFLICT | Vi phạm UNIQUE do tranh chấp, deadlock, quá 5 giây chờ khóa |
| 413 | PAYLOAD_TOO_LARGE | Request tải ảnh > 25 MB |
| 429 | TOO_MANY_REQUESTS | Đăng nhập sai > 5 lần/15 phút theo (email, IP) |
| 500 | INTERNAL_ERROR | Lỗi không lường trước; không trả stack trace |
| 502 | IMAGE_UPLOAD_FAILED | Cloudinary lỗi/không phản hồi khi tải ảnh; dữ liệu không được lưu |

Không dùng mã nào khác (ví dụ `AUCTION_NOT_FOUND`, `USER_NOT_FOUND`, `EMAIL_ALREADY_EXISTS`, `INVALID_CREDENTIALS`, `INVALID_PASSWORD`, `INVALID_REPORT_ACTION` phải quy về các mã trên).

## 6. Hình dạng response bắt buộc (mục 4.5)
**Đăng nhập** `data`:
```json
{ "accessToken": "eyJ...", "tokenType": "Bearer", "expiresIn": 86400, "expiresAt": "2026-11-21T09:00:00+07:00",
  "role": "USER",
  "user": { "id": 5, "fullName": "Nguyễn Văn A", "email": "a@example.com", "role": "USER", "status": "ACTIVE", "avatarUrl": null } }
```
**Đăng ký** (201): trả hồ sơ người dùng (`id, fullName, email, phoneNumber, role, status, avatarUrl, createdAt`), **không cấp token** (UC01 1a: chuyển tới trang đăng nhập).
**Đăng xuất** `data`: `{ "message": "Đăng xuất thành công, mọi phiên đăng nhập của tài khoản đã kết thúc" }`.
**Ảnh đại diện** `data`: `{ "avatarUrl": "https://res.cloudinary.com/<cloud-name>/image/upload/.../omnibid/avatars/5.png" }`.
**Đặt giá** (201) `data`: `{ "bidId": 31, "auctionId": 12, "amount": 520000, "currentPrice": 520000, "bidCount": 13, "minNextBid": 530000 }`.
**BID_TOO_LOW**: `error.details = { "minNextBid": 540000 }`.
**Thanh toán** `data`: `{ "paymentId": 15, "attemptNo": 1, "status": "FAILED", "orderStatus": "PENDING_PAYMENT", "failureReason": "MOCK_DECLINED" }`; thành công: `{ "paymentId": 16, "attemptNo": 2, "status": "SUCCESS", "orderStatus": "PAID" }`. Gửi lặp cùng `Idempotency-Key` → trả nguyên kết quả cũ, không tạo bản ghi mới.
**Gửi hàng** `data`: `{ "id": 9, "orderStatus": "SHIPPING", "shippedAt": "...+07:00", "confirmDeadline": "...+07:00" }`.
**Xác nhận nhận hàng** `data`: `{ "id": 9, "orderStatus": "COMPLETED", "productStatus": "SOLD" }` (HIDDEN nếu sản phẩm đã bị ẩn).
Tiền trong JSON là **số nguyên** (520000), không có `.00`.

## 7. Ẩn thông tin
- Lịch sử đặt giá công khai và WebSocket: chỉ tên ẩn danh, không trả `bidderId`, email.
- `reservePrice` chỉ trả cho chủ phiên (và ADMIN qua API quản trị); người khác chỉ thấy `reserveStatus`.
- Chi tiết phiên/danh sách phiên trả thêm `minNextBid` (BR-01) và `reserveStatus`.

## 8. Ma trận phân quyền (Bảng 6.1)
| Chức năng | Guest | User | Chủ tài nguyên | Admin |
|---|---|---|---|---|
| Tìm kiếm, xem phiên, lịch sử đặt giá | Có | Có | – | Có |
| Hồ sơ, ảnh đại diện, đổi mật khẩu | – | Có | – | Có |
| Thông báo | – | Có | – | – |
| Tạo sản phẩm, tạo phiên | – | Có | – | – |
| Sửa/xóa sản phẩm; sửa/hủy phiên | – | – | Người bán | – |
| Đặt giá | – | Có (không phải chủ phiên) | – | – |
| Thanh toán, thông tin nhận hàng, xác nhận nhận hàng | – | – | Người mua | – |
| Gửi hàng | – | – | Người bán của đơn | – |
| Xem chi tiết đơn | – | – | Hai bên | Có (API quản trị) |
| Lịch sử thanh toán | – | – | Người mua | Có (API quản trị) |
| Gửi báo cáo | – | Có | – | – |
| Khóa/mở khóa USER, ẩn sản phẩm, hủy phiên, hủy đơn, xử lý báo cáo, danh mục, thống kê | – | – | – | Có |
