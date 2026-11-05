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
    @DisplayName("IT-SCHEMA-01 Kiem tra 14 JPA entity khop hoan toan voi db/design_schema.sql")
    void contextLoadsAndSchemaIsValid() {
        // Neu co bat ky entity nao sai lech kieu du lieu, ten cot, ten bang, constraint
        // thi Spring context khoi dong voi hibernate ddl-auto=validate se nem loi va test fail.
        assertThat(true).isTrue();
    }
}
