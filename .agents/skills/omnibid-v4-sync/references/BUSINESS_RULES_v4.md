# BUSINESS_RULES_v4 — Quy tắc nghiệp vụ (trích 03_PhanTich_Omnibid_v4 và 04_ThietKe_Omnibid_v4)

## 1. Quy tắc nghiệp vụ BR-01…BR-23 (nguyên văn Bảng 2.3, Phân tích)

- **BR-01** Mô hình đấu giá tăng dần (English auction). Mức giá tối thiểu tiếp theo = giá hiện tại + bước giá. Nếu phiên chưa có lượt đặt giá nào, mức tối thiểu bằng giá khởi điểm
- **BR-02** Chỉ nhận lượt đặt giá khi phiên ACTIVE và thời gian máy chủ < end_time. Người đặt phải ACTIVE, không phải chủ phiên và không phải người đang dẫn đầu (người dẫn đầu không tự nâng giá của mình)
- **BR-03** Lượt đặt giá đã được chấp nhận là cam kết: người đặt không rút được, hệ thống không xóa và không vô hiệu hóa lượt này. Quy tắc không có ngoại lệ, kể cả khi người đặt bị khóa tài khoản sau đó
- **BR-04** Phiên kết thúc đúng end_time, không tự động gia hạn, kể cả khi có lượt đặt giá ngay trước thời điểm đó
- **BR-05** Giá sàn là tùy chọn, nếu có phải ≥ giá khởi điểm. Giá sàn không công khai, người xem chỉ thấy "Đã đạt", "Chưa đạt" hoặc "Không có giá sàn"
- **BR-06** Điều kiện tạo phiên: sản phẩm thuộc người bán, AVAILABLE, có 1-5 ảnh và chưa có phiên SCHEDULED/ACTIVE. Giá khởi điểm > 0, bước giá > 0, start_time ở tương lai, end_time sau start_time. Độ dài phiên từ 1 giờ đến 30 ngày (giá trị cấu hình)
- **BR-07** Chỉ được sửa giá khởi điểm, bước giá, giá sàn, thời gian khi phiên còn SCHEDULED. Khi phiên đã ACTIVE thì không sửa được, kể cả khi chưa có Bid
- **BR-08** Người bán chỉ được hủy phiên SCHEDULED, hoặc ACTIVE khi bid_count = 0. Admin được hủy phiên SCHEDULED/ACTIVE bất kỳ lúc nào, bắt buộc có lý do. Phiên bị hủy giữ nguyên lịch sử đặt giá, không tạo đơn hàng
- **BR-09** Khi chốt phiên, hệ thống lấy lượt đặt giá cao nhất của phiên. Nếu lượt này tồn tại, người đặt còn ACTIVE và mức giá đạt giá sàn (nếu có), phiên chuyển ENDED, ghi người thắng, giá cuối, closed_at và tạo đúng một đơn hàng. Ngược lại (không có lượt đặt giá, chưa đạt giá sàn, hoặc người dẫn đầu đang BANNED), phiên chuyển UNSOLD. Hệ thống không trao quyền mua cho người đặt giá thứ hai
- **BR-10** Hạn thanh toán = closed_at + 48 giờ. Mốc tính là thời điểm chốt thực tế để việc bộ lập lịch chạy trễ không làm người thắng mất thời gian thanh toán
- **BR-11** Thanh toán mô phỏng xử lý đồng bộ, trả SUCCESS hoặc FAILED ngay. Thanh toán thất bại được thử lại không giới hạn số lần trong hạn. Mỗi yêu cầu có khóa chống trùng riêng, gửi lặp cùng khóa trả lại kết quả cũ. Mỗi đơn có tối đa một thanh toán SUCCESS
- **BR-12** Quá hạn thanh toán mà chưa có SUCCESS thì đơn chuyển EXPIRED, sản phẩm về AVAILABLE (theo BR-16). Hệ thống không trao quyền mua cho người đặt giá cao thứ hai, người bán có thể tạo phiên mới
- **BR-13** Chỉ người bán của đơn được cập nhật đã gửi hàng khi đơn PAID (mã vận đơn tùy chọn). Chỉ người mua của đơn được xác nhận nhận hàng khi đơn SHIPPING. Hạn xác nhận = shipped_at + 7 ngày, quá hạn hệ thống tự hoàn tất
- **BR-14** Đơn chỉ bị hủy (CANCELLED) khi còn PENDING_PAYMENT: do Admin hủy trực tiếp hoặc là hệ quả của BR-17, BR-18. Đơn PAID/SHIPPING không bị hủy và tiếp tục được xử lý. Không có hoàn tiền thật
- **BR-15** Sản phẩm IN_AUCTION khi có phiên SCHEDULED/ACTIVE, RESERVED từ khi phiên có người thắng đến khi đơn kết thúc, SOLD khi đơn COMPLETED. Một sản phẩm chỉ có tối đa một phiên SCHEDULED/ACTIVE; quy tắc này được bảo vệ cả ở Service và bằng ràng buộc duy nhất trong cơ sở dữ liệu
- **BR-16** Sản phẩm HIDDEN không bao giờ được tác vụ tự động đưa về AVAILABLE. Nếu đơn của sản phẩm đã PAID/SHIPPING khi bị ẩn, đơn vẫn tiếp tục. Khi đơn COMPLETED, sản phẩm giữ trạng thái HIDDEN và kết quả giao dịch được ghi nhận ở đơn hàng. HIDDEN là trạng thái cuối theo quyết định có chủ đích: hệ thống không có chức năng bỏ ẩn, người bán muốn bán lại phải tạo sản phẩm mới
- **BR-17** Khóa tài khoản chỉ áp dụng cho vai trò USER và đặt users.status = BANNED. Hệ quả: (a) tài khoản bị chặn mọi API (ACCOUNT_BANNED) và không đặt giá được nữa; (b) các phiên SCHEDULED/ACTIVE do người này bán bị hủy; (c) đơn PENDING_PAYMENT mà người này là người mua hoặc người bán bị hủy, đơn PAID/SHIPPING tiếp tục; (d) các lượt đặt giá đã có của người này được giữ nguyên, nếu người này đang dẫn đầu một phiên thì phiên đó chuyển UNSOLD khi chốt (BR-09). Mở khóa chỉ đổi lại trạng thái ACTIVE, không khôi phục phiên hay đơn đã bị hủy
- **BR-18** Ẩn sản phẩm: products.status = HIDDEN. Phiên SCHEDULED/ACTIVE của sản phẩm bị hủy, đơn PENDING_PAYMENT của sản phẩm bị hủy, đơn PAID/SHIPPING tiếp tục (BR-16)
- **BR-19** Báo cáo vi phạm phải chỉ ra ít nhất một đối tượng: sản phẩm, người dùng hoặc đơn hàng. Admin xử lý báo cáo bằng cách chuyển báo cáo sang RESOLVED (đã xử lý) hoặc REJECTED (bác bỏ), kèm ghi chú bắt buộc. Xử lý báo cáo chỉ thay đổi báo cáo; việc khóa tài khoản, ẩn sản phẩm, hủy phiên (nếu cần) do Admin thực hiện riêng theo BR-17, BR-18, BR-08. Báo cáo không làm thay đổi trạng thái đơn hàng và không tạm dừng việc tự hoàn tất
- **BR-20** Vi phạm được xử lý bằng đổi trạng thái: khóa tài khoản (BANNED), ẩn sản phẩm (HIDDEN), hủy phiên (CANCELLED). Sản phẩm chỉ được xóa khi chưa từng được đưa lên phiên
- **BR-21** Đặt giá, chốt phiên, hủy phiên và khóa tài khoản trên cùng một phiên được xử lý tuần tự. Mọi kiểm tra được thực hiện trên dữ liệu mới nhất tại thời điểm xử lý, độc lập với giá mà giao diện đang hiển thị
- **BR-22** Thông báo và cập nhật thời gian thực chỉ được gửi sau khi thay đổi dữ liệu đã được lưu thành công
- **BR-23** Người bán chỉ được sửa thông tin, thêm hoặc xóa ảnh của sản phẩm khi sản phẩm ở trạng thái AVAILABLE. Quy tắc này ngăn việc thay đổi mô tả, hình ảnh khi sản phẩm đã lên phiên (IN_AUCTION) hoặc đang giao dịch (RESERVED, SOLD)

