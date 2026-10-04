package com.example.gsb.delimited;

/**
 * Immutable parser configuration. Use {@link #builder()} to create instances.
 */
public final class ParserConfig {

    private final char delimiter;
    private final char quote;
    private final EscapeMode escapeMode;
    private final FieldCountPolicy fieldCountPolicy;
    private final boolean header;
    private final boolean lenient;
    private final int expectedFields;

    private ParserConfig(Builder builder) {
        this.delimiter = builder.delimiter;
        this.quote = builder.quote;
        this.escapeMode = builder.escapeMode;
        this.fieldCountPolicy = builder.fieldCountPolicy;
        this.header = builder.header;
        this.lenient = builder.lenient;
        this.expectedFields = builder.expectedFields;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** RFC 4180 style CSV defaults: comma, double quote, doubled-quote escaping. */
    public static ParserConfig csv() {
        return builder().build();
    }

    public char getDelimiter() {
        return delimiter;
    }

    public char getQuote() {
        return quote;
    }

    public EscapeMode getEscapeMode() {
        return escapeMode;
    }

    public FieldCountPolicy getFieldCountPolicy() {
        return fieldCountPolicy;
    }

    public boolean isHeader() {
        return header;
    }

    /**
     * When {@code false} (default) the first parse error throws
     * {@link ParseException}. When {@code true} errors are collected on the
     * row and in the statistics, and the field count policy is applied.
     */
    public boolean isLenient() {
        return lenient;
    }

    /** Expected field count, or {@code -1} when derived from the header or first record. */
    public int getExpectedFields() {
        return expectedFields;
    }

    public static final class Builder {

        private char delimiter = ',';
        private char quote = '"';
        private EscapeMode escapeMode = EscapeMode.DOUBLE_QUOTE;
        private FieldCountPolicy fieldCountPolicy = FieldCountPolicy.STRICT;
        private boolean header = false;
        private boolean lenient = false;
        private int expectedFields = -1;

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
            this.escapeMode = escapeMode;
            return this;
        }

        public Builder fieldCountPolicy(FieldCountPolicy fieldCountPolicy) {
            this.fieldCountPolicy = fieldCountPolicy;
            return this;
        }

        /** Whether the first record is a header row used for name-based access. */
        public Builder header(boolean header) {
            this.header = header;
            return this;
        }

        /** Lenient mode: collect errors on rows instead of throwing. */
        public Builder lenient(boolean lenient) {
            this.lenient = lenient;
            return this;
        }

        /**
         * Fix the expected field count. When unset, it is derived from the
         * header row, or from the first data record when there is no header.
         */
        public Builder expectedFields(int expectedFields) {
            this.expectedFields = expectedFields;
            return this;
        }

        public ParserConfig build() {
            if (delimiter == quote) {
                throw new IllegalArgumentException("delimiter and quote must differ");
            }
            if (delimiter == '\n' || delimiter == '\r' || quote == '\n' || quote == '\r') {
                throw new IllegalArgumentException("delimiter and quote must not be line breaks");
            }
            if (expectedFields == 0 || expectedFields < -1) {
                throw new IllegalArgumentException("expectedFields must be positive or -1");
            }
            return new ParserConfig(this);
        }
    }
}
