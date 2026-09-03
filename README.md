# Cost Platform Lite 集成交付包

本仓库只保存第三方系统接入轻量计费所需的代码、配置、SQL、前端工作台和已发布运行制品，不保存母体平台源码。

## 当前交付状态

| 目录 | 状态 | 说明 |
| --- | --- | --- |
| `Mysql/` | 可集成 | 2026-09-02 已完成建表、场景、费目、要素、规则、发布、试算成功/失败日志和试算结果回归 |
| `Front/` | 可集成 | Vue 3 + Element Plus 可嵌入工作台，已在现有业务项目完成生产构建 |
| `Oracle/` | 可集成 | 2026-09-02 已完成 Oracle DDL、Oracle 方言适配 Jar、场景/费目/要素/规则/发布与运行链路回归 |

## 运行边界

Oracle 和 MySQL 共用 `Front/` 工作台、统一的 `CostLiteApi` 协议以及同一版本的 Spring Boot Starter。Starter 只负责宿主侧 Controller/代理、鉴权边界、超时和路由归一，不包含数据库 SQL，也不连接数据库。

`Mysql/runtime/cost-lite-server-1.0.0.jar` 和 `Oracle/runtime/cost-lite-server-1.0.0.jar` 是分别构建的数据库运行制品：Jar 内置计费 Controller、计费核心和对应数据库方言适配，分别读取 MySQL 或 Oracle 配置。两者不能交叉替换，但前端和 Starter 不需要改动。

数据库连接信息不写入 Jar，也不写入 Git。独立计费库和业务库同库都支持：只需把对应 SQL 初始化到目标库，再通过环境变量或外部 `application.yml` 配置目标 JDBC URL、账号和密码。业务项目只配置 Starter 的 `base-url` 和令牌，不承担计费库账号管理。

## 目录边界

```text
cost_platform_lite/
├─ Mysql/
│  ├─ backend-integration/   Java 8 兼容 Client 与 Spring Boot Starter
│  ├─ bin/                   MySQL 运行脚本
│  ├─ config/                运行端与宿主端配置样例
│  ├─ runtime/               已验证的轻量计费运行 Jar
│  ├─ sql/                   轻量运行所需、与母体实体对齐的 MySQL 建表脚本
│  ├─ API.md                 MySQL 后端接口文档
│  └─ README.md              MySQL 完整集成教程
├─ Oracle/
│  ├─ backend-integration/   Java 8 兼容 Client 与 Spring Boot Starter
│  ├─ bin/                   Oracle 运行脚本
│  ├─ config/                Oracle 运行端与宿主端配置样例
│  ├─ runtime/               已验证的 Oracle 轻量计费运行 Jar
│  ├─ sql/                   轻量运行所需、与母体实体对齐的 Oracle 建表脚本
│  ├─ API.md                 Oracle 后端接口文档
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
- MySQL 接口文档：[Mysql/API.md](Mysql/API.md)
- Oracle 后端和数据库：[Oracle/README.md](Oracle/README.md)
- Oracle 接口文档：[Oracle/API.md](Oracle/API.md)
- 前端工作台：[Front/README.md](Front/README.md)

## 冻结原则

1. 计费表名、字段名和业务编码与母体保持一致，不新建 `lite_*` 或 `billing_*` 平行实体。
2. 宿主系统只引入 Starter、配置代理地址并接入一个前端页面，不复制母体 Controller、Service、Mapper 或实体。
3. 前端适配器同时支持 `proxy` 和 `runtime` 两种部署入口：Starter 接入统一使用 `/cost-lite/**`，独立 Jar 接入使用 `/cost/**`；业务组件不维护两套接口地址。
4. 数据库只初始化轻量运行所需的 `cost_*` 表和母体字典对应的 `sys_dict_type`、`sys_dict_data`，不迁移其他 RuoYi `sys_*` 表或无关业务表；前端下拉直接读取轻量库字典，数据库始终保存母体统一编码。
5. 公式后端接口保留。当前精简工作台已在同一页面提供公式资产维护、试算、版本和回退；直接“公式费目无规则”暂不启用，规则仍负责条件命中，公式负责金额表达式。
6. 工作台只提供真实业务数据试算、试算结果预览和成功/失败试算日志；同步计费是业务系统后端调用能力，正式任务与正式结果不开放给工作台。
7. 开放接口只提供场景、版本、费目、模板和同步计费；正式任务接口即使保留在运行端，也只允许受保护的业务后端或内部运维调用。

## 不允许提交的内容

- 母体平台后端、前端、实体、Mapper、Service 和 Controller 源码。
- 任一第三方业务项目的完整源码。
- 数据库口令、Token、客户数据和真实环境地址。
- `target/`、`node_modules/`、`dist/`、日志和临时文件。
