# GAP_ANALYSIS — Mã nguồn hiện tại vs tài liệu v4

Khảo sát ngày 20/11/2026 trên nhánh `main` (commit `8c9fcb5` + thay đổi chưa commit), 185 tệp Java main, ~20.800 dòng.
Đường dẫn tương đối từ `src/main/java/com/omnibid/`. Mã D1…D14 là quyết định trong `CHANGELOG_v4.md`.

Mức độ: 🔴 sai nghiệp vụ/hợp đồng (bắt buộc sửa) · 🟠 lệch thiết kế (phải sửa để khớp tài liệu) · 🟡 dọn dẹp.

## A. Nền tảng
| # | Mức | Hiện trạng | Yêu cầu v4 | Tệp |
|---|---|---|---|---|
| A1 | 🔴 | Schema 14 bảng ở `db/design_schema.sql`, không có Flyway; test mount script vào container | Flyway `V1__init_schema.sql` + `V2__seed_data.sql`, 10 bảng (D10, mục 3.4) | `pom.xml`, `db/*`, `test/.../support/MySqlContainerConfig.java` |
| A2 | 🔴 | `LocalDateTime` ở 59 tệp; `Clock` theo `Asia/Ho_Chi_Minh`; JDBC `serverTimezone=Asia/Ho_Chi_Minh` | `Instant` + UTC ở JVM/JDBC/Hibernate; JSON trả +07:00 (D8) | `config/DateTimeConfig.java`, mọi entity/DTO/service |
| A3 | 🔴 | Tiền `DECIMAL(12,2)`, `precision=12, scale=2` | `DECIMAL(15,0)`, BigDecimal scale 0, JSON số nguyên (D8) | entity Auction, Bid, Order, Payment; DTO |
| A4 | 🟠 | `AppProperties`: `paymentDeadlineHours`(int), `confirmWindowDays`, `reminderHours`, `payment.mock.*`, `rateLimit`, `scheduler.fixedRateMs`, `auction.minDurationHours` | Khóa theo Bảng 1.2 + `Duration`/`@DurationUnit`; `app.payment.simulate-enabled`; profile `demo` (D13) | `config/AppProperties.java`, `scheduler/*` |
| A5 | 🟠 | `application.properties` bị `.gitignore`, chỉ có example; không có profile demo | `application.properties` commit (bí mật qua env), `application-demo.properties` | `src/main/resources`, `.gitignore` |
| A6 | 🟠 | `AuctionScheduler` `@Scheduled(fixedRate = 10000)` cứng; `OrderScheduler` dùng `app.scheduler.order-delay` | Cả hai theo `app.scheduler.interval-seconds` (mặc định 10, demo 5) | `scheduler/AuctionScheduler.java`, `scheduler/OrderScheduler.java` |

## B. Định dạng phản hồi và lỗi
| # | Mức | Hiện trạng | Yêu cầu v4 | Tệp |
|---|---|---|---|---|
| B1 | 🔴 | `ApiResponse{success, errorCode, message, data, timestamp}` | `{success, data}` / `{success:false, error:{code,message,details}}` | `common/ApiResponse.java`, `common/GlobalExceptionHandler.java`, `security/JwtAuthenticationEntryPoint.java`, `security/CustomAccessDeniedHandler.java` |
| B2 | 🔴 | `AppException(code:String, …)` với mã tự do (`AUCTION_NOT_FOUND`, `USER_NOT_FOUND`, `EMAIL_ALREADY_EXISTS`, `INVALID_CREDENTIALS`, `INVALID_PASSWORD`, …) | `BusinessException(ErrorCode)`; enum `ErrorCode` đúng 24 mã Bảng 4.22, mỗi mã gắn `HttpStatus` | `common/AppException.java` → `common/BusinessException.java`, `common/ErrorCode.java` |
| B3 | 🔴 | `SELF_BID_NOT_ALLOWED` trả 422 | 409 (D9) | `service/impl/BidServiceImpl.java` |
| B4 | 🟠 | Chưa có 413 PAYLOAD_TOO_LARGE, 429, 502, CONFLICT cho lock timeout/deadlock | Bổ sung handler | `common/GlobalExceptionHandler.java` |
| B5 | 🟠 | `PageResponse` cần kiểm tra trường; `@PageableDefault(size = 20)` | `{content,page,size,totalElements,totalPages}`; size mặc định 10, tối đa 100; whitelist sort | `common/PageResponse.java`, controllers |

