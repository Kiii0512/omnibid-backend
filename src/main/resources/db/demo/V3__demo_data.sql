-- V3__demo_data.sql — Dữ liệu demo cho Omnibid (chạy sau V1, V2).
-- MySQL 8.0.16+. Mọi mốc thời gian lưu theo UTC, tính tương đối từ thời điểm chạy migration (@now).
--
-- YÊU CẦU: placeholder Flyway cho Cloudinary, khai báo trong application.properties:
--   spring.flyway.placeholders.cloudinary_cloud_name=(lấy từ cloudinary.cloud-name)
-- Ảnh được upload sẵn bằng upload_to_cloudinary.mjs với public_id = omnibid/products/<tên file không đuôi>.
--
-- Tài khoản demo (mật khẩu chung: Demo@123456, BCrypt cost 10):
--   2 an.seller@omnibid.local     Người bán A  (sản phẩm 1–8)
--   3 binh.seller@omnibid.local   Người bán B  (sản phẩm 9–15)
--   4 cuong@omnibid.local         Người mua
--   5 dung@omnibid.local          Người mua
--   6 huy@omnibid.local           Người mua
--   7 lan.banned@omnibid.local    Tài khoản đã bị khóa (BANNED)
--
-- Kịch bản phiên đấu giá:
--   A1  Seiko 6139        ACTIVE     4 lượt, đã vượt giá sàn (reserve)
--   A2  G-Shock GA-100    ACTIVE     sắp kết thúc (~3 giờ), 3 lượt
--   A3  Canon AE-1        ACTIVE     chưa có lượt đặt giá
--   A4  Nikon D90         SCHEDULED  bắt đầu sau 1 ngày
--   A5  iPhone 13 Pro     ACTIVE     3 lượt, CHƯA đạt giá sàn
--   A6  Nokia 8210        ENDED  →  đơn 1 PENDING_PAYMENT (1 lần thanh toán thất bại)
--   A7  Bộ sách cổ        ENDED  →  đơn 2 PAID
--   A8  Đồng bạc 1908     ENDED  →  đơn 3 SHIPPING (thất bại 1 lần rồi thành công)
--   A9  Air Jordan IV     ENDED  →  đơn 4 COMPLETED
--   A10 Samsung i7500     UNSOLD     không đạt giá sàn
--   A11 Đồng hồ bỏ túi    CANCELLED
--   A12 Pioneer PL-200    ENDED  →  đơn 5 EXPIRED (quá hạn thanh toán 48h)
-- Quy ước trạng thái sản phẩm: phiên SCHEDULED/ACTIVE → IN_AUCTION; chờ thanh toán → RESERVED;
-- đã thanh toán trở đi → SOLD; UNSOLD/CANCELLED/EXPIRED → AVAILABLE trở lại.

SET @now  = UTC_TIMESTAMP(3);
SET @pwd  = '$2a$10$true7kMzs2wEtiHvTFzPu.Nq.zg9iAey0ib1Ajm4eDOF.G6x1SGa6'; -- Demo@123456
SET @cdn  = 'https://res.cloudinary.com/${cloudinary_cloud_name}/image/upload/';
SET @admin = (SELECT `id` FROM `users` WHERE `email` = 'admin@omnibid.local');

SET @c_dongho    = (SELECT `id` FROM `categories` WHERE `slug` = 'dong-ho');
SET @c_mayanh    = (SELECT `id` FROM `categories` WHERE `slug` = 'may-anh');
SET @c_dienthoai = (SELECT `id` FROM `categories` WHERE `slug` = 'dien-thoai');
SET @c_sach      = (SELECT `id` FROM `categories` WHERE `slug` = 'sach');
SET @c_suutam    = (SELECT `id` FROM `categories` WHERE `slug` = 'do-suu-tam');
SET @c_thoitrang = (SELECT `id` FROM `categories` WHERE `slug` = 'thoi-trang');

