package com.example.agent.service;

import com.example.agent.model.dto.DatasetResponse;
import com.example.agent.model.enums.DatasetStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class DatasetServiceUploadTest {

    @Autowired
    private DatasetService datasetService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM cleaning_history");
        jdbcTemplate.update("DELETE FROM datasets");
        jdbcTemplate.update("DELETE FROM analysis_conversation");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update("INSERT INTO users (id, username, password) VALUES (1, 'testuser', 'password')");
    }

    @Test
    void shouldMarkPendingCleanForDirtyData() {
        String csv = "a,b\n1,\n2,y";
        MockMultipartFile file = new MockMultipartFile(
            "file", "dirty.csv", "text/csv", csv.getBytes()
        );
        DatasetResponse response = datasetService.uploadDataset(1L, file);
        assertEquals(DatasetStatus.PENDING_CLEAN, response.status());
    }

    @Test
    void shouldMarkReadyForCleanData() {
        String csv = "a,b\n1,x\n2,y";
        MockMultipartFile file = new MockMultipartFile(
            "file", "clean.csv", "text/csv", csv.getBytes()
        );
        DatasetResponse response = datasetService.uploadDataset(1L, file);
        assertEquals(DatasetStatus.READY, response.status());
    }
}