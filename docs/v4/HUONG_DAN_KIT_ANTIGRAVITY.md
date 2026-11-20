# Hướng dẫn dùng bộ kit Antigravity 2.0 — đồng bộ backend Omnibid theo tài liệu v4

## Kit gồm gì
```
AGENTS.md                                   ← luật dự án (Antigravity đọc tự động)
.agents/rules/                              ← 5 rule: 1 luôn bật, 4 bật theo glob (DB, API, security, test)
.agents/skills/omnibid-v4-sync/             ← skill điều phối
    SKILL.md
    references/  API_SPEC_v4.md, DB_SPEC_v4.md, BUSINESS_RULES_v4.md, CONFIG_v4.md,
                 GAP_ANALYSIS.md, DELETE_LIST.md, sql/V1__init_schema.sql, sql/V2__seed_data.sql
    scripts/verify-v4.ps1                   ← kiểm tra tự động độ khớp v4
.agents/skills/v4-phase-0-prepare … v4-phase-9-tests-docs/   ← 10 skill, mỗi skill một phase
docs/v4/PROGRESS.md                         ← agent ghi tiến độ, quyết định, việc tồn
docs/v4/API_CHANGES_FOR_FRONTEND.md         ← thay đổi hợp đồng API để nhóm Frontend cập nhật
```

## Chuẩn bị
1. Mở thư mục `omnibid-backend` làm workspace trong Antigravity 2.0 (cần mở đúng thư mục gốc để `.agents/` được nhận).
2. Kiểm tra trong Customizations: 5 rule và 11 skill hiển thị.
3. Bật Docker Desktop nếu muốn chạy integration test (Testcontainers).
4. Chuẩn bị (không bắt buộc ngay): Cloudinary cloud name/API key/secret, một database MySQL **mới** (ví dụ `omnibid_v4`) để Flyway tạo schema.

## Chạy
- Cách khuyến nghị — từng phase, có kiểm duyệt: gõ `/omnibid-v4-sync` (hoặc trực tiếp `/v4-phase-0-prepare`). Agent làm xong một phase sẽ dừng, báo cáo, commit. Bạn xem diff rồi gõ "tiếp tục phase N" hoặc `/v4-phase-N-...`.
- Cách nhanh: "Chạy /omnibid-v4-sync liên tục từ phase 0 đến 9, chỉ dừng khi gặp mục Cần người duyệt". Nên dùng chế độ Agent có review cho các lệnh xóa tệp và git.
- Model: dùng model mạnh nhất có sẵn (phase 2, 5, 8 thay đổi nhiều tệp).

## Kiểm tra sau mỗi phase
```powershell
.\mvnw.cmd -q compile
powershell -ExecutionPolicy Bypass -File .agents\skills\omnibid-v4-sync\scripts\verify-v4.ps1
```
Script sẽ còn báo lỗi cho tới phase 8–9; dùng nó để theo dõi phần còn thiếu. Kết thúc phase 9 phải in `ALL v4 CHECKS PASSED`.

## Quyết định thiết kế kit đã chốt (ngoài tài liệu, cần nhóm biết)
| Vấn đề | Kit chọn | Lý do |
|---|---|---|
| Ảnh chính sản phẩm | Ảnh đầu tiên là ảnh chính; xóa ảnh chính thì ảnh id nhỏ nhất còn lại thay thế; bỏ endpoint đặt ảnh chính | Danh mục 54 endpoint không có endpoint này |
| Giới hạn 5 MB/ảnh | Spring multipart đặt 25 MB/tệp và 25 MB/request; tự kiểm tra 5 MB/ảnh | Để phân biệt đúng 400 INVALID_IMAGE và 413 PAYLOAD_TOO_LARGE |
| JSON thời gian | Serializer toàn cục `Instant` → `+07:00` | Jackson mặc định in `Z`, bỏ qua `spring.jackson.time-zone` |
| Lưu ảnh cục bộ | Bỏ hẳn; test dùng ImageStorageService giả | Thiết kế chỉ có Cloudinary |
| Admin dùng lại token sau unban | Không tăng token_version khi unban | Thiết kế không yêu cầu |
| `docs/` cũ | Chuyển sang `docs/legacy/` | Tránh agent và người đọc dùng nhầm |
| DB đang dùng (Aiven, 14 bảng) | Tạo database mới cho Flyway, không baseline | Schema cũ khác hoàn toàn v4 |

## Lưu ý
- Kit **không sửa Frontend**. Sau khi backend xong, đưa `docs/v4/API_CHANGES_FOR_FRONTEND.md` cho người phụ trách Frontend.
- CHANGELOG v4 mục (b) yêu cầu nhóm chạy thử `V1__init_schema.sql` + `V2__seed_data.sql` trên MySQL 8.0.16+: phase 9 làm việc này qua Testcontainers. Nếu script lỗi, agent sửa tối thiểu và ghi vào PROGRESS.md — khi đó cần cập nhật lại Phụ lục A trong tài liệu Thiết kế cho khớp.
- Mật khẩu admin seed `Admin@123456` công khai trong tài liệu: đổi ngay sau lần đăng nhập đầu.
