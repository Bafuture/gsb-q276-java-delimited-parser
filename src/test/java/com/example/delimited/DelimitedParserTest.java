package com.example.delimited;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DelimitedParserTest {

    private static DelimitedParser parse(String input) {
        return DelimitedParser.csv(new StringReader(input));
    }

    private static List<ParseRecord> drain(DelimitedParser parser) {
        List<ParseRecord> all = new ArrayList<>();
        ParseRecord row;
        while ((row = parser.nextRecord()) != null) {
            all.add(row);
        }
        return all;
    }

    // ------------------------------------------------------------------
    // 1) 自定义分隔符/引号，引号内允许分隔符与换行
    // ------------------------------------------------------------------

    @Test
    void quotedFieldMayContainDelimiterAndNewline() {
        DelimitedParser parser = parse("name,note\n\"Alice\",\"line1\nline2\"\n\"Bob\",\"a,b\"\n");
        List<ParseRecord> rows = drain(parser);

        assertThat(rows).hasSize(3);
        assertThat(rows.get(1).record().fields()).containsExactly("Alice", "line1\nline2");
        assertThat(rows.get(2).record().fields()).containsExactly("Bob", "a,b");
        // 第二条记录跨第 2~3 物理行，第三条从第 4 行开始
        assertThat(rows.get(2).record().lineNumber()).isEqualTo(4);
        assertThat(parser.stats().physicalLines()).isEqualTo(4);
    }

    @Test
    void customDelimiterAndQuoteChar() {
        DelimitedFormat format = DelimitedFormat.builder()
                .delimiter(';').quote('\'').build();
        DelimitedParser parser = DelimitedParser.builder(new StringReader("a;'b;c';'it''s'\n"))
                .format(format).build();

        ParseRecord row = parser.nextRecord();
        assertThat(row.isSuccess()).isTrue();
        assertThat(row.record().fields()).containsExactly("a", "b;c", "it's");
    }

    @Test
    void crlfAndCrAreNormalized() {
        List<ParseRecord> rows = drain(parse("a,b\r\nc,d\re,f"));
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).record().fields()).containsExactly("a", "b");
        assertThat(rows.get(1).record().fields()).containsExactly("c", "d");
        assertThat(rows.get(2).record().fields()).containsExactly("e", "f");
    }

    // ------------------------------------------------------------------
    // 2) 转义规则
    // ------------------------------------------------------------------

    @Test
    void doubledQuoteMeansLiteralQuote() {
        List<ParseRecord> rows = drain(parse("\"say \"\"hi\"\"\",x\n"));
        assertThat(rows.get(0).record().fields()).containsExactly("say \"hi\"", "x");
    }

    @Test
    void backslashEscapeMode() {
        DelimitedFormat format = DelimitedFormat.builder()
                .escapeMode(EscapeMode.BACKSLASH).build();
        // 原始文本: a\,b,"c\"d",tab\there<换行续行>z
        String input = "a\\,b,\"c\\\"d\",tab\\there,line1\\\nline2\n";
        DelimitedParser parser = DelimitedParser.builder(new StringReader(input))
                .format(format).build();

        ParseRecord row = parser.nextRecord();
        assertThat(row.record().fields())
                .containsExactly("a,b", "c\"d", "tab\there", "line1line2");
    }

    @Test
    void doubledQuoteIsNotEscapeInBackslashMode() {
        // 同一段输入在两种模式下的差异：DOUBLED_QUOTE 视为字面引号，BACKSLASH 视为非法
        String input = "\"a\"\"b\"\n";

        DelimitedParser doubled = parse(input);
        assertThat(drain(doubled).get(0).record().fields()).containsExactly("a\"b");

        DelimitedParser backslash = DelimitedParser.builder(new StringReader(input))
                .format(DelimitedFormat.builder().escapeMode(EscapeMode.BACKSLASH).build())
                .build();
        ParseRecord row = backslash.nextRecord();
        assertThat(row.isSuccess()).isFalse();
        assertThat(row.errors().get(0).type()).isEqualTo(ErrorType.UNEXPECTED_CHARACTER);
    }

    @Test
    void noneModeTreatsBackslashAsPlainChar() {
        DelimitedParser parser = DelimitedParser.builder(new StringReader("\"a\\b\",c\n"))
                .format(DelimitedFormat.builder().escapeMode(EscapeMode.NONE).build())
                .build();
        assertThat(parser.nextRecord().record().fields()).containsExactly("a\\b", "c");
    }

    // ------------------------------------------------------------------
    // 3) 错误定位：行号、列号、可读原因
    // ------------------------------------------------------------------

    @Test
    void unclosedQuoteReportsRecordStartPosition() {
        List<ParseRecord> rows = drain(parse("ok,fine\n\"broken,field\nrest\n"));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).isSuccess()).isTrue();

        ParseRecord bad = rows.get(1);
        assertThat(bad.isSuccess()).isFalse();
        ParseError error = bad.errors().get(0);
        assertThat(error.type()).isEqualTo(ErrorType.UNCLOSED_QUOTE);
        assertThat(error.position().line()).isEqualTo(2);
        assertThat(error.position().column()).isEqualTo(1);
        assertThat(error.message()).contains("未闭合");
    }

    @Test
    void fieldCountMismatchReportsPositionAndReason() {
        DelimitedParser parser = DelimitedParser.builder(new StringReader("a,b,c\n1,2\n"))
                .withHeader().build();

        ParseRecord row = parser.nextRecord();
        assertThat(row.isSuccess()).isFalse();
        ParseError error = row.errors().get(0);
        assertThat(error.type()).isEqualTo(ErrorType.FIELD_COUNT_MISMATCH);
        assertThat(error.position().line()).isEqualTo(2);
        assertThat(error.position().column()).isEqualTo(1);
        assertThat(error.message()).contains("期望 3").contains("实际 2");
    }

    @Test
    void invalidBackslashEscapeReportsExactColumn() {
        DelimitedParser parser = DelimitedParser.builder(new StringReader("ab\\qcd\nnext,row\n"))
                .format(DelimitedFormat.builder().escapeMode(EscapeMode.BACKSLASH).build())
                .build();

        ParseRecord bad = parser.nextRecord();
        ParseError error = bad.errors().get(0);
        assertThat(error.type()).isEqualTo(ErrorType.INVALID_ESCAPE);
        assertThat(error.position().line()).isEqualTo(1);
        assertThat(error.position().column()).isEqualTo(4); // 'q' 所在列
        assertThat(error.message()).contains("\\q");

        // 出错记录不影响后续记录
        assertThat(parser.nextRecord().record().fields()).containsExactly("next", "row");
    }

    @Test
    void strayQuoteInPlainFieldIsInvalidEscape() {
        List<ParseRecord> rows = drain(parse("ab\"cd,ef\n"));
        ParseError error = rows.get(0).errors().get(0);
        assertThat(error.type()).isEqualTo(ErrorType.INVALID_ESCAPE);
        assertThat(error.position().line()).isEqualTo(1);
        assertThat(error.position().column()).isEqualTo(3);
    }

    @Test
    void unexpectedCharAfterClosingQuote() {
        List<ParseRecord> rows = drain(parse("\"abc\"x,1\n"));
        ParseError error = rows.get(0).errors().get(0);
        assertThat(error.type()).isEqualTo(ErrorType.UNEXPECTED_CHARACTER);
        assertThat(error.position().line()).isEqualTo(1);
        assertThat(error.position().column()).isEqualTo(6);
    }

    // ------------------------------------------------------------------
    // 4) 容错模式
    // ------------------------------------------------------------------

    @Test
    void padShortRows() {
        DelimitedParser parser = DelimitedParser.builder(new StringReader("a,b,c\n1,2\n1,2,3,4\n"))
                .withHeader().tolerance(Tolerance.padShortRows()).build();

        ParseRecord shortRow = parser.nextRecord();
        assertThat(shortRow.isSuccess()).isTrue();
        assertThat(((RecordSuccess) shortRow).adjusted()).isTrue();
        assertThat(shortRow.record().fields()).containsExactly("1", "2", "");

        ParseRecord longRow = parser.nextRecord(); // PAD 不处理字段过多
        assertThat(longRow.isSuccess()).isFalse();
        assertThat(longRow.errors().get(0).type()).isEqualTo(ErrorType.FIELD_COUNT_MISMATCH);
    }

    @Test
    void truncateLongRows() {
        DelimitedParser parser = DelimitedParser.builder(new StringReader("a,b,c\n1,2,3,4,5\n"))
                .withHeader().tolerance(Tolerance.truncateLongRows()).build();

        ParseRecord row = parser.nextRecord();
        assertThat(row.isSuccess()).isTrue();
        assertThat(row.record().fields()).containsExactly("1", "2", "3");
        assertThat(parser.stats().adjustedRows()).isEqualTo(1);
    }

    @Test
    void padOrTruncateHandlesBothDirections() {
        DelimitedParser parser = DelimitedParser.builder(
                        new StringReader("a,b,c\n1,2\n1,2,3,4\n1,2,3\n"))
                .withHeader().tolerance(Tolerance.padOrTruncate()).build();
        List<ParseRecord> rows = drain(parser);

        assertThat(rows).allMatch(ParseRecord::isSuccess);
        assertThat(rows.get(0).record().fields()).containsExactly("1", "2", "");
        assertThat(rows.get(1).record().fields()).containsExactly("1", "2", "3");
        assertThat(parser.stats().adjustedRows()).isEqualTo(2);
        assertThat(parser.stats().errorRows()).isZero();
    }

    @Test
    void strictModeRejectsMismatch() {
        DelimitedParser parser = DelimitedParser.builder(new StringReader("a,b\n1,2,3\n"))
                .withHeader().tolerance(Tolerance.strict()).build();
        assertThat(parser.nextRecord().isSuccess()).isFalse();
    }

    @Test
    void explicitExpectedFieldCountWithoutHeader() {
        DelimitedParser parser = DelimitedParser.builder(new StringReader("1,2,3\n1,2\n"))
                .expectedFieldCount(3).tolerance(Tolerance.padShortRows()).build();

        assertThat(parser.nextRecord().isSuccess()).isTrue();
        ParseRecord padded = parser.nextRecord();
        assertThat(padded.record().fields()).containsExactly("1", "2", "");
    }

    // ------------------------------------------------------------------
    // 5) 流式解析
    // ------------------------------------------------------------------

    @Test
    void parserIsLazyAndDoesNotBufferWholeInput() {
        class InfiniteReader extends Reader {
            private final char[] chunk = "a,b\n".toCharArray();
            private int index;
            long served;

            @Override
            public int read(char[] cbuf, int off, int len) {
                if (len == 0) {
                    return 0;
                }
                int n = Math.min(len, chunk.length - index);
                System.arraycopy(chunk, index, cbuf, off, n);
                index = (index + n) % chunk.length;
                served += n;
                return n;
            }

            @Override
            public void close() {
            }
        }

        InfiniteReader reader = new InfiniteReader();
        DelimitedParser parser = DelimitedParser.csv(reader);

        ParseRecord first = parser.nextRecord();
        assertThat(first.record().fields()).containsExactly("a", "b");
        // 只消费了第一条记录所需的 4 个字符，没有预读整个"文件"
        assertThat(reader.served).isLessThanOrEqualTo(4);

        long count = parser.stream().limit(1000).filter(ParseRecord::isSuccess).count();
        assertThat(count).isEqualTo(1000);
    }

    @Test
    void iteratorAndStreamApi() {
        DelimitedParser parser = parse("1\n2\n3\n");
        assertThat(parser.hasNext()).isTrue();
        List<String> values = parser.stream()
                .map(r -> r.record().get(0))
                .toList();
        assertThat(values).containsExactly("1", "2", "3");
        assertThat(parser.hasNext()).isFalse();
        assertThat(parser.nextRecord()).isNull();
    }

    // ------------------------------------------------------------------
    // 6) 表头关联
    // ------------------------------------------------------------------

    @Test
    void lookupByHeaderName() {
        DelimitedParser parser = DelimitedParser.builder(new StringReader("id,name\n1,Alice\n"))
                .withHeader().build();
        Record record = parser.nextRecord().record();

        assertThat(record.lookup("name")).isEqualTo(new Lookup.Found("Alice"));
        assertThat(record.lookup("id")).isEqualTo(new Lookup.Found("1"));
        assertThat(record.lookup("missing")).isEqualTo(new Lookup.MissingColumn("missing"));
        assertThat(record.get(0)).isEqualTo("1");
        assertThat(record.get(5)).isNull();
        assertThat(parser.header()).isPresent();
        assertThat(parser.header().get().names()).containsExactly("id", "name");
    }

    @Test
    void lookupWithoutHeaderIsExplicit() {
        Record record = parse("1,Alice\n").nextRecord().record();
        assertThat(record.lookup("name")).isEqualTo(new Lookup.NoHeader("name"));
    }

    @Test
    void emptyInputWithHeaderFailsFast() {
        assertThatThrownBy(() -> DelimitedParser.builder(new StringReader("")).withHeader().build())
                .isInstanceOf(DelimitedParseException.class)
                .hasMessageContaining("表头");
    }

    @Test
    void brokenHeaderFailsFast() {
        assertThatThrownBy(() -> DelimitedParser.builder(new StringReader("\"unclosed\n"))
                .withHeader().build())
                .isInstanceOf(DelimitedParseException.class)
                .satisfies(e -> assertThat(((DelimitedParseException) e).errors())
                        .extracting(ParseError::type)
                        .containsExactly(ErrorType.UNCLOSED_QUOTE));
    }

    // ------------------------------------------------------------------
    // 7) 统计
    // ------------------------------------------------------------------

    @Test
    void statsTrackTotalsAndErrorDistribution() {
        String input = "id,name,tag\n"
                + "1,Alice,x\n"
                + "2,Bob\n"
                + "3,Ca\"rol,y\n"
                + "4,Dave,z,extra\n";
        DelimitedParser parser = DelimitedParser.builder(new StringReader(input))
                .withHeader().build();
        drain(parser);

        ParseStats stats = parser.stats();
        assertThat(stats.totalRows()).isEqualTo(4);
        assertThat(stats.successRows()).isEqualTo(1);
        assertThat(stats.errorRows()).isEqualTo(3);
        assertThat(stats.errorCount(ErrorType.FIELD_COUNT_MISMATCH)).isEqualTo(2);
        assertThat(stats.errorCount(ErrorType.INVALID_ESCAPE)).isEqualTo(1);
        assertThat(stats.errorCount(ErrorType.UNCLOSED_QUOTE)).isZero();
        assertThat(stats.physicalLines()).isEqualTo(5);
    }

    @Test
    void blankLinesAreSkippedButCountedAsPhysicalLines() {
        DelimitedParser parser = parse("a,b\n\n\nc,d\n");
        List<ParseRecord> rows = drain(parser);

        assertThat(rows).hasSize(2);
        assertThat(parser.stats().physicalLines()).isEqualTo(4);
        assertThat(parser.stats().totalRows()).isEqualTo(2);
    }

    @Test
    void emptyInputProducesNoRecords() {
        DelimitedParser parser = parse("");
        assertThat(parser.nextRecord()).isNull();
        assertThat(parser.stats().totalRows()).isZero();
    }

    @Test
    void formatRejectsSameDelimiterAndQuote() {
        assertThatThrownBy(() -> DelimitedFormat.builder().delimiter('"').build())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
