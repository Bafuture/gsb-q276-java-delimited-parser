package com.example.delimited;

/**
 * 字段内容的转义规则。
 *
 * <p>{@link #DOUBLED_QUOTE} 与 {@link #BACKSLASH} 的区别：前者是 CSV（RFC 4180）的惯例，
 * 用"引号双写"表达字面引号，反斜杠没有特殊含义；后者是编程语言风格的转义，
 * 用 {@code \} 加后续字符表达特殊字符，此时引号双写只是两个普通引号字符。
 */
public enum EscapeMode {

    /** 连续两个引号表示一个字面引号（仅引号字段内生效）；反斜杠是普通字符。 */
    DOUBLED_QUOTE,

    /** 反斜杠转义：{@code \\ \" \, \n \r \t} 及换行续行；引号双写不表示转义。 */
    BACKSLASH,

    /** 不支持任何转义。 */
    NONE
}
