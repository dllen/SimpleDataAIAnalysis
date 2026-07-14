package com.example.agent.service;

import com.example.agent.model.dto.CleaningIssue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class DataQualityScannerTest {

    @Autowired
    private DuckDbService duckDbService;

    @Autowired
    private DataQualityScanner scanner;

    @Test
    void shouldDetectMissingValues() throws SQLException {
        duckDbService.dropTableIfExists(1L, "scan_test");
        duckDbService.loadFromSql(1L, "SELECT 1 AS a, 'x' AS b UNION ALL SELECT NULL, 'y'", "scan_test", "csv");
        List<CleaningIssue> issues = scanner.scan(1L, "scan_test");
        assertTrue(issues.stream().anyMatch(i -> i.type().equals("MISSING_VALUE") && i.column().equals("a")));
    }
}