## C. Bảo mật, xác thực (D3, D12)
| # | Mức | Hiện trạng | Yêu cầu v4 | Tệp |
|---|---|---|---|---|
| C1 | 🔴 | JWT `sub=email`, không có claim `ver`; không có `token_version` | `sub=userId`, `role`, `ver`; filter so `ver` → 401 | `security/JwtTokenProvider.java`, `security/JwtAuthenticationFilter.java`, `entity/User.java` |
| C2 | 🔴 | Không có `POST /auth/logout` | Có; tăng `token_version` | `controller/AuthController.java`, `service/impl/AuthServiceImpl.java` |
| C3 | 🔴 | Đổi mật khẩu không vô hiệu JWT; thiếu `confirmPassword` | Tăng `token_version`; kiểm tra confirmPassword | `service/impl/UserServiceImpl.java`, `dto/user/ChangePasswordRequest.java` |
| C4 | 🔴 | Không giới hạn đăng nhập sai | `LoginAttemptLimiter` 5 lần/15 phút theo (email, IP) → 429 | mới: `security/LoginAttemptLimiter.java` |
| C5 | 🔴 | Filter khi BANNED chỉ đặt attribute rồi để request đi tiếp; JWT hỏng ở endpoint bảo vệ không phân biệt | BANNED → 403 ACCOUNT_BANNED; `ver` sai → 401; Public bỏ qua token hỏng | `security/JwtAuthenticationFilter.java`, `security/JwtAuthenticationEntryPoint.java` |
| C6 | 🔴 | Đăng ký trả token; login trả `{accessToken, tokenType, userId, email, fullName, role}`; sai mật khẩu trả `INVALID_CREDENTIALS` | Đăng ký trả hồ sơ (201, không token); login trả cấu trúc mục 4.5a; sai → 401 UNAUTHORIZED; email trùng → 400 VALIDATION_ERROR | `dto/auth/*`, `service/impl/AuthServiceImpl.java` |
| C7 | 🟠 | `SecurityConfig` permit `GET /api/v1/products/**` (lộ sản phẩm), `/ws-auction/**`, `/uploads/**`; CORS `allowCredentials=true` | Permit đúng 5 nhóm Public (auth register/login, GET categories, GET auctions, GET auctions/{id}, GET auctions/{id}/bids, `/ws`); `/auctions/mine` phải xác thực; CORS từ cấu hình | `config/SecurityConfig.java` |
| C8 | 🟠 | Phân quyền vai trò không đồng nhất (`isAuthenticated()` ở UserController, `hasRole('USER')` rải rác) | Theo Bảng 6.1: endpoint "USER" chặn ADMIN; `/users/me/**` cho USER, ADMIN | controllers |
| C9 | 🔴 | WebSocket `/ws-auction` + SockJS, `setAllowedOriginPatterns("*")`, cho phép SEND `/app` | `/ws`, origin từ cấu hình, chặn SEND, kiểm tra `ver`/BANNED khi có JWT | `config/WebSocketConfig.java` |
| C10 | 🟠 | Mật khẩu: cần kiểm tra regex hiện tại | ≥8, hoa, thường, số | `dto/auth/RegisterRequest.java`, `dto/user/ChangePasswordRequest.java` |

