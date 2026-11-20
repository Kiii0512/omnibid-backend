---
name: v4-phase-0-prepare
description: Phase 0 của đồng bộ v4 Omnibid — tạo nhánh, lưu thay đổi dở dang, ghi nhận trạng thái build ban đầu.
---
# Phase 0 — Chuẩn bị

> Tài liệu tham chiếu (references) nằm ở `.agents/skills/omnibid-v4-sync/references/`; script kiểm tra ở `.agents/skills/omnibid-v4-sync/scripts/verify-v4.ps1`. Tiến độ ghi ở `docs/v4/PROGRESS.md`.

1. `git status`. Repo đang có thay đổi chưa commit (README, pom.xml, SecurityConfig, controllers…). **Hỏi người dùng** có muốn commit chúng lên `main` trước không. Mặc định nếu không trả lời: tạo commit `chore: wip before v4 sync` trên nhánh mới (không đẩy lên remote).
2. `git checkout -b refactor/v4-sync`.
3. Chạy `.\mvnw.cmd -q test-compile`; ghi kết quả (đạt/lỗi, số lỗi) vào `docs/v4/PROGRESS.md` mục "Baseline".
4. Kiểm tra Docker Desktop: `docker version`. Ghi vào PROGRESS.md (cần cho integration test ở phase 9).
5. Liệt kê nhanh số tệp test hiện có (`src/test/java`) vào PROGRESS.md để so sánh cuối kỳ.
6. Không sửa mã nguồn trong phase này. Commit `docs(v4): phase 0 - baseline`.
