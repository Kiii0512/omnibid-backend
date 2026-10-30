package com.omnibid;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OmnibidBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(OmnibidBackendApplication.class, args);
	}

}