## D. Ảnh, Cloudinary (D1, D2)
| # | Mức | Hiện trạng | Yêu cầu v4 | Tệp |
|---|---|---|---|---|
| D1 | 🔴 | `POST /products` nhận JSON (không ảnh) | multipart `product` + `files` 1–5 | `controller/ProductController.java`, `service/impl/ProductServiceImpl.java` |
| D2 | 🔴 | `uploadProductImage` 1 tệp/lần, không kiểm tra magic bytes, upload trong transaction, không bù trừ | `files[]`, magic bytes, upload ngoài tx, khóa product, kiểm tra BR-23 + 1–5, bù trừ, xóa asset sau COMMIT | `service/impl/ProductServiceImpl.java`, mới `service/ImageStorageService.java`, `common/ImageValidator.java` |
| D3 | 🔴 | Avatar: không lưu `avatar_public_id`, không xóa ảnh cũ, có cả `PATCH` lẫn `POST`, trả cả profile | Chỉ `POST`, 200 `{avatarUrl}`, lưu public_id, xóa ảnh cũ sau COMMIT | `controller/UserController.java`, `service/impl/UserServiceImpl.java` |
| D4 | 🟠 | `StorageConfig` chọn Cloudinary hoặc fallback lưu đĩa `uploads/` | Chỉ Cloudinary (`ImageStorageService`); test dùng bean giả | `config/StorageConfig.java`, `service/impl/LocalStorageServiceImpl.java`, `service/impl/CloudinaryStorageServiceImpl.java` |
| D5 | 🟠 | `Category.iconUrl` | `icon` VARCHAR(50) | `entity/Category.java`, `dto/category/*`, `service/impl/CategoryServiceImpl.java` |

## E. Phiên và đặt giá (D4, D6, D7)
| # | Mức | Hiện trạng | Yêu cầu v4 | Tệp |
|---|---|---|---|---|
| E1 | 🔴 | Bid có `status` LEADING/OUTBID/WON/NOT_WON/INVALIDATED; mỗi bid khóa & cập nhật bid LEADING cũ | Bid không trạng thái; chỉ insert bid + cập nhật auction | `entity/Bid.java`, `enums/BidStatus.java`, `service/impl/BidServiceImpl.java`, `repository/BidRepository.java` |
| E2 | 🔴 | Sau insert bid đọc `users.status ... LOCK IN SHARE MODE`; `findById` user trong tx | Không khóa/đọc lại users; dùng user từ JWT Filter | `service/impl/BidServiceImpl.java`, `repository/UserRepository.java` |
| E3 | 🔴 | `POST /api/v1/bids` (auctionId trong body); response `BidResponse` có `status`, `bidderId` | `POST /auctions/{id}/bids` body `{amount}`; response `{bidId, auctionId, amount, currentPrice, bidCount, minNextBid}` | `controller/BidController.java`, `dto/bid/*` |
| E4 | 🔴 | `AuctionCloser` chọn bid LEADING/OUTBID cao nhất của bidder ACTIVE (tức có thể trao cho người thứ hai), đặt WON/NOT_WON | BR-09: chỉ xét người dẫn đầu; leader BANNED → UNSOLD; thông báo AUCTION_RESULT | `service/AuctionCloser.java` |
| E5 | 🔴 | Không có ràng buộc DB BR-15 | `active_product_id` + UNIQUE; Service vẫn kiểm tra trước | `service/impl/AuctionServiceImpl.java`, migration |
| E6 | 🟠 | `Auction.version` (optimistic) | Bỏ; chỉ khóa bi quan | `entity/Auction.java` |
| E7 | 🟠 | Topic `/topic/auction/{id}`; payload `AuctionWebSocketMessage` | `/topic/auctions/{id}`; `AuctionEvent` 12 trường, 5 `type` | `service/impl/WebSocketPublisherImpl.java`, `dto/websocket/*` |
| E8 | 🟠 | `GET /auctions/{id}/bids` trả `List` sắp theo amount | Phân trang; tên ẩn danh, không `bidderId` | `controller/BidController.java`, `service/impl/BidServiceImpl.java` |
| E9 | 🟠 | Tham số tìm kiếm/sort: cần đối chiếu | keyword, categoryId, minPrice, maxPrice, status, endBefore; sort whitelist 4 giá trị; ẩn phiên của sản phẩm HIDDEN | `controller/AuctionController.java`, `repository/specification/AuctionSpecification.java` |
| E10 | 🟠 | Hủy phiên: kiểm tra thông báo | Người bán: SCHEDULED hoặc ACTIVE & bid_count=0 (CANNOT_CANCEL); AUCTION_CANCELLED cho người bán (+ leader); product `release()` | `service/impl/AuctionServiceImpl.java` |
| E11 | 🟠 | Sửa phiên chưa phát `AUCTION_UPDATED` (cần kiểm tra) | Phát AUCTION_UPDATED sau COMMIT | `service/impl/AuctionServiceImpl.java` |
| E12 | 🟠 | Độ dài phiên tối thiểu theo giờ | `app.auction.min-duration-minutes` (demo 2 phút) | `service/impl/AuctionServiceImpl.java` |

