package com.omnibid.it;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@ActiveProfiles("demo")
public class DemoProfileMigrationIT {

    @Container
    @ServiceConnection
    static MySQLContainer<?> demoMysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("omnibid_demo_test")
            .withUsername("test")
            .withPassword("test")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("IT-DEMO-01 Profile demo nạp V3__demo_data.sql thành công ngay cả khi thiếu Cloudinary credentials")
    void demoProfileLoadsDemoDataWithoutCloudinaryCredentials() {
        Long demoUserCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = 'an.seller@omnibid.local'", Long.class
        );
        Long productCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Long.class);

        assertThat(demoUserCount).isEqualTo(1L);
        assertThat(productCount).isEqualTo(15L);
    }
}
