# Oracle 同进程嵌入式集成

Java 8 + Spring Boot 2.7.x Servlet 业务项目只引入 `cost-lite-starter-oracle`。计费 Controller、Service、Mapper 和核心规则执行在业务应用同一个 JVM 内运行，不启动单独的 Lite 进程，也不复制 `Oracle/source/`；入口不依赖若依包名。数据源模式必须显式配置为 `dedicated`（分离版）或 `host`（同库版）。

## 1. 制品关系

| 制品 | 职责 | 是否单独启动 |
| --- | --- | --- |
| `cost-lite-core-oracle` | 母体计费实体、Mapper、规则/公式执行、发布和试算核心 | 否，作为依赖加载 |
| `cost-lite-starter-oracle` | Spring Boot 自动配置、Oracle 专用数据源或宿主数据源复用、Mapper 注册和 `/cost/**` 入口 | 否，随业务应用加载 |

业务项目只声明 `cost-lite-starter-oracle`，Maven 会传递引入 Core 和 Oracle JDBC 驱动。Starter 的实现契约与 MySQL Starter 一致，数据库差异由对应 Core、驱动、SQL 和 Oracle 方言适配承担。

## 2. 添加 POM 依赖

```xml
<properties>
    <cost-lite.version>1.0.0</cost-lite.version>
</properties>

<dependency>
    <groupId>com.costplatform.lite</groupId>
    <artifactId>cost-lite-starter-oracle</artifactId>
    <version>${cost-lite.version}</version>
</dependency>
```

本仓库构建并安装本地制品：

```bash
mvn -f Oracle/pom.xml clean install -DskipTests
```

客户项目只引用 Starter，不复制 `Oracle/source/`，不新增计费 Controller、Service、Mapper 或实体。

## 3. 配置计费数据库

分离版不在业务 Schema 初始化任何 `cost_*` 表；独立核算 Schema 的表结构由部署方按 [cost-lite-schema.sql](sql/cost-lite-schema.sql) 预先准备。同库版才把脚本执行到业务 Schema。正式任务、正式结果追溯、重算/告警或 OpenApp 只有在明确启用时，才继续执行 [cost-lite-formal-schema.sql](sql/cost-lite-formal-schema.sql)。

### 3.1 复用宿主业务数据源

仅同库版把 SQL 执行到宿主业务 Schema，并明确设置 `cost.lite.datasource.mode=host`；Starter 复用 Bean 名称为 `dataSource` 的宿主数据源：

```yaml
cost:
  lite:
    embedded:
      enabled: true
    datasource:
      mode: host
      host-bean-name: ${COST_LITE_HOST_DATASOURCE_BEAN:dataSource}
```

宿主使用其他数据源 Bean 名称时只调整 `host-bean-name`。

### 3.2 使用专用计费库

分离版只连接已准备好的专用 Oracle Schema，业务 Schema 不执行计费表 DDL；按下面配置专用 Oracle 连接：

在业务项目外部配置文件或配置中心加入：

```yaml
cost:
  lite:
    embedded:
      enabled: true
    auth-enabled: false
    operator: ${COST_LITE_OPERATOR:lite-admin}
    datasource:
      mode: dedicated
      driver-class-name: ${COST_LITE_DB_DRIVER:oracle.jdbc.OracleDriver}
      url: ${COST_LITE_DB_URL}
      username: ${COST_LITE_DB_USERNAME}
      password: ${COST_LITE_DB_PASSWORD}
      hikari:
        pool-name: cost-lite-oracle-pool
        minimum-idle: 1
        maximum-pool-size: 10
        connection-timeout: 5000
        initialization-fail-timeout: ${COST_LITE_DB_INITIALIZATION_FAIL_TIMEOUT:5000}
        validation-timeout: 3000
```

数据库地址、账号和密码属于宿主外部配置，不写入 Jar、前端或 Git。Starter 使用命名 Bean `costLiteDataSource`、`costLiteSqlSessionFactory` 和 `costLiteTransactionManager`，不会覆盖业务项目自己的 `spring.datasource`。Starter 按 `mode` 选择数据源，不再仅根据 URL 是否为空猜测；健康接口只读检查当前数据源的必要表。

## 4. 入口和前端

Starter 会在业务项目当前端口注册母体兼容路径：

```text
GET  /cost/lite/health
GET  /cost/lite/bootstrap
GET  /cost/dictionary/options
GET/POST/PUT/DELETE /cost/**
```

工作台使用 `Front/src`，嵌入 Starter 时选择母体兼容路由：

```ts
const costLiteApi = createCostLiteApi(
  (config) => request(config),
  { basePath: "/cost", routeMode: "runtime" },
);
```

宿主只增加一个菜单和页面包装组件。生产环境继续由宿主登录、权限和网关保护 `/cost/**`；`auth-enabled=false` 仅用于本地验收。

## 5. 验收顺序

```text
1. GET  /cost/lite/health，确认 service=UP、database=UP、databaseProduct=Oracle。
2. GET  /cost/dictionary/options，确认中文字典来自当前计费库。
3. 新增场景、费目、要素、条件组、规则和公式。
4. 通过发布前检查并发布一个版本。
5. 用真实业务 JSON 执行单条和批量试算。
6. 在试算日志查看 input、variables、explain、result.amountTotal 和 result.feeResults。
7. 提交错误 JSON，确认失败日志仍然留存。
```

工作台只开放配置、发布检查、单条试算、批量试算、试算日志和试算结果；同步计费由业务后端调用，不开放正式任务和正式结果页面。正式接口和场景级/指定费目调用示例见 [API.md](API.md)。

## 6. 老项目兼容方式

传统 SSM、非 Spring Boot 宿主使用 `Oracle/backend-integration/cost-lite-client` 直接调用独立 Jar；Spring Boot 2.7 但无法引入同进程 Starter，才使用同目录的 HTTP Starter 代理。需要独立进程隔离的项目使用 `Oracle/runtime/cost-lite-server-1.0.0.jar`，不复制核心源码；前端直连 Jar 使用 `routeMode: "runtime"`，经过 HTTP Starter 才使用 `routeMode: "proxy"`。
