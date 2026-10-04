package com.example.gsb.delimited;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Entry point for parsing delimited text (CSV-like files).
 *
 * <p>One parser instance holds a {@link ParserConfig} and can be reused across
 * files; each {@link #open(Reader)} call starts an independent streaming
 * session that reads the input lazily, one character at a time:
 *
 * <pre>{@code
 * DelimitedParser parser = new DelimitedParser(ParserConfig.builder().header(true).build());
 * try (DelimitedParser.Session session = parser.open(reader)) {
 *     session.forEachRemaining(row -> System.out.println(row.get("name").orElse("N/A")));
 *     ParseStats stats = session.getStats();
 * }
 * }</pre>
 */
public final class DelimitedParser {

    private final ParserConfig config;

    public DelimitedParser(ParserConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Opens a lazy parsing session. The header record (if configured) is read
     * immediately; data records are read only when the iterator is advanced.
     */
    public Session open(Reader reader) {
        return new Session(Objects.requireNonNull(reader, "reader"));
    }

    /**
     * A streaming parsing session. Implements {@link Iterator} for pull-style
     * access and {@link AutoCloseable} to release the underlying reader.
     */
    public final class Session implements Iterator<ParsedRow>, AutoCloseable {

        private final Cursor cursor;
        private final ParseStats stats = new ParseStats();
        private Header header;
        private int expectedFields;

        private ParsedRow bufferedRow;
        private boolean finished;
        private boolean closed;

        private Session(Reader reader) {
            this.cursor = new Cursor(reader);
            this.expectedFields = config.getExpectedFields();
            if (config.isHeader()) {
                ParsedRow headerRow = readRecord();
                if (headerRow != null) {
                    this.header = new Header(headerRow.getFields());
                    if (expectedFields <= 0) {
                        expectedFields = headerRow.size();
                    }
                }
            }
        }

        /** Parsed header, available when {@link ParserConfig.Builder#header(boolean)} was set. */
        public Optional<Header> getHeader() {
            return Optional.ofNullable(header);
        }

        public ParseStats getStats() {
            return stats;
        }

        /**
         * Lazy stream of records; closing the stream closes the session (and
         * the underlying reader).
         */
        public Stream<ParsedRow> stream() {
            return StreamSupport.stream(
                            Spliterators.spliteratorUnknownSize(this, Spliterator.ORDERED), false)
                    .onClose(this::close);
        }

        @Override
        public boolean hasNext() {
            if (closed) {
                throw new IllegalStateException("session is closed");
            }
            if (finished) {
                return false;
            }
            if (bufferedRow == null) {
                bufferedRow = readRecord();
                if (bufferedRow == null) {
                    finished = true;
                }
            }
            return bufferedRow != null;
        }

        @Override
        public ParsedRow next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            ParsedRow row = bufferedRow;
            bufferedRow = null;
            return row;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            finished = true;
            try {
                cursor.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        /* ----------------------------------------------------------------- */

        private ParsedRow readRecord() {
            if (cursor.peek() == -1) {
                return null;
            }

            int recordLine = cursor.line();
            List<String> fields = new ArrayList<>();
            List<ParseError> errors = new ArrayList<>();
            StringBuilder field = new StringBuilder();
            boolean inQuotes = false;
            boolean afterQuote = false;
            boolean fieldWasQuoted = false;
            int quoteLine = -1;
            int quoteColumn = -1;

            while (true) {
                int ch = cursor.peek();

                if (ch == -1) {
                    if (inQuotes) {
                        report(errors, new ParseError(ErrorType.UNCLOSED_QUOTE, quoteLine, quoteColumn,
                                "quoted field starting here is never closed: end of input reached "
                                        + "without a closing quote ('" + config.getQuote() + "')"));
                    }
                    fields.add(field.toString());
                    break;
                }

                if (inQuotes) {
                    if (config.getEscapeMode() == EscapeMode.BACKSLASH && ch == '\\') {
                        readBackslashEscape(field, errors);
                    } else if (ch == config.getQuote()) {
                        cursor.next();
                        if (config.getEscapeMode() == EscapeMode.DOUBLE_QUOTE
                                && cursor.peek() == config.getQuote()) {
                            cursor.next();
                            field.append(config.getQuote());
                        } else {
                            inQuotes = false;
                            afterQuote = true;
                        }
                    } else {
                        field.append((char) cursor.next());
                    }
                    continue;
                }

                if (afterQuote) {
                    if (ch == config.getDelimiter()) {
                        cursor.next();
                        fields.add(field.toString());
                        field.setLength(0);
                        afterQuote = false;
                        fieldWasQuoted = false;
                    } else if (ch == '\n') {
                        cursor.next();
                        fields.add(field.toString());
                        break;
                    } else {
                        report(errors, new ParseError(ErrorType.ILLEGAL_ESCAPE,
                                cursor.line(), cursor.column(),
                                "unexpected character '" + printable(ch)
                                        + "' after closing quote; expected delimiter ('"
                                        + config.getDelimiter() + "') or end of line"));
                        field.append((char) cursor.next());
                    }
                    continue;
                }

                if (ch == config.getDelimiter()) {
                    cursor.next();
                    fields.add(field.toString());
                    field.setLength(0);
                    fieldWasQuoted = false;
                } else if (ch == '\n') {
                    cursor.next();
                    fields.add(field.toString());
                    break;
                } else if (ch == config.getQuote()) {
                    if (field.length() == 0 && !fieldWasQuoted) {
                        quoteLine = cursor.line();
                        quoteColumn = cursor.column();
                        cursor.next();
                        inQuotes = true;
                        fieldWasQuoted = true;
                    } else {
                        report(errors, new ParseError(ErrorType.ILLEGAL_ESCAPE,
                                cursor.line(), cursor.column(),
                                "quote character ('" + config.getQuote()
                                        + "') inside an unquoted field; " + escapingHint()));
                        field.append((char) cursor.next());
                    }
                } else if (config.getEscapeMode() == EscapeMode.BACKSLASH && ch == '\\') {
                    readBackslashEscape(field, errors);
                } else {
                    field.append((char) cursor.next());
                }
            }

            enforceFieldCount(fields, errors, recordLine);
            stats.record(errors);
            stats.physicalLines(cursor.line());
            return new ParsedRow(recordLine, fields, errors, header);
        }

        private void readBackslashEscape(StringBuilder field, List<ParseError> errors) {
            int backslashLine = cursor.line();
            int backslashColumn = cursor.column();
            cursor.next();
            int escaped = cursor.next();

            if (escaped == -1) {
                report(errors, new ParseError(ErrorType.ILLEGAL_ESCAPE,
                        backslashLine, backslashColumn,
                        "dangling backslash at end of input: nothing left to escape"));
                return;
            }

            char mapped;
            switch (escaped) {
                case '\\' -> mapped = '\\';
                case 'n' -> mapped = '\n';
                case 'r' -> mapped = '\r';
                case 't' -> mapped = '\t';
                default -> {
                    if (escaped == config.getQuote() || escaped == config.getDelimiter()) {
                        mapped = (char) escaped;
                    } else {
                        report(errors, new ParseError(ErrorType.ILLEGAL_ESCAPE,
                                backslashLine, backslashColumn,
                                "invalid escape sequence '\\" + printable(escaped)
                                        + "': only \\\\, \\n, \\r, \\t, the quote and the "
                                        + "delimiter character may be escaped"));
                        mapped = (char) escaped;
                    }
                }
            }
            field.append(mapped);
        }

        private void enforceFieldCount(List<String> fields, List<ParseError> errors, int recordLine) {
            if (expectedFields <= 0) {
                expectedFields = fields.size();
                return;
            }
            if (fields.size() == expectedFields) {
                return;
            }

            ParseError error = new ParseError(ErrorType.FIELD_COUNT_MISMATCH, recordLine, 1,
                    "record starting here has " + fields.size() + " field(s) but "
                            + expectedFields + " were expected");
            report(errors, error);

            switch (config.getFieldCountPolicy()) {
                case PAD -> {
                    while (fields.size() < expectedFields) {
                        fields.add(null);
                    }
                }
                case TRUNCATE -> {
                    while (fields.size() > expectedFields) {
                        fields.remove(fields.size() - 1);
                    }
                }
                case STRICT -> {
                    // fields are kept unchanged; the error is already recorded
                }
            }
        }

        private void report(List<ParseError> errors, ParseError error) {
            if (config.isLenient()) {
                errors.add(error);
            } else {
                throw new ParseException(error);
            }
        }

        private String escapingHint() {
            if (config.getEscapeMode() == EscapeMode.DOUBLE_QUOTE) {
                return "use two consecutive quote characters inside a quoted field for a literal quote";
            }
            return "quote the field or escape the quote with a backslash";
        }

        private static String printable(int ch) {
            if (ch == '\n') {
                return "\\n";
            }
            if (ch == '\r') {
                return "\\r";
            }
            if (ch == '\t') {
                return "\\t";
            }
            if (Character.isISOControl(ch)) {
                return String.format("U+%04X", ch);
            }
            return Character.toString((char) ch);
        }
    }

    /* --------------------------------------------------------------------- */

    /**
     * Single-character cursor over a {@link Reader} with line/column tracking.
     * Reads are never buffered beyond the one-character pushback, so consuming
     * one record never pulls data of later records from the reader: this is
     * what makes parsing truly streaming.
     */
    private static final class Cursor {

        private static final int EMPTY = Integer.MIN_VALUE;

        private final Reader reader;
        private int pushback = EMPTY;
        private int line = 1;
        private int column = 1;

        Cursor(Reader reader) {
            this.reader = reader;
        }

        int line() {
            return line;
        }

        int column() {
            return column;
        }

        int peek() {
            int ch = readRaw();
            pushback = ch;
            return ch == '\r' ? '\n' : ch;
        }

        int next() {
            int ch = readRaw();
            if (ch == '\r') {
                int following = readRaw();
                if (following != '\n') {
                    pushback = following;
                }
                ch = '\n';
            }
            if (ch == '\n') {
                line++;
                column = 1;
            } else if (ch != -1) {
                column++;
            }
            return ch;
        }

        private int readRaw() {
            if (pushback != EMPTY) {
                int ch = pushback;
                pushback = EMPTY;
                return ch;
            }
            try {
                return reader.read();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        void close() throws IOException {
            reader.close();
        }
    }
}
