package com.example.delimited;

import java.util.List;
import java.util.Optional;

/**
 * 一条解析成功的记录。
 *
 * @param lineNumber 记录起始物理行号（1 起始）
 * @param fields     字段值列表（引号已剥离、转义已还原）
 * @param header     关联的表头；解析器未启用表头时为 null
 */
public record Record(long lineNumber, List<String> fields, Header header) {

    public Record {
        fields = List.copyOf(fields);
    }

    public int fieldCount() {
        return fields.size();
    }

    /** 按下标取字段；越界返回 null。 */
    public String get(int index) {
        return index >= 0 && index < fields.size() ? fields.get(index) : null;
    }

    /** 按下标取字段的 Optional 形式。 */
    public Optional<String> find(int index) {
        return Optional.ofNullable(get(index));
    }

    /**
     * 按表头列名取值，返回明确的 {@link Lookup} 结果：
     * 列存在返回 {@link Lookup.Found}（值可能为 null），
     * 列名不存在返回 {@link Lookup.MissingColumn}，
     * 解析器无表头返回 {@link Lookup.NoHeader}。
     */
    public Lookup lookup(String columnName) {
        if (header == null) {
            return new Lookup.NoHeader(columnName);
        }
        int index = header.indexOf(columnName);
        if (index < 0) {
            return new Lookup.MissingColumn(columnName);
        }
        return new Lookup.Found(get(index));
    }
}