## 2. Yêu cầu phi chức năng (nguyên văn Bảng 2.2)

- **NFR-01** (Tính đúng đắn khi đồng thời) Khi nhiều người đặt giá cùng lúc trên một phiên, các lượt được xử lý tuần tự. Không có hai lượt đặt giá cùng mức giá trong một phiên và không lượt nào được chấp nhận dựa trên giá đã cũ
- **NFR-02** (Tính đúng thời gian) Không chấp nhận lượt đặt giá khi thời gian máy chủ ≥ end_time. Phiên được chốt chậm nhất một chu kỳ lập lịch (mặc định 10 giây) sau end_time. Thời gian được lưu theo UTC và hiển thị cho người dùng theo múi giờ UTC+7 (Asia/Ho_Chi_Minh)
- **NFR-03** (Thời gian thực) Giá mới được đẩy tới người đang xem phiên mà không cần tải lại trang, mục tiêu ≤ 2 giây trong môi trường demo
- **NFR-04** (Hiệu năng) API đọc thông thường (danh sách, chi tiết) phản hồi ≤ 2 giây với bộ dữ liệu mẫu gồm 100 người dùng, 500 sản phẩm, 500 phiên đấu giá và 5.000 lượt đặt giá
- **NFR-05** (Bảo mật) Mật khẩu tối thiểu 8 ký tự, gồm ít nhất 1 chữ hoa, 1 chữ thường, 1 chữ số và được lưu dạng băm. API cần đăng nhập được xác thực bằng JWT có hiệu lực 24 giờ. Đăng xuất và đổi mật khẩu vô hiệu mọi JWT đã cấp của tài khoản. Đăng nhập sai quá 5 lần liên tiếp trong 15 phút (cùng email và địa chỉ IP) thì bị tạm chặn 15 phút. Quyền theo vai trò và quyền sở hữu được kiểm tra ở Backend. Tài khoản BANNED bị chặn ngay cả khi token còn hạn
- **NFR-06** (Toàn vẹn dữ liệu) Lịch sử đặt giá, đơn hàng, thanh toán, báo cáo được lưu giữ đầy đủ. Vi phạm được xử lý bằng đổi trạng thái. Giá trị tiền là số nguyên VND, lưu bằng kiểu số thập phân chính xác DECIMAL(15,0)
- **NFR-07** (Tính nhất quán nghiệp vụ) Mỗi phiên có tối đa một đơn hàng, mỗi đơn có tối đa một thanh toán thành công. Gửi lặp cùng khóa chống trùng không tạo giao dịch mới
- **NFR-08** (Khả dụng giao diện) Giao diện tiếng Việt, hỗ trợ trình duyệt Chrome, Edge bản hiện hành trên máy tính. Thông báo lỗi rõ ràng theo mã lỗi, luôn hiển thị mức giá tối thiểu tiếp theo và thời gian còn lại
- **NFR-09** (Khả năng bảo trì) Backend phân tầng Controller - Service - Repository, mã nguồn quản lý trên Git, API có tài liệu mô tả

