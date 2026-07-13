package com.example.agent.controller;

import com.example.agent.model.dto.ColumnInfo;
import com.example.agent.model.dto.DatasetResponse;
import com.example.agent.model.dto.QueryResult;
import com.example.agent.service.DatasetService;
import com.example.agent.service.DuckDbService;
import com.example.agent.service.DuckDbService.DatasetMeta;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/datasets")
public class DatasetController {

    private final DatasetService datasetService;
    private final DuckDbService duckDbService;

    public DatasetController(DatasetService datasetService, DuckDbService duckDbService) {
        this.datasetService = datasetService;
        this.duckDbService = duckDbService;
    }

    @PostMapping("/upload")
    public ResponseEntity<DatasetResponse> upload(@RequestParam("file") MultipartFile file) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(datasetService.uploadDataset(userId, file));
    }

    @GetMapping
    public ResponseEntity<List<DatasetResponse>> list() {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(datasetService.listDatasets(userId));
    }

    @GetMapping("/{datasetId}")
    public ResponseEntity<DatasetResponse> get(@PathVariable Long datasetId) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(datasetService.getDataset(userId, datasetId));
    }

    @GetMapping("/{datasetId}/schema")
    public ResponseEntity<List<ColumnInfo>> schema(@PathVariable Long datasetId) {
        Long userId = getCurrentUserId();
        DatasetMeta meta = datasetService.getDatasetMeta(userId, datasetId);
        return ResponseEntity.ok(meta.columns());
    }

    @GetMapping("/{datasetId}/preview")
    public ResponseEntity<QueryResult> preview(@PathVariable Long datasetId) {
        Long userId = getCurrentUserId();
        var dataset = datasetService.findDatasetEntity(userId, datasetId);
        try {
            return ResponseEntity.ok(duckDbService.previewData(userId, dataset.getTableName()));
        } catch (Exception e) {
            throw new com.example.agent.exception.BusinessException("数据预览失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{datasetId}")
    public ResponseEntity<Void> delete(@PathVariable Long datasetId) {
        Long userId = getCurrentUserId();
        datasetService.deleteDataset(userId, datasetId);
        return ResponseEntity.ok().build();
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof org.springframework.security.core.userdetails.User user) {
            return datasetService.findUserIdByUsername(user.getUsername());
        }
        throw new com.example.agent.exception.BusinessException("未登录");
    }
}
