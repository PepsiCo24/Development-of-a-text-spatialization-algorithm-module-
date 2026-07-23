package com.cug.geotext.controller;

import com.cug.geotext.common.ApiResponse;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {
    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.ok(Map.of("service", "geotext-backend", "status", "UP", "phase", "1"));
    }

    @GetMapping("/health/database")
    public ApiResponse<Map<String, String>> databaseHealth() {
        try (Connection connection = dataSource.getConnection()) {
            boolean valid = connection.isValid(2);
            return ApiResponse.ok(Map.of(
                "service", "postgresql",
                "status", valid ? "UP" : "DOWN",
                "database", connection.getCatalog() == null ? "" : connection.getCatalog()
            ));
        } catch (SQLException exception) {
            return ApiResponse.ok(Map.of("service", "postgresql", "status", "DOWN", "database", ""));
        }
    }
}

