package com.example.agent.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class DuckDbConcurrencyTest {

    @TempDir
    Path tempDir;

    @Test
    public void concurrentQueriesForSameUserShouldNotCorruptResults() throws Exception {
        Path csvFile = generateCsv(100_000);
        Path dbFile = tempDir.resolve("concurrent.db");

        DuckDbConnectionPool pool = new DuckDbConnectionPool(tempDir.toString());
        DuckDbService service = new DuckDbService(pool);

        long userId = 1L;
        service.loadCsv(userId, csvFile.toString(), "sales");

        int threads = 8;
        int queriesPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads * queriesPerThread);
        AtomicInteger errors = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            futures.add(executor.submit(() -> {
                for (int i = 0; i < queriesPerThread; i++) {
                    try {
                        var result = service.executeReadOnlyQuery(userId, "SELECT category, COUNT(*) FROM sales GROUP BY category ORDER BY category");
                        assertEquals(2, result.columns().size(), "Should have 2 columns");
                        assertEquals(5, result.rows().size(), "Should have 5 categories");
                    } catch (Exception e) {
                        errors.incrementAndGet();
                        e.printStackTrace();
                    } finally {
                        latch.countDown();
                    }
                }
            }));
        }

        latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(0, errors.get(), "Concurrent queries should not produce errors");

        Files.deleteIfExists(csvFile);
        Files.deleteIfExists(dbFile);
    }

    @Test
    public void concurrentQueriesAcrossUsersShouldNotInterfere() throws Exception {
        int users = 4;
        List<Path> csvFiles = new ArrayList<>();
        DuckDbConnectionPool pool = new DuckDbConnectionPool(tempDir.toString());
        DuckDbService service = new DuckDbService(pool);

        for (long userId = 1; userId <= users; userId++) {
            Path csvFile = generateCsv(50_000);
            csvFiles.add(csvFile);
            service.loadCsv(userId, csvFile.toString(), "sales");
        }

        ExecutorService executor = Executors.newFixedThreadPool(users * 2);
        CountDownLatch latch = new CountDownLatch(users * 20);
        AtomicInteger errors = new AtomicInteger(0);

        for (long userId = 1; userId <= users; userId++) {
            final long id = userId;
            for (int i = 0; i < 20; i++) {
                executor.submit(() -> {
                    try {
                        var result = service.executeReadOnlyQuery(id, "SELECT COUNT(*) FROM sales");
                        assertEquals(1, result.rows().size());
                        assertEquals(50_000L, ((Number) result.rows().get(0).get(0)).longValue());
                    } catch (Exception e) {
                        errors.incrementAndGet();
                        e.printStackTrace();
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }

        latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(0, errors.get(), "Cross-user queries should not interfere");

        for (Path csvFile : csvFiles) {
            Files.deleteIfExists(csvFile);
        }
        for (long userId = 1; userId <= users; userId++) {
            Files.deleteIfExists(tempDir.resolve("user_" + userId + ".db"));
        }
    }

    private Path generateCsv(int rowCount) throws IOException {
        Path csvFile = tempDir.resolve("sales_" + rowCount + "_" + System.nanoTime() + ".csv");
        Random random = new Random(42);
        String[] categories = {"Electronics", "Clothing", "Food", "Books", "Sports"};

        try (BufferedWriter writer = Files.newBufferedWriter(csvFile)) {
            writer.write("id,category,amount");
            writer.newLine();

            for (int i = 1; i <= rowCount; i++) {
                String category = categories[random.nextInt(categories.length)];
                writer.write(String.format("%d,%s,%.2f", i, category, random.nextDouble() * 100));
                writer.newLine();
            }
        }

        return csvFile;
    }
}
