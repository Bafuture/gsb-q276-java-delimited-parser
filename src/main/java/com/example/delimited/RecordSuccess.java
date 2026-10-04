package com.example.delimited;

/** 解析成功的记录。容错模式下的补齐/截断会通过 {@link RecordSuccess#adjusted()} 标记。 */
public record RecordSuccess(Record record, boolean adjusted) implements ParseRecord {

    public RecordSuccess(Record record) {
        this(record, false);
    }

    @Override
    public long lineNumber() {
        return record.lineNumber();
    }
}
