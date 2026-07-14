package com.example.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class DuckDbConnectionPool {

    private static final Logger log = LoggerFactory.getLogger(DuckDbConnectionPool.class);
    private final ConcurrentHashMap<Long, Connection> connections = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final String basePath;

    public DuckDbConnectionPool(String basePath) {
        this.basePath = basePath;
    }

    public <T> T withConnection(Long userId, SqlFunction<T> action) throws SQLException {
        ReentrantLock lock = locks.computeIfAbsent(userId, id -> new ReentrantLock());
        lock.lock();
        try {
            Connection conn = getConnectionInternal(userId);
            return action.apply(conn);
        } finally {
            lock.unlock();
        }
    }

    public void withConnection(Long userId, SqlConsumer action) throws SQLException {
        withConnection(userId, conn -> {
            action.accept(conn);
            return null;
        });
    }

    public Connection getConnection(Long userId) throws SQLException {
        return connections.computeIfAbsent(userId, id -> {
            try {
                return createConnection(id);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to create DuckDB connection for user: " + id, e);
            }
        });
    }

    public void closeConnection(Long userId) {
        locks.remove(userId);
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

    private Connection getConnectionInternal(Long userId) throws SQLException {
        return connections.computeIfAbsent(userId, id -> {
            try {
                return createConnection(id);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to create DuckDB connection for user: " + id, e);
            }
        });
    }

    private Connection createConnection(Long userId) throws SQLException {
        String url = "jdbc:duckdb:" + basePath + "/user_" + userId + ".db";
        log.debug("Creating DuckDB connection for user {}: {}", userId, url);
        return DriverManager.getConnection(url);
    }

    @FunctionalInterface
    public interface SqlFunction<T> {
        T apply(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlConsumer {
        void accept(Connection connection) throws SQLException;
    }
}