## 3. Ma trận chuyển trạng thái (nguyên văn Bảng 4.2, Phân tích)

| Đối tượng | Hiện tại | Kế tiếp | Điều kiện |
|---|---|---|---|
| Auction | (mới) | SCHEDULED | Người bán tạo phiên hợp lệ (BR-06) |
| Auction | SCHEDULED | SCHEDULED | Người bán sửa điều kiện (BR-07) |
| Auction | SCHEDULED | ACTIVE | Đến start_time |
| Auction | ACTIVE | ENDED | Hết end_time, có lượt đặt giá cao nhất, người dẫn đầu còn ACTIVE và đạt giá sàn nếu có (BR-09) |
| Auction | ACTIVE | UNSOLD | Hết end_time, không có lượt đặt giá, chưa đạt giá sàn, hoặc người dẫn đầu đang BANNED (BR-09) |
| Auction | SCHEDULED / ACTIVE | CANCELLED | Người bán hủy theo BR-08, Admin hủy có lý do, hoặc hệ quả của BR-17, BR-18. Người bán và người dẫn đầu nhận AUCTION_CANCELLED |
| Product | (mới) | AVAILABLE | Người bán tạo sản phẩm có 1-5 ảnh |
| Product | AVAILABLE | IN_AUCTION | Phiên được tạo |
| Product | IN_AUCTION | RESERVED | Phiên ENDED và đơn được tạo |
| Product | RESERVED | SOLD | Đơn COMPLETED và sản phẩm không HIDDEN |
| Product | IN_AUCTION / RESERVED | AVAILABLE | Phiên UNSOLD/CANCELLED hoặc đơn EXPIRED/CANCELLED, sản phẩm không HIDDEN |
| Product | AVAILABLE / IN_AUCTION / RESERVED | HIDDEN | Admin ẩn sản phẩm (BR-18). HIDDEN là trạng thái cuối, không có chuyển ngược về AVAILABLE (BR-16) |
| Order | (mới) | PENDING_PAYMENT | Phiên ENDED (hạn theo BR-10) |
| Order | PENDING_PAYMENT | PENDING_PAYMENT | Thanh toán FAILED, được thử lại khi còn hạn |
| Order | PENDING_PAYMENT | PAID | Thanh toán SUCCESS trước hạn |
| Order | PENDING_PAYMENT | EXPIRED | Quá hạn thanh toán, chưa có SUCCESS (BR-12) |
| Order | PENDING_PAYMENT | CANCELLED | Admin hủy, người mua hoặc người bán bị khóa, sản phẩm bị ẩn (BR-14) |
| Order | PAID | SHIPPING | Người bán cập nhật đã gửi hàng (BR-13) |
| Order | SHIPPING | COMPLETED | Người mua xác nhận, hoặc quá hạn xác nhận (BR-13) |
| Payment | (mới) | SUCCESS | Yêu cầu hợp lệ với khóa chống trùng mới, bộ mô phỏng trả thành công |
| Payment | (mới) | FAILED | Yêu cầu hợp lệ với khóa chống trùng mới, bộ mô phỏng trả thất bại |
| Report | (mới) | PENDING | User gửi báo cáo (BR-19) |
| Report | PENDING | RESOLVED / REJECTED | Admin đánh dấu đã xử lý / bác bỏ, kèm ghi chú (BR-19) |
| User | ACTIVE | BANNED | Admin khóa tài khoản vai trò USER (BR-17) |
| User | BANNED | ACTIVE | Admin mở khóa; phiên, đơn đã bị hủy không được khôi phục (BR-17) |

