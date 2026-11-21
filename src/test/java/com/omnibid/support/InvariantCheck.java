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
        // (1) Mỗi product <= 1 auction SCHEDULED/ACTIVE
        INVARIANTS.put("INV-1",
                "SELECT product_id FROM auctions WHERE status IN ('SCHEDULED','ACTIVE') GROUP BY product_id HAVING COUNT(*) > 1");

        // (2) auctions.bid_count = COUNT(bids)
        INVARIANTS.put("INV-2",
                "SELECT a.id FROM auctions a LEFT JOIN bids b ON b.auction_id = a.id GROUP BY a.id, a.bid_count HAVING a.bid_count <> COUNT(b.id)");

        // (3) Phiên có bid: current_price = MAX(bids.amount) và highest_bidder_id = bidder của bid max
        INVARIANTS.put("INV-3A",
                "SELECT a.id FROM auctions a JOIN bids b ON b.auction_id = a.id GROUP BY a.id, a.current_price HAVING a.current_price <> MAX(b.amount)");
        INVARIANTS.put("INV-3B",
                "SELECT a.id FROM auctions a JOIN (SELECT auction_id, MAX(amount) max_amt FROM bids GROUP BY auction_id) m ON m.auction_id = a.id " +
                "JOIN bids b ON b.auction_id = a.id AND b.amount = m.max_amt WHERE a.highest_bidder_id <> b.bidder_id");

        // (4) Mỗi order <= 1 payment SUCCESS
        INVARIANTS.put("INV-4",
                "SELECT order_id FROM payments WHERE status = 'SUCCESS' GROUP BY order_id HAVING COUNT(*) > 1");

        // (5) Order PAID/SHIPPING/COMPLETED có đúng 1 SUCCESS
        INVARIANTS.put("INV-5",
                "SELECT o.id FROM orders o LEFT JOIN payments p ON p.order_id = o.id AND p.status = 'SUCCESS' " +
                "WHERE o.status IN ('PAID','SHIPPING','COMPLETED') GROUP BY o.id HAVING COUNT(p.id) <> 1");

        // (6) Product HIDDEN không có phiên mở
        INVARIANTS.put("INV-6",
                "SELECT a.id FROM auctions a JOIN products p ON p.id = a.product_id WHERE p.status = 'HIDDEN' AND a.status IN ('SCHEDULED','ACTIVE')");

        // (7) Auction ENDED <=> có đúng 1 order
        INVARIANTS.put("INV-7A",
                "SELECT a.id FROM auctions a LEFT JOIN orders o ON o.auction_id = a.id WHERE a.status = 'ENDED' GROUP BY a.id HAVING COUNT(o.id) <> 1");
        INVARIANTS.put("INV-7B",
                "SELECT o.id FROM orders o JOIN auctions a ON a.id = o.auction_id WHERE a.status <> 'ENDED'");
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
