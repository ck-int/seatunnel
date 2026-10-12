import ChangeLog from '../changelog/connector-tdengine.md';

# TDengine

> TDengine 源端连接器

## 描述

从 TDengine 超级表读取数据。

该 Source 以批处理方式读取一个超级表的数据。配置时间边界时按边界读取；未配置时不自动追加时间边界，可在过滤条件中自行指定时间范围。可以读取该超级表下的所有子表，也可以只读取指定子表；同时支持只读取部分列。

每个 source 分片会读取一个 TDengine 子表。输出字段会自动把 `subtable_name` 放在第一列，用来保留原始子表名；如果下游继续写入 TDengine，TDengine sink 也会使用这一列作为目标子表名。

## 主要特性

- [x] [批处理](../../introduction/concepts/connector-v2-features.md)
- [ ] [流式](../../introduction/concepts/connector-v2-features.md)
- [x] [精确一次](../../introduction/concepts/connector-v2-features.md)
- [x] [列投影](../../introduction/concepts/connector-v2-features.md)

- [x] [并行度](../../introduction/concepts/connector-v2-features.md)
- [ ] [支持用户自定义分片](../../introduction/concepts/connector-v2-features.md)

## 配置项

| 名称           | 类型   | 必填 | 默认值         |
|----------------|--------|------|----------------|
| url            | string | 是   | -              |
| username       | string | 是   | -              |
| password       | string | 是   | -              |
| database       | string | 是   | -              |
| stable         | string | 是   | -              |
| sub_tables     | list   | 否   | -              |
| lower_bound    | string | 条件必填 | -           |
| upper_bound    | string | 条件必填 | -           |
| where          | string | 否   | -              |
| read_columns   | list   | 否   | -              |

### url [string]

TDengine JDBC 连接地址。端口一般为 `6041`（taosAdapter）。

**驱动与服务端主版本必须一致**：TDengine 2.x 请使用内嵌 `taos-jdbcdriver` 2.0.38 的连接器包；TDengine 3.x 请使用内嵌 3.5.3 的连接器包。同一 `connectors/` 目录下不要同时放置多个 `connector-tdengine*.jar`。

- TDengine 2.x：支持 `jdbc:TAOS://`、`jdbc:TAOS-RS://`（不支持 WebSocket）。
- TDengine 3.x：支持 REST（`TAOS-RS`）与 WebSocket（`TAOS-WS`），推荐 WebSocket。

例如：

```
jdbc:TAOS-RS://localhost:6041/
jdbc:TAOS-WS://localhost:6041/
```

### username [string]

连接 TDengine 使用的用户名。

### password [string]

连接 TDengine 使用的密码。

### database [string]

TDengine 数据库名称。

### stable [string]

TDengine 超级表名称。

### sub_tables [list]

TDengine 子表名称列表。不配置时读取该超级表下的所有子表；配置后只读取指定子表。

### lower_bound [string]

可选的查询时间范围下界，包含该时间点。必须与 `upper_bound` 一起配置；配置后连接器会为每个子表查询追加 `timestamp_column >= lower_bound` 条件。未配置时不会自动追加时间边界，请使用 TDengine 可识别的时间字符串，例如 `2018-10-03 14:38:05.000`。

### upper_bound [string]

可选的查询时间范围上界，不包含该时间点。必须与 `lower_bound` 一起配置；配置后连接器会为每个子表查询追加 `timestamp_column < upper_bound` 条件。未配置时不会自动追加时间边界，请使用 TDengine 可识别的时间字符串，例如 `2018-10-03 14:38:16.801`。

### where [string]

可选的 TDengine 完整过滤表达式，不要包含 `WHERE` 关键字。表达式可以引用配置的超级表中的普通列和 TAGS，例如
`location = 'beijing' and voltage > 220`。连接器会自动拼接
`tbname = '<子表名>'`；如果同时配置了 `lower_bound` 和 `upper_bound`，还会将两者与该表达式用 `AND` 连接。未配置时间边界时，时间过滤可以直接写在此表达式中。请不要传入 `WHERE`、`FROM` 或结尾分号。

如果既未配置时间边界，也未在 `where` 中提供时间条件，连接器会读取匹配子表的全部历史数据，请谨慎使用。

### read_columns [list]

要读取的列名列表。不配置时读取所有列。读取超级表时，请将 TAGS 字段放在列表末尾。不要在这里配置 `subtable_name`，连接器会自动把它作为第一列输出。

`read_columns` 的顺序会决定 `subtable_name` 之后的输出字段顺序。如果后续继续写入 TDengine Sink，请保持普通列在前、TAGS 字段在后，这样 Sink 才能正确区分普通列和 TAGS 值。

## 示例

### 按自定义条件读取所有子表

```hocon
env {
  parallelism = 2
  job.mode = "BATCH"
}

source {
  TDengine {
    url = "jdbc:TAOS-RS://localhost:6041/"
    username = "root"
    password = "taosdata"
    database = "power"
    stable = "meters"
    where = "location = 'beijing' and voltage > 220"
    plugin_output = "tdengine_result"
  }
}
```

### 从 TDengine 读取并写入 TDengine

```hocon
env {
  parallelism = 2
  job.mode = "BATCH"
}

source {
  TDengine {
    url = "jdbc:TAOS-RS://tdengine-src:6041/"
    username = "root"
    password = "taosdata"
    database = "power"
    stable = "meters"
    lower_bound = "2018-10-03 14:38:05.000"
    upper_bound = "2018-10-03 14:38:16.801"
    plugin_output = "tdengine_result"
  }
}

sink {
  TDengine {
    url = "jdbc:TAOS-RS://tdengine-sink:6041/"
    username = "root"
    password = "taosdata"
    database = "power2"
    stable = "meters2"
    timezone = "UTC"
  }
}
```

### 读取指定子表和指定列

```hocon
source {
  TDengine {
    url = "jdbc:TAOS-RS://localhost:6041/"
    username = "root"
    password = "taosdata"
    database = "power"
    stable = "meters"
    lower_bound = "2018-10-03 14:38:05.000"
    upper_bound = "2018-10-03 14:38:16.801"
    sub_tables = ["d1001", "d1002"]
    read_columns = ["ts", "current", "voltage", "phase", "off", "nc", "location", "groupid"]
  }
}
```

## 变更日志

<ChangeLog />