## 4. Phương thức chuyển trạng thái trên entity (Bảng 2.1, Thiết kế)
| Chuyển trạng thái | Phương thức entity | Lớp gọi |
|---|---|---|
| Auction SCHEDULED → ACTIVE | `Auction.activate()` | AuctionService (AuctionScheduler) |
| Auction ACTIVE → ENDED/UNSOLD | `Auction.close(...)` | AuctionService (AuctionScheduler) |
| Auction SCHEDULED/ACTIVE → CANCELLED | `Auction.cancel(reason)` | AuctionService, AdminService |
| Bid mới | `Auction.placeBid(bidderId, amount)` (kiểm tra BR-01/02, cập nhật currentPrice, highestBidder, bidCount, trả Bid mới) | BidService |
| Product → IN_AUCTION/RESERVED/AVAILABLE/SOLD/HIDDEN | `reserve()`, `release()`, `markSold()`, `hide()` (+ `markInAuction()`) | AuctionService, OrderService, AdminService |
| Order → PAID/SHIPPING/COMPLETED | `markPaid()`, `markShipped()`, `confirmReceived()`, `autoComplete()` | PaymentService, OrderService (OrderScheduler) |
| Order → EXPIRED/CANCELLED | `expire()`, `cancel(reason)` | OrderService (OrderScheduler), AdminService |
| Report PENDING → RESOLVED/REJECTED | `resolve(note, adminId)`, `reject(note, adminId)` | ReportService |
| User ACTIVE ↔ BANNED | `ban()`, `unban()` | AdminService |
| User tăng token_version | `increaseTokenVersion()` | AuthService, UserService |

