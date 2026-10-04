package com.example.gsb.delimited;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuotingAndEscapeTest {

    private static List<ParsedRow> parseAll(ParserConfig config, String input) {
        DelimitedParser parser = new DelimitedParser(config);
        try (DelimitedParser.Session session = parser.open(new StringReader(input))) {
            return session.stream().toList();
        }
    }

    @Test
    void quotedFieldMayContainDelimiter() {
        List<ParsedRow> rows = parseAll(ParserConfig.csv(), "\"a,b\",c");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getFields()).containsExactly("a,b", "c");
    }

    @Test
    void quotedFieldMayContainNewlines() {
        List<ParsedRow> rows = parseAll(ParserConfig.csv(), "\"line1\nline2\",b\nc,d");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getFields()).containsExactly("line1\nline2", "b");
        assertThat(rows.get(1).getFields()).containsExactly("c", "d");
        // record line numbers track physical lines, even across quoted newlines
        assertThat(rows.get(0).getLineNumber()).isEqualTo(1);
        assertThat(rows.get(1).getLineNumber()).isEqualTo(3);
    }

    @Test
    void customDelimiterAndQuoteAreSupported() {
        ParserConfig config = ParserConfig.builder()
                .delimiter(';')
                .quote('\'')
                .build();
        List<ParsedRow> rows = parseAll(config, "'a;b';'c''d'");
        assertThat(rows.get(0).getFields()).containsExactly("a;b", "c'd");
    }

    @Test
    void crlfAndBareCrAreTreatedAsLineBreaks() {
        List<ParsedRow> rows = parseAll(ParserConfig.csv(), "a,b\r\nc,d\re,f");
        assertThat(rows).hasSize(3);
        assertThat(rows.get(2).getFields()).containsExactly("e", "f");
    }

    @Test
    void doubleQuoteModeEscapesQuoteByDoubling() {
        List<ParsedRow> rows = parseAll(ParserConfig.csv(), "\"say \"\"hi\"\"\",x");
        assertThat(rows.get(0).getFields()).containsExactly("say \"hi\"", "x");
        assertThat(rows.get(0).hasErrors()).isFalse();
    }

    @Test
    void doubleQuoteModeTreatsBackslashAsLiteral() {
        // in DOUBLE_QUOTE mode a backslash has no special meaning
        List<ParsedRow> rows = parseAll(ParserConfig.csv(), "a\\nb,c");
        assertThat(rows.get(0).getFields()).containsExactly("a\\nb", "c");
    }

    @Test
    void backslashModeEscapesQuoteBackslashAndControlChars() {
        ParserConfig config = ParserConfig.builder()
                .escapeMode(EscapeMode.BACKSLASH)
                .build();
        List<ParsedRow> rows = parseAll(config, "\"a\\\"b\",c\\\\d,e\\nf,g\\th");
        assertThat(rows.get(0).getFields())
                .containsExactly("a\"b", "c\\d", "e\nf", "g\th");
        assertThat(rows.get(0).hasErrors()).isFalse();
    }

    @Test
    void backslashModeAllowsEscapedDelimiterOutsideQuotes() {
        ParserConfig config = ParserConfig.builder()
                .escapeMode(EscapeMode.BACKSLASH)
                .build();
        List<ParsedRow> rows = parseAll(config, "a\\,b,c");
        assertThat(rows.get(0).getFields()).containsExactly("a,b", "c");
    }

    @Test
    void doubledQuoteIsNotAnEscapeInBackslashMode() {
        // documents the difference between the two escape modes:
        // in BACKSLASH mode "" closes the field and the second quote is illegal
        ParserConfig config = ParserConfig.builder()
                .escapeMode(EscapeMode.BACKSLASH)
                .lenient(true)
                .build();
        List<ParsedRow> rows = parseAll(config, "\"a\"\"b\"");
        assertThat(rows.get(0).hasErrors()).isTrue();
        assertThat(rows.get(0).getErrors())
                .allMatch(e -> e.getType() == ErrorType.ILLEGAL_ESCAPE);
    }

    @Test
    void emptyInputYieldsNoRecords() {
        assertThat(parseAll(ParserConfig.csv(), "")).isEmpty();
    }

    @Test
    void trailingDelimiterProducesEmptyField() {
        List<ParsedRow> rows = parseAll(ParserConfig.csv(), "a,b,");
        assertThat(rows.get(0).getFields()).containsExactly("a", "b", "");
    }
}
