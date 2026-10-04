package com.example.delimited;

/** 数据行字段数与期望字段数不一致时的容错策略。 */
public enum FieldCountPolicy {

    /** 严格模式：字段数不匹配即产生错误记录。 */
    STRICT,

    /** 字段不足时用空字符串补齐；字段过多仍是错误。 */
    PAD,

    /** 字段过多时截断多余字段；字段不足仍是错误。 */
    TRUNCATE,

    /** 字段不足补齐、字段过多截断，总是产出成功记录。 */
    PAD_OR_TRUNCATE
}