## F. Đơn hàng, thanh toán, giao hàng (D9, D13)
| # | Mức | Hiện trạng | Yêu cầu v4 | Tệp |
|---|---|---|---|---|
| F1 | 🔴 | Thanh toán 2 transaction: tạo Payment PENDING → gateway xử lý trễ → `finalizePayment`; có `transactionRef`, `pending_order_id`, job timeout | Đồng bộ trong 1 transaction đã khóa order; chỉ SUCCESS/FAILED | `service/impl/PaymentServiceImpl.java`, `gateway/*`, `entity/Payment.java` |
| F2 | 🔴 | Gửi hàng qua bảng `shipments` + `PATCH /shipments/{id}/status` 4 bước | `POST /orders/{id}/ship` (trackingCode tùy chọn) → SHIPPING, `shipped_at`, `confirm_deadline` | `controller/OrderController.java`, `service/impl/OrderServiceImpl.java`, xóa Shipment* |
| F3 | 🔴 | `PATCH /orders/{id}/confirm-receipt` | `POST` | `controller/OrderController.java` |
| F4 | 🔴 | `OrderAccessPolicy.requireViewer` cho ADMIN xem `/orders/{id}`; `requireSellerOrAdmin` | `/orders/{id}`: chỉ 2 bên; `/orders/{id}/payments`: chỉ người mua; Admin dùng `/admin/orders/{id}` | `security/OrderAccessPolicy.java`, `service/impl/PaymentServiceImpl.java` |
| F5 | 🔴 | `simulateResult` luôn được nhận khi `allow-simulate-result=true` mặc định | Chỉ khi `app.payment.simulate-enabled=true` (mặc định false) | `gateway/DefaultMockPaymentGateway.java` |
| F6 | 🟠 | Nhắc thanh toán 24h/2h | Bỏ | `service/impl/OrderServiceImpl.java`, `scheduler/OrderScheduler.java` |
| F7 | 🟠 | `PaymentMethod` 3 giá trị | Chỉ `MOCK_CARD` | `enums/PaymentMethod.java` |
| F8 | 🟠 | Response thanh toán, gửi hàng, xác nhận | Theo API_SPEC §6 | `dto/payment/*`, `dto/order/*` |
| F9 | 🟠 | Đơn EXPIRED/COMPLETED: cập nhật sản phẩm cần đối chiếu BR-16 | EXPIRED → product `release()` trừ HIDDEN; COMPLETED → `markSold()` trừ HIDDEN | `service/impl/OrderServiceImpl.java`, `service/OrderLifecycleSupport.java` |

