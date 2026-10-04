package com.example.delimited;

/** 解析错误类别，用于错误分布统计。 */
public enum ErrorType {

    /** 引号字段遇到 EOF 仍未闭合。 */
    UNCLOSED_QUOTE,

    /** 出现不合法的转义序列（反斜杠模式下未知序列，或引号模式下非法引号用法）。 */
    INVALID_ESCAPE,

    /** 字段数与期望（表头或配置）字段数不一致。 */
    FIELD_COUNT_MISMATCH,

    /** 引号闭合后紧跟非分隔符/换行的杂散字符。 */
    UNEXPECTED_CHARACTER
}
