package com.koshly.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A throwaway Postgres for tests, the same version as production. Started in
 * Docker before the app and removed after the tests, so tests never touch
 * your development database and don't need Docker Compose running.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection // Spring reads its address, user and password: no spring.datasource settings needed
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:17");
    }
}
