package com.example.delimited;

import java.util.Objects;

/**
 * 定界文本的格式定义：分隔符、引号字符与转义规则。
 *
 * <p>转义规则由 {@link EscapeMode} 决定：
 * <ul>
 *   <li>{@link EscapeMode#DOUBLED_QUOTE}（默认，RFC 4180 风格）：引号仅在字段开头特殊；
 *       引号字段内连续两个引号 {@code ""} 表示一个字面引号；不支持反斜杠转义，
 *       反斜杠只是普通字符。</li>
 *   <li>{@link EscapeMode#BACKSLASH}：用 {@code \} 转义，支持
 *       {@code \\ \" \, \n \r \t} 以及"换行续行"；此时连续两个引号不再表示转义。</li>
 *   <li>{@link EscapeMode#NONE}：没有任何转义规则，引号只负责包裹字段。</li>
 * </ul>
 */
public final class DelimitedFormat {

    private final char delimiter;
    private final char quote;
    private final EscapeMode escapeMode;

    private DelimitedFormat(char delimiter, char quote, EscapeMode escapeMode) {
        this.delimiter = delimiter;
        this.quote = quote;
        this.escapeMode = escapeMode;
    }

    /** 默认 CSV 格式：逗号分隔、双引号包裹、双引号双写转义。 */
    public static DelimitedFormat csv() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public char delimiter() {
        return delimiter;
    }

    public char quote() {
        return quote;
    }

    public EscapeMode escapeMode() {
        return escapeMode;
    }

    public static final class Builder {
        private char delimiter = ',';
        private char quote = '"';
        private EscapeMode escapeMode = EscapeMode.DOUBLED_QUOTE;

        private Builder() {
        }

        public Builder delimiter(char delimiter) {
            this.delimiter = delimiter;
            return this;
        }

        public Builder quote(char quote) {
            this.quote = quote;
            return this;
        }

        public Builder escapeMode(EscapeMode escapeMode) {
            this.escapeMode = Objects.requireNonNull(escapeMode, "escapeMode");
            return this;
        }

        public DelimitedFormat build() {
            if (delimiter == quote) {
                throw new IllegalArgumentException("分隔符与引号字符不能相同: '" + delimiter + "'");
            }
            if (delimiter == '\r' || delimiter == '\n' || quote == '\r' || quote == '\n') {
                throw new IllegalArgumentException("分隔符与引号字符不能是换行符");
            }
            return new DelimitedFormat(delimiter, quote, escapeMode);
        }
    }
}
