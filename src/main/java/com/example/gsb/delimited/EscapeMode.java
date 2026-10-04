package com.example.gsb.delimited;

/**
 * How a literal quote character (and other special characters) is escaped
 * inside a field.
 *
 * <p>The two modes are mutually exclusive and differ fundamentally:
 * <ul>
 *   <li>{@link #DOUBLE_QUOTE} (RFC 4180 style): the backslash has no special
 *   meaning at all. A literal quote is written as two consecutive quote
 *   characters, but only inside a quoted field. Example: {@code "say ""hi"""}
 *   parses as {@code say "hi"}.</li>
 *   <li>{@link #BACKSLASH}: a literal quote is written as backslash + quote
 *   ({@code \"}), and the backslash itself must be escaped as {@code \\}.
 *   Additionally {@code \n}, {@code \r}, {@code \t} and an escaped delimiter
 *   are recognized. In this mode a doubled quote {@code ""} does NOT produce a
 *   literal quote: the first quote closes the field and the second one is an
 *   error (or starts a new quoted section).</li>
 * </ul>
 */
public enum EscapeMode {
    DOUBLE_QUOTE,
    BACKSLASH
}
