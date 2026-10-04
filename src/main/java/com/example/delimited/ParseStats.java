package com.example.delimited;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 解析统计快照。
 *
 * @param totalRows    已产出的逻辑数据记录总数（成功 + 失败；不含表头）
 * @param successRows  成功记录数（含容错补齐/截断的记录）
 * @param errorRows    失败记录数
 * @param adjustedRows 经容错补齐或截断的记录数
 * @param physicalLines 已消费的物理行数（含引号字段内的换行与空行）
 * @param errorCounts  各类错误的分布（按条数计，一条记录可能有多条错误）
 */
public record ParseStats(
        long totalRows,
        long successRows,
        long errorRows,
        long adjustedRows,
        long physicalLines,
        Map<ErrorType, Long> errorCounts) {

    public ParseStats {
        errorCounts = Collections.unmodifiableMap(new EnumMap<>(errorCounts));
    }

    public long errorCount(ErrorType type) {
        return errorCounts.getOrDefault(type, 0L);
    }

    static ParseStats empty() {
        return new ParseStats(0, 0, 0, 0, 0, Map.of());
    }
}
