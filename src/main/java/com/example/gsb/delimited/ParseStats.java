package com.example.gsb.delimited;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Running statistics of a parsing session. Updated as records are produced,
 * so partial results are visible even while streaming.
 */
public final class ParseStats {

    private long totalRecords;
    private long successRecords;
    private long errorRecords;
    private long physicalLines;
    private final EnumMap<ErrorType, Long> errorCounts = new EnumMap<>(ErrorType.class);

    void record(List<ParseError> errors) {
        totalRecords++;
        if (errors.isEmpty()) {
            successRecords++;
        } else {
            errorRecords++;
            for (ParseError error : errors) {
                errorCounts.merge(error.getType(), 1L, Long::sum);
            }
        }
    }

    void physicalLines(long lines) {
        this.physicalLines = Math.max(this.physicalLines, lines);
    }

    /** Total records parsed (including the header record, if any). */
    public long getTotalRecords() {
        return totalRecords;
    }

    /** Records parsed without any error. */
    public long getSuccessRecords() {
        return successRecords;
    }

    /** Records carrying at least one error. */
    public long getErrorRecords() {
        return errorRecords;
    }

    /** Number of physical lines consumed so far. */
    public long getPhysicalLines() {
        return physicalLines;
    }

    public long getErrorCount(ErrorType type) {
        return errorCounts.getOrDefault(type, 0L);
    }

    /** Error occurrence counts per error type. */
    public Map<ErrorType, Long> getErrorDistribution() {
        return Collections.unmodifiableMap(errorCounts);
    }

    @Override
    public String toString() {
        return "ParseStats{total=" + totalRecords + ", success=" + successRecords
                + ", errors=" + errorRecords + ", distribution=" + errorCounts + "}";
    }
}
