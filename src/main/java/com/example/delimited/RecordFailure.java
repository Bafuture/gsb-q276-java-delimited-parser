package com.example.delimited;

import java.util.List;

/**
 * 解析失败的记录。
 *
 * @param lineNumber    记录起始物理行号
 * @param errors        该记录上的全部错误（至少一条）
 * @param partialFields 出错前已解析出的字段（尽力而为，可能为空列表）
 */
public record RecordFailure(long lineNumber, List<ParseError> errors, List<String> partialFields)
        implements ParseRecord {

    public RecordFailure {
        if (errors == null || errors.isEmpty()) {
            throw new IllegalArgumentException("RecordFailure 至少需要一条错误");
        }
        errors = List.copyOf(errors);
        partialFields = partialFields == null ? List.of() : List.copyOf(partialFields);
    }
}
