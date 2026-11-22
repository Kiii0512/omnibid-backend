package com.omnibid.common;

import com.omnibid.config.DateTimeConfig;
import com.omnibid.enums.NotificationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

public final class NotificationTemplates {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(DateTimeConfig.DISPLAY_ZONE);

    private NotificationTemplates() {}

    public record TemplateResult(String title, String content) {}

    public static String formatCurrency(BigDecimal amount) {
        if (amount == null) {
            return "0đ";
        }
        return String.format("%,dđ", amount.longValue());
    }

    public static String formatDateTime(Instant instant) {
        if (instant == null) {
            return "";
        }
        return DATE_TIME_FORMATTER.format(instant);
    }

    public static String outbidTitle() {
        return "Bạn đã bị vượt giá";
    }

    public static String outbidContent(Long auctionId, BigDecimal currentPrice) {
        return "Bạn vừa bị vượt giá tại phiên #" + auctionId + ". Mức giá cao nhất hiện tại là " + formatCurrency(currentPrice) + ".";
    }

    public static String auctionWonTitle() {
        return "Bạn đã thắng phiên đấu giá";
    }

    public static String auctionWonContent(Long auctionId, BigDecimal finalPrice) {
        return "Chúc mừng bạn đã thắng phiên đấu giá #" + auctionId + " với mức giá " + formatCurrency(finalPrice) + ".";
    }

    public static String orderCreatedTitle(Long orderId, Instant deadline) {
        return "Đơn hàng #" + orderId + ": thanh toán trước " + formatDateTime(deadline);
    }

    public static String orderCreatedContent(Long orderId, Long auctionId, Instant deadline) {
        return "Đơn hàng #" + orderId + " đã được tạo cho phiên #" + auctionId + ". Vui lòng hoàn tất thanh toán trước " + formatDateTime(deadline) + ".";
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

    public static String auctionCancelledTitle(String reason) {
        return "Phiên đã bị hủy: " + (reason != null ? reason : "");
    }

    public static String auctionCancelledContent(Long auctionId, String reason) {
        return "Phiên đấu giá #" + auctionId + " đã bị hủy. Lý do: " + (reason != null ? reason : "Không có lý do cụ thể") + ".";
    }

    public static String paymentSuccessBuyerTitle() {
        return "Thanh toán thành công";
    }

    public static String paymentSuccessBuyerContent(Long orderId) {
        return "Đơn #" + orderId + " đã được thanh toán thành công.";
    }

    public static String paymentSuccessSellerTitle(Long orderId) {
        return "Đơn #" + orderId + " đã được thanh toán";
    }

    public static String paymentSuccessSellerContent(Long orderId) {
        return "Đơn hàng #" + orderId + " đã được thanh toán thành công bởi người mua.";
    }

    public static String paymentFailedTitle(String reason) {
        return "Thanh toán thất bại: " + (reason != null ? reason : "");
    }

    public static String paymentFailedContent(Long orderId, String reason) {
        return "Thanh toán cho đơn hàng #" + orderId + " thất bại. Lý do: " + (reason != null ? reason : "Giao dịch không thành công") + ".";
    }

    public static String orderShippedTitle(Long orderId) {
        return "Đơn #" + orderId + " đã được gửi hàng";
    }

    public static String orderShippedContent(Long orderId, String trackingCode, Instant confirmDeadline) {
        String codePart = (trackingCode != null && !trackingCode.isBlank()) ? " (mã vận đơn: " + trackingCode + ")" : "";
        String deadlinePart = confirmDeadline != null ? " Vui lòng xác nhận nhận hàng trước " + formatDateTime(confirmDeadline) + "." : "";
        return "Người bán đã gửi hàng cho đơn #" + orderId + codePart + "." + deadlinePart;
    }

    public static String orderExpiredTitle(Long orderId) {
        return "Đơn #" + orderId + " đã hết hạn thanh toán";
    }

    public static String orderExpiredContent(Long orderId) {
        return "Đơn hàng #" + orderId + " đã bị hủy do hết thời gian thanh toán.";
    }

    public static String orderReceiptConfirmedTitle() {
        return "Người mua đã xác nhận nhận hàng";
    }

    public static String orderReceiptConfirmedContent(Long orderId) {
        return "Người mua đã xác nhận nhận hàng cho đơn #" + orderId + ". Giao dịch hoàn tất.";
    }

    public static String orderCompletedTitle(Long orderId) {
        return "Đơn #" + orderId + " đã hoàn tất";
    }

    public static String orderCompletedContent(Long orderId) {
        return "Đơn hàng #" + orderId + " đã hoàn tất thành công.";
    }

    public static String orderCancelledTitle(Long orderId, String reason) {
        return "Đơn #" + orderId + " đã bị hủy: " + (reason != null ? reason : "");
    }

    public static String orderCancelledContent(Long orderId, String reason) {
        return "Đơn hàng #" + orderId + " đã bị hủy. Lý do: " + (reason != null ? reason : "Theo quyết định của hệ thống") + ".";
    }

    public static String reportResolvedTitle(Long reportId, String status) {
        return "Báo cáo #" + reportId + ": " + (status != null ? status : "Đã xử lý");
    }

    public static String reportResolvedContent(Long reportId, String note) {
        return "Báo cáo #" + reportId + " đã được xử lý. Ghi chú: " + (note != null ? note : "Đã hoàn thành") + ".";
    }

    public static TemplateResult forType(NotificationType type) {
        if (type == null) {
            return new TemplateResult("Thông báo hệ thống", "Bạn có thông báo mới.");
        }
        return switch (type) {
            case OUTBID -> new TemplateResult(outbidTitle(), outbidContent(1L, BigDecimal.valueOf(100000)));
            case AUCTION_RESULT -> new TemplateResult(auctionEndedSellerTitle(), auctionEndedSellerContent(1L, BigDecimal.valueOf(100000)));
            case AUCTION_CANCELLED -> new TemplateResult(auctionCancelledTitle("Hủy phiên"), auctionCancelledContent(1L, "Hủy phiên"));
            case ORDER_UPDATE -> new TemplateResult(orderCompletedTitle(1L), orderCompletedContent(1L));
            case REPORT_RESOLVED -> new TemplateResult(reportResolvedTitle(1L, "RESOLVED"), reportResolvedContent(1L, "Đã xử lý vi phạm"));
        };
    }
}
