-- OmniBid Seed Demo Data
-- Tài khoản demo (Mật khẩu gốc cho tất cả tài khoản: Demo@123)
-- BCrypt hash ($2a$10$ZwPf6iJlgIdhRui.qUcbzusLS5Y.etDe/o2vQhgGl72x25It0OP76)
--
-- Dữ liệu mẫu tuân thủ tuyệt đối 14 quy tắc bất biến tại DATABASE_DESIGN.md §8 (INV-1 -> INV-14)

-- 1. users (1 Admin, 4 Users: seller1, buyer1, buyer2, buyer3)
INSERT INTO `users` (`id`, `full_name`, `email`, `password_hash`, `phone_number`, `avatar_url`, `address`, `role`, `status`) VALUES
(1, 'Admin OmniBid', 'admin@omnibid.com', '$2a$10$ZwPf6iJlgIdhRui.qUcbzusLS5Y.etDe/o2vQhgGl72x25It0OP76', '0901000001', 'https://avatar.iran.liara.run/public/boy?username=admin', 'Hà Nội', 'ADMIN', 'ACTIVE'),
(2, 'Nguyen Van Seller', 'seller1@omnibid.com', '$2a$10$ZwPf6iJlgIdhRui.qUcbzusLS5Y.etDe/o2vQhgGl72x25It0OP76', '0901000002', 'https://avatar.iran.liara.run/public/boy?username=seller1', 'Hà Nội', 'USER', 'ACTIVE'),
(3, 'Tran Thi Buyer 1', 'buyer1@omnibid.com', '$2a$10$ZwPf6iJlgIdhRui.qUcbzusLS5Y.etDe/o2vQhgGl72x25It0OP76', '0901000003', 'https://avatar.iran.liara.run/public/girl?username=buyer1', 'TP. Hồ Chí Minh', 'USER', 'ACTIVE'),
(4, 'Le Van Buyer 2', 'buyer2@omnibid.com', '$2a$10$ZwPf6iJlgIdhRui.qUcbzusLS5Y.etDe/o2vQhgGl72x25It0OP76', '0901000004', 'https://avatar.iran.liara.run/public/boy?username=buyer2', 'Đà Nẵng', 'USER', 'ACTIVE'),
(5, 'Pham Van Buyer 3', 'buyer3@omnibid.com', '$2a$10$ZwPf6iJlgIdhRui.qUcbzusLS5Y.etDe/o2vQhgGl72x25It0OP76', '0901000005', 'https://avatar.iran.liara.run/public/boy?username=buyer3', 'Hải Phòng', 'USER', 'ACTIVE');

-- 2. categories (3 categories)
INSERT INTO `categories` (`id`, `name`, `slug`, `icon_url`) VALUES
(1, 'Đồng hồ & Trang sức', 'dong-ho-trang-suc', 'https://example.com/icons/watch.png'),
(2, 'Đồ điện tử & Công nghệ', 'dien-tu-cong-nghe', 'https://example.com/icons/tech.png'),
(3, 'Đồ cổ & Sưu tầm', 'do-co-suu-tam', 'https://example.com/icons/antique.png');

-- 3. products (8 products ở các trạng thái khác nhau)
INSERT INTO `products` (`id`, `seller_id`, `category_id`, `title`, `description`, `condition`, `status`) VALUES
(1, 2, 1, 'Đồng hồ Rolex Submariner Date', 'Rolex Submariner Date thép Oystersteel chính hãng', 'LIKE_NEW', 'IN_AUCTION'),
(2, 2, 2, 'iPhone 15 Pro Max 256GB', 'iPhone 15 Pro Max màu titan tự nhiên nguyên hộp', 'NEW', 'IN_AUCTION'),
(3, 2, 2, 'MacBook Pro 16 inch M3 Max', 'MacBook Pro 16 inch 36GB RAM 1TB SSD Space Black', 'LIKE_NEW', 'IN_AUCTION'),
(4, 2, 1, 'Đồng hồ Omega Speedmaster Moonwatch', 'Omega Speedmaster Professional Chronograph 42mm', 'USED', 'RESERVED'),
(5, 2, 3, 'Bình gốm thời Chu thế kỷ 18', 'Bình gốm hoa lam cổ thời Chu nguyên bản men lam', 'USED', 'AVAILABLE'),
(6, 2, 3, 'Tiền xu cổ Đông Dương 1885', 'Đồng bạc Đông Dương Piastre de Commerce hiếm', 'USED', 'AVAILABLE'),
(7, 2, 2, 'Sony PlayStation 5 Slim', 'Máy chơi game PS5 Slim bản đĩa đã qua sử dụng', 'USED', 'SOLD'),
(8, 2, 3, 'Tranh sơn dầu nghệ thuật cổ', 'Tranh sơn dầu trừu tượng không rõ nguồn gốc', 'USED', 'HIDDEN');

