package com.example.agent.model.enums;

import com.example.agent.exception.BusinessException;

public enum FileType {
    CSV("csv"),
    EXCEL("xlsx"),
    JSON("json");

    private final String extension;

    FileType(String extension) {
        this.extension = extension;
    }

    public String getExtension() {
        return extension;
    }

    public static FileType fromFilename(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new BusinessException("无效的文件名: " + filename);
        }
        String ext = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        return switch (ext) {
            case "csv" -> CSV;
            case "xlsx", "xls" -> EXCEL;
            case "json" -> JSON;
            default -> throw new BusinessException("不支持的文件格式: " + ext);
        };
    }
}
