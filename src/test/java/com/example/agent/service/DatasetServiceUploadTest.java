package com.example.agent.service;

import com.example.agent.model.dto.DatasetResponse;
import com.example.agent.model.enums.DatasetStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class DatasetServiceUploadTest {

    @Autowired
    private DatasetService datasetService;

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