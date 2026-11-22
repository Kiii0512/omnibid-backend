package com.omnibid.common;

import com.omnibid.config.DateTimeConfig;
import com.omnibid.enums.NotificationType;
import com.omnibid.enums.ReportStatus;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class NotificationTemplates {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(DateTimeConfig.DISPLAY_ZONE);

    private static final DecimalFormatSymbols VND_SYMBOLS = createVndSymbols();

    private NotificationTemplates() {}

    public record TemplateResult(String title, String content) {}

    private static DecimalFormatSymbols createVndSymbols() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator('.');
        return symbols;
    }

    public static String formatCurrency(BigDecimal amount) {
        if (amount == null) {
            return "0 ₫";
        }
        DecimalFormat formatter = new DecimalFormat("#,##0", VND_SYMBOLS);
        return formatter.format(amount.longValue()) + " ₫";
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
        String deadlinePart = paymentDeadline != null
                ? " Vui lòng hoàn tất thanh toán trước " + formatDateTime(paymentDeadline) + "."
                : "";
        return "Chúc mừng bạn đã thắng phiên đấu giá #" + auctionId + " với mức giá " + formatCurrency(finalPrice) + "." + deadlinePart;
    }

    public static String auctionEndedSellerTitle() {
        return "Phiên đấu giá đã kết thúc";
    }

    public static String auctionEndedSellerContent(Long auctionId, BigDecimal finalPrice) {
        return "Phiên đấu giá #" + auctionId + " đã kết thúc với mức giá " + formatCurrency(finalPrice) + ".";
    }

    public static String auctionUnsoldSellerTitle() {
        return "Phiên đấu giá không có người mua";
    }

    public static String auctionUnsoldSellerContent(Long auctionId) {
        return "Phiên đấu giá #" + auctionId + " đã kết thúc mà không có người mua hoặc chưa đạt giá sàn.";
    }

    // 3. AUCTION_CANCELLED
    public static String auctionCancelledTitle(String reason) {
        if (reason == null || reason.isBlank()) {
            return "Phiên đấu giá đã bị hủy";
        }
        return "Phiên đấu giá đã bị hủy: " + reason;
    }

    public static String auctionCancelledContent(Long auctionId, String reason) {
        String resolvedReason = (reason != null && !reason.isBlank()) ? reason : "Không có lý do cụ thể";
        return "Phiên đấu giá #" + auctionId + " đã bị hủy. Lý do: " + resolvedReason + ".";
    }

    // 4. ORDER_UPDATE
    public static String paymentSuccessBuyerTitle() {
        return "Thanh toán thành công";
    }

    public static String paymentSuccessBuyerContent(Long orderId) {
        return "Đơn hàng #" + orderId + " đã được thanh toán thành công.";
    }

    public static String paymentSuccessSellerTitle(Long orderId) {
        return "Đơn hàng #" + orderId + " đã được thanh toán";
    }

    public static String paymentSuccessSellerContent(Long orderId) {
        return "Đơn hàng #" + orderId + " đã được người mua thanh toán thành công. Vui lòng chuẩn bị gửi hàng.";
    }

    public static String orderShippedTitle(Long orderId) {
        return "Đơn hàng #" + orderId + " đã được gửi đi";
    }

    public static String orderShippedContent(Long orderId, String trackingCode, Instant confirmDeadline) {
        String codePart = (trackingCode != null && !trackingCode.isBlank()) ? " (mã vận đơn: " + trackingCode + ")" : "";
        String deadlinePart = confirmDeadline != null
                ? " Vui lòng xác nhận nhận hàng trước " + formatDateTime(confirmDeadline) + "."
                : "";
        return "Người bán đã gửi hàng cho đơn #" + orderId + codePart + "." + deadlinePart;
    }

    public static String orderExpiredTitle(Long orderId) {
        return "Đơn hàng #" + orderId + " đã hết hạn thanh toán";
    }

    public static String orderExpiredContent(Long orderId) {
        return "Đơn hàng #" + orderId + " đã hết hạn do quá thời gian thanh toán.";
    }

    public static String orderReceiptConfirmedTitle() {
        return "Người mua đã xác nhận nhận hàng";
    }

    public static String orderReceiptConfirmedContent(Long orderId) {
        return "Người mua đã xác nhận nhận hàng cho đơn #" + orderId + ". Giao dịch hoàn tất.";
    }

    public static String orderCompletedTitle(Long orderId) {
        return "Đơn hàng #" + orderId + " đã hoàn tất";
    }

    public static String orderCompletedContent(Long orderId) {
        return "Đơn hàng #" + orderId + " đã tự động hoàn tất thành công.";
    }

    public static String orderCancelledTitle(Long orderId, String reason) {
        if (reason == null || reason.isBlank()) {
            return "Đơn hàng #" + orderId + " đã bị hủy";
        }
        return "Đơn hàng #" + orderId + " đã bị hủy: " + reason;
    }

    public static String orderCancelledContent(Long orderId, String reason) {
        String resolvedReason = (reason != null && !reason.isBlank()) ? reason : "Theo quyết định của hệ thống";
        return "Đơn hàng #" + orderId + " đã bị hủy. Lý do: " + resolvedReason + ".";
    }

    // 5. REPORT_RESOLVED
    public static String reportResolvedTitle(Long reportId, ReportStatus status) {
        String label = status == ReportStatus.REJECTED ? "Bị bác bỏ" : "Đã xử lý";
        return "Báo cáo #" + reportId + ": " + label;
    }

    public static String reportResolvedContent(Long reportId, String note) {
        String resolvedNote = (note != null && !note.isBlank()) ? note : "Đã hoàn thành";
        return "Báo cáo #" + reportId + " đã được quản trị viên xử lý. Ghi chú: " + resolvedNote + ".";
    }

    public static TemplateResult forType(NotificationType type) {
        if (type == null) {
            return new TemplateResult("Thông báo hệ thống", "Bạn có thông báo mới.");
        }
        return switch (type) {
            case OUTBID -> new TemplateResult(outbidTitle(), outbidContent(1L, BigDecimal.valueOf(520000)));
            case AUCTION_RESULT -> new TemplateResult(auctionEndedSellerTitle(), auctionEndedSellerContent(1L, BigDecimal.valueOf(520000)));
            case AUCTION_CANCELLED -> new TemplateResult(auctionCancelledTitle("Hủy phiên"), auctionCancelledContent(1L, "Hủy phiên"));
            case ORDER_UPDATE -> new TemplateResult(orderCompletedTitle(1L), orderCompletedContent(1L));
            case REPORT_RESOLVED -> new TemplateResult(reportResolvedTitle(1L, ReportStatus.RESOLVED), reportResolvedContent(1L, "Đã xử lý vi phạm"));
        };
    }
}
