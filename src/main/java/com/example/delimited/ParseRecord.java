package com.example.delimited;

import java.util.List;

/**
 * 一条逻辑记录的解析结果：要么成功（{@link RecordSuccess}），要么失败（{@link RecordFailure}）。
 * 解析器不会因单条记录出错而中断，调用方通过模式匹配分别处理。
 */
public sealed interface ParseRecord permits RecordSuccess, RecordFailure {

    /** 记录起始物理行号（1 起始）。 */
    long lineNumber();

    default boolean isSuccess() {
        return this instanceof RecordSuccess;
    }

    /** 成功记录；失败时抛出 IllegalStateException。 */
    default Record record() {
        if (this instanceof RecordSuccess s) {
            return s.record();
        }
        throw new IllegalStateException("错误记录没有 Record: " + this);
    }

    /** 错误列表；成功记录返回空列表。 */
    default List<ParseError> errors() {
        return List.of();
    }
}
