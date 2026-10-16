package com.ekp.system;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Minimal, unauthenticated system endpoint used by the frontend status page to
 * confirm end-to-end connectivity. Deep component health (db, redis, disk) is
 * reported separately by Spring Boot Actuator at {@code /actuator/health}.
 */
@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "System", description = "Liveness and build information")
public class SystemController {

    private final String appName;
    private final String version;

    public SystemController(@Value("${info.app.name}") String appName,
                            @Value("${info.app.version}") String version) {
        this.appName = appName;
        this.version = version;
    }

    @GetMapping("/ping")
    @Operation(summary = "Liveness ping with build metadata")
    public Map<String, Object> ping() {
        return Map.of(
                "status", "UP",
                "app", appName,
                "version", version,
                "time", Instant.now().toString()
        );
    }
}
