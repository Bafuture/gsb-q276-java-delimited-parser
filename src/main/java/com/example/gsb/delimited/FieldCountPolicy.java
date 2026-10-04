package com.example.gsb.delimited;

/**
 * What to do (in lenient mode) when a record's field count differs from the
 * expected count.
 */
public enum FieldCountPolicy {

    /** Keep the fields as parsed; the mismatch is only recorded as an error. */
    STRICT,

    /** Append {@code null} fields until the expected count is reached. */
    PAD,

    /** Drop trailing fields beyond the expected count. */
    TRUNCATE
}