-- 4. product_images (1..5 images per product)
INSERT INTO `product_images` (`id`, `product_id`, `image_url`, `is_primary`) VALUES
(1, 1, 'https://images.unsplash.com/photo-1523275335684-37898b6baf30', TRUE),
(2, 2, 'https://images.unsplash.com/photo-1511707171634-5f897ff02aa9', TRUE),
(3, 3, 'https://images.unsplash.com/photo-1517336714731-489689fd1ca8', TRUE),
(4, 4, 'https://images.unsplash.com/photo-1522335789203-aabd1fc54bc9', TRUE),
(5, 5, 'https://images.unsplash.com/photo-1578749556568-bc2c40e68b61', TRUE),
(6, 6, 'https://images.unsplash.com/photo-1558611848-73f7eb4001a1', TRUE),
(7, 7, 'https://images.unsplash.com/photo-1606813907291-d86efa9b94db', TRUE),
(8, 8, 'https://images.unsplash.com/photo-1579783902614-a3fb3927b675', TRUE);

-- 5. auctions (6 phiên: SCHEDULED, ACTIVE 0 bid, ACTIVE 3 bids, ENDED kèm Order, UNSOLD, CANCELLED)
INSERT INTO `auctions` (`id`, `product_id`, `seller_id`, `start_price`, `bid_increment`, `reserve_price`, `current_price`, `highest_bidder_id`, `bid_count`, `start_time`, `end_time`, `status`, `winner_id`, `final_price`, `closed_at`, `cancel_reason`, `version`) VALUES
(1, 1, 2, 50000000.00, 1000000.00, NULL, 50000000.00, NULL, 0, '2026-11-05 08:00:00', '2026-11-07 08:00:00', 'SCHEDULED', NULL, NULL, NULL, NULL, 0),
(2, 2, 2, 25000000.00, 500000.00, NULL, 25000000.00, NULL, 0, '2026-11-01 08:00:00', '2026-11-04 08:00:00', 'ACTIVE', NULL, NULL, NULL, NULL, 0),
(3, 3, 2, 60000000.00, 1000000.00, NULL, 63000000.00, 5, 3, '2026-11-01 08:00:00', '2026-11-04 08:00:00', 'ACTIVE', NULL, NULL, NULL, NULL, 3),
(4, 4, 2, 40000000.00, 1000000.00, 42000000.00, 43000000.00, 3, 2, '2026-10-30 08:00:00', '2026-11-02 10:00:00', 'ENDED', 3, 43000000.00, '2026-11-02 10:00:00', NULL, 2),
(5, 5, 2, 10000000.00, 500000.00, 15000000.00, 12000000.00, 4, 1, '2026-10-28 08:00:00', '2026-10-31 08:00:00', 'UNSOLD', NULL, NULL, '2026-10-31 08:00:00', NULL, 1),
(6, 6, 2, 2000000.00, 100000.00, NULL, 2000000.00, NULL, 0, '2026-10-29 08:00:00', '2026-10-30 08:00:00', 'CANCELLED', NULL, NULL, '2026-10-29 12:00:00', 'Người bán yêu cầu hủy phiên khi chưa có người đặt giá', 0);

-- 6. bids (Auction 3 có 3 bids; Auction 4 có 2 bids; Auction 5 có 1 bid)
INSERT INTO `bids` (`id`, `auction_id`, `bidder_id`, `amount`, `status`, `invalid_reason`, `created_at`) VALUES
-- Auction 3 (ACTIVE, current_price=63000000, highest_bidder_id=5)
(1, 3, 3, 61000000.00, 'OUTBID', NULL, '2026-11-01 09:00:00.000'),
(2, 3, 4, 62000000.00, 'OUTBID', NULL, '2026-11-01 10:00:00.000'),
(3, 3, 5, 63000000.00, 'LEADING', NULL, '2026-11-01 11:00:00.000'),
-- Auction 4 (ENDED, winner_id=3, final_price=43000000)
(4, 4, 4, 41000000.00, 'OUTBID', NULL, '2026-10-30 09:00:00.000'),
(5, 4, 3, 43000000.00, 'WON', NULL, '2026-10-30 10:00:00.000'),
-- Auction 5 (UNSOLD do không đạt giá sàn 15tr)
(6, 5, 4, 12000000.00, 'NOT_WON', NULL, '2026-10-28 09:00:00.000');

-- 7. orders (Đúng 1 order cho Auction 4 ENDED, payment_deadline = closed_at + 48h)
INSERT INTO `orders` (`id`, `auction_id`, `product_id`, `seller_id`, `buyer_id`, `final_price`, `shipping_address`, `phone_number`, `status`, `payment_deadline`, `paid_at`, `delivered_at`, `confirm_deadline`, `reminder_24h_sent_at`, `reminder_2h_sent_at`, `completed_at`, `cancelled_at`, `cancel_reason`, `created_at`) VALUES
(1, 4, 4, 2, 3, 43000000.00, '123 Nguyen Hue, Quan 1, TP.HCM', '0901000003', 'PENDING_PAYMENT', '2026-11-04 10:00:00', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-11-02 10:00:00');

-- 8. watchlists
INSERT INTO `watchlists` (`id`, `user_id`, `auction_id`) VALUES
(1, 3, 1),
(2, 4, 2),
(3, 5, 3);
