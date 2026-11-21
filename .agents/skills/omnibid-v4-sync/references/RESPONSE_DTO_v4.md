# RESPONSE_DTO_v4 — Hợp đồng dữ liệu trả về của 54 endpoint (chuẩn chung backend ↔ frontend)

Chốt ngày 21/11/2026. Bổ sung cho `API_SPEC_v4.md` (endpoint, tham số, mã lỗi). Tệp này quy định **hình dạng `data`** của mọi response.
Backend phải trả đúng tên trường dưới đây; frontend viết type theo đúng tệp này (không cần backend đang chạy để biết tên trường).
Tệp có bản giống hệt ở cả hai repo: `omnibid-backend/.agents/skills/omnibid-v4-sync/references/` và `omnibid-frontend/.agents/skills/omnibid-fe-v4/references/backend/`. Sửa thì sửa cả hai.

Quy ước: tiền `number` nguyên VND; thời gian `string` ISO 8601 có offset `+07:00`; `T | null` = trường luôn có mặt, giá trị có thể null; `?` = trường chỉ có mặt trong điều kiện ghi chú. Danh sách phân trang là `Page<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number }`. Xóa thành công trả `data: null`.

## 1. Kiểu dùng chung
```ts
type UserRole = 'USER' | 'ADMIN';
type UserStatus = 'ACTIVE' | 'BANNED';
type ProductCondition = 'NEW' | 'LIKE_NEW' | 'GOOD' | 'FAIR' | 'POOR';
type ProductStatus = 'AVAILABLE' | 'IN_AUCTION' | 'RESERVED' | 'SOLD' | 'HIDDEN';
type AuctionStatus = 'SCHEDULED' | 'ACTIVE' | 'ENDED' | 'UNSOLD' | 'CANCELLED';
type ReserveStatus = 'MET' | 'NOT_MET' | 'NO_RESERVE';
type OrderStatus = 'PENDING_PAYMENT' | 'PAID' | 'SHIPPING' | 'COMPLETED' | 'CANCELLED' | 'EXPIRED';
type PaymentStatus = 'SUCCESS' | 'FAILED';
type NotificationType = 'OUTBID' | 'AUCTION_RESULT' | 'AUCTION_CANCELLED' | 'ORDER_UPDATE' | 'REPORT_RESOLVED';
type ReportStatus = 'PENDING' | 'RESOLVED' | 'REJECTED';

interface UserRef { id: number; fullName: string }                        // tham chiếu ngắn tới người dùng
interface UserSummary { id: number; fullName: string; email: string; role: UserRole; status: UserStatus; avatarUrl: string | null }
interface UserProfile extends UserSummary { phoneNumber: string | null; address: string | null; createdAt: string }
interface Category { id: number; name: string; slug: string; icon: string | null }
interface ProductImage { id: number; imageUrl: string; isPrimary: boolean }
```

## 2. Xác thực, người dùng
| # | Endpoint | `data` |
|---|---|---|
| 1 | POST /auth/register (201) | `UserProfile` |
| 2 | POST /auth/login | `LoginResponse` |
| 3 | POST /auth/logout | `{ message: string }` |
| 4 | GET /users/me | `UserProfile` |
| 5 | PATCH /users/me | `UserProfile` |
| 6 | PATCH /users/me/password | `{ message: string }` |
| 7 | POST /users/me/avatar | `{ avatarUrl: string }` |
```ts
interface LoginResponse { accessToken: string; tokenType: 'Bearer'; expiresIn: number; expiresAt: string; role: UserRole; user: UserSummary }
```

## 3. Danh mục, sản phẩm
| # | Endpoint | `data` |
|---|---|---|
| 8 | GET /categories | `Page<Category>` |
| 9 | POST /products (201) | `Product` |
| 10 | GET /products/mine | `Page<ProductListItem>` |
| 11 | GET /products/{id} | `Product` |
| 12 | PATCH /products/{id} | `Product` |
| 13 | DELETE /products/{id} | `null` |
| 14 | POST /products/{id}/images (201) | `Product` (đã gồm ảnh mới) |
| 15 | DELETE /products/{id}/images/{imageId} | `Product` |
```ts
interface ProductListItem { id: number; title: string; categoryId: number; categoryName: string; condition: ProductCondition; status: ProductStatus;
  primaryImageUrl: string | null; imageCount: number; activeAuctionId: number | null; everAuctioned: boolean; createdAt: string }
interface Product extends ProductListItem { description: string; images: ProductImage[]; updatedAt: string }
```
`activeAuctionId` = id phiên SCHEDULED/ACTIVE của sản phẩm (null nếu không có). `everAuctioned` = đã từng có phiên (frontend dùng để ẩn nút Xóa).

