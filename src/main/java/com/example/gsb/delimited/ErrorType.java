package com.example.gsb.delimited;

/**
 * Categories of parse errors that can be located and reported.
 */
public enum ErrorType {

    /** A quoted field was opened but never closed before end of input. */
    UNCLOSED_QUOTE,

    /** A record has fewer or more fields than expected. */
    FIELD_COUNT_MISMATCH,

    /**
     * An invalid escape construct: an unknown backslash sequence, a dangling
     * backslash, a quote inside an unquoted field, or unexpected content after
     * a closing quote.
     */
    ILLEGAL_ESCAPE
}
