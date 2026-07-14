package com.example.agent.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class DuckDbPerformanceTest {

    private static final int[] ROW_COUNTS = {100_000, 1_000_000};
    private static final int WARMUP_RUNS = 2;
    private static final int BENCHMARK_RUNS = 5;

    @TempDir
    Path tempDir;

    @Test
    public void runDuckDbBenchmark() throws Exception {
        System.out.println("\n===== DuckDB Performance Benchmark =====\n");

        for (int rowCount : ROW_COUNTS) {
            System.out.printf("--- Dataset: %,d rows ---%n", rowCount);

            Path csvFile = generateCsv(rowCount);
            Path dbFile = tempDir.resolve("bench_" + rowCount + ".db");

            try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + dbFile.toString())) {
                benchmarkLoadCsv(conn, csvFile, rowCount);
                benchmarkCount(conn);
                benchmarkGroupBy(conn);
                benchmarkFilter(conn);
                benchmarkJoin(conn, rowCount);
                benchmarkWindowFunction(conn);
                benchmarkReadCsvDirect(conn, csvFile);
            }

            Files.deleteIfExists(csvFile);
            Files.deleteIfExists(dbFile);
            System.out.println();
        }
    }

    private Path generateCsv(int rowCount) throws IOException {
        Path csvFile = tempDir.resolve("sales_" + rowCount + ".csv");
        Random random = new Random(42);
        String[] categories = {"Electronics", "Clothing", "Food", "Books", "Sports"};
        String[] regions = {"North", "South", "East", "West"};

        try (BufferedWriter writer = Files.newBufferedWriter(csvFile)) {
            writer.write("id,category,region,amount,quantity,score,timestamp");
            writer.newLine();

            for (int i = 1; i <= rowCount; i++) {
                String category = categories[random.nextInt(categories.length)];
                String region = regions[random.nextInt(regions.length)];
                double amount = 10 + random.nextDouble() * 990;
                int quantity = 1 + random.nextInt(50);
                double score = random.nextDouble() * 5;
                long timestamp = System.currentTimeMillis() - random.nextInt(86400000 * 365);

                writer.write(String.format("%d,%s,%s,%.2f,%d,%.2f,%d",
                        i, category, region, amount, quantity, score, timestamp));
                writer.newLine();
            }
        }

        long fileSize = Files.size(csvFile);
        System.out.printf("  CSV file: %s (%.2f MB)%n", csvFile.getFileName(), fileSize / 1024.0 / 1024.0);
        return csvFile;
    }

    private void benchmarkLoadCsv(Connection conn, Path csvFile, int expectedRows) throws SQLException {
        execute(conn, "DROP TABLE IF EXISTS sales");

        long duration = time(() -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE sales AS FROM read_csv('" + csvFile.toString() + "', auto_detect=true)");
            }
            return null;
        }, 1);

        long actualRows = queryLong(conn, "SELECT COUNT(*) FROM sales");
        assertEquals(expectedRows, actualRows);
        System.out.printf("  Load CSV: %,d ms (%,d rows/s)%n", duration, actualRows * 1000 / Math.max(duration, 1));
    }

    private void benchmarkCount(Connection conn) throws SQLException {
        long duration = benchmarkQuery(conn, "SELECT COUNT(*) FROM sales");
        System.out.printf("  COUNT: %,d ms%n", duration);
    }

    private void benchmarkGroupBy(Connection conn) throws SQLException {
        long duration = benchmarkQuery(conn, "SELECT category, region, SUM(amount), AVG(quantity), COUNT(*) FROM sales GROUP BY category, region ORDER BY category, region");
        System.out.printf("  GROUP BY (category, region): %,d ms%n", duration);
    }

    private void benchmarkFilter(Connection conn) throws SQLException {
        long duration = benchmarkQuery(conn, "SELECT * FROM sales WHERE amount > 500 AND quantity > 25 AND category = 'Electronics'");
        System.out.printf("  Filter: %,d ms%n", duration);
    }

    private void benchmarkJoin(Connection conn, int rowCount) throws SQLException {
        execute(conn, "DROP TABLE IF EXISTS categories");
        execute(conn, "CREATE TABLE categories AS SELECT DISTINCT category FROM sales");

        long duration = benchmarkQuery(conn,
            "SELECT c.category, SUM(s.amount) as total, AVG(s.score) as avg_score " +
            "FROM sales s JOIN categories c ON s.category = c.category " +
            "GROUP BY c.category ORDER BY total DESC");
        System.out.printf("  JOIN + GROUP BY: %,d ms%n", duration);
    }

    private void benchmarkWindowFunction(Connection conn) throws SQLException {
        long duration = benchmarkQuery(conn,
            "SELECT id, category, amount, " +
            "RANK() OVER (PARTITION BY category ORDER BY amount DESC) as rank " +
            "FROM sales LIMIT 1000");
        System.out.printf("  Window Function + LIMIT: %,d ms%n", duration);
    }

    private void benchmarkReadCsvDirect(Connection conn, Path csvFile) throws SQLException {
        long duration = benchmarkQuery(conn,
            "SELECT category, region, SUM(amount), AVG(quantity), COUNT(*) " +
            "FROM read_csv('" + csvFile.toString() + "', auto_detect=true) " +
            "GROUP BY category, region");
        System.out.printf("  Direct CSV query (no load): %,d ms%n", duration);
    }

    private long benchmarkQuery(Connection conn, String sql) throws SQLException {
        for (int i = 0; i < WARMUP_RUNS; i++) {
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {}
            }
        }

        List<Long> times = new ArrayList<>();
        for (int i = 0; i < BENCHMARK_RUNS; i++) {
            long start = System.currentTimeMillis();
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {}
            }
            times.add(System.currentTimeMillis() - start);
        }

        return median(times);
    }

    private long time(SqlCallable<Void> task, int runs) throws SQLException {
        List<Long> times = new ArrayList<>();
        for (int i = 0; i < runs; i++) {
            long start = System.currentTimeMillis();
            task.call();
            times.add(System.currentTimeMillis() - start);
        }
        return median(times);
    }

    @FunctionalInterface
    private interface SqlCallable<T> {
        T call() throws SQLException;
    }

    private long median(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);
        return sorted.get(sorted.size() / 2);
    }

    private void execute(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private long queryLong(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        return -1;
    }
}
