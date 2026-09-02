# Oracle 轻量计费集成教程

## 当前状态

Oracle 版本计划作为第二次独立提交交付。当前目录不包含 Oracle 建表脚本、运行 Jar 或可执行接入包，请勿将 MySQL SQL 直接改后缀后用于 Oracle。

## 第二次提交将包含

```text
Oracle/
├─ backend-integration/   Java Client 与 Spring Boot Starter
├─ bin/                   Oracle 运行脚本
├─ config/                Oracle JDBC 与宿主代理配置样例
├─ runtime/               通过 Oracle 回归的运行 Jar
├─ sql/                   Oracle 12c+ 母体对齐建表脚本
└─ README.md              完整 Oracle 集成与验收步骤
```

## 必须完成的 Oracle 验收

1. Oracle DDL 可在空 Schema 完整执行，并可安全重复执行。
2. 主键生成、CLOB JSON、时间字段和唯一约束语义正确。
3. Mapper 的分页、单行限制、字符串拼接、聚合和批量插入已完成 Oracle 方言实现。
4. 场景、费目、要素、规则、发布和版本生效链路通过。
5. 同步计费成功、同步计费失败日志通过。
6. 正式任务、结果台账和结果追溯通过。
7. 在 Oracle 业务项目中完成 Starter 构建和代理联调。

Oracle 版本未完成上述回归前，根目录状态始终标记为“待第二次提交”。
