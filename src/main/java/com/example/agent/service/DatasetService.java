package com.example.agent.service;

import com.example.agent.model.dto.CleaningProposal;
import com.example.agent.model.dto.ColumnInfo;
import com.example.agent.model.dto.DatasetResponse;
import com.example.agent.model.entity.Dataset;
import com.example.agent.model.enums.DatasetStatus;
import com.example.agent.model.enums.FileType;
import com.example.agent.exception.BusinessException;
import com.example.agent.repository.DatasetRepository;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

@Service
public class DatasetService {

    private static final Logger log = LoggerFactory.getLogger(DatasetService.class);

    private final DuckDbService duckDbService;
    private final DatasetRepository datasetRepository;
    private final com.example.agent.repository.UserRepository userRepository;
    private final DataCleaningService dataCleaningService;

    @Value("${app.upload.storage-path}")
    private String uploadPath;

    @Value("${app.upload.max-file-size}")
    private long maxFileSize;

    public DatasetService(DuckDbService duckDbService, DatasetRepository datasetRepository,
                          com.example.agent.repository.UserRepository userRepository,
                          DataCleaningService dataCleaningService) {
        this.duckDbService = duckDbService;
        this.datasetRepository = datasetRepository;
        this.userRepository = userRepository;
        this.dataCleaningService = dataCleaningService;
    }

    @Transactional
    public DatasetResponse uploadDataset(Long userId, MultipartFile file) {
        validateFile(file);

        FileType fileType = FileType.fromFilename(file.getOriginalFilename());
        String finalTableName = generateTableName(userId);
        String rawTableName = "_raw_" + finalTableName;

        try {
            Path userDir = Paths.get(uploadPath, "user_" + userId);
            Files.createDirectories(userDir);

            String storedFileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path filePath = userDir.resolve(storedFileName);
            file.transferTo(filePath);

            DuckDbService.DatasetMeta rawMeta = switch (fileType) {
                case CSV -> duckDbService.loadCsv(userId, filePath.toString(), rawTableName);
                case JSON -> duckDbService.loadJson(userId, filePath.toString(), rawTableName);
                case EXCEL -> loadExcelToDuckDb(userId, file, filePath, rawTableName);
            };

            DatasetStatus status = DatasetStatus.READY;
            CleaningProposal proposal = dataCleaningService.analyze(userId, null, rawTableName);
            if (!proposal.issues().isEmpty()) {
                status = DatasetStatus.PENDING_CLEAN;
            } else {
                duckDbService.cloneTable(userId, rawTableName, finalTableName);
            }

            Dataset dataset = new Dataset();
            dataset.setUserId(userId);
            dataset.setFileName(file.getOriginalFilename());
            dataset.setFileType(fileType.getExtension());
            dataset.setTableName(status == DatasetStatus.READY ? finalTableName : rawTableName);
            dataset.setRawTableName(rawTableName);
            dataset.setColumnInfo(serializeColumns(rawMeta.columns()));
            dataset.setRowCount(rawMeta.rowCount());
            dataset.setStatus(status);
            dataset = datasetRepository.save(dataset);

            return toResponse(dataset, rawMeta.columns());

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to upload dataset for user {}", userId, e);
            throw new BusinessException("文件上传失败: " + e.getMessage());
        }
    }

