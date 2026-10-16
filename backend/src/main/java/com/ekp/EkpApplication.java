package com.ekp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Enterprise Knowledge Platform backend.
 *
 * <p>Phase 0 (skeleton) wires the runtime infrastructure: PostgreSQL + pgvector,
 * Redis, Flyway migrations, Actuator health/metrics, structured logging and
 * OpenAPI. Feature modules (auth, document, ingestion, search, chat, ...) are
 * added in later phases.
 */
@SpringBootApplication
public class EkpApplication {

    public static void main(String[] args) {
        SpringApplication.run(EkpApplication.class, args);
    }
}