-- 1. NGƯỜI DÙNG
INSERT INTO `users` (`id`, `full_name`, `email`, `password_hash`, `phone_number`, `address`, `role`, `status`, `created_at`) VALUES
(2, 'Nguyễn Văn An',  'an.seller@omnibid.local',   @pwd, '0901234567', '12 Lê Lợi, Quận 1, TP. Hồ Chí Minh',          'USER', 'ACTIVE', @now - INTERVAL 60 DAY),
(3, 'Trần Thị Bình',  'binh.seller@omnibid.local', @pwd, '0912345678', '45 Hàng Bài, Hoàn Kiếm, Hà Nội',              'USER', 'ACTIVE', @now - INTERVAL 55 DAY),
(4, 'Lê Minh Cường',  'cuong@omnibid.local',       @pwd, '0923456789', '88 Nguyễn Văn Linh, Hải Châu, Đà Nẵng',       'USER', 'ACTIVE', @now - INTERVAL 40 DAY),
(5, 'Phạm Thu Dung',  'dung@omnibid.local',        @pwd, '0934567890', '210 Cách Mạng Tháng 8, Quận 3, TP. Hồ Chí Minh', 'USER', 'ACTIVE', @now - INTERVAL 35 DAY),
(6, 'Hoàng Gia Huy',  'huy@omnibid.local',         @pwd, '0945678901', '7 Trần Hưng Đạo, TP. Huế',                    'USER', 'ACTIVE', @now - INTERVAL 30 DAY),
(7, 'Vũ Thị Lan',     'lan.banned@omnibid.local',  @pwd, '0956789012', NULL,                                          'USER', 'BANNED', @now - INTERVAL 25 DAY);

-- 2. SẢN PHẨM
INSERT INTO `products` (`id`, `seller_id`, `category_id`, `title`, `description`, `item_condition`, `status`, `created_at`) VALUES
(1,  2, @c_dongho,    'Seiko 6139 "Pogue" chronograph tự động (1970s)',
     'Đồng hồ chronograph tự động Seiko cal. 6139, mặt vàng đặc trưng "Pogue". Máy chạy tốt, sai số khoảng ±20 giây/ngày, kính và vỏ có vết xước nhẹ theo thời gian.', 'GOOD', 'IN_AUCTION', @now - INTERVAL 10 DAY),
(2,  2, @c_dongho,    'Casio G-Shock GA-100 đen',
     'G-Shock GA-100 analog-digital, chống va đập, chống nước 200m. Dây và vỏ còn đẹp, đầy đủ chức năng, pin mới thay.', 'LIKE_NEW', 'IN_AUCTION', @now - INTERVAL 9 DAY),
(3,  2, @c_dongho,    'Đồng hồ bỏ túi cổ vỏ bạc',
     'Đồng hồ bỏ túi lên cót tay, vỏ bạc chạm hoa văn, mặt số La Mã. Máy chạy, cần bảo dưỡng định kỳ.', 'FAIR', 'AVAILABLE', @now - INTERVAL 8 DAY),
(4,  2, @c_mayanh,    'Canon AE-1 kèm ống kính 50mm f/1.8',
     'Máy ảnh film SLR Canon AE-1 kèm ống kính FD 50mm f/1.8. Đo sáng hoạt động, màn trập chính xác, đã thay lớp mút chống sáng.', 'GOOD', 'IN_AUCTION', @now - INTERVAL 7 DAY),
(5,  2, @c_mayanh,    'Nikon D90 (chỉ thân máy)',
     'DSLR Nikon D90, khoảng 18.000 lần chụp. Kèm pin, sạc và dây đeo. Không kèm ống kính.', 'GOOD', 'IN_AUCTION', @now - INTERVAL 6 DAY),
(6,  2, @c_dienthoai, 'iPhone 13 Pro 256GB',
     'iPhone 13 Pro 256GB, pin 88%, Face ID hoạt động, máy quốc tế. Kèm hộp và cáp.', 'LIKE_NEW', 'IN_AUCTION', @now - INTERVAL 5 DAY),
