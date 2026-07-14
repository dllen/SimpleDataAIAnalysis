package com.example.agent.service;

import com.example.agent.model.dto.CleaningExecutionRequest;
import com.example.agent.model.dto.CleaningHistoryRecord;
import com.example.agent.model.dto.CleaningProposal;
import com.example.agent.model.entity.Dataset;
import com.example.agent.model.enums.DatasetStatus;
import com.example.agent.repository.DatasetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class DataCleaningServiceTest {

    @Autowired
    private DataCleaningService cleaningService;

    @Autowired
    private DuckDbService duckDbService;

    @Autowired
    private DatasetRepository datasetRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("MERGE INTO users (id, username, password) KEY(id) VALUES (1, 'testuser', 'password')");
    }

    @Test
    void shouldAnalyzeAndExecuteCleaning() throws SQLException {
        duckDbService.dropTableIfExists(1L, "clean_test");
        duckDbService.loadFromSql(1L, "SELECT 1 AS a, 'x' AS b UNION ALL SELECT NULL, 'y'", "clean_test", "csv");

        Dataset ds = new Dataset();
        ds.setUserId(1L);
        ds.setFileName("test_clean.csv");
        ds.setFileType("csv");
        ds.setTableName("clean_test");
        ds.setColumnInfo("a:int,b:varchar");
        ds.setRowCount(2L);
        ds = datasetRepository.save(ds);

        CleaningProposal proposal = cleaningService.analyze(1L, ds.getId(), "clean_test");
        assertFalse(proposal.issues().isEmpty());

        int missingIdx = -1;
        for (int i = 0; i < proposal.issues().size(); i++) {
            if (proposal.issues().get(i).type().equals("MISSING_VALUE")) {
                missingIdx = i;
                break;
            }
        }
        assertTrue(missingIdx >= 0);

        CleaningExecutionRequest request = new CleaningExecutionRequest(
            List.of(missingIdx), null, false, null
        );
        CleaningHistoryRecord record = cleaningService.execute(1L, ds.getId(), "clean_test", request);
        assertEquals(DatasetStatus.CLEANED, record.status());
    }
}