Phương thức entity chỉ đổi trạng thái của chính nó và ném `BusinessException(INVALID_STATUS_TRANSITION …)` khi sai điều kiện.
Điều phối nhiều đối tượng (chốt phiên + tạo đơn, khóa user + hủy phiên/đơn) nằm ở Service. `release()` và mọi tác vụ tự động
**không bao giờ** đưa HIDDEN về AVAILABLE (BR-16); `markSold()` trên sản phẩm HIDDEN giữ HIDDEN.

## 5. Khóa dòng và đồng thời (mục 5.2, Thiết kế)
1. Ghi trên phiên/đơn/sản phẩm: `@Transactional` + `SELECT … FOR UPDATE` (`@Lock(PESSIMISTIC_WRITE)`), kiểm tra lại mọi điều kiện **sau** khi giữ khóa.
2. Thứ tự khóa cố định **auctions → orders → products**, cùng bảng theo **id tăng dần**. **Không** khóa `users` (bỏ mọi `FOR SHARE`/`findByIdForUpdate` trên users).
3. Đặt giá: khóa dòng auction → kiểm tra status ACTIVE, `now < endTime`, người đặt ≠ seller (409 SELF_BID_NOT_ALLOWED), người đặt ≠ highestBidder (ALREADY_LEADING), `amount ≥ minNextBid` (BID_TOO_LOW + details) → insert bid → cập nhật auction. Trạng thái tài khoản người đặt lấy từ User do JWT Filter nạp trong **cùng request** (không đọc lại, không khóa). Bỏ bước khóa/sửa các Bid LEADING cũ.
4. Chốt phiên & hủy phiên: khóa cùng dòng auction, kiểm tra lại trạng thái trước khi ghi → mọi bid đến sau bị từ chối.
5. Thanh toán: khóa dòng order; kiểm tra idempotency + trạng thái + hạn trong cùng transaction; gateway mô phỏng trả kết quả **đồng bộ** trong transaction (không có trạng thái PENDING, không @Async, không job timeout).
6. Tạo phiên: khóa dòng product (sau khi đã không còn khóa auction nào cần lấy trước) để kiểm tra BR-15 → PRODUCT_NOT_AVAILABLE; UNIQUE `active_product_id` là lớp cuối.
7. Sửa sản phẩm, thêm/xóa ảnh: khóa product kiểm tra BR-23. Upload Cloudinary diễn ra **trước**, ngoài transaction.
8. Ẩn sản phẩm (bắt đầu từ product): đọc **không khóa** danh sách auction_id (SCHEDULED/ACTIVE), order_id (PENDING_PAYMENT) liên quan → khóa auctions (id tăng) → orders (id tăng) → product → kiểm tra lại từng điều kiện → ghi.
9. Khóa tài khoản: `UPDATE users SET status='BANNED'` một câu lệnh (không khóa trước) → vòng 1 khóa & hủy các phiên SCHEDULED/ACTIVE do user bán (id tăng) → vòng 2 khóa & hủy các đơn PENDING_PAYMENT user là người mua/bán (id tăng) → vòng 3 khóa các product liên quan và `release()` (trừ HIDDEN). Tất cả trong một transaction (quyết định CHANGELOG c.3).
10. Isolation READ COMMITTED. Quá 5 giây chờ khóa, deadlock (`CannotAcquireLockException`, `PessimisticLockingFailureException`, `DeadlockLoserDataAccessException`) hoặc `DataIntegrityViolationException` do tranh chấp → 409 `CONFLICT`.
11. Race "bị khóa đúng lúc đặt giá" được chấp nhận (BR-09 hấp thụ). Không thêm kiểm tra đọc lại users.status sau insert bid.