(7,  2, @c_dienthoai, 'Nokia 8210 nguyên bản (sưu tầm)',
     'Nokia 8210 đời đầu, nghe gọi tốt, vỏ thay thế màu sáng. Phù hợp sưu tầm.', 'FAIR', 'RESERVED', @now - INTERVAL 12 DAY),
(8,  2, @c_dienthoai, 'Samsung Galaxy i7500 (2009) Onyx Black',
     'Chiếc Galaxy đầu tiên của Samsung chạy Android. Lên nguồn bình thường, màn hình có xước nhẹ.', 'FAIR', 'AVAILABLE', @now - INTERVAL 14 DAY),
(9,  3, @c_sach,      'Bộ sách cổ bìa da (5 cuốn)',
     'Bộ 5 cuốn sách in thế kỷ 19, bìa da gáy mạ vàng. Giấy ngả màu tự nhiên, không rách trang.', 'FAIR', 'SOLD', @now - INTERVAL 15 DAY),
(10, 3, @c_sach,      'Lô sách cũ văn học (20 cuốn)',
     'Lô 20 cuốn sách văn học cũ nhiều thể loại, phù hợp trang trí kệ sách hoặc sưu tầm.', 'GOOD', 'AVAILABLE', @now - INTERVAL 4 DAY),
(11, 3, @c_suutam,    'Đồng bạc Đông Dương 1 Piastre 1908',
     'Đồng bạc thương mại Đông Dương (Piastre de Commerce) năm 1908, bạc 90%, nặng khoảng 27g. Mặt chữ rõ.', 'GOOD', 'SOLD', @now - INTERVAL 20 DAY),
(12, 3, @c_suutam,    'Mâm đĩa than Pioneer PL-200',
     'Mâm đĩa than Pioneer PL-200 truyền động trực tiếp, kèm chổi quét bụi. Tốc độ ổn định 33/45 vòng.', 'GOOD', 'AVAILABLE', @now - INTERVAL 16 DAY),
(13, 3, @c_thoitrang, 'Nike Air Jordan IV size 42',
     'Air Jordan IV size 42 (US 8.5), mang vài lần, đế còn tốt, kèm hộp.', 'LIKE_NEW', 'SOLD', @now - INTERVAL 25 DAY),
(14, 3, @c_thoitrang, 'Túi da chạm thủ công N. Raddi (vintage)',
     'Túi xách da chạm hoa văn thủ công, phong cách thập niên 1930–1940. Khóa kim loại còn chắc.', 'GOOD', 'AVAILABLE', @now - INTERVAL 3 DAY),
(15, 3, @c_thoitrang, 'Áo khoác da đen phong cách biker',
     'Áo khoác da thật màu đen, đã qua sử dụng, da mềm và có vết sờn tạo vẻ cổ điển.', 'FAIR', 'HIDDEN', @now - INTERVAL 11 DAY);

