# 轻量计费平台迁移执行 Prompt

你是一名资深 Java、Spring Boot、Vue 和数据库集成工程师。请把本交付包中的轻量计费平台接入当前业务项目，并完成可运行验证。

## 一、输入信息

执行前先读取并确认以下信息；缺失时从项目实际结构中判断，不要臆造：

```text
交付包目录：<cost_platform_lite_integration_repo>
目标业务项目目录：<host-project>
数据库类型：MYSQL 或 ORACLE
计费 Jar 地址：<jar-path-or-url>
计费 Jar 基础地址：<cost-lite-base-url>
目标宿主入口：/cost/**
前端技术栈：从目标项目实际 package.json 判断
后端技术栈：从目标项目实际 pom.xml、build.gradle 和源码判断
```

如果用户已经明确提供了本地测试数据库地址、账号或密码，只能用于当前运行验证，禁止写入 Git、源码、前端包、日志和最终文档。没有明确授权时，不得写入生产库。

本分支提供 MySQL 和 Oracle 两套同进程嵌入模式。开始迁移时先判断目标宿主：Java 8 + Spring Boot 2.7 Servlet 应用优先使用数据库匹配的 `cost-lite-starter-mysql` 或 `cost-lite-starter-oracle`；传统 SSM、无法共存 MyBatis 数据源或需要进程隔离的项目继续使用对应 `backend-integration` HTTP Starter + 独立 `cost-lite-server`。不要把两种模式混用。

## 二、必须遵守的架构边界

1. 嵌入模式中，`cost-lite-core-mysql`/`cost-lite-core-oracle` 是计费核心普通 Jar，`cost-lite-starter-mysql`/`cost-lite-starter-oracle` 只负责自动配置和入口注册；两者随宿主业务应用同进程运行。兼容模式中，独立 MySQL/Oracle Jar 才是计费核心运行单元，HTTP Starter 只负责代理。两种模式都不得复制或重写计费核心逻辑。
2. 目标业务项目只接入可迁移的 Starter/Client 入口和前台页面，不得把母体项目的全部源码、模块、Controller、Service、Mapper、RuoYi 基础表或业务代码复制进目标项目。
3. 宿主业务后端通过自身的 `/cost/**` 接口访问计费能力。浏览器只能访问宿主接口，不能直接访问计费 Jar，也不能携带数据库账号、管理 Token 或 OpenApp 密钥。
4. 嵌入 Starter 使用 `cost.lite.datasource.*` 连接独立计费库，并固定到命名的 Lite 数据源和事务管理器；兼容模式的 HTTP Starter 不连接数据库，由独立计费 Jar 读取 JDBC 配置。两种模式都可以使用独立库或业务库中的 `cost_*` 表。
5. 必须复用母体 `cost_*` 表、字段、编码和接口语义。禁止新建 `lite_*`、`billing_*` 或其他平行实体来替代母体实体。
6. 不得把完整母体工程或无关模块复制到目标项目。目标项目只接入集成入口、前端页面、配置模板、对应数据库初始化 SQL 和已发布 Jar；核心源码只在本交付仓库对应数据库的 `source/` 目录维护。

### 2.1 Jar 源码和构建边界

- 本交付仓库已经分别保存 `Mysql/source/` 和 `Oracle/source/`，它们是我们后续维护核心 Jar 的源码目录，不是目标业务项目的迁移内容。
- MySQL 嵌入迁移只发布并引用 `cost-lite-core-mysql` 和 `cost-lite-starter-mysql`；Oracle 嵌入迁移只发布并引用 `cost-lite-core-oracle` 和 `cost-lite-starter-oracle`。兼容迁移才使用对应 `runtime/cost-lite-server-1.0.0.jar`，并核对 `runtime/SHA256SUMS`。
- 发生核心升级时，MySQL 在本仓库执行 `mvn -f Mysql/pom.xml clean install -DskipTests`，Oracle 执行 `mvn -f Oracle/pom.xml clean install -DskipTests`；完成数据库、接口和前端回归后再发布对应制品。
- 任何 `source/target/`、`.class`、日志和临时文件都不能提交；不要下载、反编译或复制完整母体工程，也不要在客户业务项目中重新编译计费核心。

