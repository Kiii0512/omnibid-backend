---
trigger: always_on
description: "Omnibid backend đang được đồng bộ theo tài liệu v4; references v4 là chuẩn duy nhất."
---
# Đồng bộ v4 — luật nền
- Chuẩn duy nhất: `.agents/skills/omnibid-v4-sync/references/*` (trích từ bộ tài liệu v4 đã duyệt). Không dùng `docs/01-…04-*` hay `docs/legacy/` làm căn cứ.
- Tiến độ và quyết định ghi tại `docs/v4/PROGRESS.md`. Đọc tệp này đầu mỗi phiên làm việc để biết phase hiện tại.
- Làm theo phase (`/omnibid-v4-sync` hoặc `/v4-phase-N-...`). Không nhảy phase khi phase trước chưa `test-compile` sạch, trừ khi người dùng yêu cầu.
- Mọi endpoint, mã lỗi, tên cột, tên trường JSON phải khớp từng ký tự với references. Không tự thêm endpoint/cột/enum ngoài references.
- Khi references không quy định: chọn phương án ít thay đổi nhất, ghi vào PROGRESS.md mục "Quyết định của agent".
- Kết thúc mỗi lượt làm việc: `.\mvnw.cmd -q test-compile` phải đạt 0 lỗi.