-- 3. ẢNH SẢN PHẨM (ảnh số 1 là ảnh đại diện)
INSERT INTO `product_images` (`product_id`, `image_url`, `public_id`, `is_primary`, `created_at`)
SELECT t.pid, CONCAT(@cdn, 'omnibid/products/', t.name, '.jpg'), CONCAT('omnibid/products/', t.name), t.pri, p.`created_at`
FROM (
  SELECT 1 AS pid, 'p01-seiko-6139-pogue-1' AS name, TRUE AS pri UNION ALL
  SELECT 1,  'p01-seiko-6139-pogue-2',          FALSE UNION ALL
  SELECT 2,  'p02-casio-gshock-ga100-1',        TRUE  UNION ALL
  SELECT 2,  'p02-casio-gshock-ga100-2',        FALSE UNION ALL
  SELECT 3,  'p03-dong-ho-bo-tui-co-1',         TRUE  UNION ALL
  SELECT 3,  'p03-dong-ho-bo-tui-co-2',         FALSE UNION ALL
  SELECT 4,  'p04-canon-ae1-1',                 TRUE  UNION ALL
  SELECT 4,  'p04-canon-ae1-2',                 FALSE UNION ALL
  SELECT 4,  'p04-canon-ae1-3',                 FALSE UNION ALL
  SELECT 5,  'p05-nikon-d90-1',                 TRUE  UNION ALL
  SELECT 5,  'p05-nikon-d90-2',                 FALSE UNION ALL
  SELECT 6,  'p06-iphone-13-pro-1',             TRUE  UNION ALL
  SELECT 6,  'p06-iphone-13-pro-2',             FALSE UNION ALL
  SELECT 7,  'p07-nokia-8210-1',                TRUE  UNION ALL
  SELECT 7,  'p07-nokia-8210-2',                FALSE UNION ALL
  SELECT 8,  'p08-samsung-galaxy-i7500-1',      TRUE  UNION ALL
  SELECT 8,  'p08-samsung-galaxy-i7500-2',      FALSE UNION ALL
  SELECT 9,  'p09-bo-sach-co-bia-da-1',         TRUE  UNION ALL
  SELECT 9,  'p09-bo-sach-co-bia-da-2',         FALSE UNION ALL
  SELECT 9,  'p09-bo-sach-co-bia-da-3',         FALSE UNION ALL
  SELECT 10, 'p10-lo-sach-cu-1',                TRUE  UNION ALL
  SELECT 10, 'p10-lo-sach-cu-2',                FALSE UNION ALL
  SELECT 11, 'p11-dong-bac-dong-duong-1908-1',  TRUE  UNION ALL
  SELECT 11, 'p11-dong-bac-dong-duong-1908-2',  FALSE UNION ALL
  SELECT 12, 'p12-pioneer-pl200-1',             TRUE  UNION ALL
  SELECT 12, 'p12-pioneer-pl200-2',             FALSE UNION ALL
  SELECT 12, 'p12-pioneer-pl200-3',             FALSE UNION ALL
  SELECT 13, 'p13-nike-air-jordan-4-1',         TRUE  UNION ALL
  SELECT 14, 'p14-tui-da-raddi-1',              TRUE  UNION ALL
  SELECT 14, 'p14-tui-da-raddi-2',              FALSE UNION ALL
  SELECT 14, 'p14-tui-da-raddi-3',              FALSE UNION ALL
  SELECT 15, 'p15-ao-khoac-da-den-1',           TRUE  UNION ALL
  SELECT 15, 'p15-ao-khoac-da-den-2',           FALSE
) AS t
JOIN `products` p ON p.`id` = t.pid
ORDER BY t.pid, t.name;

-- 4. PHIÊN ĐẤU GIÁ
INSERT INTO `auctions` (`id`, `product_id`, `seller_id`, `start_price`, `bid_increment`, `reserve_price`, `current_price`,
  `highest_bidder_id`, `bid_count`, `start_time`, `end_time`, `status`, `winner_id`, `final_price`, `closed_at`, `cancel_reason`, `created_at`) VALUES
