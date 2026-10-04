# 定界文本解析与错误定位组件

Pair-wise GSB 标注任务仓库（第 17 批 / 276）。

| 项目 | 内容 |
|------|------|
| 任务类型 | Feature 迭代 |
| 任务难度 | 困难 |
| 语言/框架 | Java, Maven, JUnit 5 |
| 环境可复现等级 | 无外部依赖 |
| 构建方式 | Maven（含 mvnw wrapper，无需本机安装 Maven） |

> 本仓库是**初始环境快照**：只有工程骨架，不含任何实现代码。
> 分支说明：`main` 为初始环境；`A`、`B` 为两次独立执行各自的工作分支，均从 `main` 的同一个提交拉出。

## 运行方式

```bash
./mvnw -q verify
```

## 组件说明

实现位于 `src/main/java/com/example/gsb/delimited/`，无第三方运行时依赖。

| 类 | 职责 |
|----|------|
| `DelimitedParser` / `DelimitedParser.Session` | 流式解析入口；`Session` 同时是 `Iterator<ParsedRow>` 与 `AutoCloseable`，逐字符读取、零预读，不把文件载入内存 |
| `ParserConfig` | 分隔符、引号字符、转义模式、容错策略、表头、期望字段数等配置 |
| `EscapeMode` | `DOUBLE_QUOTE`（双引号翻倍，RFC 4180 风格）/ `BACKSLASH`（反斜杠转义） |
| `FieldCountPolicy` | 字段数不匹配时的策略：`STRICT`（仅记录）、`PAD`（补 `null`）、`TRUNCATE`（截断） |
| `ErrorType` / `ParseError` / `ParseException` | 错误分类（`UNCLOSED_QUOTE`、`FIELD_COUNT_MISMATCH`、`ILLEGAL_ESCAPE`）、带 1 起始行号列号的可读错误、严格模式异常 |
| `ParsedRow` / `Header` | 一条记录；按表头名取值，列不存在或该行无值时返回 `Optional.empty()` |
| `ParseStats` | 总行数、成功记录数、错误记录数与各错误类型分布 |

### 用法

```java
ParserConfig config = ParserConfig.builder()
        .header(true)                                  // 首行为表头
        .lenient(true)                                // 容错：收集错误而非抛异常
        .fieldCountPolicy(FieldCountPolicy.PAD)       // 字段不足时补 null
        .build();

DelimitedParser parser = new DelimitedParser(config);
try (DelimitedParser.Session session = parser.open(reader)) {
    session.stream().forEach(row -> {
        String name = row.get("name").orElse("<missing>");
        row.getErrors().forEach(e -> System.out.println(e));
    });
    ParseStats stats = session.getStats();
}
```

默认（非 lenient）为严格模式，遇到第一个错误即抛 `ParseException`，异常上携带行号、列号、错误类型与可读原因，例如：
`line 2, column 1: UNCLOSED_QUOTE - quoted field starting here is never closed: ...`

### 两种转义规则的区别

- **双引号翻倍（`DOUBLE_QUOTE`，默认，RFC 4180 风格）**：字面引号只能在引号字段内写成两个连续引号。`"say ""hi"""` 解析为 `say "hi"`。反斜杠没有任何特殊含义，`a\nb` 就是四个普通字符。
- **反斜杠转义（`BACKSLASH`）**：字面引号写作 `\"`，反斜杠自身写作 `\\`，另支持 `\n`、`\r`、`\t` 与被转义的分隔符（引号内外皆可）。此模式下 `""` 不是转义：第一个引号结束字段，第二个引号会报 `ILLEGAL_ESCAPE`。

两种模式互斥；非法的反斜杠序列、引号末尾悬空反斜杠、未加引号字段中出现引号、闭合引号后紧跟非分隔符内容，均按 `ILLEGAL_ESCAPE` 报出行号列号。

## 任务提示词

以下为本题完整的 User Prompt 原文，两次执行必须使用完全相同的文本。

我们的外部数据是分隔符文本，字段里可能带引号与换行，解析出错时现在只说"格式错误"。请从零实现一个定界文本解析与错误定位组件。仓库目前只有一个空的 Maven 工程（pom.xml 只声明 JUnit 5 与 AssertJ）。要求：1) 支持自定义分隔符与引号字符，引号内允许出现分隔符与换行；2) 支持转义规则：连续两个引号表示一个字面引号，需说明与反斜杠转义的区别；3) 支持解析错误定位：引号未闭合、字段数不匹配、非法转义都要报出行号列号与可读原因；4) 支持容错模式：字段数不匹配时可补齐或截断并记录，规则可配置；5) 支持流式解析：逐行产出记录，不把整个文件读进内存；6) 支持表头与数据行关联：按表头名取值，缺失列要返回明确结果；7) 提供统计：总行数、成功记录数、错误记录数与各类错误分布；8) 测试覆盖引号内分隔符与换行、转义、错误定位、容错模式、流式解析与统计；`mvn -q verify` 一条命令跑通。

## 提交要求

1. 在本仓库中完成提示词要求的全部内容。
2. `./mvnw -q verify` 必须通过。
3. 完成后在所属分支（A 或 B）上提交，产物快照的父提交必须是初始环境快照。