## 4. Phiên, đặt giá
| # | Endpoint | `data` |
|---|---|---|
| 16 | GET /auctions | `Page<AuctionSummary>` |
| 17 | GET /auctions/{id} | `AuctionDetail` |
| 18 | POST /auctions (201) | `AuctionDetail` |
| 19 | PATCH /auctions/{id} | `AuctionDetail` |
| 20 | POST /auctions/{id}/cancel | `AuctionDetail` |
| 21 | GET /auctions/mine | `Page<MyAuctionItem>` |
| 22 | GET /auctions/{id}/bids | `Page<BidHistoryItem>` |
| 23 | POST /auctions/{id}/bids (201) | `PlaceBidResponse` |
| 24 | GET /bids/mine | `Page<MyBidItem>` |
```ts
interface AuctionSummary { id: number; productId: number; title: string; primaryImageUrl: string | null; categoryId: number; categoryName: string;
  status: AuctionStatus; startPrice: number; currentPrice: number; minNextBid: number; bidCount: number; reserveStatus: ReserveStatus;
  startTime: string; endTime: string; createdAt: string }
interface AuctionDetail extends AuctionSummary { description: string; condition: ProductCondition; images: ProductImage[]; bidIncrement: number;
  seller: UserRef; highestBidderName: string | null;          // tên đã ẩn danh n***h
  viewerIsSeller: boolean; viewerIsLeading: boolean;          // theo người gọi (Guest: false)
  reservePrice?: number | null;                               // chỉ có khi viewerIsSeller
  winnerName: string | null; finalPrice: number | null; closedAt: string | null; cancelReason: string | null; orderId?: number | null }  // orderId chỉ có cho người bán và người thắng khi ENDED
interface MyAuctionItem extends AuctionSummary { reservePrice: number | null; orderId: number | null }
interface BidHistoryItem { id: number; amount: number; bidderName: string; createdAt: string }   // không có bidderId
interface PlaceBidResponse { bidId: number; auctionId: number; amount: number; currentPrice: number; bidCount: number; minNextBid: number }
interface MyBidItem { id: number; auctionId: number; title: string; primaryImageUrl: string | null; amount: number; createdAt: string;
  auctionStatus: AuctionStatus; currentPrice: number; endTime: string; leading: boolean; orderId: number | null }  // orderId khi tôi thắng
```

## 5. Đơn hàng, thanh toán, giao hàng
| # | Endpoint | `data` |
|---|---|---|
| 25 | GET /orders | `Page<OrderSummary>` |
| 26 | GET /orders/{id} | `OrderDetail` |
| 27 | PATCH /orders/{id}/shipping-info | `OrderDetail` |
| 28 | POST /orders/{id}/payments | `PaymentResult` |
| 29 | GET /orders/{id}/payments | `Page<PaymentHistoryItem>` |
| 30 | POST /orders/{id}/ship | `ShipResult` |
| 31 | POST /orders/{id}/confirm-receipt | `ConfirmReceiptResult` |
```ts
interface OrderSummary { id: number; auctionId: number; productId: number; title: string; primaryImageUrl: string | null; finalPrice: number;
  status: OrderStatus; buyer: UserRef; seller: UserRef; paymentDeadline: string; confirmDeadline: string | null; createdAt: string }
interface OrderDetail extends OrderSummary { shippingAddress: string | null; phoneNumber: string | null; trackingCode: string | null;
  paidAt: string | null; shippedAt: string | null; completedAt: string | null; cancelledAt: string | null; cancelReason: string | null;
  productStatus: ProductStatus; viewerRole: 'BUYER' | 'SELLER' }
interface PaymentResult { paymentId: number; attemptNo: number; status: PaymentStatus; orderStatus: OrderStatus; failureReason?: string }
interface PaymentHistoryItem { id: number; attemptNo: number; amount: number; method: 'MOCK_CARD'; status: PaymentStatus; failureReason: string | null; createdAt: string; paidAt: string | null }
interface ShipResult { id: number; orderStatus: OrderStatus; shippedAt: string; confirmDeadline: string; trackingCode: string | null }
interface ConfirmReceiptResult { id: number; orderStatus: OrderStatus; productStatus: ProductStatus }
```

