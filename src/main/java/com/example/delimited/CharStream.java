package com.example.delimited;

import java.io.IOException;
import java.io.Reader;

/**
 * 带行列跟踪的单字符流。内部一次只预读一个字符，配合调用方逐记录消费，
 * 不会把整个输入读进内存。换行统一规范化为 '\n'（\r\n 与 \r 都算一次换行）。
 */
final class CharStream {

    private final Reader reader;
    private int pushback = -2; // -2 表示无预读字符
    private int extra = -2;    // \r\n 场景下暂存的第二个字符
    private int line = 1;
    private int column = 1;
    private long physicalLines = 0;
    private boolean atEof = false;

    CharStream(Reader reader) {
        this.reader = reader;
    }

    /** 当前位置：下一个将被消费字符的行列号。 */
    ParsePosition position() {
        return new ParsePosition(line, column);
    }

    int line() {
        return line;
    }

    int column() {
        return column;
    }

    boolean atEof() {
        return atEof;
    }

    /** 已消费的物理行数（每遇到一个换行符 +1）。 */
    long physicalLines() {
        return physicalLines;
    }

    /** 预读下一个字符（规范化后），EOF 返回 -1。 */
    int peek() throws IOException {
        if (pushback == -2) {
            pushback = readNormalized();
        }
        return pushback;
    }

    /** 消费并返回下一个字符（规范化后），EOF 返回 -1。 */
    int read() throws IOException {
        int c;
        if (pushback != -2) {
            c = pushback;
            pushback = -2;
        } else {
            c = readNormalized();
        }
        if (c == -1) {
            atEof = true;
            return -1;
        }
        if (c == '\n') {
            line++;
            column = 1;
            physicalLines++;
        } else {
            column++;
        }
        return c;
    }

    /** 读取一个字符并把 \r、\r\n 规范化为 '\n'。 */
    private int readNormalized() throws IOException {
        int c = takeRaw();
        if (c == '\r') {
            int next = takeRaw();
            if (next != '\n' && next != -1) {
                extra = next;
            }
            return '\n';
        }
        return c;
    }

    private int takeRaw() throws IOException {
        if (extra != -2) {
            int c = extra;
            extra = -2;
            return c;
        }
        return reader.read();
    }

    void close() throws IOException {
        reader.close();
    }
}
