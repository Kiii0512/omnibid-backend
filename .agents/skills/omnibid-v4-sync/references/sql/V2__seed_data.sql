-- V2__seed_data.sql — trích nguyên văn từ 04_ThietKe_Omnibid_v4.docx, Phụ lục A.2

-- Tài khoản quản trị ban đầu. Mật khẩu khởi tạo: Admin@123456 (BCrypt, cost 10).
-- BẮT BUỘC đổi mật khẩu ngay sau lần đăng nhập đầu tiên (PATCH /users/me/password).
INSERT INTO `users` (`full_name`, `email`, `password_hash`, `role`, `status`)
VALUES ('Quản trị viên', 'admin@omnibid.local', '$2a$10$WqaGTHWjZFhAK2dEKsYOLO97jvDXMhBJjpfnFawcFJgZ.twzefJwW', 'ADMIN', 'ACTIVE');
-- Danh mục mẫu (icon là tên icon trong thư viện lucide-react)
INSERT INTO `categories` (`name`, `slug`, `icon`) VALUES
('Đồng hồ', 'dong-ho', 'watch'),
('Máy ảnh', 'may-anh', 'camera'),
('Điện thoại', 'dien-thoai', 'smartphone'),
('Sách', 'sach', 'book-open'),
('Đồ sưu tầm', 'do-suu-tam', 'gem'),
('Thời trang', 'thoi-trang', 'shirt');
