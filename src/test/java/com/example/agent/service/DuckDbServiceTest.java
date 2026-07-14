package com.example.agent.service;

import com.example.agent.model.dto.QueryResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class DuckDbServiceTest {

    @Autowired
    private DuckDbService duckDbService;

    @Test
    void shouldRejectDisallowedCleaningSql() {
        assertThrows(Exception.class, () -> {
            duckDbService.executeCleaningSql(1L, "DROP DATABASE main");
        });
    }

    @Test
    void shouldExecuteAllowedCleaningSql() throws Exception {
        duckDbService.dropTableIfExists(1L, "test_src");
        duckDbService.dropTableIfExists(1L, "test_dst");
        duckDbService.loadFromSql(1L, "SELECT 1 AS a, 2 AS b", "test_src", "csv");
        duckDbService.executeCleaningSql(1L, "CREATE TABLE test_dst AS SELECT * FROM test_src");
        assertTrue(duckDbService.tableExists(1L, "test_src"));
        assertTrue(duckDbService.tableExists(1L, "test_dst"));
        QueryResult srcCount = duckDbService.executeReadOnlyQuery(1L, "SELECT COUNT(*) FROM test_src");
        QueryResult dstCount = duckDbService.executeReadOnlyQuery(1L, "SELECT COUNT(*) FROM test_dst");
        assertEquals(1, srcCount.rows().size());
        assertEquals(1, dstCount.rows().size());
        QueryResult srcData = duckDbService.executeReadOnlyQuery(1L, "SELECT * FROM test_src");
        QueryResult dstData = duckDbService.executeReadOnlyQuery(1L, "SELECT * FROM test_dst");
        assertEquals(srcData.rows(), dstData.rows());
    }
}