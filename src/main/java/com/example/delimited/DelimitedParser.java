package com.example.delimited;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.EnumMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * 流式定界文本解析器。
 *
 * <p>使用方式：
 * <pre>{@code
 * try (DelimitedParser parser = DelimitedParser.builder(reader)
 *         .withHeader()
 *         .tolerance(Tolerance.padOrTruncate())
 *         .build()) {
 *     ParseRecord row;
 *     while ((row = parser.nextRecord()) != null) {
 *         if (row.isSuccess()) {
 *             Lookup value = row.record().lookup("name");
 *         } else {
 *             log.warn(row.errors());
 *         }
 *     }
 *     ParseStats stats = parser.stats();
 * }
 * }</pre>
 *
 * <p>解析器每次只预读一个字符，逐记录产出，不把整个文件读入内存；
 * 单条记录出错不会中断整个解析，而是产出 {@link RecordFailure} 后继续下一条逻辑记录。
 */
public final class DelimitedParser implements Iterator<ParseRecord>, AutoCloseable {

    private final DelimitedFormat format;
    private final Tolerance tolerance;
    private final boolean skipBlankLines;
    private final CharStream in;
    private final Header header;
    private final int expectedFieldCount;

    private long totalRows;
    private long successRows;
    private long errorRows;
    private long adjustedRows;
    private final EnumMap<ErrorType, Long> errorCounts = new EnumMap<>(ErrorType.class);

    private ParseRecord pending;
    private boolean pendingLoaded;
    private boolean closed;

    private DelimitedParser(Builder builder) throws IOException {
        this.format = builder.format;
        this.tolerance = builder.tolerance;
        this.skipBlankLines = builder.skipBlankLines;
        this.in = new CharStream(builder.reader);

        Header h = null;
        if (builder.withHeader) {
            Outcome headerRow = parseOneRecord();
            if (headerRow == null) {
                throw new DelimitedParseException("输入为空，无法读取表头");
            }
            if (!headerRow.errors().isEmpty()) {
                throw new DelimitedParseException("表头解析失败: " + headerRow.errors(), headerRow.errors());
            }
            h = new Header(headerRow.fields());
        }
        this.header = h;
        this.expectedFieldCount = builder.expectedFieldCount != null
                ? builder.expectedFieldCount
                : (h != null ? h.size() : -1);
    }

    /** 创建解析器构建器。 */
    public static Builder builder(Reader reader) {
        return new Builder(Objects.requireNonNull(reader, "reader"));
    }

    /** 快捷方式：默认 CSV 格式、无表头、严格模式。 */
    public static DelimitedParser csv(Reader reader) {
        return builder(reader).build();
    }

    public Optional<Header> header() {
        return Optional.ofNullable(header);
    }

    /**
     * 产出下一条逻辑记录；输入结束返回 null。
     * 每条记录只在被请求时解析（懒加载）。
     */
    public ParseRecord nextRecord() {
        ensureOpen();
        if (pendingLoaded) {
            pendingLoaded = false;
            ParseRecord result = pending;
            pending = null;
            return result;
        }
        try {
            Outcome outcome = parseOneRecord();
            if (outcome == null) {
                return null;
            }
            ParseRecord result = finalizeRecord(outcome);
            totalRows++;
            if (result instanceof RecordSuccess success) {
                successRows++;
                if (success.adjusted()) {
                    adjustedRows++;
                }
            } else {
                errorRows++;
                for (ParseError error : result.errors()) {
                    errorCounts.merge(error.type(), 1L, Long::sum);
                }
            }
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException("读取输入失败", e);
        }
    }

    @Override
    public boolean hasNext() {
        ensureOpen();
        if (!pendingLoaded) {
            pending = nextRecord();
            pendingLoaded = true;
        }
        return pending != null;
    }

    @Override
    public ParseRecord next() {
        if (!hasNext()) {
            throw new NoSuchElementException("没有更多记录");
        }
        return nextRecord();
    }

