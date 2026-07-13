package com.example.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DuckDbConnectionPool {

    private static final Logger log = LoggerFactory.getLogger(DuckDbConnectionPool.class);
    private final ConcurrentHashMap<Long, Connection> connections = new ConcurrentHashMap<>();
    private final String basePath;

    public DuckDbConnectionPool(String basePath) {
        this.basePath = basePath;
    }

    public Connection getConnection(Long userId) throws SQLException {
        return connections.computeIfAbsent(userId, id -> {
            try {
                String url = "jdbc:duckdb:" + basePath + "/user_" + id + ".db";
                log.debug("Creating DuckDB connection for user {}: {}", id, url);
                return DriverManager.getConnection(url);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to create DuckDB connection for user: " + id, e);
            }
        });
    }

    public void closeConnection(Long userId) {
        Connection conn = connections.remove(userId);
        if (conn != null) {
            try {
                conn.close();
                log.debug("Closed DuckDB connection for user {}", userId);
            } catch (SQLException e) {
                log.warn("Error closing DuckDB connection for user {}", userId, e);
            }
        }
    }

    public boolean hasConnection(Long userId) {
        return connections.containsKey(userId);
    }
}