## G. Thông báo, báo cáo (D5, D11)
| # | Mức | Hiện trạng | Yêu cầu v4 | Tệp |
|---|---|---|---|---|
| G1 | 🔴 | NotificationType 9 giá trị | 5 giá trị (BUSINESS_RULES §7) | `enums/NotificationType.java`, `common/NotificationTemplates.java`, mọi chỗ `notify(...)` |
| G2 | 🟠 | `PATCH /{id}/read`, có `unread-count`, `read-all` | `POST /{id}/read`; bỏ 2 endpoint | `controller/NotificationController.java` |
| G3 | 🔴 | Report có `reportType` VIOLATION/COMPLAINT, `action`, `orderId`; resolve thực thi BAN/HIDE/CANCEL/COMPLETE | Chỉ `decision` + `note` bắt buộc; `reportedOrderId`; ReportService độc lập AdminService; REPORT_RESOLVED | `entity/Report.java`, `dto/report/*`, `service/impl/ReportServiceImpl.java`, `controller/ReportController.java` |
| G4 | 🟠 | Không có `GET /admin/reports/{id}`; lọc theo `reportType` | Có; lọc `status`, `targetType` | `controller/ReportController.java` (nên chuyển các endpoint admin sang `AdminReportController` hoặc `AdminController`) |
| G5 | 🟠 | Thông báo: `NotificationPushListener` AFTER_COMMIT đã có — giữ; kiểm tra mọi chỗ gửi đều qua sự kiện | BR-22 | `listener/*`, `service/impl/NotificationServiceImpl.java` |

## H. Quản trị (D5, D6, D9, D10, D14)
| # | Mức | Hiện trạng | Yêu cầu v4 | Tệp |
|---|---|---|---|---|
| H1 | 🔴 | Khóa tài khoản: `findByIdForUpdate(user)`, **INVALIDATED các bid** và trao dẫn đầu cho người thứ hai | Không khóa users; giữ nguyên bid (BR-03, BR-17d); hủy phiên đang bán, hủy đơn PENDING_PAYMENT, release product; khóa theo 3 vòng | `service/impl/AdminServiceImpl.java` |
| H2 | 🔴 | `PATCH /admin/users/{id}` (body status) | `POST …/ban`, `POST …/unban`; CANNOT_BAN_ADMIN | `controller/AdminController.java`, `dto/admin/*` |
| H3 | 🔴 | `PATCH /admin/products/{id}` | `POST …/hide`; HIDDEN là trạng thái cuối, không bỏ ẩn (D14) | như trên |
| H4 | 🟠 | Thiếu `GET /admin/products/{id}`, `GET /admin/auctions`, `GET /admin/auctions/{id}`, `GET /admin/orders/{id}` | Bổ sung | `controller/AdminController.java`, `service/impl/AdminServiceImpl.java` |
| H5 | 🟠 | `GET /admin/statistics?from&to` | Không tham số; 5 nhóm số liệu FR-20 | như trên |
| H6 | 🟠 | Audit log ghi DB | Bỏ; `reason` tùy chọn ghi log ứng dụng (SLF4J) | như trên |
| H7 | 🟠 | Seed admin `admin@omnibid.com` / `Demo@123` trong `seed_demo.sql` | `admin@omnibid.local` / `Admin@123456` qua V2 | migration |

## I. Kiểm thử
| # | Mức | Hiện trạng | Yêu cầu |
|---|---|---|---|
| I1 | 🔴 | 39 tệp test, nhiều tệp phụ thuộc Shipment/Watchlist/Audit/BidStatus/PENDING | Sửa hoặc xóa theo DELETE_LIST; giữ khung `BaseIT`, `Concurrently`, `MutableClock` |
| I2 | 🟠 | `BaseIT` TRUNCATE 14 bảng | 10 bảng; Flyway tạo schema |
| I3 | 🟠 | `InvariantCheck` dùng SQL trên cột cũ (bids.status…) | Viết lại bất biến theo v4 (ví dụ: mỗi product ≤1 phiên mở; auction.bid_count = COUNT(bids); current_price = MAX(amount) khi có bid; mỗi order ≤1 SUCCESS) |
| I4 | 🟠 | Thiếu test cho tính năng mới | Xem `v4-phase-9-tests-docs` |
