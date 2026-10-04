package com.example.delimited;

/**
 * 一条可读的解析错误。
 *
 * @param type     错误类别
 * @param position 错误位置（1 起始行列号；字段数不匹配定位到记录起始处）
 * @param message  人类可读的中文原因说明
 */
public record ParseError(ErrorType type, ParsePosition position, String message) {

    public ParseError {
        if (type == null || position == null) {
            throw new IllegalArgumentException("type/position 不能为 null");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message 不能为空");
        }
    }

    @Override
    public String toString() {
        return "[" + type + "] " + position + ": " + message;
    }
}
