---
trigger: glob
globs: "src/test/**"
description: "Quy ước kiểm thử khi đồng bộ v4."
---
# Testing (v4)
- Integration test: Testcontainers `mysql:8.0` + Flyway thật (không mount script cũ). `BaseIT` TRUNCATE đúng 10 bảng, rồi chạy lại dữ liệu V2 nếu test cần admin seed.
- Thời gian giả lập bằng `MutableClock` (UTC). Scheduler tắt trong test, gọi tick thủ công.
- `ImageStorageService` thay bằng bean giả trong bộ nhớ (đếm upload/delete để kiểm tra bù trừ và xóa sau COMMIT).
- Test đã lỗi thời (Shipment, Watchlist, Audit, BidStatus, Payment PENDING, report action) → xóa; test còn giá trị → sửa theo v4, không giữ assert hành vi cũ.
- Mỗi BR quan trọng có ít nhất một test: BR-01/02 (đặt giá), BR-09 (leader BANNED → UNSOLD), BR-11 (idempotency, 1 SUCCESS), BR-15 (UNIQUE active product), BR-16 (HIDDEN không về AVAILABLE), BR-17 (khóa user giữ bid), BR-23; NFR-05 (token_version, 429); magic bytes; JSON +07:00 và tiền số nguyên.
- Đồng thời: dùng `Concurrently` để bắn ≥ 10 lượt đặt giá cùng lúc; khẳng định không trùng amount, `bid_count` = số bid, `current_price` = MAX(amount).
- Unit test chạy được không cần Docker; IT cần Docker Desktop. Nếu máy không có Docker, ghi rõ trong PROGRESS.md là IT chưa chạy.
