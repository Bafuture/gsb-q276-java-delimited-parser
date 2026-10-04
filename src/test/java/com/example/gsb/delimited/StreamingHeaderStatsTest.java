package com.example.gsb.delimited;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class StreamingHeaderStatsTest {

    /** Reader that counts how many characters were actually pulled from it. */
    private static final class CountingReader extends Reader {
        private final StringReader delegate;
        private final AtomicInteger charsRead = new AtomicInteger();

        CountingReader(String input) {
            this.delegate = new StringReader(input);
        }

        @Override
        public int read() {
            try {
                int ch = delegate.read();
                if (ch != -1) {
                    charsRead.incrementAndGet();
                }
                return ch;
            } catch (java.io.IOException e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public int read(char[] buffer, int offset, int length) {
            throw new UnsupportedOperationException("only single-char reads expected");
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    /** Reader that generates records on the fly without holding them in memory. */
    private static final class GeneratingReader extends Reader {
        private final int total;
        private int produced;
        private String current;
        private int offset;

        GeneratingReader(int total) {
            this.total = total;
        }

        @Override
        public int read() {
            if (current == null || offset >= current.length()) {
                if (produced >= total) {
                    return -1;
                }
                current = "value" + produced + ",x\n";
                produced++;
                offset = 0;
            }
            return current.charAt(offset++);
        }

        @Override
        public int read(char[] buffer, int offset, int length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
        }
    }

    @Test
    void recordsAreProducedLazilyOneByOne() {
        CountingReader reader = new CountingReader("a,b\nc,d\n");
        DelimitedParser parser = new DelimitedParser(ParserConfig.csv());

        try (DelimitedParser.Session session = parser.open(reader)) {
            assertThat(reader.charsRead).hasValue(0); // nothing read before first pull

            assertThat(session.hasNext()).isTrue();
            ParsedRow first = session.next();
            assertThat(first.getFields()).containsExactly("a", "b");
            assertThat(reader.charsRead).hasValue(4); // exactly "a,b\n", no read-ahead

            ParsedRow second = session.next();
            assertThat(second.getFields()).containsExactly("c", "d");
            assertThat(session.hasNext()).isFalse();
        }
    }

    @Test
    void largeInputsAreStreamedWithoutBufferingEverything() {
        int total = 100_000;
        DelimitedParser parser = new DelimitedParser(ParserConfig.csv());
        try (DelimitedParser.Session session = parser.open(new GeneratingReader(total))) {
            long count = session.stream()
                    .peek(row -> assertThat(row.get(0)).startsWith("value"))
                    .count();
            assertThat(count).isEqualTo(total);
            assertThat(session.getStats().getTotalRecords()).isEqualTo(total);
            assertThat(session.getStats().getSuccessRecords()).isEqualTo(total);
        }
    }

    @Test
    void headerEnablesNameBasedAccess() {
        ParserConfig config = ParserConfig.builder().header(true).build();
        DelimitedParser parser = new DelimitedParser(config);
        try (DelimitedParser.Session session = parser.open(new StringReader("name,age\nAlice,30"))) {
            assertThat(session.getHeader()).isPresent();
            assertThat(session.getHeader().get().names()).containsExactly("name", "age");

            ParsedRow row = session.next();
            assertThat(row.get("name")).hasValue("Alice");
            assertThat(row.get("age")).hasValue("30");
            assertThat(row.get(0)).isEqualTo("Alice");
        }
    }

    @Test
    void missingColumnsReturnEmptyOptional() {
        ParserConfig config = ParserConfig.builder()
                .header(true)
                .lenient(true)
                .fieldCountPolicy(FieldCountPolicy.PAD)
                .build();
        DelimitedParser parser = new DelimitedParser(config);
        try (DelimitedParser.Session session = parser.open(new StringReader("name,age\nBob"))) {
            ParsedRow row = session.next();
            assertThat(row.get("unknown")).isEmpty();   // column not in header
            assertThat(row.get("age")).isEmpty();        // column exists but row is short
            assertThat(row.get("name")).hasValue("Bob");
        }
    }

    @Test
    void rowWithoutHeaderReturnsEmptyForNameLookup() {
        DelimitedParser parser = new DelimitedParser(ParserConfig.csv());
        try (DelimitedParser.Session session = parser.open(new StringReader("a,b"))) {
            assertThat(session.next().get("anything")).isEqualTo(Optional.empty());
        }
    }

    @Test
    void statsTrackTotalsAndErrorDistribution() {
        ParserConfig config = ParserConfig.builder()
                .expectedFields(2)
                .lenient(true)
                .build();
        DelimitedParser parser = new DelimitedParser(config);
        String input = "a,b\n1,2,3\n\"x,y";
        try (DelimitedParser.Session session = parser.open(new StringReader(input))) {
            List<ParsedRow> rows = session.stream().toList();
            assertThat(rows).hasSize(3);
            // row 3 has an unclosed quote AND, as a consequence, only 1 of 2 fields
            assertThat(rows.get(2).getErrors())
                    .extracting(ParseError::getType)
                    .containsExactly(ErrorType.UNCLOSED_QUOTE, ErrorType.FIELD_COUNT_MISMATCH);

            ParseStats stats = session.getStats();
            assertThat(stats.getTotalRecords()).isEqualTo(3);
            assertThat(stats.getSuccessRecords()).isEqualTo(1);
            assertThat(stats.getErrorRecords()).isEqualTo(2);
            assertThat(stats.getErrorCount(ErrorType.FIELD_COUNT_MISMATCH)).isEqualTo(2);
            assertThat(stats.getErrorCount(ErrorType.UNCLOSED_QUOTE)).isEqualTo(1);
            assertThat(stats.getErrorCount(ErrorType.ILLEGAL_ESCAPE)).isZero();
            assertThat(stats.getErrorDistribution())
                    .containsEntry(ErrorType.FIELD_COUNT_MISMATCH, 2L)
                    .containsEntry(ErrorType.UNCLOSED_QUOTE, 1L);
            assertThat(stats.getPhysicalLines()).isEqualTo(3);
        }
    }

    @Test
    void statsIncludeHeaderRecord() {
        ParserConfig config = ParserConfig.builder().header(true).build();
        DelimitedParser parser = new DelimitedParser(config);
        try (DelimitedParser.Session session = parser.open(new StringReader("h1,h2\na,b\nc,d"))) {
            session.stream().toList();
            ParseStats stats = session.getStats();
            assertThat(stats.getTotalRecords()).isEqualTo(3);
            assertThat(stats.getSuccessRecords()).isEqualTo(3);
            assertThat(stats.getErrorRecords()).isZero();
        }
    }
}
