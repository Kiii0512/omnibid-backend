package com.omnibid;

import com.omnibid.support.MySqlContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(MySqlContainerConfig.class)
class OmnibidBackendApplicationIT {

	@Test
	void contextLoads() {
	}

}
