package com.omnibid.common;

import com.omnibid.enums.NotificationType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class NotificationTemplates {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private NotificationTemplates() {}

    public record TemplateResult(String title, String content) {}

    public static String formatCurrency(BigDecimal amount) {
        if (amount == null) {
            return "0đ";
        }
        return String.format("%,dđ", amount.longValue());
    }

    public static String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DATE_TIME_FORMATTER);
    }

    // N-01: OUTBID
    public static String outbidTitle() {
        return "Bạn đã bị vượt giá";
    }

    public static String outbidContent(Long auctionId, BigDecimal currentPrice) {
        return "Bạn vừa bị vượt giá tại phiên #" + auctionId + ". Mức giá cao nhất hiện tại là " + formatCurrency(currentPrice) + ".";
    }

    // N-02: AUCTION_WON
    public static String auctionWonTitle() {
        return "Bạn đã thắng phiên đấu giá";
    }

    public static String auctionWonContent(Long auctionId, BigDecimal finalPrice) {
        return "Chúc mừng bạn đã thắng phiên đấu giá #" + auctionId + " với mức giá " + formatCurrency(finalPrice) + ".";
    }

    // N-03: ORDER_CREATED
    public static String orderCreatedTitle(Long orderId, LocalDateTime deadline) {
        return "Đơn hàng #" + orderId + ": thanh toán trước " + formatDateTime(deadline);
    }

    public static String orderCreatedContent(Long orderId, Long auctionId, LocalDateTime deadline) {
        return "Đơn hàng #" + orderId + " đã được tạo cho phiên #" + auctionId + ". Vui lòng hoàn tất thanh toán trước " + formatDateTime(deadline) + ".";
    }

    // N-04: AUCTION_ENDED
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

    // N-05: AUCTION_ENDED (cancelled)
    public static String auctionCancelledTitle(String reason) {
        return "Phiên đã bị hủy: " + (reason != null ? reason : "");
    }

    public static String auctionCancelledContent(Long auctionId, String reason) {
        return "Phiên đấu giá #" + auctionId + " đã bị hủy. Lý do: " + (reason != null ? reason : "Không có lý do cụ thể") + ".";
    }

    // N-06: OUTBID (restored leader)
    public static String leadingBidRestoredTitle() {
        return "Bạn đang dẫn đầu phiên";
    }

    public static String leadingBidRestoredContent(Long auctionId, BigDecimal currentPrice) {
        return "Bạn đang dẫn đầu phiên #" + auctionId + " với mức giá " + formatCurrency(currentPrice) + ".";
    }

    // N-07: PAYMENT_REMINDER
    public static String paymentReminderTitle(String timeLeft, Long orderId) {
        return "Còn " + timeLeft + " để thanh toán đơn #" + orderId;
    }

    public static String paymentReminderContent(Long orderId, LocalDateTime deadline) {
        return "Vui lòng hoàn tất thanh toán cho đơn hàng #" + orderId + " trước " + formatDateTime(deadline) + " để tránh bị hủy đơn.";
    }

    // N-08 & N-09: PAYMENT_RESULT
    public static String paymentSuccessBuyerTitle() {
        return "Thanh toán thành công";
    }

    public static String paymentSuccessBuyerContent(Long orderId, String trackingCode) {
        return "Đơn #" + orderId + " đã được thanh toán thành công." + (trackingCode != null ? " Mã vận đơn " + trackingCode + "." : "");
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

    // N-10: SHIPMENT_UPDATE
    public static String shipmentUpdateTitle(String trackingCode, String status) {
        return "Vận đơn " + trackingCode + ": " + status;
    }

    public static String shipmentUpdateContent(String trackingCode, Long orderId, String status, LocalDateTime confirmDeadline) {
        String base = "Vận đơn " + trackingCode + " của đơn #" + orderId + " đã chuyển sang trạng thái " + status + ".";
        if (confirmDeadline != null) {
            base += " Vui lòng xác nhận nhận hàng trước " + formatDateTime(confirmDeadline) + ".";
        }
        return base;
    }

    // N-11..15: ORDER_UPDATE
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

    public static String orderComplaintSubmittedTitle(Long orderId) {
        return "Đơn #" + orderId + " có khiếu nại đang chờ xử lý";
    }

    public static String orderComplaintSubmittedContent(Long orderId) {
        return "Đơn hàng #" + orderId + " có khiếu nại đang chờ xử lý từ phía người mua.";
    }

    // N-16: REPORT_RESOLVED
    public static String reportResolvedTitle(Long reportId, String statusOrAction) {
        return "Báo cáo #" + reportId + ": " + (statusOrAction != null ? statusOrAction : "Đã xử lý");
    }

    public static String reportResolvedContent(Long reportId, String note) {
        return "Báo cáo #" + reportId + " đã được xử lý. Ghi chú: " + (note != null ? note : "Đã hoàn thành") + ".";
    }

    /**
     * Dựng template mẫu mặc định theo NotificationType phục vụ kiểm tra và fallback.
     */
    public static TemplateResult forType(NotificationType type) {
        if (type == null) {
            return new TemplateResult("Thông báo hệ thống", "Bạn có thông báo mới.");
        }
        return switch (type) {
            case OUTBID -> new TemplateResult(outbidTitle(), outbidContent(1L, BigDecimal.valueOf(100000)));
            case AUCTION_WON -> new TemplateResult(auctionWonTitle(), auctionWonContent(1L, BigDecimal.valueOf(100000)));
            case AUCTION_ENDED -> new TemplateResult(auctionEndedSellerTitle(), auctionEndedSellerContent(1L, BigDecimal.valueOf(100000)));
            case ORDER_CREATED -> new TemplateResult(orderCreatedTitle(1L, LocalDateTime.now()), orderCreatedContent(1L, 1L, LocalDateTime.now()));
            case PAYMENT_RESULT -> new TemplateResult(paymentSuccessBuyerTitle(), paymentSuccessBuyerContent(1L, "SIM-000001"));
            case SHIPMENT_UPDATE -> new TemplateResult(shipmentUpdateTitle("SIM-000001", "DELIVERED"), shipmentUpdateContent("SIM-000001", 1L, "DELIVERED", LocalDateTime.now()));
            case ORDER_UPDATE -> new TemplateResult(orderCompletedTitle(1L), orderCompletedContent(1L));
            case PAYMENT_REMINDER -> new TemplateResult(paymentReminderTitle("2 giờ", 1L), paymentReminderContent(1L, LocalDateTime.now()));
            case REPORT_RESOLVED -> new TemplateResult(reportResolvedTitle(1L, "Chấp nhận"), reportResolvedContent(1L, "Đã xử lý vi phạm"));
        };
    }
}
