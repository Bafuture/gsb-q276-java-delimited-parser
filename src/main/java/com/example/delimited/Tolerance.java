package com.example.delimited;

import java.util.Objects;

/**
 * 容错规则配置。目前只针对"字段数不匹配"这一类可恢复问题；
 * 引号未闭合、非法转义等结构性错误在任何模式下都会作为错误记录上报。
 */
public final class Tolerance {

    private static final Tolerance STRICT = new Tolerance(FieldCountPolicy.STRICT);

    private final FieldCountPolicy fieldCountPolicy;

    private Tolerance(FieldCountPolicy fieldCountPolicy) {
        this.fieldCountPolicy = fieldCountPolicy;
    }

    /** 严格模式（默认）：字段数不匹配即错误。 */
    public static Tolerance strict() {
        return STRICT;
    }

    /** 字段不足时补齐空字段；字段过多仍报错。 */
    public static Tolerance padShortRows() {
        return new Tolerance(FieldCountPolicy.PAD);
    }

    /** 字段过多时截断；字段不足仍报错。 */
    public static Tolerance truncateLongRows() {
        return new Tolerance(FieldCountPolicy.TRUNCATE);
    }

    /** 字段不足补齐、字段过多截断。 */
    public static Tolerance padOrTruncate() {
        return new Tolerance(FieldCountPolicy.PAD_OR_TRUNCATE);
    }

    public static Tolerance of(FieldCountPolicy policy) {
        return new Tolerance(Objects.requireNonNull(policy, "policy"));
    }

    public FieldCountPolicy fieldCountPolicy() {
        return fieldCountPolicy;
    }
}