## 6. Chốt phiên (UC07, BR-09)
- Mỗi phiên một transaction riêng (lỗi một phiên không ảnh hưởng phiên khác).
- Lấy người dẫn đầu từ `auctions.highest_bidder_id` / `current_price` (đồng nhất với lượt đặt giá cao nhất). Nếu có, người đó **ACTIVE** và `currentPrice ≥ reservePrice` (khi có) → ENDED: ghi `winnerId`, `finalPrice`, `closedAt=now`, product `reserve()` (RESERVED, trừ HIDDEN), tạo đúng một Order (`paymentDeadline = closedAt + payment-deadline`).
- Ngược lại (không bid / chưa đạt sàn / leader BANNED) → UNSOLD, `closedAt=now`, product `release()` (trừ HIDDEN). **Không** xét người thứ hai.
- Sản phẩm HIDDEN mà phiên vẫn ACTIVE không thể xảy ra (ẩn sản phẩm hủy phiên), nhưng code vẫn phải phòng thủ: không đổi HIDDEN.

## 7. Thông báo (NotificationType chỉ còn 5 giá trị)
| Sự kiện | Loại | Người nhận | ref_type/ref_id |
|---|---|---|---|
| Bid mới vượt người dẫn đầu cũ | OUTBID | người dẫn đầu trước | AUCTION/auctionId |
| Phiên ENDED | AUCTION_RESULT | người thắng (kèm hạn thanh toán) **và** người bán | AUCTION/auctionId |
| Phiên UNSOLD | AUCTION_RESULT | người bán | AUCTION/auctionId |
| Phiên CANCELLED (người bán hủy, Admin hủy, hệ quả khóa user/ẩn sản phẩm) | AUCTION_CANCELLED | người bán **và** người đang dẫn đầu (nếu có) | AUCTION/auctionId |
| Thanh toán SUCCESS | ORDER_UPDATE | người mua và người bán | ORDER/orderId |
| Thanh toán FAILED | (không bắt buộc thông báo) | — | — |
| Gửi hàng | ORDER_UPDATE | người mua | ORDER/orderId |
| Người mua xác nhận nhận hàng | ORDER_UPDATE | người bán | ORDER/orderId |
| Tự hoàn tất (UC10) | ORDER_UPDATE | hai bên | ORDER/orderId |
| Đơn EXPIRED (UC10) | ORDER_UPDATE | hai bên | ORDER/orderId |
| Admin hủy đơn trực tiếp | ORDER_UPDATE | người mua và người bán | ORDER/orderId |
| Đơn bị hủy do khóa tài khoản | ORDER_UPDATE | **bên còn lại** của đơn | ORDER/orderId |
| Đơn bị hủy do ẩn sản phẩm | ORDER_UPDATE | người mua và người bán | ORDER/orderId |
| Báo cáo RESOLVED/REJECTED | REPORT_RESOLVED | người gửi báo cáo | REPORT/reportId |

Không gửi thông báo cho tài khoản ADMIN (Admin không xem thông báo). Mọi thông báo và sự kiện WebSocket phát **sau COMMIT**
(gom vào sự kiện ứng dụng, xử lý bằng `@TransactionalEventListener(phase = AFTER_COMMIT)`).
Mapping loại cũ → mới: AUCTION_WON, AUCTION_ENDED → AUCTION_RESULT; ORDER_CREATED, PAYMENT_RESULT, SHIPMENT_UPDATE → ORDER_UPDATE; PAYMENT_REMINDER → bỏ.