    private DuckDbService.DatasetMeta loadExcelToDuckDb(Long userId, MultipartFile file, Path filePath, String tableName) throws Exception {
        Path csvPath = filePath.resolveSibling(tableName + ".csv");
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            try (BufferedWriter writer = Files.newBufferedWriter(csvPath)) {
                Iterator<Row> rowIterator = sheet.iterator();
                while (rowIterator.hasNext()) {
                    Row row = rowIterator.next();
                    List<String> cells = new ArrayList<>();
                    for (int i = 0; i < row.getLastCellNum(); i++) {
                        Cell cell = row.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        cells.add(formatter.formatCellValue(cell));
                    }
                    writer.write(String.join(",", cells));
                    writer.newLine();
                }
            }
        }
        return duckDbService.loadCsv(userId, csvPath.toString(), tableName);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }
        if (file.getSize() > maxFileSize) {
            throw new BusinessException("文件大小超过限制 (最大 " + (maxFileSize / 1024 / 1024) + "MB)");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.contains(".")) {
            throw new BusinessException("无效的文件名");
        }
        try {
            FileType.fromFilename(filename);
        } catch (BusinessException e) {
            throw new BusinessException("不支持的文件格式，仅支持 CSV、Excel(.xlsx/.xls)、JSON");
        }
    }

    public List<DatasetResponse> listDatasets(Long userId) {
        List<Dataset> datasets = datasetRepository.findByUserId(userId);
        return datasets.stream().map(ds -> {
            List<ColumnInfo> columns = deserializeColumns(ds.getColumnInfo());
            return toResponse(ds, columns);
        }).toList();
    }

    public DatasetResponse getDataset(Long userId, Long datasetId) {
        Dataset dataset = datasetRepository.findByIdAndUserId(datasetId, userId)
                .orElseThrow(() -> new BusinessException("数据集不存在"));
        List<ColumnInfo> columns = deserializeColumns(dataset.getColumnInfo());
        return toResponse(dataset, columns);
    }

    public DuckDbService.DatasetMeta getDatasetMeta(Long userId, Long datasetId) {
        Dataset dataset = datasetRepository.findByIdAndUserId(datasetId, userId)
                .orElseThrow(() -> new BusinessException("数据集不存在"));
        try {
            List<ColumnInfo> columns = duckDbService.getSchema(userId, dataset.getTableName());
            long rowCount = duckDbService.previewData(userId, dataset.getTableName()).totalRows();
            return new DuckDbService.DatasetMeta(dataset.getTableName(), dataset.getFileType(), rowCount, columns);
        } catch (Exception e) {
            throw new BusinessException("获取数据集信息失败: " + e.getMessage());
        }
    }

    @Transactional
    public void deleteDataset(Long userId, Long datasetId) {
        Dataset dataset = datasetRepository.findByIdAndUserId(datasetId, userId)
                .orElseThrow(() -> new BusinessException("数据集不存在"));
        try {
            duckDbService.dropTable(userId, dataset.getTableName());
        } catch (Exception e) {
            log.warn("Failed to drop table {} for user {}", dataset.getTableName(), userId, e);
        }
        datasetRepository.deleteById(datasetId);
    }

    public Dataset findDatasetEntity(Long userId, Long datasetId) {
        return datasetRepository.findByIdAndUserId(datasetId, userId)
                .orElseThrow(() -> new BusinessException("数据集不存在"));
    }

    public Long findUserIdByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(com.example.agent.model.entity.User::getId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
    }

    private String generateTableName(Long userId) {
        return "ds_" + userId + "_" + System.currentTimeMillis();
    }

    private String serializeColumns(List<ColumnInfo> columns) {
        if (columns == null || columns.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < columns.size(); i++) {
            ColumnInfo col = columns.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"name\":\"").append(col.name().replace("\"", "\\\""))
              .append("\",\"type\":\"").append(col.type())
              .append("\",\"nullable\":").append(col.nullable()).append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private List<ColumnInfo> deserializeColumns(String json) {
        List<ColumnInfo> columns = new ArrayList<>();
        if (json == null || json.isBlank() || json.equals("[]")) return columns;
        try {
            json = json.trim();
            if (json.startsWith("[")) json = json.substring(1);
            if (json.endsWith("]")) json = json.substring(0, json.length() - 1);
            if (json.isBlank()) return columns;

            String[] items = json.split("(?<=\\}),(?=\\{)");
            for (String item : items) {
                item = item.trim();
                if (item.isEmpty()) continue;
                String name = extractJsonField(item, "name");
                String type = extractJsonField(item, "type");
                boolean nullable = !item.contains("\"nullable\":false");
                columns.add(new ColumnInfo(name, type, nullable));
            }
        } catch (Exception e) {
            log.warn("Failed to deserialize column info: {}", json, e);
        }
        return columns;
    }

    private String extractJsonField(String json, String field) {
        String search = "\"" + field + "\":";
        int start = json.indexOf(search);
        if (start < 0) return "";
        start += search.length();
        if (json.charAt(start) == '"') {
            start++;
            int end = json.indexOf('"', start);
            return json.substring(start, end);
        } else {
            int end = json.indexOf(',', start);
            if (end < 0) end = json.indexOf('}', start);
            return json.substring(start, end).trim();
        }
    }

    private DatasetResponse toResponse(Dataset dataset, List<ColumnInfo> columns) {
        return new DatasetResponse(
            dataset.getId(),
            dataset.getFileName(),
            dataset.getFileType(),
            dataset.getTableName(),
            columns,
            dataset.getRowCount(),
            dataset.getCreatedAt(),
            dataset.getStatus()
        );
    }
}
