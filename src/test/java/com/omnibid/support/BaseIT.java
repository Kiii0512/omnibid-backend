package com.omnibid.support;

import com.omnibid.entity.Category;
import com.omnibid.entity.User;
import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;
import com.omnibid.scheduler.AuctionScheduler;
import com.omnibid.scheduler.OrderScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({MySqlContainerConfig.class, TestSupportConfig.class})
@ExtendWith(InvariantCheck.class)
public abstract class BaseIT {

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected Seed seed;

    @Autowired
    protected Api api;

    @Autowired
    protected MutableClock mutableClock;

    @Autowired
    protected CountingMockPaymentGateway gateway;

    @Autowired
    protected OrderScheduler orderScheduler;

    @Autowired
    protected AuctionScheduler auctionScheduler;

    @org.springframework.boot.test.web.server.LocalServerPort
    protected int port;

    protected User seller;
    protected User buyer;
    protected User bidderB;
    protected User admin;
    protected User other;
    protected Category category;

    @BeforeEach
    public void baseSetUp() {
        api.setPort(port);
        mutableClock.reset();
        gateway.reset();

        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbcTemplate.execute("TRUNCATE TABLE audit_logs");
        jdbcTemplate.execute("TRUNCATE TABLE notifications");
        jdbcTemplate.execute("TRUNCATE TABLE shipment_events");
        jdbcTemplate.execute("TRUNCATE TABLE shipments");
        jdbcTemplate.execute("TRUNCATE TABLE payments");
        jdbcTemplate.execute("TRUNCATE TABLE reports");
        jdbcTemplate.execute("TRUNCATE TABLE orders");
        jdbcTemplate.execute("TRUNCATE TABLE bids");
        jdbcTemplate.execute("TRUNCATE TABLE auctions");
        jdbcTemplate.execute("TRUNCATE TABLE product_images");
        jdbcTemplate.execute("TRUNCATE TABLE products");
        jdbcTemplate.execute("TRUNCATE TABLE categories");
        jdbcTemplate.execute("TRUNCATE TABLE users");
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");

        seller = seed.createUser("seller@omnibid.com", "Seller One", UserRole.USER, UserStatus.ACTIVE);
        buyer = seed.createUser("buyer@omnibid.com", "Buyer One", UserRole.USER, UserStatus.ACTIVE);
        bidderB = seed.createUser("bidderb@omnibid.com", "Bidder B", UserRole.USER, UserStatus.ACTIVE);
        admin = seed.createUser("admin@omnibid.com", "Admin One", UserRole.ADMIN, UserStatus.ACTIVE);
        other = seed.createUser("other@omnibid.com", "Other User", UserRole.USER, UserStatus.ACTIVE);

        category = seed.createCategory("Electronics", "electronics");
    }
}