    /** 以 Java Stream 形式流式消费记录。 */
    public Stream<ParseRecord> stream() {
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(this, Spliterator.ORDERED | Spliterator.NONNULL),
                false);
    }

    /** 当前解析进度的统计快照。流式解析过程中随时可查看。 */
    public ParseStats stats() {
        return new ParseStats(totalRows, successRows, errorRows, adjustedRows,
                in.physicalLines(), new EnumMap<>(errorCounts));
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            in.close();
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("解析器已关闭");
        }
    }

    // ------------------------------------------------------------------
    // 核心状态机
    // ------------------------------------------------------------------

    private enum State { PLAIN, QUOTED, CLOSED }

    private record Outcome(List<String> fields, List<ParseError> errors,
                           long startLine, ParsePosition startPosition) {
    }

    private Outcome parseOneRecord() throws IOException {
        if (skipBlankLines) {
            while (in.peek() == '\n') {
                in.read();
            }
        }
        if (in.peek() == -1) {
            return null;
        }

        long startLine = in.line();
        ParsePosition startPosition = in.position();
        List<String> fields = new ArrayList<>();
        List<ParseError> errors = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        State state = State.PLAIN;
        boolean fieldStarted = false;

        char delimiter = format.delimiter();
        char quote = format.quote();
        EscapeMode escapeMode = format.escapeMode();

        while (true) {
            int c = in.read();

            if (c == -1) {
                if (state == State.QUOTED) {
                    errors.add(new ParseError(ErrorType.UNCLOSED_QUOTE, startPosition,
                            "引号字段从 " + startPosition + " 开始，直到文件结束仍未闭合（缺少成对引号 "
                                    + quote + quote + "）"));
                }
                break;
            }

            switch (state) {
                case QUOTED -> {
                    if (c == quote) {
                        if (escapeMode == EscapeMode.DOUBLED_QUOTE && in.peek() == quote) {
                            in.read();
                            field.append(quote);
                        } else {
                            state = State.CLOSED;
                        }
                    } else if (c == '\\' && escapeMode == EscapeMode.BACKSLASH) {
                        appendBackslashEscape(field, errors);
                    } else {
                        field.append((char) c);
                    }
                }
                case CLOSED -> {
                    if (c == delimiter) {
                        endField(fields, field);
                        state = State.PLAIN;
                        fieldStarted = false;
                    } else if (c == '\n') {
                        endField(fields, field);
                        return new Outcome(fields, errors, startLine, startPosition);
                    } else {
                        errors.add(new ParseError(ErrorType.UNEXPECTED_CHARACTER, lastCharPosition(),
                                "引号闭合后出现非法字符 " + printable(c)
                                        + "，此处只允许分隔符 '" + delimiter + "' 或换行"));
                        resyncToEndOfLine();
                        return new Outcome(fields, errors, startLine, startPosition);
                    }
                }
                case PLAIN -> {
                    if (c == delimiter) {
                        endField(fields, field);
                        fieldStarted = false;
                    } else if (c == '\n') {
                        endField(fields, field);
                        return new Outcome(fields, errors, startLine, startPosition);
                    } else if (c == quote && !fieldStarted) {
                        state = State.QUOTED;
                        fieldStarted = true;
                    } else if (c == quote && escapeMode == EscapeMode.DOUBLED_QUOTE) {
                        errors.add(new ParseError(ErrorType.INVALID_ESCAPE, lastCharPosition(),
                                "非引号字段中间出现引号 " + printable(quote)
                                        + "：它只能出现在字段开头；若要表示字面引号，请用引号包裹整个字段并双写（"
                                        + quote + quote + "）"));
                        resyncToEndOfLine();
                        return new Outcome(fields, errors, startLine, startPosition);
                    } else if (c == '\\' && escapeMode == EscapeMode.BACKSLASH) {
                        fieldStarted = true;
                        appendBackslashEscape(field, errors);
                        if (!errors.isEmpty()) {
                            resyncToEndOfLine();
                            return new Outcome(fields, errors, startLine, startPosition);
                        }
                    } else {
                        field.append((char) c);
                        fieldStarted = true;
                    }
                }
            }
        }

        if (!fieldStarted && fields.isEmpty() && errors.isEmpty()) {
            return null;
        }
        endField(fields, field);
        return new Outcome(fields, errors, startLine, startPosition);
    }

    private void endField(List<String> fields, StringBuilder field) {
        fields.add(field.toString());
        field.setLength(0);
    }

    /** 消费 '\' 后的一个字符并还原转义；非法序列记入 errors。 */
    private void appendBackslashEscape(StringBuilder field, List<ParseError> errors)
            throws IOException {
        ParsePosition backslashPosition = lastCharPosition();
        int escaped = in.read();
        if (escaped == -1) {
            errors.add(new ParseError(ErrorType.INVALID_ESCAPE, backslashPosition,
                    "反斜杠位于文件末尾，缺少被转义的字符"));
            return;
        }
        switch (escaped) {
            case '\\' -> field.append('\\');
            case 'n' -> field.append('\n');
            case 'r' -> field.append('\r');
            case 't' -> field.append('\t');
            case '\n' -> {
            }
            default -> {
                if (escaped == format.quote() || escaped == format.delimiter()) {
                    field.append((char) escaped);
                } else {
                    errors.add(new ParseError(ErrorType.INVALID_ESCAPE, lastCharPosition(),
                            "非法转义序列 '\\" + (char) escaped + "'；BACKSLASH 模式支持 \\\\、\\"
                                    + format.quote() + "、\\" + format.delimiter()
                                    + "、\\n、\\r、\\t 以及换行续行"));
                }
            }
        }
    }

    /** 出错后丢弃当前逻辑记录的剩余内容，直到换行或 EOF。 */
    private void resyncToEndOfLine() throws IOException {
        int c;
        while ((c = in.read()) != -1 && c != '\n') {
        }
    }

    /** 刚刚消费的字符所在位置（非换行字符）。 */
    private ParsePosition lastCharPosition() {
        return new ParsePosition(in.line(), Math.max(1, in.column() - 1));
    }

    private static String printable(int c) {
        return Character.isISOControl(c)
                ? String.format("U+%04X", c)
                : "'" + (char) c + "'";
    }

    // ------------------------------------------------------------------
    // 容错与结果装配
    // ------------------------------------------------------------------

    private ParseRecord finalizeRecord(Outcome outcome) {
        List<String> fields = outcome.fields();
        List<ParseError> errors = outcome.errors();

        if (!errors.isEmpty()) {
            return new RecordFailure(outcome.startLine(), errors, fields);
        }

        int actual = fields.size();
        if (expectedFieldCount >= 0 && actual != expectedFieldCount) {
            String message = "字段数不匹配：期望 " + expectedFieldCount + " 个字段，实际 "
                    + actual + " 个（记录起始于 " + outcome.startPosition() + "）";
            ParseError error = new ParseError(
                    ErrorType.FIELD_COUNT_MISMATCH, outcome.startPosition(), message);

            FieldCountPolicy policy = tolerance.fieldCountPolicy();
            boolean tooFew = actual < expectedFieldCount;
            boolean pad = tooFew && (policy == FieldCountPolicy.PAD
                    || policy == FieldCountPolicy.PAD_OR_TRUNCATE);
            boolean truncate = !tooFew && (policy == FieldCountPolicy.TRUNCATE
                    || policy == FieldCountPolicy.PAD_OR_TRUNCATE);

            if (pad) {
                List<String> adjusted = new ArrayList<>(fields);
                while (adjusted.size() < expectedFieldCount) {
                    adjusted.add("");
                }
                return new RecordSuccess(
                        new Record(outcome.startLine(), adjusted, header), true);
            }
            if (truncate) {
                List<String> adjusted = new ArrayList<>(
                        fields.subList(0, expectedFieldCount));
                return new RecordSuccess(
                        new Record(outcome.startLine(), adjusted, header), true);
            }
            return new RecordFailure(outcome.startLine(), List.of(error), fields);
        }

        return new RecordSuccess(new Record(outcome.startLine(), fields, header));
    }

    // ------------------------------------------------------------------
    // Builder
    // ------------------------------------------------------------------

    public static final class Builder {

        private final Reader reader;
        private DelimitedFormat format = DelimitedFormat.csv();
        private Tolerance tolerance = Tolerance.strict();
        private boolean withHeader;
        private Integer expectedFieldCount;
        private boolean skipBlankLines = true;

        private Builder(Reader reader) {
            this.reader = reader;
        }

        public Builder format(DelimitedFormat format) {
            this.format = Objects.requireNonNull(format, "format");
            return this;
        }

        public Builder tolerance(Tolerance tolerance) {
            this.tolerance = Objects.requireNonNull(tolerance, "tolerance");
            return this;
        }

        /** 启用表头：第一条逻辑记录作为表头，默认期望字段数取表头列数。 */
        public Builder withHeader() {
            this.withHeader = true;
            return this;
        }

        /** 显式配置期望字段数；与表头同时存在时以此值为准。 */
        public Builder expectedFieldCount(int count) {
            if (count <= 0) {
                throw new IllegalArgumentException("期望字段数必须为正数: " + count);
            }
            this.expectedFieldCount = count;
            return this;
        }

        /** 是否跳过完全为空的物理行，默认 true（空行会计入物理行数统计）。 */
        public Builder skipBlankLines(boolean skip) {
            this.skipBlankLines = skip;
            return this;
        }

        public DelimitedParser build() {
            try {
                return new DelimitedParser(this);
            } catch (IOException e) {
                throw new UncheckedIOException("无法初始化解析器", e);
            }
        }
    }
}