## 三、开始前的检查

先执行只读检查并报告结果：

1. 识别目标项目的 Spring Boot 大版本、Java 版本、Maven/Gradle 构建方式、Vue 版本和组件库。
2. 检查目标项目是否已有 `/cost/**` 路由、同名 Bean、同名依赖、全局登录拦截器、网关转发规则和前端菜单。
3. 检查数据库类型，并确认目标库中是否已经存在 `cost_*`、`sys_dict_type`、`sys_dict_data` 同名表。存在同名表时先比对字段和索引，禁止直接覆盖。
4. Java 8 + Spring Boot 2.7 Servlet 项目检查对应数据库的 `starter`；其他项目检查对应数据库的 `backend-integration` 和独立运行 Jar，只使用匹配的集成入口。当前交付包不把 Boot 2 的 `javax.*` 依赖与 Boot 3 的 `jakarta.*` 依赖混装。
5. 检查当前 Git 工作区，保留用户已有改动，不使用 `git reset --hard`、`git checkout --` 或其他破坏性回退命令。

## 四、后端集成要求

### 4.1 依赖和配置

Java 8 + Spring Boot 2.7 Servlet 项目优先使用数据库匹配的嵌入 Starter。如果目标项目不能直接引用本地 Maven 模块，则先执行对应数据库根目录的 `mvn clean install -DskipTests` 或发布 Maven 制品，再在目标项目中增加依赖，不得复制 Java 源码。传统 SSM、数据源冲突项目或需要进程隔离的项目使用对应 `cost-lite-spring-boot-starter` HTTP 代理。

嵌入模式宿主 POM：

```xml
<dependency>
    <groupId>com.costplatform.lite</groupId>
    <!-- MYSQL 使用 cost-lite-starter-mysql；ORACLE 使用 cost-lite-starter-oracle。两者不要同时引入。 -->
    <artifactId>cost-lite-starter-mysql</artifactId>
    <version>${cost-lite.version}</version>
</dependency>
```

嵌入模式配置：

```yaml
cost:
  lite:
    embedded:
      enabled: true
    datasource:
      url: ${COST_LITE_DB_URL}
      username: ${COST_LITE_DB_USERNAME}
      password: ${COST_LITE_DB_PASSWORD}
```

Starter 会自动注册 `/cost/**` Controller、Service、Mapper 和独立数据源，不需要新增启动类或单独启动 `cost-lite-server`。业务项目已有 `spring.datasource` 保持不变。

以下是旧项目 HTTP 代理模式配置：

宿主配置应类似下面的结构，实际配置文件位置和环境变量命名应遵循目标项目约定：

```yaml
cost:
  lite:
    integration:
      enabled: true
      base-url: ${COST_LITE_BASE_URL:http://127.0.0.1:18080}
      admin-token: ${COST_LITE_ADMIN_TOKEN:}
      web-path: /cost
      proxy-enabled: true
      connect-timeout: ${COST_LITE_CONNECT_TIMEOUT:5000}
      read-timeout: ${COST_LITE_READ_TIMEOUT:30000}
      max-retries: ${COST_LITE_MAX_RETRIES:0}
```

要求：

- 只有 HTTP 代理模式配置 `base-url`，它指向 MySQL 或 Oracle 对应的计费 Jar；嵌入模式改配 `cost.lite.datasource.*`，不配置 `base-url`。
- `web-path` 默认配置为 `/cost`，使宿主项目对外暴露 `/cost/**`。
- 管理 Token 只能由宿主后端配置读取；不得下发浏览器。
- 代理路由必须使用 Starter 的稳定路由键和默认上游路径。只有目标项目确有冲突时才通过 `upstream-paths` 覆盖单个路径。
- OpenApp 不是内嵌 Starter 的前置条件。除非用户明确要求跨系统令牌模式，否则不要创建或配置 OpenApp。
- 不要全局关闭目标项目的登录和权限。若本地验收需要免登录，只能在本地 profile 或明确的受保护测试配置中放行 `/cost/**`，生产环境必须保留宿主权限控制。

### 4.2 数据库初始化

根据数据库类型只执行一份初始化脚本：

