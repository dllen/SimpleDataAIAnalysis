package com.example.agent.controller;

import com.example.agent.model.dto.CleaningExecutionRequest;
import com.example.agent.model.dto.CleaningHistoryRecord;
import com.example.agent.model.dto.CleaningProposal;
import com.example.agent.model.dto.DatasetResponse;
import com.example.agent.model.entity.Dataset;
import com.example.agent.service.DataCleaningService;
import com.example.agent.service.DatasetService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;

@RestController
@RequestMapping("/api/datasets/{datasetId}/cleaning")
public class DataCleaningController {

    private final DataCleaningService dataCleaningService;
    private final DatasetService datasetService;

    public DataCleaningController(DataCleaningService dataCleaningService, DatasetService datasetService) {
        this.dataCleaningService = dataCleaningService;
        this.datasetService = datasetService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<CleaningProposal> analyze(@PathVariable Long datasetId) throws SQLException {
        Long userId = getCurrentUserId();
        var dataset = datasetService.findDatasetEntity(userId, datasetId);
        return ResponseEntity.ok(dataCleaningService.analyze(userId, datasetId, dataset.getTableName()));
    }

    @PostMapping("/execute")
    public ResponseEntity<CleaningHistoryRecord> execute(@PathVariable Long datasetId,
                                                          @RequestBody CleaningExecutionRequest request) throws SQLException {
        Long userId = getCurrentUserId();
        var dataset = datasetService.findDatasetEntity(userId, datasetId);
        return ResponseEntity.ok(dataCleaningService.execute(userId, datasetId, dataset.getTableName(), request));
    }

    @PostMapping("/save-as")
    public ResponseEntity<DatasetResponse> saveAs(@PathVariable Long datasetId,
                                                   @RequestBody CleaningExecutionRequest request) throws SQLException {
        Long userId = getCurrentUserId();
        var dataset = datasetService.findDatasetEntity(userId, datasetId);
        Dataset newDataset = dataCleaningService.saveAs(userId, datasetId, dataset.getTableName(), request);
        return ResponseEntity.ok(datasetService.getDataset(userId, newDataset.getId()));
    }

    @GetMapping("/history")
    public ResponseEntity<List<CleaningHistoryRecord>> history(@PathVariable Long datasetId) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(dataCleaningService.history(userId, datasetId));
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof org.springframework.security.core.userdetails.User user) {
            return datasetService.findUserIdByUsername(user.getUsername());
        }
        throw new com.example.agent.exception.BusinessException("未登录");
    }
}