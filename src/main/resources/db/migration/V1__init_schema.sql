-- V1__init_schema.sql — trích nguyên văn từ 04_ThietKe_Omnibid_v4.docx, Phụ lục A.1
-- MySQL 8.0.16+. Database tạo trước với utf8mb4 / utf8mb4_unicode_ci.

-- 1. TÀI KHOẢN VÀ DANH MỤC
CREATE TABLE `users` (
`id` BIGINT AUTO_INCREMENT PRIMARY KEY,
`full_name` VARCHAR(255) NOT NULL,
`email` VARCHAR(255) NOT NULL UNIQUE,
`password_hash` VARCHAR(255) NOT NULL,
`phone_number` VARCHAR(20) DEFAULT NULL,
`avatar_url` VARCHAR(500) DEFAULT NULL,
`avatar_public_id` VARCHAR(255) DEFAULT NULL, -- public_id ảnh đại diện trên Cloudinary
`address` VARCHAR(255) DEFAULT NULL,
`role` ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER',
`status` ENUM('ACTIVE','BANNED') NOT NULL DEFAULT 'ACTIVE',
`token_version` INT NOT NULL DEFAULT 0, -- tăng khi đăng xuất, đổi mật khẩu
`created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
`updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB;
CREATE TABLE `categories` (
`id` BIGINT AUTO_INCREMENT PRIMARY KEY,
`name` VARCHAR(100) NOT NULL,
`slug` VARCHAR(100) NOT NULL UNIQUE,
`icon` VARCHAR(50) DEFAULT NULL, -- tên icon lucide-react, ví dụ watch
`created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB;
-- 2. SẢN PHẨM
CREATE TABLE `products` (
`id` BIGINT AUTO_INCREMENT PRIMARY KEY,
`seller_id` BIGINT NOT NULL,
`category_id` BIGINT NOT NULL,
`title` VARCHAR(255) NOT NULL,
`description` TEXT NOT NULL,
`item_condition` ENUM('NEW','LIKE_NEW','GOOD','FAIR','POOR') NOT NULL,
`status` ENUM('AVAILABLE','IN_AUCTION','RESERVED','SOLD','HIDDEN') NOT NULL DEFAULT 'AVAILABLE',
`created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
`updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
INDEX `idx_products_category_status` (`category_id`, `status`),
INDEX `idx_products_seller` (`seller_id`),
CONSTRAINT `fk_products_seller` FOREIGN KEY (`seller_id`) REFERENCES `users`(`id`),
CONSTRAINT `fk_products_category` FOREIGN KEY (`category_id`) REFERENCES `categories`(`id`)
) ENGINE=InnoDB;
CREATE TABLE `product_images` ( -- mỗi sản phẩm 1-5 ảnh, kiểm tra ở Service
`id` BIGINT AUTO_INCREMENT PRIMARY KEY,
`product_id` BIGINT NOT NULL,
`image_url` VARCHAR(500) NOT NULL,
`public_id` VARCHAR(255) NOT NULL, -- dùng để xóa asset trên Cloudinary
`is_primary` BOOLEAN NOT NULL DEFAULT FALSE,
`created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
CONSTRAINT `fk_images_product` FOREIGN KEY (`product_id`) REFERENCES `products`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB;
-- 3. PHIÊN ĐẤU GIÁ VÀ ĐẶT GIÁ
CREATE TABLE `auctions` (
`id` BIGINT AUTO_INCREMENT PRIMARY KEY,
`product_id` BIGINT NOT NULL,
`seller_id` BIGINT NOT NULL,
`start_price` DECIMAL(15,0) NOT NULL,
`bid_increment` DECIMAL(15,0) NOT NULL,
`reserve_price` DECIMAL(15,0) DEFAULT NULL,
`current_price` DECIMAL(15,0) NOT NULL, -- khởi tạo = start_price
`highest_bidder_id` BIGINT DEFAULT NULL,
`bid_count` INT NOT NULL DEFAULT 0, -- số lượt đặt giá của phiên
`start_time` DATETIME(3) NOT NULL, -- UTC
`end_time` DATETIME(3) NOT NULL,
`status` ENUM('SCHEDULED','ACTIVE','ENDED','UNSOLD','CANCELLED') NOT NULL DEFAULT 'SCHEDULED',
`active_product_id` BIGINT GENERATED ALWAYS AS (IF(`status` IN ('SCHEDULED','ACTIVE'), `product_id`, NULL)) STORED,
`winner_id` BIGINT DEFAULT NULL,
`final_price` DECIMAL(15,0) DEFAULT NULL,
`closed_at` DATETIME(3) NULL DEFAULT NULL, -- thời điểm chốt thực tế
`cancel_reason` VARCHAR(255) DEFAULT NULL,
`created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
`updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
UNIQUE KEY `uq_auctions_active_product` (`active_product_id`), -- BR-15
INDEX `idx_auctions_status_end` (`status`, `end_time`),
INDEX `idx_auctions_status_start` (`status`, `start_time`),
INDEX `idx_auctions_product` (`product_id`),
INDEX `idx_auctions_seller` (`seller_id`),
CONSTRAINT `chk_auctions_start_price` CHECK (`start_price` > 0),
CONSTRAINT `chk_auctions_increment` CHECK (`bid_increment` > 0),
CONSTRAINT `chk_auctions_reserve` CHECK (`reserve_price` IS NULL OR `reserve_price` >= `start_price`),
CONSTRAINT `chk_auctions_time` CHECK (`end_time` > `start_time`),
CONSTRAINT `fk_auctions_product` FOREIGN KEY (`product_id`) REFERENCES `products`(`id`),
CONSTRAINT `fk_auctions_seller` FOREIGN KEY (`seller_id`) REFERENCES `users`(`id`),
CONSTRAINT `fk_auctions_leader` FOREIGN KEY (`highest_bidder_id`) REFERENCES `users`(`id`),
CONSTRAINT `fk_auctions_winner` FOREIGN KEY (`winner_id`) REFERENCES `users`(`id`)
) ENGINE=InnoDB;
CREATE TABLE `bids` (
`id` BIGINT AUTO_INCREMENT PRIMARY KEY,
`auction_id` BIGINT NOT NULL,
`bidder_id` BIGINT NOT NULL,
`amount` DECIMAL(15,0) NOT NULL,
`created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
UNIQUE KEY `uq_bids_auction_amount` (`auction_id`, `amount`),
INDEX `idx_bids_auction_time` (`auction_id`, `created_at`),
INDEX `idx_bids_bidder` (`bidder_id`),
CONSTRAINT `chk_bids_amount` CHECK (`amount` > 0),
CONSTRAINT `fk_bids_auction` FOREIGN KEY (`auction_id`) REFERENCES `auctions`(`id`),
CONSTRAINT `fk_bids_bidder` FOREIGN KEY (`bidder_id`) REFERENCES `users`(`id`)
) ENGINE=InnoDB;
-- 4. ĐƠN HÀNG VÀ THANH TOÁN MÔ PHỎNG
CREATE TABLE `orders` (
`id` BIGINT AUTO_INCREMENT PRIMARY KEY,
`auction_id` BIGINT NOT NULL,
`product_id` BIGINT NOT NULL,
`seller_id` BIGINT NOT NULL,
`buyer_id` BIGINT NOT NULL,
`final_price` DECIMAL(15,0) NOT NULL,
`shipping_address` VARCHAR(255) DEFAULT NULL,
`phone_number` VARCHAR(20) DEFAULT NULL,
`status` ENUM('PENDING_PAYMENT','PAID','SHIPPING','COMPLETED','CANCELLED','EXPIRED') NOT NULL DEFAULT 'PENDING_PAYMENT',
`payment_deadline` DATETIME(3) NOT NULL, -- closed_at + 48 giờ
`paid_at` DATETIME(3) NULL DEFAULT NULL,
`tracking_code` VARCHAR(50) DEFAULT NULL,
`shipped_at` DATETIME(3) NULL DEFAULT NULL,
`confirm_deadline` DATETIME(3) NULL DEFAULT NULL, -- shipped_at + 7 ngày
`completed_at` DATETIME(3) NULL DEFAULT NULL,
`cancelled_at` DATETIME(3) NULL DEFAULT NULL,
`cancel_reason` VARCHAR(255) DEFAULT NULL,
`created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
`updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
UNIQUE KEY `uq_orders_auction` (`auction_id`),
INDEX `idx_orders_buyer` (`buyer_id`),
INDEX `idx_orders_seller` (`seller_id`),
INDEX `idx_orders_status_deadline` (`status`, `payment_deadline`),
INDEX `idx_orders_status_confirm` (`status`, `confirm_deadline`),
CONSTRAINT `chk_orders_price` CHECK (`final_price` > 0),
CONSTRAINT `chk_orders_parties` CHECK (`seller_id` <> `buyer_id`),
CONSTRAINT `fk_orders_auction` FOREIGN KEY (`auction_id`) REFERENCES `auctions`(`id`),
CONSTRAINT `fk_orders_product` FOREIGN KEY (`product_id`) REFERENCES `products`(`id`),
CONSTRAINT `fk_orders_seller` FOREIGN KEY (`seller_id`) REFERENCES `users`(`id`),
CONSTRAINT `fk_orders_buyer` FOREIGN KEY (`buyer_id`) REFERENCES `users`(`id`)
) ENGINE=InnoDB;
CREATE TABLE `payments` (
`id` BIGINT AUTO_INCREMENT PRIMARY KEY,
`order_id` BIGINT NOT NULL,
`attempt_no` INT NOT NULL,
`amount` DECIMAL(15,0) NOT NULL,
`method` ENUM('MOCK_CARD') NOT NULL DEFAULT 'MOCK_CARD',
`status` ENUM('SUCCESS','FAILED') NOT NULL,
`idempotency_key` VARCHAR(64) NOT NULL, -- Frontend sinh cho mỗi lần bấm Thanh toán
`failure_reason` VARCHAR(255) DEFAULT NULL,
`paid_at` DATETIME(3) NULL DEFAULT NULL, -- thời điểm thành công
`created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
`success_order_id` BIGINT GENERATED ALWAYS AS (IF(`status` = 'SUCCESS', `order_id`, NULL)) STORED,
UNIQUE KEY `uq_payments_order_attempt` (`order_id`, `attempt_no`),
UNIQUE KEY `uq_payments_one_success` (`success_order_id`),
UNIQUE KEY `uq_payments_idempotency` (`order_id`, `idempotency_key`),
CONSTRAINT `chk_payments_amount` CHECK (`amount` > 0),
CONSTRAINT `chk_payments_attempt` CHECK (`attempt_no` >= 1),
CONSTRAINT `fk_payments_order` FOREIGN KEY (`order_id`) REFERENCES `orders`(`id`)
) ENGINE=InnoDB;
-- 5. THÔNG BÁO VÀ BÁO CÁO
CREATE TABLE `notifications` (
`id` BIGINT AUTO_INCREMENT PRIMARY KEY,
`user_id` BIGINT NOT NULL,
`type` ENUM('OUTBID','AUCTION_RESULT','AUCTION_CANCELLED','ORDER_UPDATE','REPORT_RESOLVED') NOT NULL,
`title` VARCHAR(255) NOT NULL,
`content` TEXT NOT NULL,
`ref_type` VARCHAR(30) DEFAULT NULL, -- AUCTION, ORDER, REPORT
`ref_id` BIGINT DEFAULT NULL,
`is_read` BOOLEAN NOT NULL DEFAULT FALSE,
`created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
INDEX `idx_notifications_user_read` (`user_id`, `is_read`, `created_at`),
CONSTRAINT `fk_notifications_user` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE TABLE `reports` (
`id` BIGINT AUTO_INCREMENT PRIMARY KEY,
`reporter_id` BIGINT NOT NULL,
`reported_user_id` BIGINT DEFAULT NULL,
`reported_product_id` BIGINT DEFAULT NULL,
`reported_order_id` BIGINT DEFAULT NULL,
`reason` TEXT NOT NULL,
`status` ENUM('PENDING','RESOLVED','REJECTED') NOT NULL DEFAULT 'PENDING',
`admin_note` TEXT DEFAULT NULL,
`resolved_by` BIGINT DEFAULT NULL,
`resolved_at` DATETIME(3) NULL DEFAULT NULL,
`created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
INDEX `idx_reports_status` (`status`, `created_at`),
CONSTRAINT `chk_reports_target` CHECK (`reported_user_id` IS NOT NULL OR `reported_product_id` IS NOT NULL OR `reported_order_id` IS NOT NULL),
CONSTRAINT `fk_reports_reporter` FOREIGN KEY (`reporter_id`) REFERENCES `users`(`id`),
CONSTRAINT `fk_reports_user` FOREIGN KEY (`reported_user_id`) REFERENCES `users`(`id`),
CONSTRAINT `fk_reports_product` FOREIGN KEY (`reported_product_id`) REFERENCES `products`(`id`),
CONSTRAINT `fk_reports_order` FOREIGN KEY (`reported_order_id`) REFERENCES `orders`(`id`),
CONSTRAINT `fk_reports_admin` FOREIGN KEY (`resolved_by`) REFERENCES `users`(`id`)
) ENGINE=InnoDB;
