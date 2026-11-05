package com.omnibid.support;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InvariantCheck implements AfterEachCallback {

    private static final Map<String, String> INVARIANTS = new LinkedHashMap<>();

    static {
        // INV-1 Phiên ENDED phải có đúng một Order
        INVARIANTS.put("INV-1",
                "SELECT a.id FROM auctions a LEFT JOIN orders o ON o.auction_id = a.id " +
                "WHERE a.status = 'ENDED' AND o.id IS NULL");

        // INV-2 Phiên không ENDED không được có Order
        INVARIANTS.put("INV-2",
                "SELECT a.id FROM auctions a JOIN orders o ON o.auction_id = a.id WHERE a.status <> 'ENDED'");

        // INV-3 Phiên đã đóng/hủy không còn Bid LEADING
        INVARIANTS.put("INV-3",
                "SELECT DISTINCT b.auction_id FROM bids b JOIN auctions a ON a.id = b.auction_id " +
                "WHERE a.status IN ('ENDED','UNSOLD','CANCELLED') AND b.status = 'LEADING'");

        // INV-4 Phiên ACTIVE có Bid: đúng một Bid LEADING và khớp highest_bidder_id/current_price
        INVARIANTS.put("INV-4",
                "SELECT a.id FROM auctions a " +
                "LEFT JOIN bids b ON b.auction_id = a.id AND b.status = 'LEADING' " +
                "WHERE a.status = 'ACTIVE' AND a.bid_count > 0 " +
                "GROUP BY a.id, a.highest_bidder_id, a.current_price " +
                "HAVING COUNT(b.id) <> 1 OR MAX(b.bidder_id) <> a.highest_bidder_id OR MAX(b.amount) <> a.current_price");

        // INV-5 Order PAID/SHIPPING/COMPLETED phải có Shipment; đơn khác không có Shipment
        INVARIANTS.put("INV-5",
                "SELECT o.id FROM orders o LEFT JOIN shipments s ON s.order_id = o.id " +
                "WHERE (o.status IN ('PAID','SHIPPING','COMPLETED') AND s.id IS NULL) " +
                "   OR (o.status IN ('PENDING_PAYMENT','EXPIRED') AND s.id IS NOT NULL)");

        // INV-6 Order PAID/SHIPPING/COMPLETED có đúng một Payment SUCCESS; đơn khác không có
        INVARIANTS.put("INV-6",
                "SELECT o.id FROM orders o LEFT JOIN payments p ON p.order_id = o.id AND p.status = 'SUCCESS' " +
                "GROUP BY o.id, o.status " +
                "HAVING (o.status IN ('PAID','SHIPPING','COMPLETED') AND COUNT(p.id) <> 1) " +
                "    OR (o.status IN ('PENDING_PAYMENT','EXPIRED')   AND COUNT(p.id) <> 0)");

        // INV-7 Order đã đóng không còn Payment PENDING
        INVARIANTS.put("INV-7",
                "SELECT p.order_id FROM payments p JOIN orders o ON o.id = p.order_id " +
                "WHERE p.status = 'PENDING' AND o.status IN ('EXPIRED','CANCELLED','COMPLETED')");

        // INV-8 Product RESERVED ⇒ có Order PENDING_PAYMENT/PAID/SHIPPING
        INVARIANTS.put("INV-8",
                "SELECT p.id FROM products p " +
                "WHERE p.status = 'RESERVED' " +
                "  AND NOT EXISTS (SELECT 1 FROM orders o WHERE o.product_id = p.id " +
                "                  AND o.status IN ('PENDING_PAYMENT','PAID','SHIPPING'))");

        // INV-9 Product AVAILABLE không có phiên mở hay Order tiến hành
        INVARIANTS.put("INV-9",
                "SELECT p.id FROM products p " +
                "WHERE p.status = 'AVAILABLE' " +
                "  AND (EXISTS (SELECT 1 FROM auctions a WHERE a.product_id = p.id AND a.status IN ('SCHEDULED','ACTIVE')) " +
                "    OR EXISTS (SELECT 1 FROM orders o   WHERE o.product_id = p.id AND o.status IN ('PENDING_PAYMENT','PAID','SHIPPING')))");

        // INV-10 Order COMPLETED ⇒ Product SOLD
        INVARIANTS.put("INV-10",
                "SELECT o.id FROM orders o JOIN products p ON p.id = o.product_id " +
                "WHERE o.status = 'COMPLETED' AND p.status <> 'SOLD'");

        // INV-11 Không còn Order PENDING_PAYMENT của user BANNED hoặc Product HIDDEN
        INVARIANTS.put("INV-11",
                "SELECT o.id FROM orders o " +
                "JOIN users b ON b.id = o.buyer_id JOIN users s ON s.id = o.seller_id JOIN products p ON p.id = o.product_id " +
                "WHERE o.status = 'PENDING_PAYMENT' AND (b.status = 'BANNED' OR s.status = 'BANNED' OR p.status = 'HIDDEN')");

        // INV-12 Shipment: sự kiện cuối khớp trạng thái hiện tại
        INVARIANTS.put("INV-12",
                "SELECT s.id FROM shipments s " +
                "WHERE s.status <> (SELECT e.status FROM shipment_events e WHERE e.shipment_id = s.id " +
                "                   ORDER BY e.created_at DESC, e.id DESC LIMIT 1)");

        // INV-13 Report đã xử lý có người xử lý và thời điểm
        INVARIANTS.put("INV-13",
                "SELECT id FROM reports WHERE status <> 'PENDING' AND (resolved_by IS NULL OR resolved_at IS NULL)");

        // INV-14 Hạn thanh toán đúng closed_at + 48h (cho phép sai số 2 giây)
        INVARIANTS.put("INV-14",
                "SELECT o.id FROM orders o JOIN auctions a ON a.id = o.auction_id " +
                "WHERE ABS(TIMESTAMPDIFF(SECOND, o.payment_deadline, a.closed_at + INTERVAL 48 HOUR)) > 2");
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        ApplicationContext applicationContext = SpringExtension.getApplicationContext(context);
        JdbcTemplate jdbcTemplate = applicationContext.getBean(JdbcTemplate.class);

        String testName = context.getRequiredTestClass().getSimpleName() + "." + context.getRequiredTestMethod().getName();

        for (Map.Entry<String, String> entry : INVARIANTS.entrySet()) {
            String invCode = entry.getKey();
            String sql = entry.getValue();

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            if (!rows.isEmpty()) {
                throw new AssertionError(String.format(
                        "Invariant violation [%s] in test [%s]. Violated rows count: %d, rows: %s, sql: %s",
                        invCode, testName, rows.size(), rows, sql
                ));
            }
        }
    }
}
