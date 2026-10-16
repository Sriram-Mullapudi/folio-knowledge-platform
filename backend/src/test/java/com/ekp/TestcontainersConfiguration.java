package com.ekp;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Spins up a real PostgreSQL instance with the pgvector extension for tests,
 * so migrations and vector-aware queries run against the same engine used in
 * production rather than an H2 stand-in. {@code @ServiceConnection} auto-wires
 * the datasource from the container - no manual URL/credentials.
 *
 * Requires a running Docker daemon (locally and in CI).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        DockerImageName image = DockerImageName.parse("pgvector/pgvector:pg16")
                .asCompatibleSubstituteFor("postgres");
        return new PostgreSQLContainer<>(image)
                .withDatabaseName("ekp")
                .withUsername("ekp")
                .withPassword("ekp");
    }
}