- MySQL：`Mysql/sql/cost-lite-schema.sql`
- Oracle：`Oracle/sql/cost-lite-schema.sql`

初始化原则：

- 只初始化轻量工作台和计费运行所需的母体 `cost_*` 表，以及页面实际需要的 `sys_dict_type`、`sys_dict_data` 字典数据。
- 不迁移 RuoYi 其他 `sys_*` 表、账单明细、宿主业务表和无关基础设施表。
- 独立计费库和业务库同库两种方式都要支持，表名和字段不变。
- 先备份并检查外键、索引、字符集、大小写规则和已有表结构。
- 试算日志与试算结果必须保留在母体已有的 `cost_simulation_record` JSON 字段中；不得另造结果表。

数据库落位由实施人员二选一，迁移工具不得擅自创建数据库、切换 Schema 或修改业务表：

```text
独立库：创建专用 MySQL database 或 Oracle schema，连接该目标库后执行对应 SQL，Jar 指向该库。
业务库同库：连接客户业务库执行同一份 SQL，Jar 的 JDBC URL、账号和密码指向该业务库；宿主原有业务数据源无需迁移。
```

MySQL 可执行：`mysql -h <host> -P <port> -u <user> -p <database> < Mysql/sql/cost-lite-schema.sql`。
Oracle 可使用 SQL*Plus/SQLcl 登录目标 Schema 后执行：`@Oracle/sql/cost-lite-schema.sql`。
执行前必须备份并检查同名 `cost_*`、`sys_dict_type`、`sys_dict_data` 表；脚本不是覆盖式升级脚本，不得在生产库直接覆盖已有表。

### 4.3 宿主接口和前端路径

确认宿主入口至少能访问以下能力。嵌入 Starter 和独立 Jar 直接使用母体兼容的 `runtime` 路径；旧版 HTTP Starter 才使用下面的 `proxy` 稳定路径。前端不能直接访问独立 Jar 的数据库或管理令牌：

```text
嵌入 Starter/独立 Jar 的 runtime 路径：
GET    /cost/lite/health
GET    /cost/lite/bootstrap
GET    /cost/dictionary/options
场景、费目、要素、条件组、规则、公式、版本维护接口均位于 /cost/**
POST   /cost/run/simulation/execute
POST   /cost/run/simulation/batch-execute
GET    /cost/lite/billing-log/list
GET    /cost/lite/billing-log/{simulationId}
POST   /cost/run/fee/calculate

旧版 HTTP Starter 的 proxy 路径：
GET    /cost/health
GET    /cost/bootstrap
GET    /cost/dictionary/options
POST   /cost/simulations
POST   /cost/simulations/batch
GET    /cost/logs
GET    /cost/logs/{simulationId}
POST   /cost/calculate

旧版 HTTP Starter 服务端转发到独立 Jar 的上游路径：
POST   /cost/run/simulation/execute
POST   /cost/run/simulation/batch-execute
GET    /cost/lite/billing-log/list
GET    /cost/lite/billing-log/{simulationId}
POST   /cost/run/fee/calculate
```

正式任务接口即使存在，也不在工作台开放。工作台只展示配置、发布检查、单条试算、批量试算、试算日志和试算结果。

## 五、前端迁移要求

1. 使用交付包 `Front/` 中的页面和 `costLiteApi.ts`，不要从母体前端复制无关页面。
2. 前端 API 使用宿主同源的 `/cost` 路径，嵌入 Starter 或独立 Jar 直连时使用 `routeMode: "runtime"`，旧版 HTTP Starter 代理时使用 `routeMode: "proxy"`；不能把数据库地址或 Token 打进浏览器包。
3. 按目标项目现有路由、菜单、布局、登录和组件库约定接入 `CostLiteWorkbench.vue`。
4. 菜单只增加一个轻量计费工作台入口；不要复制母体整套菜单树。
5. 字典下拉从计费库现有字典接口读取中文标签，不新建单独的字典 TypeScript 文件。高基数货种、客户、船舶等不初始化为全量字典。
6. 页面必须支持：场景选择、版本发布/回退、费目维护、要素维护、条件组、规则与费率维护、公式维护、单条试算、批量试算、调用日志和结果查看。
7. 批量结果必须显示母体既有字段：

