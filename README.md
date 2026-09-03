# Cost Platform Lite 集成交付包

本仓库只保存第三方系统接入轻量计费所需的代码、配置、SQL、前端工作台和已发布运行制品，不保存母体平台源码。

## 当前交付状态

| 目录 | 状态 | 说明 |
| --- | --- | --- |
| `Mysql/` | 可集成 | 2026-09-02 已完成建表、场景、费目、要素、规则、发布、同步计费、失败日志、正式任务和结果台账回归 |
| `Front/` | 可集成 | Vue 3 + Element Plus 可嵌入工作台，已在现有业务项目完成生产构建 |
| `Oracle/` | 可集成 | 2026-09-02 已完成 Oracle DDL、Oracle 方言适配 Jar、场景/费目/要素/规则/发布与运行链路回归 |

## 目录边界

```text
cost_platform_lite/
├─ Mysql/
│  ├─ backend-integration/   Java 8 兼容 Client 与 Spring Boot Starter
│  ├─ bin/                   MySQL 运行脚本
│  ├─ config/                运行端与宿主端配置样例
│  ├─ runtime/               已验证的轻量计费运行 Jar
│  ├─ sql/                   母体实体对齐的 MySQL 建表脚本
│  └─ README.md              MySQL 完整集成教程
├─ Oracle/
│  ├─ backend-integration/   Java 8 兼容 Client 与 Spring Boot Starter
│  ├─ bin/                   Oracle 运行脚本
│  ├─ config/                Oracle 运行端与宿主端配置样例
│  ├─ runtime/               已验证的 Oracle 轻量计费运行 Jar
│  ├─ sql/                   母体实体对齐的 Oracle 建表脚本
│  └─ README.md              Oracle 完整集成教程
├─ Front/
│  ├─ src/                   通用 Vue 工作台源码
│  ├─ examples/              宿主页接入示例
│  ├─ menu-manifest.yml      通用菜单清单
│  └─ README.md              前端完整集成教程
└─ README.md
```

## 选择教程

- MySQL 后端和数据库：[Mysql/README.md](Mysql/README.md)
- Oracle 后端和数据库：[Oracle/README.md](Oracle/README.md)
- 前端工作台：[Front/README.md](Front/README.md)

## 冻结原则

1. 计费表名、字段名和业务编码与母体保持一致，不新建 `lite_*` 或 `billing_*` 平行实体。
2. 宿主系统只引入 Starter、配置代理地址并接入一个前端页面，不复制母体 Controller、Service、Mapper 或实体。
3. 前端适配器同时支持 `proxy` 和 `runtime` 两种部署入口：Starter 接入统一使用 `/cost-lite/**`，独立 Jar 接入使用 `/cost/**`；业务组件不维护两套接口地址。
4. 字典差异通过配置映射或 `CostDictionaryProvider` 插件处理，数据库始终保存母体统一编码。
5. 公式后端接口保留。当前精简工作台支持选择已有公式或填写表达式，独立公式维护页面后续作为可插拔模块增加。
6. 成功和失败调用都保存计费日志；正式任务另外保存结果台账与追溯数据。

## 不允许提交的内容

- 母体平台后端、前端、实体、Mapper、Service 和 Controller 源码。
- 任一第三方业务项目的完整源码。
- 数据库口令、Token、客户数据和真实环境地址。
- `target/`、`node_modules/`、`dist/`、日志和临时文件。