## 6. Thông báo, báo cáo
| # | Endpoint | `data` |
|---|---|---|
| 32 | GET /notifications | `Page<Notification>` |
| 33 | POST /notifications/{id}/read | `Notification` |
| 34 | POST /reports (201) | `{ id: number; status: 'PENDING'; createdAt: string }` |
```ts
interface Notification { id: number; type: NotificationType; title: string; content: string; refType: 'AUCTION' | 'ORDER' | 'REPORT' | null; refId: number | null; isRead: boolean; createdAt: string }
```

## 7. Quản trị
| # | Endpoint | `data` |
|---|---|---|
| 35 | GET /admin/users | `Page<AdminUser>` |
| 36 | POST /admin/users/{id}/ban | `{ id: number; status: 'BANNED'; cancelledAuctions: number; cancelledOrders: number }` |
| 37 | POST /admin/users/{id}/unban | `{ id: number; status: 'ACTIVE' }` |
| 38 | GET /admin/products | `Page<AdminProductItem>` |
| 39 | GET /admin/products/{id} | `AdminProductDetail` |
| 40 | POST /admin/products/{id}/hide | `{ id: number; status: 'HIDDEN'; cancelledAuctions: number; cancelledOrders: number }` |
| 41 | GET /admin/auctions | `Page<AdminAuctionItem>` |
| 42 | GET /admin/auctions/{id} | `AdminAuctionDetail` |
| 43 | POST /admin/auctions/{id}/cancel | `AdminAuctionDetail` |
| 44 | GET /admin/orders | `Page<OrderSummary>` |
| 45 | GET /admin/orders/{id} | `AdminOrderDetail` |
| 46 | POST /admin/orders/{id}/cancel | `AdminOrderDetail` |
| 47 | GET /admin/reports | `Page<AdminReportItem>` |
| 48 | GET /admin/reports/{id} | `AdminReportDetail` |
| 49 | POST /admin/reports/{id}/resolve | `AdminReportDetail` |
| 50 | GET /admin/categories | `Page<AdminCategory>` |
| 51 | POST /admin/categories (201) | `AdminCategory` |
| 52 | PUT /admin/categories/{id} | `AdminCategory` |
| 53 | DELETE /admin/categories/{id} | `null` |
| 54 | GET /admin/statistics | `AdminStatistics` |
```ts
interface AdminUser extends UserSummary { phoneNumber: string | null; createdAt: string }
interface AdminProductItem extends ProductListItem { seller: UserRef }
interface AdminProductDetail extends Product { seller: UserSummary }
interface AdminAuctionItem extends AuctionSummary { seller: UserRef; reservePrice: number | null }
interface AdminAuctionDetail extends AuctionSummary { description: string; condition: ProductCondition; images: ProductImage[]; bidIncrement: number;
  reservePrice: number | null; seller: UserSummary; highestBidder: UserSummary | null; winner: UserSummary | null;
  finalPrice: number | null; closedAt: string | null; cancelReason: string | null; productStatus: ProductStatus; orderId: number | null }
interface AdminOrderDetail extends Omit<OrderDetail, 'viewerRole'> { buyer: UserSummary; seller: UserSummary; payments: PaymentHistoryItem[] }
interface ReportTargetSummary {
  product?: { id: number; title: string; status: ProductStatus; sellerId: number; activeAuction: { id: number; status: AuctionStatus; bidCount: number } | null };
  user?: { id: number; fullName: string; email: string; role: UserRole; status: UserStatus };
  order?: { id: number; status: OrderStatus; buyerId: number; sellerId: number; auctionId: number } }
interface AdminReportItem { id: number; reporter: UserRef; targets: ReportTargetSummary; reason: string; status: ReportStatus; createdAt: string }
interface AdminReportDetail extends AdminReportItem { adminNote: string | null; resolvedBy: UserRef | null; resolvedAt: string | null }
interface AdminCategory extends Category { productCount: number }
interface AdminStatistics { totalUsers: number; bannedUsers: number; auctionsByStatus: Record<AuctionStatus, number>;
  ordersByStatus: Record<OrderStatus, number>; completedOrderValue: number; pendingReports: number }
```

## 8. WebSocket `/topic/auctions/{id}`
```ts
interface AuctionEvent { type: 'BID_PLACED' | 'AUCTION_UPDATED' | 'AUCTION_STARTED' | 'AUCTION_ENDED' | 'AUCTION_CANCELLED';
  auctionId: number; status: AuctionStatus; currentPrice: number; minNextBid: number; bidCount: number; highestBidderName: string | null;
  reserveStatus: ReserveStatus; startTime: string; endTime: string; bid?: BidHistoryItem; createdAt: string }
```
