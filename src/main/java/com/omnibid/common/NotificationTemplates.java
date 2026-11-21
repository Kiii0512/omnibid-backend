package com.omnibid.common;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class NotificationTemplates {

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(DISPLAY_ZONE);

    private NotificationTemplates() {}

    public static String formatCurrency(BigDecimal amount) {
        if (amount == null) {
            return "0 ₫";
        }
        return String.format("%,d ₫", amount.longValue()).replace(',', '.');
    }

    public static String formatDateTime(Instant instant) {
        if (instant == null) {
            return "";
        }
        return DATE_TIME_FORMATTER.format(instant);
    }

    // 1. OUTBID
    public static String outbidTitle() {
        return "Bạn đã bị vượt giá";
    }

    public static String outbidContent(Long auctionId, BigDecimal currentPrice) {
        return "Bạn vừa bị vượt giá tại phiên #" + auctionId + ". Mức giá cao nhất hiện tại là " + formatCurrency(currentPrice) + ".";
    }

    // 2. AUCTION_RESULT
    public static String auctionWonTitle() {
        return "Bạn đã thắng phiên đấu giá";
    }

    public static String auctionWonContent(Long auctionId, BigDecimal finalPrice, Instant paymentDeadline) {
        return "Chúc mừng bạn đã thắng phiên đấu giá #" + auctionId + " với mức giá " + formatCurrency(finalPrice)
                + ". Vui lòng hoàn tất thanh toán trước " + formatDateTime(paymentDeadline) + ".";
    }

    public static String auctionEndedSellerTitle() {
        return "Phiên đã kết thúc";
    }

    public static String auctionEndedSellerContent(Long auctionId, BigDecimal finalPrice) {
        return "Phiên đấu giá #" + auctionId + " đã kết thúc với mức giá " + formatCurrency(finalPrice) + ".";
    }

    public static String auctionUnsoldSellerTitle() {
        return "Phiên không có người mua";
    }

    public static String auctionUnsoldSellerContent(Long auctionId) {
        return "Phiên đấu giá #" + auctionId + " đã kết thúc mà không có người mua hoặc không đạt giá sàn.";
    }

    // 3. AUCTION_CANCELLED
    public static String auctionCancelledTitle() {
        return "Phiên đấu giá đã bị hủy";
    }

    public static String auctionCancelledContent(Long auctionId, String reason) {
        return "Phiên đấu giá #" + auctionId + " đã bị hủy. Lý do: " + (reason != null ? reason : "Không có lý do cụ thể") + ".";
    }

    // 4. ORDER_UPDATE
    public static String orderPaidBuyerTitle(Long orderId) {
        return "Thanh toán thành công đơn hàng #" + orderId;
    }

    public static String orderPaidBuyerContent(Long orderId) {
        return "Đơn hàng #" + orderId + " đã được thanh toán thành công.";
    }

    public static String orderPaidSellerTitle(Long orderId) {
        return "Đơn hàng #" + orderId + " đã được thanh toán";
    }

    public static String orderPaidSellerContent(Long orderId) {
        return "Đơn hàng #" + orderId + " đã được người mua thanh toán.";
    }

    public static String orderShippedBuyerTitle(Long orderId) {
        return "Đơn hàng #" + orderId + " đang được giao";
    }

    public static String orderShippedBuyerContent(Long orderId, String trackingCode) {
        return "Đơn hàng #" + orderId + " đã được gửi hàng." + (trackingCode != null ? " Mã vận đơn: " + trackingCode : "");
    }

    public static String orderConfirmedSellerTitle(Long orderId) {
        return "Đơn hàng #" + orderId + " đã hoàn tất";
    }

    public static String orderConfirmedSellerContent(Long orderId) {
        return "Người mua đã xác nhận nhận hàng cho đơn hàng #" + orderId + ".";
    }

    public static String orderAutoCompleteTitle(Long orderId) {
        return "Đơn hàng #" + orderId + " tự động hoàn tất";
    }

    public static String orderAutoCompleteContent(Long orderId) {
        return "Đơn hàng #" + orderId + " đã tự động hoàn tất do hết thời hạn xác nhận.";
    }

    public static String orderExpiredTitle(Long orderId) {
        return "Đơn hàng #" + orderId + " đã hết hạn";
    }

    public static String orderExpiredContent(Long orderId) {
        return "Đơn hàng #" + orderId + " đã hết hạn thanh toán và bị hủy.";
    }

    public static String orderCancelledTitle(Long orderId) {
        return "Đơn hàng #" + orderId + " đã bị hủy";
    }

    public static String orderCancelledContent(Long orderId, String reason) {
        return "Đơn hàng #" + orderId + " đã bị hủy. Lý do: " + (reason != null ? reason : "Không có lý do cụ thể") + ".";
    }

    // 5. REPORT_RESOLVED
    public static String reportResolvedTitle(Long reportId) {
        return "Báo cáo #" + reportId + " đã được xử lý";
    }

    public static String reportResolvedContent(Long reportId, String decision, String note) {
        return "Báo cáo #" + reportId + " đã được quản trị viên xử lý: " + decision + ". Ghi chú: " + note + ".";
    }
}
