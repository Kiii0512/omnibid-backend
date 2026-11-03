-- OmniBid Database Schema (14 Tables)
-- Standard MySQL 8.0+, InnoDB, utf8mb4_unicode_ci

CREATE TABLE `users` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
  `full_name`     VARCHAR(255) NOT NULL,
  `email`         VARCHAR(255) NOT NULL,
  `password_hash` VARCHAR(255) NOT NULL,
  `phone_number`  VARCHAR(20) DEFAULT NULL,
  `avatar_url`    VARCHAR(500) DEFAULT NULL,
  `address`       VARCHAR(255) DEFAULT NULL,
  `role`          ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER',
  `status`        ENUM('ACTIVE','BANNED') NOT NULL DEFAULT 'ACTIVE',
  `created_at`    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uq_users_email` (`email`)
) ENGINE=InnoDB;

CREATE TABLE `categories` (
  `id`         BIGINT AUTO_INCREMENT PRIMARY KEY,
  `name`       VARCHAR(100) NOT NULL,
  `slug`       VARCHAR(100) NOT NULL,
  `icon_url`   VARCHAR(500) DEFAULT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uq_categories_slug` (`slug`)
) ENGINE=InnoDB;

CREATE TABLE `products` (
  `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
  `seller_id`   BIGINT NOT NULL,
  `category_id` BIGINT NOT NULL,
  `title`       VARCHAR(255) NOT NULL,
  `description` TEXT NOT NULL,
  `condition`   ENUM('NEW','LIKE_NEW','USED') NOT NULL,
  `status`      ENUM('AVAILABLE','IN_AUCTION','RESERVED','SOLD','HIDDEN','REMOVED') NOT NULL DEFAULT 'AVAILABLE',
  `created_at`  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_products_seller` (`seller_id`),
  INDEX `idx_products_category` (`category_id`),
  INDEX `idx_products_status` (`status`),
  CONSTRAINT `fk_products_seller` FOREIGN KEY (`seller_id`) REFERENCES `users`(`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_products_category` FOREIGN KEY (`category_id`) REFERENCES `categories`(`id`) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE `product_images` (
  `id`         BIGINT AUTO_INCREMENT PRIMARY KEY,
  `product_id` BIGINT NOT NULL,
  `image_url`  VARCHAR(500) NOT NULL,
  `public_id`  VARCHAR(255) DEFAULT NULL,
  `is_primary` BOOLEAN NOT NULL DEFAULT FALSE,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_product_images_product` (`product_id`),
  CONSTRAINT `fk_product_images_product` FOREIGN KEY (`product_id`) REFERENCES `products`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE `auctions` (
  `id`                BIGINT AUTO_INCREMENT PRIMARY KEY,
  `product_id`        BIGINT NOT NULL,
  `seller_id`         BIGINT NOT NULL,
  `start_price`       DECIMAL(12,2) NOT NULL,
  `bid_increment`     DECIMAL(12,2) NOT NULL,
  `reserve_price`     DECIMAL(12,2) DEFAULT NULL,
  `current_price`     DECIMAL(12,2) NOT NULL,
  `highest_bidder_id` BIGINT DEFAULT NULL,
  `bid_count`         INT NOT NULL DEFAULT 0,
  `start_time`        TIMESTAMP NOT NULL,
  `end_time`          TIMESTAMP NOT NULL,
  `status`            ENUM('SCHEDULED','ACTIVE','ENDED','UNSOLD','CANCELLED') NOT NULL DEFAULT 'SCHEDULED',
  `winner_id`         BIGINT DEFAULT NULL,
  `final_price`       DECIMAL(12,2) DEFAULT NULL,
  `closed_at`         TIMESTAMP NULL DEFAULT NULL,
  `cancel_reason`     VARCHAR(255) DEFAULT NULL,
  `version`           BIGINT NOT NULL DEFAULT 0,
  `created_at`        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_auctions_product` (`product_id`),
  INDEX `idx_auctions_seller` (`seller_id`),
  INDEX `idx_auctions_status_end` (`status`, `end_time`),
  INDEX `idx_auctions_status_start` (`status`, `start_time`),
  CONSTRAINT `chk_auctions_price` CHECK (`start_price` > 0),
  CONSTRAINT `chk_auctions_increment` CHECK (`bid_increment` > 0),
  CONSTRAINT `chk_auctions_reserve` CHECK (`reserve_price` IS NULL OR `reserve_price` >= `start_price`),
  CONSTRAINT `chk_auctions_times` CHECK (`end_time` > `start_time`),
  CONSTRAINT `fk_auctions_product` FOREIGN KEY (`product_id`) REFERENCES `products`(`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_auctions_seller` FOREIGN KEY (`seller_id`) REFERENCES `users`(`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_auctions_highest_bidder` FOREIGN KEY (`highest_bidder_id`) REFERENCES `users`(`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_auctions_winner` FOREIGN KEY (`winner_id`) REFERENCES `users`(`id`) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE `bids` (
  `id`             BIGINT AUTO_INCREMENT PRIMARY KEY,
  `auction_id`     BIGINT NOT NULL,
  `bidder_id`      BIGINT NOT NULL,
  `amount`         DECIMAL(12,2) NOT NULL,
  `status`         ENUM('LEADING','OUTBID','WON','NOT_WON','INVALIDATED') NOT NULL DEFAULT 'LEADING',
  `invalid_reason` VARCHAR(255) DEFAULT NULL,
  `created_at`     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY `uq_bids_auction_amount` (`auction_id`, `amount`),
  INDEX `idx_bids_auction_status` (`auction_id`, `status`),
  INDEX `idx_bids_bidder` (`bidder_id`),
  CONSTRAINT `chk_bids_amount` CHECK (`amount` > 0),
  CONSTRAINT `fk_bids_auction` FOREIGN KEY (`auction_id`) REFERENCES `auctions`(`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_bids_bidder` FOREIGN KEY (`bidder_id`) REFERENCES `users`(`id`) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE `watchlists` (
  `id`         BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id`    BIGINT NOT NULL,
  `auction_id` BIGINT NOT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uq_watchlists_user_auction` (`user_id`, `auction_id`),
  INDEX `idx_watchlists_user` (`user_id`),
  INDEX `idx_watchlists_auction` (`auction_id`),
  CONSTRAINT `fk_watchlists_user` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_watchlists_auction` FOREIGN KEY (`auction_id`) REFERENCES `auctions`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE `orders` (
  `id`                   BIGINT AUTO_INCREMENT PRIMARY KEY,
  `auction_id`           BIGINT NOT NULL,
  `product_id`           BIGINT NOT NULL,
  `seller_id`            BIGINT NOT NULL,
  `buyer_id`             BIGINT NOT NULL,
  `final_price`          DECIMAL(12,2) NOT NULL,
  `shipping_address`     VARCHAR(255) DEFAULT NULL,
  `phone_number`         VARCHAR(20)  DEFAULT NULL,
  `status`               ENUM('PENDING_PAYMENT','PAID','SHIPPING','COMPLETED','CANCELLED','EXPIRED')
                         NOT NULL DEFAULT 'PENDING_PAYMENT',
  `payment_deadline`     TIMESTAMP NOT NULL,
  `paid_at`              TIMESTAMP NULL DEFAULT NULL,
  `delivered_at`         TIMESTAMP NULL DEFAULT NULL,
  `confirm_deadline`     TIMESTAMP NULL DEFAULT NULL,
  `reminder_24h_sent_at` TIMESTAMP NULL DEFAULT NULL,
  `reminder_2h_sent_at`  TIMESTAMP NULL DEFAULT NULL,
  `completed_at`         TIMESTAMP NULL DEFAULT NULL,
  `cancelled_at`         TIMESTAMP NULL DEFAULT NULL,
  `cancel_reason`        VARCHAR(255) DEFAULT NULL,
  `created_at`           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_at`           TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uq_orders_auction` (`auction_id`),
  INDEX `idx_orders_buyer` (`buyer_id`),
  INDEX `idx_orders_seller` (`seller_id`),
  INDEX `idx_orders_status_deadline` (`status`, `payment_deadline`),
  INDEX `idx_orders_status_confirm` (`status`, `confirm_deadline`),
  CONSTRAINT `chk_orders_price` CHECK (`final_price` > 0),
  CONSTRAINT `chk_orders_parties` CHECK (`seller_id` <> `buyer_id`),
  CONSTRAINT `fk_orders_auction` FOREIGN KEY (`auction_id`) REFERENCES `auctions`(`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_orders_product` FOREIGN KEY (`product_id`) REFERENCES `products`(`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_orders_seller` FOREIGN KEY (`seller_id`) REFERENCES `users`(`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_orders_buyer` FOREIGN KEY (`buyer_id`) REFERENCES `users`(`id`) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE `payments` (
  `id`               BIGINT AUTO_INCREMENT PRIMARY KEY,
  `order_id`         BIGINT NOT NULL,
  `attempt_no`       INT NOT NULL,
  `amount`           DECIMAL(12,2) NOT NULL,
  `method`           ENUM('MOCK_CARD','MOCK_BANK','MOCK_WALLET') NOT NULL,
  `status`           ENUM('PENDING','SUCCESS','FAILED') NOT NULL DEFAULT 'PENDING',
  `idempotency_key`  VARCHAR(64)  NOT NULL,
  `transaction_ref`  VARCHAR(64)  NOT NULL,
  `failure_reason`   VARCHAR(255) DEFAULT NULL,
  `paid_at`          TIMESTAMP NULL DEFAULT NULL,
  `created_at`       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `success_order_id` BIGINT GENERATED ALWAYS AS (IF(`status` = 'SUCCESS', `order_id`, NULL)) STORED,
  `pending_order_id` BIGINT GENERATED ALWAYS AS (IF(`status` = 'PENDING', `order_id`, NULL)) STORED,
  UNIQUE KEY `uq_payments_order_attempt` (`order_id`, `attempt_no`),
  UNIQUE KEY `uq_payments_one_success` (`success_order_id`),
  UNIQUE KEY `uq_payments_one_pending` (`pending_order_id`),
  UNIQUE KEY `uq_payments_idempotency` (`order_id`, `idempotency_key`),
  UNIQUE KEY `uq_payments_tx_ref` (`transaction_ref`),
  INDEX `idx_payments_order_time` (`order_id`, `created_at`),
  INDEX `idx_payments_order_status` (`order_id`, `status`),
  INDEX `idx_payments_status_created` (`status`, `created_at`),
  CONSTRAINT `chk_payments_amount` CHECK (`amount` > 0),
  CONSTRAINT `chk_payments_attempt` CHECK (`attempt_no` >= 1),
  CONSTRAINT `fk_payments_order` FOREIGN KEY (`order_id`) REFERENCES `orders`(`id`) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE `shipments` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
  `order_id`      BIGINT NOT NULL,
  `tracking_code` VARCHAR(50)  NOT NULL,
  `carrier_name`  VARCHAR(100) NOT NULL DEFAULT 'SIMULATED_CARRIER',
  `status`        ENUM('CREATED','PICKED_UP','IN_TRANSIT','DELIVERED') NOT NULL DEFAULT 'CREATED',
  `created_at`    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uq_shipments_order` (`order_id`),
  UNIQUE KEY `uq_shipments_tracking` (`tracking_code`),
  CONSTRAINT `fk_shipments_order` FOREIGN KEY (`order_id`) REFERENCES `orders`(`id`) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE `shipment_events` (
  `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
  `shipment_id` BIGINT NOT NULL,
  `status`      ENUM('CREATED','PICKED_UP','IN_TRANSIT','DELIVERED') NOT NULL,
  `note`        VARCHAR(255) DEFAULT NULL,
  `updated_by`  BIGINT DEFAULT NULL,
  `created_at`  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_shipment_events_time` (`shipment_id`, `created_at`),
  CONSTRAINT `fk_events_shipment` FOREIGN KEY (`shipment_id`) REFERENCES `shipments`(`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_events_user` FOREIGN KEY (`updated_by`) REFERENCES `users`(`id`) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE `notifications` (
  `id`         BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id`    BIGINT NOT NULL,
  `type`       ENUM('OUTBID','AUCTION_WON','AUCTION_ENDED','ORDER_CREATED','PAYMENT_RESULT',
                    'SHIPMENT_UPDATE','ORDER_UPDATE','PAYMENT_REMINDER','REPORT_RESOLVED') NOT NULL,
  `title`      VARCHAR(255) NOT NULL,
  `content`    TEXT NOT NULL,
  `ref_type`   VARCHAR(30) DEFAULT NULL,
  `ref_id`     BIGINT DEFAULT NULL,
  `is_read`    BOOLEAN NOT NULL DEFAULT FALSE,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_notifications_user_read` (`user_id`, `is_read`, `created_at`),
  CONSTRAINT `fk_notifications_user` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE `reports` (
  `id`                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  `reporter_id`         BIGINT NOT NULL,
  `report_type`         ENUM('VIOLATION','COMPLAINT') NOT NULL,
  `reported_user_id`    BIGINT DEFAULT NULL,
  `reported_product_id` BIGINT DEFAULT NULL,
  `order_id`            BIGINT DEFAULT NULL,
  `reason`              TEXT NOT NULL,
  `status`              ENUM('PENDING','RESOLVED','REJECTED') NOT NULL DEFAULT 'PENDING',
  `action`              ENUM('BAN_USER','HIDE_PRODUCT','CANCEL_AUCTION','COMPLETE_ORDER','CANCEL_ORDER','NONE') DEFAULT NULL,
  `admin_note`          TEXT DEFAULT NULL,
  `resolved_by`         BIGINT DEFAULT NULL,
  `resolved_at`         TIMESTAMP NULL DEFAULT NULL,
  `created_at`          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_reports_status` (`status`, `created_at`),
  CONSTRAINT `chk_reports_target` CHECK (
       (`report_type` = 'VIOLATION' AND (`reported_user_id` IS NOT NULL OR `reported_product_id` IS NOT NULL))
    OR (`report_type` = 'COMPLAINT' AND `order_id` IS NOT NULL)),
  CONSTRAINT `fk_reports_reporter` FOREIGN KEY (`reporter_id`) REFERENCES `users`(`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_reports_user` FOREIGN KEY (`reported_user_id`) REFERENCES `users`(`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_reports_product` FOREIGN KEY (`reported_product_id`) REFERENCES `products`(`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_reports_order` FOREIGN KEY (`order_id`) REFERENCES `orders`(`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_reports_admin` FOREIGN KEY (`resolved_by`) REFERENCES `users`(`id`) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE `audit_logs` (
  `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
  `admin_id`    BIGINT NOT NULL,
  `action`      VARCHAR(50) NOT NULL,
  `target_type` VARCHAR(30) NOT NULL,
  `target_id`   BIGINT NOT NULL,
  `detail`      TEXT DEFAULT NULL,
  `created_at`  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_audit_admin_time` (`admin_id`, `created_at`),
  INDEX `idx_audit_target` (`target_type`, `target_id`),
  CONSTRAINT `fk_audit_admin` FOREIGN KEY (`admin_id`) REFERENCES `users`(`id`) ON DELETE RESTRICT
) ENGINE=InnoDB;
