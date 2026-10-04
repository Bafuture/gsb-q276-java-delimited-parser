package com.example.gsb.delimited;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;

class ErrorLocationTest {

    private static List<ParsedRow> parseLenient(ParserConfig.Builder builder, String input) {
        DelimitedParser parser = new DelimitedParser(builder.lenient(true).build());
        try (DelimitedParser.Session session = parser.open(new StringReader(input))) {
            return session.stream().toList();
        }
    }

    @Test
    void unclosedQuoteIsLocatedAtTheOpeningQuote() {
        DelimitedParser parser = new DelimitedParser(ParserConfig.csv());
        try (DelimitedParser.Session session = parser.open(new StringReader("a,b\n\"c,d"))) {
            assertThatThrownBy(() -> session.stream().toList())
                    .isInstanceOf(ParseException.class)
                    .hasMessageContaining("line 2, column 1")
                    .hasMessageContaining("UNCLOSED_QUOTE")
                    .satisfies(e -> {
                        ParseException pe = (ParseException) e;
                        assertThat(pe.getLine()).isEqualTo(2);
                        assertThat(pe.getColumn()).isEqualTo(1);
                        assertThat(pe.getErrorType()).isEqualTo(ErrorType.UNCLOSED_QUOTE);
                    });
        }
    }

    @Test
    void unclosedQuoteIsCollectedInLenientMode() {
        List<ParsedRow> rows = parseLenient(ParserConfig.builder(), "a,b\n\"c,d");
        assertThat(rows).hasSize(2);
        ParseError error = rows.get(1).getErrors().get(0);
        assertThat(error.getType()).isEqualTo(ErrorType.UNCLOSED_QUOTE);
        assertThat(error.getLine()).isEqualTo(2);
        assertThat(error.getColumn()).isEqualTo(1);
        assertThat(error.getMessage()).contains("never closed");
    }

    @Test
    void fieldCountMismatchReportsRecordLineAndCounts() {
        DelimitedParser parser = new DelimitedParser(ParserConfig.builder()
                .expectedFields(3)
                .build());
        try (DelimitedParser.Session session = parser.open(new StringReader("a,b,c\n1,2"))) {
            assertThatThrownBy(() -> session.stream().toList())
                    .isInstanceOf(ParseException.class)
                    .hasMessageContaining("line 2, column 1")
                    .hasMessageContaining("2 field(s) but 3 were expected");
        }
    }

    @Test
    void invalidBackslashEscapeReportsExactPosition() {
        ParserConfig config = ParserConfig.builder()
                .escapeMode(EscapeMode.BACKSLASH)
                .build();
        DelimitedParser parser = new DelimitedParser(config);
        try (DelimitedParser.Session session = parser.open(new StringReader("ab\\q,c"))) {
            assertThatThrownBy(() -> session.stream().toList())
                    .isInstanceOf(ParseException.class)
                    .hasMessageContaining("line 1, column 3")
                    .hasMessageContaining("'\\q'");
        }
    }

    @Test
    void danglingBackslashAtEndOfInputIsReported() {
        List<ParsedRow> rows = parseLenient(
                ParserConfig.builder().escapeMode(EscapeMode.BACKSLASH), "ab\\");
        ParseError error = rows.get(0).getErrors().get(0);
        assertThat(error.getType()).isEqualTo(ErrorType.ILLEGAL_ESCAPE);
        assertThat(error.getLine()).isEqualTo(1);
        assertThat(error.getColumn()).isEqualTo(3);
        assertThat(error.getMessage()).contains("dangling backslash");
    }

    @Test
    void quoteInsideUnquotedFieldIsReported() {
        List<ParsedRow> rows = parseLenient(ParserConfig.builder(), "ab\"cd,ef");
        ParseError error = rows.get(0).getErrors().get(0);
        assertThat(error.getType()).isEqualTo(ErrorType.ILLEGAL_ESCAPE);
        assertThat(error.getLine()).isEqualTo(1);
        assertThat(error.getColumn()).isEqualTo(3);
        assertThat(error.getMessage()).contains("inside an unquoted field");
    }

    @Test
    void unexpectedCharacterAfterClosingQuoteIsReported() {
        List<ParsedRow> rows = parseLenient(ParserConfig.builder(), "\"ab\"x,cd");
        ParseError error = rows.get(0).getErrors().get(0);
        assertThat(error.getType()).isEqualTo(ErrorType.ILLEGAL_ESCAPE);
        assertThat(error.getLine()).isEqualTo(1);
        assertThat(error.getColumn()).isEqualTo(5);
        assertThat(error.getMessage()).contains("after closing quote");
    }

    @Test
    void errorMessageIsReadableAndSelfContained() {
        List<ParsedRow> rows = parseLenient(ParserConfig.builder(), "a,b\n\"c,d");
        String rendered = rows.get(1).getErrors().get(0).toString();
        assertThat(rendered).startsWith("line 2, column 1: UNCLOSED_QUOTE - ");
    }
}
