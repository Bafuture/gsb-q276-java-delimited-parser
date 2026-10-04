package com.example.delimited;

import java.util.List;

/** 解析器构造/表头读取等不可恢复场景抛出的异常。单条数据记录出错不会抛异常，而是产出 {@link RecordFailure}。 */
public class DelimitedParseException extends RuntimeException {

    private final List<ParseError> errors;

    public DelimitedParseException(String message) {
        this(message, List.of());
    }

    public DelimitedParseException(String message, List<ParseError> errors) {
        super(message);
        this.errors = List.copyOf(errors);
    }

    public List<ParseError> errors() {
        return errors;
    }
}