```text
records[*].input
records[*].variables
records[*].explain
records[*].result.amountTotal
records[*].result.feeResults
```

禁止为了显示金额另造响应字段或数据库字段。

## 六、笛卡尔和高基数费率要求

对于“外贸 + 进口/出口 + 3000 个货名分别对应不同单价”的场景：

1. 不要创建 6000 条计费规则。
2. 业务系统或可插拔适配器根据 `goodsCode + tradeMode + direction + billMonth/priceVersion` 查询权威价格。
3. 传给计费核心的输入中提供已经归一化的 `unitPrice`，计费规则只负责命中适用范围。
4. 公式使用母体公式资产，例如 `V.quantity * V.unitPrice`，公式结果按母体 `FORMULA` 规则语义作为最终金额。
5. 批量请求必须批量查询价格，禁止对每条数据产生一次数据库或 HTTP 查询。
6. 缺少价格时要明确失败并留下试算日志，不能把缺失价格静默当成免费。
7. 不得把 3000 个货名写进字典、`cost_rule_condition` 大量枚举值或超长 `if` 公式。规则只维护低基数维度和少量特殊覆盖。

## 七、公式要求

严格遵循母体语义：

- `cost_formula` 是公式资产，`cost_formula_version` 保存历史版本。
- `cost_variable.source_type = FORMULA` 表示公式派生要素，并通过 `formula_code` 引用公式。
- `cost_rule.rule_type = FORMULA` 表示命中规则后执行金额公式，并通过 `amount_formula_code` 引用公式。
- 公式费目不能绕过规则。若公式对所有输入都适用，创建一条无条件公式规则。
- `FIXED_RATE`、`TIER_RATE` 是数量乘单价；`FIXED_AMOUNT` 是固定金额；`FORMULA` 的表达式结果就是最终金额，不能重复乘数量。
- 发布前必须检查公式编码、变量引用、费目引用和循环依赖。公式不能执行 SQL、任意 Java 代码或任意 HTTP。
- 编辑公式必须保留历史版本；已发布版本和既有试算结果不能被新草稿覆盖。

## 八、必须完成的验证

按目标项目实际命令执行并记录结果：

1. 编译 Starter/Client 和目标后端。
2. 嵌入 Starter 或独立 Jar 验证 `/cost/lite/health` 的服务和数据库均为 `UP`；HTTP 代理额外验证宿主 `/cost/health` 能正常转发。
4. 验证场景、费目、要素、条件组、规则、公式的查询和保存。
5. 验证发布前检查、发布和回退。
6. 验证单条试算能返回金额、规则解释、变量解释和费目明细。
7. 验证批量试算成功和失败数据都会保存日志，金额从 `result.amountTotal` 读取，明细从 `result.feeResults` 读取。
8. 验证同一场景存在试算记录时删除操作会被治理校验拦截，不能级联删除历史日志。
9. 验证前端页面能打开、菜单能进入、字典显示中文、无控制台错误、构建通过。
10. 验证生产配置中没有数据库密码、Token、Cookie、真实客户数据和临时日志。

## 九、交付和 Git 要求

完成后只提交以下内容：

- 目标项目需要复制或引用的 Starter/Client 集成入口代码。
- `Front/` 前端集成代码和接入说明。
- 当前数据库类型对应的初始化 SQL。
- MySQL 或 Oracle 对应的运行 Jar、校验和、启动模板。
- `Mysql/source/` 或 `Oracle/source/` 中对应的可维护核心源码，仅用于本交付仓库后续开发和打包，不复制到目标项目。
- 集成配置模板、菜单/权限接入说明、API 文档和本次验证记录。

严禁提交：

- 母体完整源码和目标客户业务项目源码。
- `target/`、`node_modules/`、`dist/`、运行日志和临时文件。
- 数据库密码、Token、私钥、Cookie、客户真实数据。
- 为绕过母体约束而新建的平行实体、平行结果表或平行公式表。

最终报告必须包含：

```text
已识别的目标项目技术栈
实际修改的文件列表
使用的数据库版本和初始化脚本
Jar 与 Starter 的职责边界
宿主入口和前端访问地址
执行过的构建、启动和接口验证命令
未通过项及原因
Git 提交号和推送结果
```
