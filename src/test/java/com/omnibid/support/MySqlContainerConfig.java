package com.omnibid.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Paths;

@TestConfiguration(proxyBeanMethods = false)
public class MySqlContainerConfig {

    private static final MySQLContainer<?> MYSQL;

    static {
        MYSQL = new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("online_auction_db")
                .withUsername("test")
                .withPassword("test")
                .withCopyFileToContainer(
                        MountableFile.forHostPath(Paths.get("db/design_schema.sql").toAbsolutePath()),
                        "/docker-entrypoint-initdb.d/init.sql"
                );
        MYSQL.start();
    }

    @Bean
    @ServiceConnection
    public MySQLContainer<?> mySqlContainer() {
        return MYSQL;
    }
}
