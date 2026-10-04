package com.example.delimited;

/**
 * 解析位置（1 起始，均针对原始输入文本）。
 *
 * @param line   物理行号（从 1 开始；引号字段内换行也会使行号增加）
 * @param column 列号（从 1 开始，按字符计数；遇到换行符后归 1）
 */
public record ParsePosition(int line, int column) {

    @Override
    public String toString() {
        return "第" + line + "行第" + column + "列";
    }
}
