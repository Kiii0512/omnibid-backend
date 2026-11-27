package com.omnibid.it;

import com.omnibid.entity.Order;
import com.omnibid.entity.Product;
import com.omnibid.entity.Report;
import com.omnibid.enums.ProductStatus;
import com.omnibid.enums.ReportStatus;
import com.omnibid.repository.ReportRepository;
import com.omnibid.security.CurrentUser;
import com.omnibid.service.OrderService;
import com.omnibid.service.ReportService;
import com.omnibid.support.BaseIT;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
public class QueryCountRegressionIT extends BaseIT {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private ReportRepository reportRepository;

    @Test
    @DisplayName("INT-PERF-01 HIGH-05: 20 Order và 20 Report không gây N+1 (<= 8 SQL queries mỗi request)")
    void testOrderAndReportListEndpointsAvoidNPlusOne() {
        for (int i = 0; i < 20; i++) {
            Order order = seed.orderPaid(buyer, seller);
            Product prod = seed.createProduct(seller, category, "Reported Item " + i, ProductStatus.AVAILABLE);
            reportRepository.save(Report.builder()
                    .reporterId(buyer.getId())
                    .reportedProductId(prod.getId())
                    .reportedUserId(seller.getId())
                    .reportedOrderId(order.getId())
                    .reason("Test report #" + i)
                    .status(ReportStatus.PENDING)
                    .build());
        }

        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        stats.clear();
        var ordersPage = orderService.getOrders(new CurrentUser(buyer.getId(), false), "BUYER", null, PageRequest.of(0, 20));
        long orderQueries = stats.getPrepareStatementCount();
        assertThat(ordersPage.getContent()).hasSize(20);
        assertThat(orderQueries).isLessThanOrEqualTo(8L);

        stats.clear();
        var reportsPage = reportService.getAdminReports(new CurrentUser(admin.getId(), true), null, null, PageRequest.of(0, 20));
        long reportQueries = stats.getPrepareStatementCount();
        assertThat(reportsPage.getContent()).hasSize(20);
        assertThat(reportQueries).isLessThanOrEqualTo(8L);
    }
}
