package com.example.delimited;

/**
 * 按表头名取值的明确结果，区分"列存在但值为 null/空"与"列不存在"。
 * 用 {@code sealed} 接口穷举三种结果，调用方须显式处理缺失列。
 */
public sealed interface Lookup permits Lookup.Found, Lookup.MissingColumn, Lookup.NoHeader {

    /** 表头中存在该列。{@code value} 可能是 null（物理行字段数不足）或空字符串。 */
    record Found(String value) implements Lookup {
    }

    /** 表头中不存在该列名。 */
    record MissingColumn(String name) implements Lookup {
    }

    /** 该解析器没有配置表头，无法按名取值。 */
    record NoHeader(String name) implements Lookup {
    }
}
