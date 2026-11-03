package com.omnibid;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Disabled("chờ Testcontainers ở bước 8")
class OmnibidBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
