package com.omnibid.it;

import com.omnibid.support.MySqlContainerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import(MySqlContainerConfig.class)
public class SchemaValidationIT {

    @Test
    @DisplayName("IT-SCHEMA-01 Kiểm tra 10 JPA entity khớp hoàn toàn với Flyway V1/V2 schema")
    void contextLoadsAndSchemaIsValid() {
        assertThat(true).isTrue();
    }
}