-- Đang diễn ra
(1,  1,  2,  8000000,  200000,  9000000,  9500000, 6, 4, @now - INTERVAL 2 DAY,  @now + INTERVAL 2 DAY,  'ACTIVE',    NULL, NULL, NULL, NULL, @now - INTERVAL 3 DAY),
(2,  2,  2,  1500000,   50000,     NULL,  1800000, 5, 3, @now - INTERVAL 3 DAY,  @now + INTERVAL 3 HOUR, 'ACTIVE',    NULL, NULL, NULL, NULL, @now - INTERVAL 4 DAY),
(3,  4,  2,  3000000,  100000,     NULL,  3000000, NULL, 0, @now - INTERVAL 1 DAY, @now + INTERVAL 5 DAY, 'ACTIVE',  NULL, NULL, NULL, NULL, @now - INTERVAL 2 DAY),
(4,  5,  2,  4500000,  100000,     NULL,  4500000, NULL, 0, @now + INTERVAL 1 DAY, @now + INTERVAL 4 DAY, 'SCHEDULED', NULL, NULL, NULL, NULL, @now - INTERVAL 1 DAY),
(5,  6,  2, 10000000,  250000, 13000000, 11500000, 4, 3, @now - INTERVAL 2 DAY,  @now + INTERVAL 1 DAY,  'ACTIVE',    NULL, NULL, NULL, NULL, @now - INTERVAL 3 DAY),
-- Đã kết thúc có người thắng
(6,  7,  2,   500000,   50000,     NULL,   800000, 5, 3, @now - INTERVAL 4 DAY,  @now - INTERVAL 6 HOUR, 'ENDED', 5, 800000,  @now - INTERVAL 6 HOUR, NULL, @now - INTERVAL 5 DAY),
(7,  9,  3,  1500000,  100000,     NULL,  2400000, 4, 4, @now - INTERVAL 7 DAY,  @now - INTERVAL 3 DAY,  'ENDED', 4, 2400000, @now - INTERVAL 3 DAY,  NULL, @now - INTERVAL 8 DAY),
(8,  11, 3,  3000000,  200000,     NULL,  4200000, 6, 4, @now - INTERVAL 10 DAY, @now - INTERVAL 6 DAY, 'ENDED', 6, 4200000, @now - INTERVAL 6 DAY,  NULL, @now - INTERVAL 11 DAY),
(9,  13, 3,  2000000,  100000,     NULL,  2800000, 5, 4, @now - INTERVAL 20 DAY, @now - INTERVAL 15 DAY, 'ENDED', 5, 2800000, @now - INTERVAL 15 DAY, NULL, @now - INTERVAL 21 DAY),
-- Không bán được / bị hủy / người thắng không thanh toán
(10, 8,  2,   800000,   50000,  1500000,  1000000, 4, 2, @now - INTERVAL 5 DAY,  @now - INTERVAL 2 DAY,  'UNSOLD',    NULL, NULL, @now - INTERVAL 2 DAY, NULL, @now - INTERVAL 6 DAY),
(11, 3,  2,  5000000,  200000,     NULL,  5000000, NULL, 0, @now - INTERVAL 3 DAY, @now + INTERVAL 4 DAY, 'CANCELLED', NULL, NULL, @now - INTERVAL 1 DAY,
     'Người bán yêu cầu hủy: mô tả tình trạng máy chưa chính xác', @now - INTERVAL 4 DAY),
(12, 12, 3,  2500000,  100000,     NULL,  2900000, 4, 2, @now - INTERVAL 9 DAY,  @now - INTERVAL 5 DAY,  'ENDED', 4, 2900000, @now - INTERVAL 5 DAY,  NULL, @now - INTERVAL 10 DAY);

