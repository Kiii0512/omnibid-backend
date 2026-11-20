---
trigger: glob
globs: "**/controller/**, **/dto/**, **/common/**"
description: "Hợp đồng REST v4: 54 endpoint, ApiResponse, ErrorCode, phân trang."
---
# API contract (v4)
- Chỉ đúng 54 endpoint trong `references/API_SPEC_v4.md` §2. Thêm/bớt endpoint là vi phạm. Kiểm tra bằng `verify-v4.ps1`.
- Trả `ApiResponse<T>`: thành công `{success:true, data}`, lỗi `{success:false, error:{code, message, details?}}`.
- HTTP: tạo mới 201 (register, products, images, auctions, bids, reports, admin categories); mọi hành động POST khác, PATCH, PUT, DELETE, GET: 200. Avatar 200.
- Lỗi: `throw new BusinessException(ErrorCode.X, "thông điệp tiếng Việt"[, details])`. Chỉ 24 mã của `ErrorCode`.
- Request DTO: `record` + Jakarta Validation; Controller `@Valid`. Tiền `@Positive @Digits(integer=15, fraction=0)`. Thông điệp lỗi tiếng Việt.
- Response DTO: tiền là `BigDecimal` scale 0 (JSON số nguyên); thời gian `Instant` (serializer toàn cục in ra +07:00).
- Phân trang: `Pageable` mặc định size 10, tối đa 100; kiểm tra `sort` theo whitelist của từng endpoint; trả `PageResponse{content,page,size,totalElements,totalPages}`.
- Phân quyền vai trò bằng `@PreAuthorize`: `hasRole('USER')` cho endpoint "USER" (ADMIN bị 403), `hasAnyRole('USER','ADMIN')` cho `/users/me/**` và `/auth/logout`, `/admin/**` chặn ở SecurityConfig + `hasRole('ADMIN')`.
- Quyền sở hữu kiểm tra trong Service, trả 403 FORBIDDEN. Không lộ `reservePrice`, `bidderId`, email người đặt giá ra API công khai.
- Controller không chứa logic nghiệp vụ, không gọi Repository.
