# AGENTS.md — Omnibid Backend (áp dụng cho Antigravity 2.0 và mọi agent)

## 0. Nguồn sự thật
Bộ tài liệu **v4** (`0x_*_v4.docx`) là chuẩn duy nhất.
Bản tóm tắt dùng cho agent nằm trong `.agents/skills/omnibid-v4-sync/references/`:

| Tệp | Nội dung |
|---|---|
| `API_SPEC_v4.md` | 54 endpoint REST + WebSocket, tham số, mã HTTP, mã lỗi, JSON mẫu |
| `DB_SPEC_v4.md`, `sql/V1__init_schema.sql`, `sql/V2__seed_data.sql` | 10 bảng, kiểu dữ liệu, Flyway |
| `BUSINESS_RULES_v4.md` | BR-01…BR-23, NFR, chuyển trạng thái, khóa dòng, thông báo |
| `CONFIG_v4.md` | `application*.properties`, profile `demo`, UTC |
| `GAP_ANALYSIS.md` | Chênh lệch giữa mã hiện tại và v4, theo từng tệp |
| `DELETE_LIST.md` | Tệp/endpoint/bảng phải gỡ bỏ |

Thứ tự ưu tiên khi mâu thuẫn: **references v4 > mã nguồn hiện tại > thư mục `docs/` cũ** (`docs/01-…04-*` là tài liệu thế hệ trước, KHÔNG được dùng làm căn cứ).
Nếu references không nói rõ một chi tiết: chọn cách ít thay đổi nhất, ghi vào `docs/v4/PROGRESS.md` mục "Quyết định của agent" và tiếp tục.

## 1. Kiến trúc (bắt buộc)
- Monolith 3 tầng: Controller → Service → Repository/Entity. Controller chỉ inject Service, không bao giờ trả Entity.
- Mọi endpoint trả `ApiResponse<T>` theo cấu trúc v4: `{ "success": true, "data": … }` hoặc `{ "success": false, "error": { "code", "message", "details" } }`.
- Service ném `BusinessException(ErrorCode, message[, details])`; `GlobalExceptionHandler` ánh xạ sang HTTP theo Bảng 4.22.
- Request DTO dùng Jakarta Validation, Controller khai báo `@Valid`.
- Không xóa cứng dữ liệu nghiệp vụ (users, products đã lên phiên, auctions, bids, orders, payments, reports). Vi phạm xử lý bằng đổi trạng thái.
- Mật khẩu luôn qua `BCryptPasswordEncoder`.
- Ghi trên phiên/đơn/sản phẩm: `@Transactional` + khóa `PESSIMISTIC_WRITE`, thứ tự khóa **auctions → orders → products**, cùng bảng theo id tăng dần. Không khóa dòng `users`.
- Thông báo và WebSocket chỉ phát **sau COMMIT** (`@TransactionalEventListener(phase = AFTER_COMMIT)`).
- Thời gian: `Instant` (UTC) trong entity/service; tiền: `BigDecimal` scale 0 ↔ `DECIMAL(15,0)`.
- Schema do Flyway quản lý (`src/main/resources/db/migration`), `spring.jpa.hibernate.ddl-auto=none`. Cấu hình chỉ dùng `.properties`, không YAML.

## 2. Lệnh kiểm tra (Windows, chạy tại thư mục gốc repo)
- Biên dịch: `.\mvnw.cmd -q test-compile`
- Unit test: `.\mvnw.cmd test`
- Integration test (cần Docker Desktop đang chạy): `.\mvnw.cmd verify`
- Rà soát v4: `powershell -ExecutionPolicy Bypass -File .agents\skills\omnibid-v4-sync\scripts\verify-v4.ps1`

Trước khi kết thúc bất kỳ nhiệm vụ nào, agent PHẢI chạy `test-compile` và đạt 0 lỗi biên dịch.
**Ngoại lệ trong lúc chạy kit đồng bộ v4:** ở phase 1–8, cổng bắt buộc là `.\mvnw.cmd -q compile` (mã main). Test được sửa dần, nhưng `test-compile` và toàn bộ test chỉ bắt buộc xanh từ phase 9. Ghi số test còn lỗi vào PROGRESS.md sau mỗi phase.

## 3. Quy trình làm việc
- Làm trên nhánh `refactor/v4-sync`. Mỗi phase một (hoặc vài) commit, thông điệp dạng `refactor(v4): phase N - <mô tả>`.
- Cập nhật `docs/v4/PROGRESS.md` sau mỗi phase (đánh dấu [x], ghi quyết định, ghi việc còn tồn). Lưu ý: thư mục `docs/` bị loại khỏi Git bằng `.git/info/exclude` trên máy này, nên các tệp trong `docs/` chỉ nằm local — đó là chủ đích, không thêm `docs/` vào Git.
- Không sửa thư mục `C:\Documents\Omnibid` (tài liệu gốc).
- Không đưa bí mật (`jwt.secret` thật, khóa Cloudinary, mật khẩu DB) vào Git; chỉ ghi vào `application-local.properties` (đã nằm trong `.gitignore`) hoặc biến môi trường.
- Được phép xóa các tệp ghi trong `DELETE_LIST.md`. Không xóa tệp khác nếu chưa ghi lý do vào PROGRESS.md.
