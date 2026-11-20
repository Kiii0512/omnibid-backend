---
name: v4-phase-3-security
description: Phase 3 đồng bộ v4 Omnibid — JWT với claim ver/token_version, đăng xuất, đổi mật khẩu, giới hạn đăng nhập sai 429, SecurityConfig, WebSocket /ws chỉ đọc, DTO auth và hồ sơ.
---
# Phase 3 — Bảo mật và xác thực

> Tài liệu tham chiếu (references) nằm ở `.agents/skills/omnibid-v4-sync/references/`; script kiểm tra ở `.agents/skills/omnibid-v4-sync/scripts/verify-v4.ps1`. Tiến độ ghi ở `docs/v4/PROGRESS.md`.

Đọc trước: `BUSINESS_RULES_v4.md` §9, `API_SPEC_v4.md` §1, §2.1, §3, §6 (login/register/logout), §8; rule `v4-security`. Mục GAP: C1–C10.

## Việc cần làm
1. `security/JwtTokenProvider`: `generateToken(Long userId, UserRole role, int tokenVersion)`; `parse(token) → JwtClaims(userId, role, ver, expiresAt)`; ném/nhận diện riêng token hết hạn/sai chữ ký. Trả kèm `expiresAt` để AuthService dựng response.
2. `security/CustomUserDetails` + `UserDetailsServiceImpl`: nạp theo **id** (không theo email) cho filter; principal mang `id, email, role, status, tokenVersion`. `CurrentUser` giữ nguyên ý nghĩa.
3. `security/JwtAuthenticationFilter`:
   - Không có header → đi tiếp (anonymous).
   - Có token: parse lỗi/hết hạn hoặc `ver` khác → nếu request khớp danh sách Public thì bỏ qua (anonymous), ngược lại trả ngay 401 `UNAUTHORIZED` JSON.
   - Token hợp lệ của user BANNED → trả ngay 403 `ACCOUNT_BANNED` JSON ở **mọi** endpoint, kể cả Public (Thiết kế 4.1: "JWT của tài khoản BANNED luôn nhận 403").
   - Dùng chung một `RequestMatcher` "public endpoints" với SecurityConfig để không lệch.
4. `security/LoginAttemptLimiter` (mới) theo §9; `AuthServiceImpl.login`: kiểm tra chặn → tìm user → sai mật khẩu: tăng đếm, 401 → BANNED: 403 → thành công: reset, cấp token.
5. `AuthController`: thêm `POST /auth/logout` (USER, ADMIN) → `AuthService.logout(userId)` tăng `tokenVersion`. Login cần `HttpServletRequest` để lấy IP.
6. DTO: `LoginResponse(accessToken, tokenType="Bearer", expiresIn=86400, expiresAt, role, user: UserSummary(id, fullName, email, role, status, avatarUrl))`; `RegisterRequest(fullName, email, password, phoneNumber)` (bỏ address nếu có) + regex mật khẩu; đăng ký trả `UserProfileResponse` 201, không token. Email trùng → 400 `VALIDATION_ERROR` với `details.fields.email`.
7. `UserController`/`UserServiceImpl`: `GET/PATCH /users/me` cho USER, ADMIN; `UpdateProfileRequest(fullName, phoneNumber, address)`; `ChangePasswordRequest(currentPassword, newPassword, confirmPassword)`; sai mật khẩu hiện tại → 400 VALIDATION_ERROR; thành công → `increaseTokenVersion()`. (Avatar làm ở phase 4; tạm giữ `POST /users/me/avatar`, xóa `PATCH /users/me/avatar` ngay.)
8. `config/SecurityConfig`: theo rule `v4-security` (permit list, thứ tự matcher, CORS `allowCredentials=false`, header mặc định, CSRF tắt, STATELESS). Bỏ permit `/uploads/**`, `/api/v1/products/**`, `/ws-auction/**`.
9. `config/WebSocketConfig`: endpoint `/ws` (bỏ SockJS và endpoint cũ), `setAllowedOrigins(<app.cors.allowed-origins>)`, broker `/topic` (bỏ `/queue`, `/user` nếu không dùng), interceptor: CONNECT xác thực tùy chọn như REST (ver/BANNED → từ chối kết nối nếu có token sai), SUBSCRIBE chỉ `/topic/auctions/{id}`, SEND → từ chối (`MessageDeliveryException`/`AccessDeniedException`). Đổi topic trong `WebSocketPublisherImpl` → `/topic/auctions/{id}`.
10. Ghi các thay đổi hợp đồng vào `docs/v4/API_CHANGES_FOR_FRONTEND.md` (login response, register không trả token, logout, `/ws`, topic mới).

## Cổng
`compile` sạch. Nếu kịp, sửa `JwtTokenProviderTest`, `AuthServiceTest` theo hành vi mới. Commit `refactor(v4): phase 3 - security`.
