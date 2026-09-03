# Cost Platform Lite 集成交付包

本仓库保存轻量计费的可维护核心源码、第三方系统接入入口、配置、SQL、前端工作台和已发布运行制品；不保存母体平台的完整工程、无关模块或任何客户业务源码。

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

## 源码与数据库落位

`Mysql/backend-integration/` 和 `Oracle/backend-integration/` 分别提供可独立构建的 Java 8 兼容 `Client/Starter` 集成源码；两套入口源码保持同一套稳定协议和 Maven 坐标，客户按数据库类型选择对应目录即可。它们只负责宿主 Controller、请求转发、响应适配和配置，不包含计费核心逻辑。

`Mysql/source/` 和 `Oracle/source/` 分别保存 MySQL、Oracle 计费核心源码及独立 Maven 构建入口，后续核心开发和 Jar 打包都在本仓库完成。它们只包含轻量计费必要的计费代码和兼容层，不包含母体后台等无关模块。计费核心运行时仍以对应数据库版本的 Jar 交付：MySQL 使用 `Mysql/runtime/cost-lite-server-1.0.0.jar`，Oracle 使用 `Oracle/runtime/cost-lite-server-1.0.0.jar`。Jar 的数据库账号、密码和 JDBC 地址由部署环境提供，不能写死进 Jar。

数据库有两种落位方式，二选一即可：

1. 独立计费库：创建专用数据库或 Schema，执行对应 `cost-lite-schema.sql`，Jar 连接这个库。
2. 业务库同库：在客户业务数据库中执行同一份 SQL，Jar 的 JDBC 配置指向该业务库；业务项目原有数据源和业务表无需迁移，Starter 仍只配置 Jar 地址。

两种方式使用相同的 `cost_*` 表、字段和编码。SQL 不会自动创建或切换客户数据库，必须由实施人员连接目标库后手动执行；已有同名表和字典表时先比对结构，脚本只补充缺失的轻量计费字典数据。

## 构建边界

客户迁移时不需要把核心源码复制到目标业务项目；集成方只引用对应 Jar 和 `backend-integration` 入口。我们自己的核心开发和打包在本仓库的数据库版本源码目录完成：

```bash
mvn -f Mysql/source/pom.xml clean package -DskipTests
mvn -f Oracle/source/pom.xml clean package -DskipTests
mvn -f Mysql/backend-integration/pom.xml clean install
mvn -f Oracle/backend-integration/pom.xml clean install
```

前两条命令分别生成两个数据库版本的计费核心 Jar；后两条命令只生成宿主侧 `cost-lite-client` 和 `cost-lite-spring-boot-starter`。构建不会自动连接数据库。通过完整回归后，把对应源码 `target/cost-lite-server.jar` 发布到 `runtime/` 并更新 `SHA256SUMS`，运行端再使用已发布 Jar。

如果后续需要升级公式引擎、规则执行或数据库方言，直接在本仓库对应的 `Mysql/source/` 或 `Oracle/source/` 中修改，并在两个版本需要保持一致时同步修改、分别构建和回归。客户迁移时不要把这些源码复制到业务项目；也不要把完整母体工程、反编译源码或 `source/target/`、`.class` 等核心构建产物带入仓库。没有明确的核心升级任务时，迁移工具遇到 Jar 缺失或校验不一致应停止并报告，不能自行重建或替换核心。

## 目录边界

```text
cost_platform_lite/
├─ Mysql/
│  ├─ source/                 MySQL 计费核心源码和独立构建入口
│  ├─ backend-integration/   Java 8 兼容 Client 与 Spring Boot Starter
│  ├─ bin/                   MySQL 运行脚本
│  ├─ config/                运行端与宿主端配置样例
│  ├─ runtime/               已验证的轻量计费运行 Jar
│  ├─ sql/                   轻量运行所需、与母体实体对齐的 MySQL 建表脚本
│  ├─ API.md                 MySQL 后端接口文档
│  └─ README.md              MySQL 完整集成教程
├─ Oracle/
│  ├─ source/                 Oracle 计费核心源码和独立构建入口
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
- 自动迁移提示词：[MIGRATION_PROMPT.md](MIGRATION_PROMPT.md)

## 冻结原则

1. 计费表名、字段名和业务编码与母体保持一致，不新建 `lite_*` 或 `billing_*` 平行实体。
2. 宿主系统只引入 Starter、配置代理地址并接入一个前端页面，不复制母体 Controller、Service、Mapper 或实体。
3. 前端适配器同时支持 `proxy` 和 `runtime` 两种部署入口：Starter 和独立 Jar 默认都使用 `/cost/**`；如宿主已有路径冲突，只需在配置中改一次 `web-path` 和前端 `basePath`。
4. 数据库只初始化轻量运行所需的 `cost_*` 表和母体字典对应的 `sys_dict_type`、`sys_dict_data`，不迁移其他 RuoYi `sys_*` 表或无关业务表；前端下拉直接读取轻量库字典，数据库始终保存母体统一编码。
5. 公式后端接口保留。当前精简工作台已在同一页面提供公式资产维护、试算、版本和回退；直接“公式费目无规则”暂不启用，规则仍负责条件命中，公式负责金额表达式。
6. 工作台只提供真实业务数据试算、试算结果预览和成功/失败试算日志；同步计费是业务系统后端调用能力，正式任务与正式结果不开放给工作台。
7. 集成主链路通过 Starter/Jar 管理路径提供场景、版本、费目、模板、同步计费和批量试算；OpenApp 仅作为可选跨系统令牌模式；正式任务接口即使保留在运行端，也只允许受保护的业务后端或内部运维调用。

## 不允许提交的内容

- 母体完整平台后端、前端、无关实体、无关 Mapper、无关 Service 和无关 Controller 源码。
- 任一第三方业务项目的完整源码。
- 数据库口令、Token、客户数据和真实环境地址。
- `target/`、`node_modules/`、`dist/`、日志和临时文件。
- 任意 `.class` 文件或其他本地编译缓存。
