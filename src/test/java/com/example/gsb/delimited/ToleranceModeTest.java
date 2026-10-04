package com.example.gsb.delimited;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToleranceModeTest {

    private static List<ParsedRow> parseAll(ParserConfig config, String input) {
        DelimitedParser parser = new DelimitedParser(config);
        try (DelimitedParser.Session session = parser.open(new StringReader(input))) {
            return session.stream().toList();
        }
    }

    @Test
    void padPolicyFillsMissingFieldsWithNull() {
        ParserConfig config = ParserConfig.builder()
                .expectedFields(3)
                .lenient(true)
                .fieldCountPolicy(FieldCountPolicy.PAD)
                .build();
        List<ParsedRow> rows = parseAll(config, "a,b\n1,2,3\nx");

        assertThat(rows.get(0).getFields()).containsExactly("a", "b", null);
        assertThat(rows.get(0).hasErrors()).isTrue();
        assertThat(rows.get(0).getErrors().get(0).getType())
                .isEqualTo(ErrorType.FIELD_COUNT_MISMATCH);

        assertThat(rows.get(1).hasErrors()).isFalse();

        assertThat(rows.get(2).getFields()).containsExactly("x", null, null);
    }

    @Test
    void truncatePolicyDropsExtraFields() {
        ParserConfig config = ParserConfig.builder()
                .expectedFields(3)
                .lenient(true)
                .fieldCountPolicy(FieldCountPolicy.TRUNCATE)
                .build();
        List<ParsedRow> rows = parseAll(config, "1,2,3,4,5\na,b,c");

        assertThat(rows.get(0).getFields()).containsExactly("1", "2", "3");
        assertThat(rows.get(0).hasErrors()).isTrue();
        assertThat(rows.get(1).getFields()).containsExactly("a", "b", "c");
        assertThat(rows.get(1).hasErrors()).isFalse();
    }

    @Test
    void strictPolicyKeepsFieldsUnchangedButRecordsError() {
        ParserConfig config = ParserConfig.builder()
                .expectedFields(3)
                .lenient(true)
                .fieldCountPolicy(FieldCountPolicy.STRICT)
                .build();
        List<ParsedRow> rows = parseAll(config, "a,b");
        assertThat(rows.get(0).getFields()).containsExactly("a", "b");
        assertThat(rows.get(0).getErrors()).hasSize(1);
    }

    @Test
    void strictModeThrowsOnFieldCountMismatch() {
        ParserConfig config = ParserConfig.builder()
                .expectedFields(2)
                .fieldCountPolicy(FieldCountPolicy.PAD) // policy is irrelevant when not lenient
                .build();
        DelimitedParser parser = new DelimitedParser(config);
        try (DelimitedParser.Session session = parser.open(new StringReader("a"))) {
            assertThatThrownBy(() -> session.stream().toList())
                    .isInstanceOf(ParseException.class)
                    .hasMessageContaining("FIELD_COUNT_MISMATCH");
        }
    }

    @Test
    void expectedFieldCountIsDerivedFromHeader() {
        ParserConfig config = ParserConfig.builder()
                .header(true)
                .lenient(true)
                .fieldCountPolicy(FieldCountPolicy.PAD)
                .build();
        List<ParsedRow> rows = parseAll(config, "name,age,city\nAlice,30\nBob,40,Paris");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getFields()).containsExactly("Alice", "30", null);
        assertThat(rows.get(1).hasErrors()).isFalse();
    }

    @Test
    void expectedFieldCountIsDerivedFromFirstRecordWithoutHeader() {
        ParserConfig config = ParserConfig.builder()
                .lenient(true)
                .fieldCountPolicy(FieldCountPolicy.PAD)
                .build();
        List<ParsedRow> rows = parseAll(config, "a,b,c\n1,2");
        assertThat(rows.get(0).hasErrors()).isFalse();
        assertThat(rows.get(1).getFields()).containsExactly("1", "2", null);
    }
}
