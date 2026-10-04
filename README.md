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

## 任务提示词

以下为本题完整的 User Prompt 原文，两次执行必须使用完全相同的文本。

我们的外部数据是分隔符文本，字段里可能带引号与换行，解析出错时现在只说"格式错误"。请从零实现一个定界文本解析与错误定位组件。仓库目前只有一个空的 Maven 工程（pom.xml 只声明 JUnit 5 与 AssertJ）。要求：1) 支持自定义分隔符与引号字符，引号内允许出现分隔符与换行；2) 支持转义规则：连续两个引号表示一个字面引号，需说明与反斜杠转义的区别；3) 支持解析错误定位：引号未闭合、字段数不匹配、非法转义都要报出行号列号与可读原因；4) 支持容错模式：字段数不匹配时可补齐或截断并记录，规则可配置；5) 支持流式解析：逐行产出记录，不把整个文件读进内存；6) 支持表头与数据行关联：按表头名取值，缺失列要返回明确结果；7) 提供统计：总行数、成功记录数、错误记录数与各类错误分布；8) 测试覆盖引号内分隔符与换行、转义、错误定位、容错模式、流式解析与统计；`mvn -q verify` 一条命令跑通。

## 提交要求

1. 在本仓库中完成提示词要求的全部内容。
2. `./mvnw -q verify` 必须通过。
3. 完成后在所属分支（A 或 B）上提交，产物快照的父提交必须是初始环境快照。

---

# 实现说明（分支 A）

组件位于 `com.example.delimited` 包，纯 JDK 17 实现，无运行时依赖。

## 快速开始

```java
try (DelimitedParser parser = DelimitedParser.builder(reader)
        .format(DelimitedFormat.builder()
                .delimiter(';')                 // 自定义分隔符（默认 ','）
                .quote('\'')                    // 自定义引号字符（默认 '"'）
                .escapeMode(EscapeMode.DOUBLED_QUOTE)
                .build())
        .withHeader()                           // 首行作为表头
        .tolerance(Tolerance.padOrTruncate())   // 容错：字段不足补齐、过多截断
        .build()) {

    ParseRecord row;
    while ((row = parser.nextRecord()) != null) {   // 流式：逐条产出，不整读文件
        if (row.isSuccess()) {
            Lookup v = row.record().lookup("name"); // 按表头名取值
            switch (v) {
                case Lookup.Found f         -> use(f.value());
                case Lookup.MissingColumn m -> log("缺列 " + m.name());
                case Lookup.NoHeader n      -> log("未启用表头");
            }
        } else {
            row.errors().forEach(e ->
                log(e.position() + " " + e.message())); // 行号列号 + 可读原因
        }
    }
    ParseStats stats = parser.stats(); // 总行数/成功/失败/容错调整数/物理行数/错误分布
}
```

## 转义规则：双写引号 vs 反斜杠

| | `DOUBLED_QUOTE`（默认，RFC 4180 风格） | `BACKSLASH`（编程语言风格） |
|---|---|---|
| 字面引号 | 引号字段内双写：`"say ""hi"""` → `say "hi"` | `\"` |
| 反斜杠 | 普通字符，无特殊含义 | 转义前缀：`\\ \" \, \n \r \t` 及换行续行 |
| 引号双写 `""` | 表示一个字面引号 | 不表示转义（第一个引号闭合字段，第二个属非法字符） |
| 典型场景 | 标准 CSV 文件 | 日志、类 shell 文本 |

`NONE` 模式则完全不支持转义，引号只负责包裹字段。

## 错误模型

- 单条记录出错不中断解析：产出 `RecordFailure`（含 `ParseError` 列表与已解析的部分字段），随后自动重同步到下一条逻辑记录。
- 错误类型：`UNCLOSED_QUOTE`（引号未闭合，定位到字段起点）、`FIELD_COUNT_MISMATCH`（字段数不匹配，定位到记录起点）、`INVALID_ESCAPE`（非法转义/非法引号，定位到出错字符）、`UNEXPECTED_CHARACTER`（引号闭合后的杂散字符）。
- 位置均为 1 起始的物理行列号；引号字段内换行也会增加行号。
- 容错仅针对字段数不匹配（`STRICT`/`PAD`/`TRUNCATE`/`PAD_OR_TRUNCATE`），结构性错误在任何模式下都会上报。

## 行为约定

- 换行统一识别 `\n`、`\r\n`、`\r`；引号字段内的换行属于字段内容。
- 完全为空的物理行默认跳过（可用 `skipBlankLines(false)` 关闭），但计入 `physicalLines`。
- 期望字段数默认取表头列数，也可用 `expectedFieldCount(n)` 显式指定。
- 表头无法读取（空输入或表头行本身非法）时在 `build()` 阶段抛出 `DelimitedParseException`。
