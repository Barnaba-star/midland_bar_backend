package com.midland.bar.Setting.Dto;

import lombok.Getter;

public record TableSizeDto(
        String schemaName,
        String tableName,
        long rowCount,
        long tableSizeBytes,
        long indexSizeBytes,
        long totalSizeBytes,
        String tableSize,
        String indexSize,
        String totalSize
) {
}
