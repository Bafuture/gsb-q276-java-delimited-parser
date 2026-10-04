package com.example.gsb.delimited;

import java.util.Objects;

/**
 * A single parse error with 1-based line/column location and a human
 * readable explanation.
 */
public final class ParseError {

    private final ErrorType type;
    private final int line;
    private final int column;
    private final String message;

    public ParseError(ErrorType type, int line, int column, String message) {
        this.type = Objects.requireNonNull(type, "type");
        this.line = line;
        this.column = column;
        this.message = Objects.requireNonNull(message, "message");
    }

    public ErrorType getType() {
        return type;
    }

    /** 1-based physical line where the problem was detected. */
    public int getLine() {
        return line;
    }

    /** 1-based column where the problem was detected. */
    public int getColumn() {
        return column;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "line " + line + ", column " + column + ": " + type + " - " + message;
    }
}