-- 5. LƯỢT ĐẶT GIÁ (mỗi lượt >= giá hiện tại + bước giá; lượt cuối = current_price)
INSERT INTO `bids` (`auction_id`, `bidder_id`, `amount`, `created_at`) VALUES
(1, 4,  8200000, @now - INTERVAL 40 HOUR),
(1, 5,  8600000, @now - INTERVAL 30 HOUR),
(1, 4,  9000000, @now - INTERVAL 20 HOUR),
(1, 6,  9500000, @now - INTERVAL 5 HOUR),
(2, 5,  1550000, @now - INTERVAL 50 HOUR),
(2, 6,  1650000, @now - INTERVAL 24 HOUR),
(2, 5,  1800000, @now - INTERVAL 2 HOUR),
(5, 4, 10250000, @now - INTERVAL 36 HOUR),
(5, 6, 10750000, @now - INTERVAL 20 HOUR),
(5, 4, 11500000, @now - INTERVAL 6 HOUR),
(6, 5,   550000, @now - INTERVAL 3 DAY),
(6, 4,   650000, @now - INTERVAL 2 DAY),
(6, 5,   800000, @now - INTERVAL 10 HOUR),
(7, 5,  1600000, @now - INTERVAL 6 DAY),
(7, 4,  1900000, @now - INTERVAL 5 DAY),
(7, 5,  2100000, @now - INTERVAL 4 DAY),
(7, 4,  2400000, @now - INTERVAL 74 HOUR),
(8, 4,  3200000, @now - INTERVAL 9 DAY),
(8, 6,  3600000, @now - INTERVAL 8 DAY),
(8, 4,  3800000, @now - INTERVAL 7 DAY),
(8, 6,  4200000, @now - INTERVAL 147 HOUR),
(9, 4,  2100000, @now - INTERVAL 19 DAY),
(9, 5,  2300000, @now - INTERVAL 18 DAY),
(9, 6,  2500000, @now - INTERVAL 17 DAY),
(9, 5,  2800000, @now - INTERVAL 16 DAY),
(10, 6,  850000, @now - INTERVAL 4 DAY),
(10, 4, 1000000, @now - INTERVAL 3 DAY),
(12, 5, 2600000, @now - INTERVAL 8 DAY),
(12, 4, 2900000, @now - INTERVAL 7 DAY);

-- 6. ĐƠN HÀNG (payment_deadline = closed_at + 48h; confirm_deadline = shipped_at + 7 ngày)
INSERT INTO `orders` (`id`, `auction_id`, `product_id`, `seller_id`, `buyer_id`, `final_price`, `shipping_address`, `phone_number`, `status`,
  `payment_deadline`, `paid_at`, `tracking_code`, `shipped_at`, `confirm_deadline`, `completed_at`, `cancelled_at`, `cancel_reason`, `created_at`) VALUES
(1, 6,  7,  2, 5,  800000, NULL, NULL, 'PENDING_PAYMENT',
    @now - INTERVAL 6 HOUR + INTERVAL 48 HOUR, NULL, NULL, NULL, NULL, NULL, NULL, NULL, @now - INTERVAL 6 HOUR),
(2, 7,  9,  3, 4, 2400000, '88 Nguyễn Văn Linh, Hải Châu, Đà Nẵng', '0923456789', 'PAID',
    @now - INTERVAL 1 DAY, @now - INTERVAL 2 DAY, NULL, NULL, NULL, NULL, NULL, NULL, @now - INTERVAL 3 DAY),
(3, 8,  11, 3, 6, 4200000, '7 Trần Hưng Đạo, TP. Huế', '0945678901', 'SHIPPING',
    @now - INTERVAL 4 DAY, @now - INTERVAL 5 DAY, 'GHN84512376VN', @now - INTERVAL 4 DAY, @now + INTERVAL 3 DAY, NULL, NULL, NULL, @now - INTERVAL 6 DAY),
(4, 9,  13, 3, 5, 2800000, '210 Cách Mạng Tháng 8, Quận 3, TP. Hồ Chí Minh', '0934567890', 'COMPLETED',
    @now - INTERVAL 13 DAY, @now - INTERVAL 14 DAY, 'GHTK2210557VN', @now - INTERVAL 13 DAY, @now - INTERVAL 6 DAY, @now - INTERVAL 10 DAY, NULL, NULL, @now - INTERVAL 15 DAY),
(5, 12, 12, 3, 4, 2900000, NULL, NULL, 'EXPIRED',
    @now - INTERVAL 3 DAY, NULL, NULL, NULL, NULL, NULL, @now - INTERVAL 3 DAY, 'Quá hạn thanh toán 48 giờ', @now - INTERVAL 5 DAY);

