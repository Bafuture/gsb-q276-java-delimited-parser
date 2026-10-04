package com.example.gsb.delimited;

/**
 * Thrown in strict (fail-fast) mode when a parse error is encountered.
 * Carries the full {@link ParseError} including line and column.
 */
public class ParseException extends RuntimeException {

    private final ParseError error;

    public ParseException(ParseError error) {
        super(error.toString());
        this.error = error;
    }

    public ParseError getError() {
        return error;
    }

    public ErrorType getErrorType() {
        return error.getType();
    }

    public int getLine() {
        return error.getLine();
    }

    public int getColumn() {
        return error.getColumn();
    }
}
