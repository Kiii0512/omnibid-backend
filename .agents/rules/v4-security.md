---
trigger: glob
globs: "**/security/**, **/config/**, **/AuthServiceImpl.java, **/UserServiceImpl.java"
description: "Xác thực JWT token_version, giới hạn đăng nhập, CORS, WebSocket chỉ đọc."
---
# Security (v4)
- JWT: `sub`=userId, `role`, `ver`=token_version; HS256; 24h; `generateToken(userId, role, tokenVersion)`. Không blacklist `jti`, không so `iat` với thời điểm khởi động.
- Filter: token hợp lệ → nạp user theo id → `ver` ≠ tokenVersion ⇒ 401 UNAUTHORIZED; BANNED ⇒ 403 ACCOUNT_BANNED (ghi thẳng response JSON chuẩn, dừng chain); endpoint Public bỏ qua token hỏng.
- Logout và đổi mật khẩu: `user.increaseTokenVersion()`.
- LoginAttemptLimiter: (email lowercase, remoteAddr), 5 lần/15 phút ⇒ chặn 15 phút, 429; kiểm tra trước khi xác thực; thành công thì reset. Dùng `Clock`.
- Permit: `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `GET /api/v1/categories`, `GET /api/v1/auctions`, `GET /api/v1/auctions/{id}`, `GET /api/v1/auctions/{id}/bids`, `/ws/**`. Khai báo `/api/v1/auctions/mine` (authenticated) TRƯỚC matcher GET auctions public. Còn lại `authenticated()`; `/api/v1/admin/**` `hasRole("ADMIN")`.
- CSRF tắt; giữ header bảo mật mặc định; CORS từ `app.cors.allowed-origins`, `allowCredentials=false`, cho phép header `Authorization, Content-Type, Idempotency-Key`.
- WebSocket `/ws`: origins từ cấu hình; inbound interceptor: CONNECT (JWT tùy chọn, xác thực như REST), SUBSCRIBE chỉ `/topic/auctions/**`, mọi SEND bị từ chối.
- Không log mật khẩu, token, khóa Cloudinary. Không đưa bí mật vào tệp được commit.