-- 7. THANH TOÁN MÔ PHỎNG
INSERT INTO `payments` (`order_id`, `attempt_no`, `amount`, `method`, `status`, `idempotency_key`, `failure_reason`, `paid_at`, `created_at`) VALUES
(1, 1,  800000, 'MOCK_CARD', 'FAILED',  'demo-order1-attempt1', 'Thẻ bị từ chối (mô phỏng)', NULL, @now - INTERVAL 2 HOUR),
(2, 1, 2400000, 'MOCK_CARD', 'SUCCESS', 'demo-order2-attempt1', NULL, @now - INTERVAL 2 DAY, @now - INTERVAL 2 DAY),
(3, 1, 4200000, 'MOCK_CARD', 'FAILED',  'demo-order3-attempt1', 'Số dư không đủ (mô phỏng)', NULL, @now - INTERVAL 121 HOUR),
(3, 2, 4200000, 'MOCK_CARD', 'SUCCESS', 'demo-order3-attempt2', NULL, @now - INTERVAL 5 DAY, @now - INTERVAL 5 DAY),
(4, 1, 2800000, 'MOCK_CARD', 'SUCCESS', 'demo-order4-attempt1', NULL, @now - INTERVAL 14 DAY, @now - INTERVAL 14 DAY);

-- 8. BÁO CÁO VI PHẠM
INSERT INTO `reports` (`reporter_id`, `reported_user_id`, `reported_product_id`, `reported_order_id`, `reason`, `status`, `admin_note`, `resolved_by`, `resolved_at`, `created_at`) VALUES
(4, NULL, 15, NULL, 'Ảnh sản phẩm có dấu hiệu lấy từ internet, không phải ảnh thật của món hàng.', 'RESOLVED',
    'Đã ẩn sản phẩm, yêu cầu người bán cập nhật ảnh thật.', @admin, @now - INTERVAL 9 DAY, @now - INTERVAL 10 DAY),
(5, 7, NULL, NULL, 'Tài khoản liên tục đặt giá rồi bỏ không thanh toán, nhắn tin quấy rối.', 'RESOLVED',
    'Xác minh vi phạm nhiều lần, đã khóa tài khoản.', @admin, @now - INTERVAL 20 DAY, @now - INTERVAL 22 DAY),
(6, NULL, NULL, 3, 'Đơn hàng đã giao cho đơn vị vận chuyển 4 ngày nhưng chưa cập nhật hành trình.', 'PENDING',
    NULL, NULL, NULL, @now - INTERVAL 1 DAY),
(4, NULL, 2, NULL, 'Nghi ngờ hàng không chính hãng.', 'REJECTED',
    'Người bán đã cung cấp hóa đơn mua hàng, không đủ căn cứ vi phạm.', @admin, @now - INTERVAL 1 DAY, @now - INTERVAL 2 DAY);

