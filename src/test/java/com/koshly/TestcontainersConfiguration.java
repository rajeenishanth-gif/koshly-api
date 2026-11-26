package com.koshly;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** A throwaway Postgres for tests, the same version as production. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection // Spring reads its address, user and password: no settings needed
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:17");
    }
}