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
        // INV-1: Mỗi product có tối đa 1 auction SCHEDULED hoặc ACTIVE (BR-15)
        INVARIANTS.put("INV-1",
                "SELECT product_id FROM auctions " +
                "WHERE status IN ('SCHEDULED','ACTIVE') " +
                "GROUP BY product_id HAVING COUNT(*) > 1");

        // INV-2: auctions.bid_count = COUNT(bids)
        INVARIANTS.put("INV-2",
                "SELECT a.id FROM auctions a " +
                "LEFT JOIN bids b ON b.auction_id = a.id " +
                "GROUP BY a.id, a.bid_count " +
                "HAVING a.bid_count <> COUNT(b.id)");

        // INV-3: Phiên có bid thì current_price = MAX(bids.amount) và highest_bidder_id = bidder của bid max
        INVARIANTS.put("INV-3",
                "SELECT a.id FROM auctions a " +
                "JOIN bids b ON b.auction_id = a.id " +
                "WHERE a.bid_count > 0 " +
                "  AND b.amount = (SELECT MAX(b2.amount) FROM bids b2 WHERE b2.auction_id = a.id) " +
                "  AND (a.current_price <> b.amount OR a.highest_bidder_id <> b.bidder_id)");

        // INV-4: Mỗi order có tối đa 1 payment SUCCESS (BR-11)
        INVARIANTS.put("INV-4",
                "SELECT order_id FROM payments " +
                "WHERE status = 'SUCCESS' " +
                "GROUP BY order_id HAVING COUNT(*) > 1");

        // INV-5: Order PAID/SHIPPING/COMPLETED có đúng 1 payment SUCCESS; PENDING_PAYMENT/EXPIRED có 0
        INVARIANTS.put("INV-5",
                "SELECT o.id FROM orders o " +
                "LEFT JOIN payments p ON p.order_id = o.id AND p.status = 'SUCCESS' " +
                "GROUP BY o.id, o.status " +
                "HAVING (o.status IN ('PAID','SHIPPING','COMPLETED') AND COUNT(p.id) <> 1) " +
                "    OR (o.status IN ('PENDING_PAYMENT','EXPIRED') AND COUNT(p.id) <> 0)");

        // INV-6: Product HIDDEN không có phiên mở (SCHEDULED/ACTIVE)
        INVARIANTS.put("INV-6",
                "SELECT p.id FROM products p " +
                "JOIN auctions a ON a.product_id = p.id " +
                "WHERE p.status = 'HIDDEN' AND a.status IN ('SCHEDULED','ACTIVE')");

        // INV-7: Auction ENDED <=> có đúng 1 order
        INVARIANTS.put("INV-7",
                "SELECT a.id FROM auctions a " +
                "LEFT JOIN orders o ON o.auction_id = a.id " +
                "GROUP BY a.id, a.status " +
                "HAVING (a.status = 'ENDED' AND COUNT(o.id) <> 1) " +
                "    OR (a.status <> 'ENDED' AND COUNT(o.id) <> 0)");
    }

    @Override
    public void afterEach(ExtensionContext context) {
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