-- 9. THÔNG BÁO
INSERT INTO `notifications` (`user_id`, `type`, `title`, `content`, `ref_type`, `ref_id`, `is_read`, `created_at`) VALUES
(4, 'OUTBID', 'Bạn đã bị vượt giá', 'Có người vừa đặt 9.500.000 ₫ cho "Seiko 6139 "Pogue" chronograph tự động (1970s)".', 'AUCTION', 1, FALSE, @now - INTERVAL 5 HOUR),
(5, 'OUTBID', 'Bạn đã bị vượt giá', 'Có người vừa đặt 9.000.000 ₫ cho "Seiko 6139 "Pogue" chronograph tự động (1970s)".', 'AUCTION', 1, TRUE,  @now - INTERVAL 20 HOUR),
(6, 'OUTBID', 'Bạn đã bị vượt giá', 'Có người vừa đặt 1.800.000 ₫ cho "Casio G-Shock GA-100 đen". Phiên sắp kết thúc!', 'AUCTION', 2, FALSE, @now - INTERVAL 2 HOUR),
(6, 'OUTBID', 'Bạn đã bị vượt giá', 'Có người vừa đặt 11.500.000 ₫ cho "iPhone 13 Pro 256GB".', 'AUCTION', 5, FALSE, @now - INTERVAL 6 HOUR),
(5, 'AUCTION_RESULT', 'Bạn đã thắng phiên đấu giá', 'Bạn thắng "Nokia 8210 nguyên bản (sưu tầm)" với giá 800.000 ₫. Vui lòng thanh toán trong 48 giờ.', 'ORDER', 1, FALSE, @now - INTERVAL 6 HOUR),
(2, 'AUCTION_RESULT', 'Phiên đấu giá đã kết thúc', '"Nokia 8210 nguyên bản (sưu tầm)" đã bán với giá 800.000 ₫, đang chờ người mua thanh toán.', 'AUCTION', 6, FALSE, @now - INTERVAL 6 HOUR),
(2, 'AUCTION_RESULT', 'Phiên đấu giá không thành công', '"Samsung Galaxy i7500 (2009) Onyx Black" chưa đạt giá sàn. Bạn có thể tạo phiên mới.', 'AUCTION', 10, TRUE, @now - INTERVAL 2 DAY),
(2, 'AUCTION_CANCELLED', 'Phiên đấu giá đã bị hủy', 'Phiên đấu giá "Đồng hồ bỏ túi cổ vỏ bạc" đã được hủy theo yêu cầu.', 'AUCTION', 11, TRUE, @now - INTERVAL 1 DAY),
(4, 'AUCTION_RESULT', 'Bạn đã thắng phiên đấu giá', 'Bạn thắng "Bộ sách cổ bìa da (5 cuốn)" với giá 2.400.000 ₫.', 'ORDER', 2, TRUE, @now - INTERVAL 3 DAY),
(3, 'ORDER_UPDATE', 'Đơn hàng đã được thanh toán', 'Đơn #2 "Bộ sách cổ bìa da (5 cuốn)" đã thanh toán. Hãy giao hàng cho người mua.', 'ORDER', 2, FALSE, @now - INTERVAL 2 DAY),
(6, 'ORDER_UPDATE', 'Đơn hàng đang được giao', 'Đơn #3 đã giao cho đơn vị vận chuyển, mã vận đơn GHN84512376VN.', 'ORDER', 3, TRUE, @now - INTERVAL 4 DAY),
(3, 'ORDER_UPDATE', 'Đơn hàng đã hoàn tất', 'Người mua đã xác nhận nhận hàng đơn #4 "Nike Air Jordan IV size 42".', 'ORDER', 4, TRUE, @now - INTERVAL 10 DAY),
(4, 'ORDER_UPDATE', 'Đơn hàng đã hết hạn', 'Đơn #5 "Mâm đĩa than Pioneer PL-200" bị hủy do quá hạn thanh toán 48 giờ.', 'ORDER', 5, TRUE, @now - INTERVAL 3 DAY),
(3, 'ORDER_UPDATE', 'Đơn hàng đã hết hạn', 'Người mua không thanh toán đơn #5, sản phẩm "Mâm đĩa than Pioneer PL-200" đã mở bán lại.', 'ORDER', 5, FALSE, @now - INTERVAL 3 DAY),
(4, 'REPORT_RESOLVED', 'Báo cáo đã được xử lý', 'Báo cáo của bạn về sản phẩm "Áo khoác da đen phong cách biker" đã được xử lý.', 'REPORT', 1, TRUE, @now - INTERVAL 9 DAY),
(5, 'REPORT_RESOLVED', 'Báo cáo đã được xử lý', 'Tài khoản bạn báo cáo đã bị khóa.', 'REPORT', 2, TRUE, @now - INTERVAL 20 DAY),
(4, 'REPORT_RESOLVED', 'Báo cáo đã được xem xét', 'Báo cáo về "Casio G-Shock GA-100 đen" không đủ căn cứ vi phạm.', 'REPORT', 4, FALSE, @now - INTERVAL 1 DAY);
