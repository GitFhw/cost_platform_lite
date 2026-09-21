# MySQL 同进程嵌入式集成

本分支的推荐方案是：业务项目只引入 `cost-lite-starter-mysql`，计费入口和核心逻辑进入业务项目自己的 Spring 容器，同一个 JVM 内运行。数据源模式必须显式配置为 `dedicated`（分离版）或 `host`（同库版）；不会再启动 `cost-lite-server`，也不需要把核心源码复制到客户项目。

## 1. 两个 Jar 的关系

交付物仍然是两个普通 Jar：

| 制品 | 职责 | 是否需要单独启动 |
| --- | --- | --- |
| `cost-lite-core-mysql` | 计费实体、Mapper、规则/公式引擎、Service、数据库 XML | 否，作为依赖加载 |
| `cost-lite-starter-mysql` | Spring Boot 自动配置、专用数据源或宿主数据源复用、Mapper 注册、`/cost/**` Controller 入口 | 否，随业务应用加载 |

客户项目只声明 `cost-lite-starter-mysql` 一个依赖，Maven 会自动传递下载 Core 和 MySQL 驱动；不需要手工写多条依赖，也不需要执行第二个 `java -jar`。`cost-lite-server` 仅保留在历史兼容目录，不属于本分支默认构建和当前交付。

## 2. 客户项目添加依赖

当前这套制品面向 Java 17、Spring Boot 4.1.x Servlet Web 应用；Core 已完成 `jakarta.*` 适配，
Starter 与宿主使用同一套 Spring 7 依赖。其他 Java 或 Spring Boot 主版本应单独编制兼容制品，
不要在同一个宿主混装不同版本的 Starter、HTTP client 或隔离重定位 Jar。建议把两个制品发布到企业 Maven 仓库：

```xml
<properties>
    <cost-lite.version>1.0.0</cost-lite.version>
</properties>

<dependency>
    <groupId>com.costplatform.lite</groupId>
    <artifactId>cost-lite-starter-mysql</artifactId>
    <version>${cost-lite.version}</version>
</dependency>
```

如果只是本机联调，在交付仓库根目录执行：

```powershell
$env:MAVEN_OPTS = "-Dmaven.repo.local=C:\Users\你的用户\.m2\repository"
mvn -f Mysql\pom.xml clean install -DskipTests
```

业务项目仍只引用 `cost-lite-starter-mysql`，不要把 `Mysql/source/` 复制进去。

## 3. 数据库配置方式

分离版不在业务库初始化任何 `cost_*` 表；独立核算库的表结构由部署方按 [cost-lite-schema.sql](sql/cost-lite-schema.sql) 预先准备。同库版才把脚本执行到业务库。正式任务、正式结果追溯、重算/告警或 OpenApp 只有在明确启用时，才继续执行 [cost-lite-formal-schema.sql](sql/cost-lite-formal-schema.sql)。数据库落位有两种方式，配置只保留一种：

### 3.1 复用宿主业务数据源

仅同库版把 SQL 初始化到宿主业务库，并明确设置 `cost.lite.datasource.mode=host`；Starter 会复用名为 `dataSource` 的宿主 `DataSource`：

```yaml
cost:
  lite:
    embedded:
      enabled: true
    datasource:
      mode: host
      host-bean-name: ${COST_LITE_HOST_DATASOURCE_BEAN:dataSource}
```

如果宿主数据源 Bean 名称不是 `dataSource`，只调整 `host-bean-name`，不改计费代码。

### 3.2 使用专用计费库

分离版只连接已准备好的独立核算库，业务库不执行计费表 DDL；在业务项目外部配置文件或配置中心加入：

```yaml
cost:
  lite:
    embedded:
      enabled: true
    auth-enabled: false
    operator: ${COST_LITE_OPERATOR:lite-admin}
    datasource:
      mode: dedicated
      driver-class-name: ${COST_LITE_DB_DRIVER:com.mysql.cj.jdbc.Driver}
      url: ${COST_LITE_DB_URL}
      username: ${COST_LITE_DB_USERNAME}
      password: ${COST_LITE_DB_PASSWORD}
      hikari:
        pool-name: cost-lite-pool
        minimum-idle: 1
        maximum-pool-size: 10
        connection-timeout: 5000
        initialization-fail-timeout: ${COST_LITE_DB_INITIALIZATION_FAIL_TIMEOUT:5000}
```

Lite 使用命名 Bean `costLiteDataSource`、`costLiteSqlSessionFactory` 和 `costLiteTransactionManager`，不会覆盖业务项目原有的 `spring.datasource`。数据库密码只通过环境变量或配置中心提供，不写入 Git。Starter 会传递 MySQL JDBC 驱动；业务项目无需再复制核心源码。Starter 按 `mode` 选择数据源，不再仅根据 URL 是否为空猜测；分离版和同库版的 SQL 初始化责任也不混淆。

## 4. 自动注册后的接口

Starter 自动把 Core 的 Controller 注册到业务项目当前端口，前端和业务后端访问业务项目自己的：

```text
GET  /cost/lite/health
GET  /cost/lite/bootstrap
GET  /cost/dictionary/options
GET/POST/PUT/DELETE /cost/**
```

`/cost/lite/health` 返回 `service=UP` 和 `database=UP`，且没有 `missingTables`，才表示入口、核心 Bean、Mapper 和当前选定数据库都已就绪；接口会只读检查必要表，不会自动执行 DDL。

嵌入模式不注册 Lite 的独立 `SecurityFilterChain`，业务项目继续负责登录、权限和 CORS。`auth-enabled=false` 只适合本地免登录验收；生产环境应由宿主保护 `/cost/**`，并按需启用管理令牌。

## 5. 同进程验证

当前交付以真实业务系统作为唯一运行宿主；只启动 `ag-system` 即可验证 Starter、Core、Mapper、专用
数据源和 `/cost/**` 接口。仓库内的 `example/`、`server/` 和 `isolated/` 目录属于历史验证或兼容资料，
不参与本分支默认聚合构建，也不应复制到业务系统或与当前 Starter 一起引入。

业务系统验证顺序以宿主项目的 `docs/cost-lite-integration.md` 为准：

```text
GET http://127.0.0.1:9201/cost/lite/health -> 200
databaseProduct                         -> MySQL
databaseName                            -> cost_platform_lite
```

## 6. 版本边界

本分支只维护 Java 17 + Spring Boot 4.1 的同进程嵌入方案。需要 Java 8、Spring Boot 2/3 或非
Spring Boot 的系统，应在轻量源码中另建对应兼容模块和版本，并通过独立制品交付；不能把不同
主版本依赖、HTTP client、隔离重定位包和嵌入 Starter 混入同一个业务系统。后续出现跨系统的
兼容性故障时，先登记到宿主项目的集成故障台账，再决定是否发布新的 Starter 兼容版本。
