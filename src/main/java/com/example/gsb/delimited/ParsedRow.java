package com.example.gsb.delimited;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * One parsed record: its fields, the physical line where the record starts,
 * and any errors collected while parsing it (lenient mode).
 */
public final class ParsedRow {

    private final int lineNumber;
    private final List<String> fields;
    private final List<ParseError> errors;
    private final Header header;

    ParsedRow(int lineNumber, List<String> fields, List<ParseError> errors, Header header) {
        this.lineNumber = lineNumber;
        this.fields = Collections.unmodifiableList(fields);
        this.errors = Collections.unmodifiableList(errors);
        this.header = header;
    }

    /** 1-based physical line where this record starts. */
    public int getLineNumber() {
        return lineNumber;
    }

    public int size() {
        return fields.size();
    }

    /** Field by zero-based index; may be {@code null} for fields padded in lenient mode. */
    public String get(int index) {
        return fields.get(index);
    }

    public List<String> getFields() {
        return fields;
    }

    /**
     * Field value by header column name. Returns {@link Optional#empty()} when
     * there is no header, the column does not exist, or the record has no value
     * for that column (short row / padded {@code null}).
     */
    public Optional<String> get(String columnName) {
        if (header == null) {
            return Optional.empty();
        }
        OptionalInt index = header.indexOf(columnName);
        if (index.isEmpty() || index.getAsInt() >= fields.size()) {
            return Optional.empty();
        }
        return Optional.ofNullable(fields.get(index.getAsInt()));
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public List<ParseError> getErrors() {
        return errors;
    }

    @Override
    public String toString() {
        return "ParsedRow{line=" + lineNumber + ", fields=" + fields
                + (errors.isEmpty() ? "" : ", errors=" + errors) + "}";
    }
}