## 8. Ảnh (mục 6.3, Thiết kế)
- `ImageValidator`: đọc tối đa 12 byte đầu. JPEG `FF D8 FF`; PNG `89 50 4E 47`; WEBP `RIFF....WEBP` (byte 0–3 = "RIFF", 8–11 = "WEBP"). Không tin đuôi tệp/Content-Type. > 5 MB/tệp → INVALID_IMAGE. Rỗng → INVALID_IMAGE.
- `ImageStorageService` (Cloudinary Java SDK) dùng chung cho ảnh sản phẩm và ảnh đại diện: `upload(bytes, folder) → (url, publicId)`, `delete(publicId)`. Lỗi SDK/timeout → `IMAGE_UPLOAD_FAILED` (502).
- Ảnh sản phẩm (tạo sản phẩm, thêm ảnh): (1) validate tất cả tệp; (2) upload Cloudinary **ngoài transaction**; (3) transaction ngắn: khóa product, kiểm tra BR-23 + tổng 1–5 ảnh, lưu `product_images` (`public_id` NOT NULL); (4) nếu (3) lỗi → xóa các asset vừa upload (bù trừ), rồi ném lỗi gốc.
- Xóa ảnh/xóa sản phẩm: xóa DB trong transaction; xóa asset Cloudinary **sau COMMIT**, best-effort (lỗi chỉ log).
- Ảnh chính: ảnh đầu tiên khi tạo là `is_primary=true`; khi xóa ảnh chính, ảnh còn lại có id nhỏ nhất thành chính. Không còn endpoint đặt ảnh chính (không có trong 54 endpoint).
- Không cho xóa ảnh cuối cùng (sản phẩm luôn có 1–5 ảnh) → INVALID_IMAGE.
- Ảnh đại diện: validate → upload `omnibid/avatars` → transaction lưu `avatar_url`, `avatar_public_id` → sau COMMIT xóa ảnh cũ best-effort. Trả 200 `{ avatarUrl }`.
- Bỏ `LocalStorageServiceImpl`, thư mục `uploads/`, handler `/uploads/**`.

## 9. Xác thực (mục 6.1, Thiết kế)
- JWT HS256: `sub` = userId, claim `role`, claim `ver` = `users.token_version` lúc cấp; hạn 24 giờ; không Refresh Token. `generateToken(userId, role, tokenVersion)`.
- JwtAuthFilter: parse → nạp User theo id → `ver` khác `tokenVersion` → 401 UNAUTHORIZED; BANNED → 403 ACCOUNT_BANNED; còn lại đặt Authentication (principal mang id, email, role, status). Ở endpoint Public, token hỏng bị bỏ qua.
- Đăng xuất (`POST /auth/logout`) và đổi mật khẩu: `user.increaseTokenVersion()` trong transaction. Không có blacklist `jti`, không kiểm tra `iat` so với thời điểm khởi động.
- `LoginAttemptLimiter` (bộ nhớ, `ConcurrentHashMap`, key = email chuẩn hóa thường + IP): ≥ 5 lần sai trong 15 phút → chặn 15 phút → 429 TOO_MANY_REQUESTS (kiểm tra **trước** khi so mật khẩu); đăng nhập thành công xóa bộ đếm. IP lấy từ `request.getRemoteAddr()` (không tin `X-Forwarded-For` mặc định). Dùng `Clock` để test được.
- Sai email/mật khẩu → 401 UNAUTHORIZED với thông điệp chung "Email hoặc mật khẩu không đúng". Tài khoản BANNED đăng nhập đúng mật khẩu → 403 ACCOUNT_BANNED.
- Mật khẩu: ≥ 8 ký tự, ≥ 1 hoa, ≥ 1 thường, ≥ 1 số (`@Pattern("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$")`) cho `password` và `newPassword`; `confirmPassword` phải bằng `newPassword` (400 VALIDATION_ERROR).
- CSRF tắt (stateless Bearer), giữ header bảo mật mặc định của Spring Security, CORS theo `app.cors.allowed-origins` (`allowCredentials=false` vì không dùng cookie).
