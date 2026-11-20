---
name: v4-phase-4-images
description: Phase 4 đồng bộ v4 Omnibid — kiểm tra ảnh theo magic bytes, ImageStorageService Cloudinary dùng chung, tạo sản phẩm multipart 1–5 ảnh, thêm/xóa ảnh có bù trừ, ảnh đại diện, danh mục icon.
---
# Phase 4 — Ảnh, sản phẩm, danh mục

> Tài liệu tham chiếu (references) nằm ở `.agents/skills/omnibid-v4-sync/references/`; script kiểm tra ở `.agents/skills/omnibid-v4-sync/scripts/verify-v4.ps1`. Tiến độ ghi ở `docs/v4/PROGRESS.md`.

Đọc trước: `BUSINESS_RULES_v4.md` §8 và BR-06, BR-20, BR-23; `API_SPEC_v4.md` §2.1 (#7), §2.2, §2.7 (#50–53); `CONFIG_v4.md` §2 (multipart). Mục GAP: D1–D5.

## Việc cần làm
1. `common/ImageValidator` (mới): `validate(MultipartFile)` → magic bytes JPEG/PNG/WEBP, ≤ 5 MB, không rỗng; trả `ValidatedImage(bytes, detectedFormat)`. Lỗi → `INVALID_IMAGE`.
2. `service/ImageStorageService` (interface mới, thay `StorageService`) + `service/impl/CloudinaryImageStorageService`: `upload(byte[] bytes, String folder) → StoredImage(url, publicId)`, `delete(String publicId)` (best-effort, log lỗi). Lỗi upload → `IMAGE_UPLOAD_FAILED` (502). Thư mục: `omnibid/products/{productId}` hoặc `omnibid/products/tmp`, `omnibid/avatars`. Cấu hình bean trong `config/CloudinaryConfig` từ `cloudinary.*`; khi thiếu cấu hình, ứng dụng vẫn khởi động nhưng mọi upload trả `IMAGE_UPLOAD_FAILED` (ghi WARN lúc khởi động).
3. Xóa `LocalStorageServiceImpl`, `StorageConfig`, `dto/storage/StorageUploadResult` (thay bằng `StoredImage`), handler `/uploads/**` trong `WebMvcConfig` (giữ lớp cho converter Instant), thư mục `uploads/` ở gốc repo.
4. Tiện ích "làm sau COMMIT": `AfterCommitExecutor` (dùng `TransactionSynchronizationManager.registerSynchronization` hoặc sự kiện `@TransactionalEventListener(AFTER_COMMIT)`) để xóa asset Cloudinary.
5. `ProductController`/`ProductServiceImpl`:
   - `POST /products` `consumes = MULTIPART_FORM_DATA`: `@RequestPart("product") @Valid ProductCreateRequest`, `@RequestPart("files") List<MultipartFile>`; 1–5 tệp. Quy trình 4 bước (§8): validate → upload ngoài transaction (Service không `@Transactional` ở phương thức ngoài; phần lưu DB tách sang phương thức `@Transactional` của bean khác hoặc `TransactionTemplate`) → lưu product + images (ảnh đầu `isPrimary`) → lỗi thì xóa asset vừa upload. 201.
   - `GET /products/mine` (keyword, status), `GET /products/{id}` chỉ chủ sở hữu (khác → 403 FORBIDDEN; không tồn tại → 404).
   - `PATCH /products/{id}`: khóa product, `isEditable()` (AVAILABLE) nếu không → `PRODUCT_NOT_EDITABLE`.
   - `DELETE /products/{id}`: chỉ khi chưa từng có auction (`auctionRepository.existsByProductId`) → nếu đã có `INVALID_STATUS_TRANSITION`; xóa product (images CASCADE) rồi xóa asset sau COMMIT.
   - `POST /products/{id}/images`: `files` nhiều tệp; validate → upload → tx: khóa product, BR-23, tổng ≤ 5 → lưu; lỗi → bù trừ. 201, trả danh sách ảnh.
   - `DELETE /products/{id}/images/{imageId}`: khóa product, BR-23, không xóa ảnh cuối, chuyển ảnh chính nếu cần, xóa asset sau COMMIT.
   - Xóa endpoint `GET /products`, `GET /products/mine/summary`, `PATCH …/primary`.
6. Ảnh đại diện: `POST /users/me/avatar` `@RequestPart("file")` → validate → upload `omnibid/avatars` → tx lưu `avatarUrl`, `avatarPublicId` → sau COMMIT xóa ảnh cũ. Trả 200 `{ avatarUrl }`. Lỗi DB → xóa ảnh mới (bù trừ).
7. Danh mục: `icon` (≤ 50 ký tự, `@Pattern("^[a-z0-9-]+$")`); `GET /categories` công khai có keyword + phân trang; admin CRUD ở phase 8 dùng chung Service; xóa `GET /categories/{id}`. Slug trùng → 400 VALIDATION_ERROR; xóa khi còn sản phẩm → 409 CATEGORY_IN_USE.
8. Ghi thay đổi hợp đồng vào `docs/v4/API_CHANGES_FOR_FRONTEND.md`.

## Cổng
`compile` sạch. Commit `refactor(v4): phase 4 - images, products, categories`.
