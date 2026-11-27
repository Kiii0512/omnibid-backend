package com.omnibid.it;

import com.omnibid.support.MySqlContainerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import(MySqlContainerConfig.class)
public class SchemaValidationIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("IT-SCHEMA-01 Kiểm tra 10 JPA entity khớp hoàn toàn với Flyway V1/V2 schema và không nạp dữ liệu demo ở profile mặc định")
    void contextLoadsAndSchemaIsValid() {
        Long demoUserCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = 'an.seller@omnibid.local'", Long.class
        );
        Long productCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Long.class);
        Long adminCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = 'admin@omnibid.local' AND role = 'ADMIN'", Long.class
        );

        assertThat(demoUserCount).isZero();
        assertThat(productCount).isZero();
        assertThat(adminCount).isEqualTo(1L);
    }
}
